package dev.pocketpulse.monitor;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Used only by one background HandlerThread; never launches shell commands. */
final class TelemetrySampler {
    static final class Sample {
        float cpu = Float.NaN, gpu = Float.NaN, ram = Float.NaN, selfCpu = Float.NaN;
        long totalRam, availableRam, appPssKb;
        int battery = -1, thermal = -1;
        float batteryC = Float.NaN, collectionMs;
        boolean charging, cpuReadable, gpuReadable;
        String cpuClock = "Clock unavailable", gpuClock = "Clock unavailable";
        String cpuSource = "Restricted by this device", gpuSource = "Not exposed by this device";
    }

    /** A failed endpoint is disabled, avoiding denied reads on every refresh. */
    private static final class Node {
        final String path;
        boolean enabled = true;
        Node(String path) { this.path = path; }
        String read() {
            if (!enabled) return null;
            try (BufferedReader reader = new BufferedReader(new FileReader(path), 256)) {
                String value = reader.readLine();
                if (value == null) enabled = false;
                return value;
            } catch (Exception e) { enabled = false; return null; }
        }
    }

    private final Context context;
    private final ActivityManager activityManager;
    private final PowerManager powerManager;
    private final ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
    private final Node cpuStat = new Node("/proc/stat");
    private final List<Node> cpuClocks = new ArrayList<>();
    private Node gpuLoad, gpuClock;
    private boolean gpuRatio;
    private long[] previousCpu;
    private long previousWall, previousProcess, lastPssAt, pssKb;
    private boolean discovered;

    TelemetrySampler(Context context) {
        this.context = context.getApplicationContext();
        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    void resetBaseline() { previousCpu = null; previousWall = 0; previousProcess = 0; }

    private Node firstNode(String... paths) {
        for (String path : paths) {
            Node node = new Node(path);
            if (node.read() != null) return node;
        }
        return null;
    }

    private void discover() {
        File[] policies = new File("/sys/devices/system/cpu/cpufreq").listFiles();
        if (policies != null) for (File policy : policies) {
            if (policy.getName().matches("policy[0-9]+") && cpuClocks.size() < 32) {
                Node node = firstNode(policy + "/scaling_cur_freq", policy + "/cpuinfo_cur_freq");
                if (node != null) cpuClocks.add(node);
            }
        }
        if (cpuClocks.isEmpty()) for (int i = 0; i < Math.min(32, Runtime.getRuntime().availableProcessors()); i++) {
            Node node = firstNode("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (node != null) cpuClocks.add(node);
        }
        gpuLoad = firstNode("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
        if (gpuLoad == null) {
            gpuLoad = firstNode("/sys/class/kgsl/kgsl-3d0/gpubusy");
            gpuRatio = true;
        }
        // Frequency units are explicit: Hz for these two Qualcomm/devfreq endpoints.
        gpuClock = firstNode("/sys/class/kgsl/kgsl-3d0/gpuclk", "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq");
        discovered = true;
    }

    Sample sample() {
        long start = SystemClock.elapsedRealtimeNanos();
        if (!discovered) discover();
        Sample s = new Sample();
        long wall = SystemClock.elapsedRealtime(), process = Process.getElapsedCpuTime();
        if (previousWall != 0) s.selfCpu = MetricMath.processCpu(process - previousProcess, wall - previousWall);
        previousWall = wall;
        previousProcess = process;

        long[] now = MetricMath.cpuTimes(cpuStat.read());
        s.cpuReadable = now != null;
        if (now != null) {
            s.cpu = MetricMath.cpuPercent(previousCpu, now);
            s.cpuSource = "/proc/stat";
        }
        previousCpu = now;
        long min = Long.MAX_VALUE, max = 0;
        for (Node clock : cpuClocks) {
            long khz = positiveLong(clock.read());
            if (khz > 0) { min = Math.min(min, khz); max = Math.max(max, khz); }
        }
        if (max > 0) s.cpuClock = min == max ? format("%.2f GHz", max / 1_000_000f) : format("%.2f–%.2f GHz", min / 1_000_000f, max / 1_000_000f);
        if (gpuLoad != null) {
            String value = gpuLoad.read();
            s.gpu = gpuRatio ? MetricMath.gpuBusy(value) : MetricMath.percent(value);
            s.gpuReadable = gpuLoad.enabled;
            s.gpuSource = gpuLoad.path;
        }
        if (gpuClock != null) {
            long hz = positiveLong(gpuClock.read());
            if (hz > 0) s.gpuClock = format("%.0f MHz", hz / 1_000_000f);
        }
        activityManager.getMemoryInfo(memory);
        s.totalRam = memory.totalMem;
        s.availableRam = memory.availMem;
        s.ram = MetricMath.usedRamPercent(s.totalRam, s.availableRam);

        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) s.battery = Math.round(100f * level / scale);
            int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (temperature != Integer.MIN_VALUE && temperature >= -400 && temperature <= 1200) s.batteryC = temperature / 10f;
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            s.charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        }
        if (Build.VERSION.SDK_INT >= 29) s.thermal = powerManager.getCurrentThermalStatus();
        if (lastPssAt == 0 || wall - lastPssAt >= 15_000) {
            pssKb = Debug.getPss();
            lastPssAt = wall;
        }
        s.appPssKb = pssKb;
        s.collectionMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000f;
        return s;
    }

    private static long positiveLong(String value) {
        if (value == null) return -1;
        try { return Long.parseLong(value.trim()); } catch (NumberFormatException e) { return -1; }
    }
    static String format(String template, Object... args) { return String.format(Locale.US, template, args); }
}
