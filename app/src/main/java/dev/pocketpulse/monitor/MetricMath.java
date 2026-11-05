package dev.pocketpulse.monitor;

/** Pure calculations shared by telemetry and the standalone regression checks. */
public final class MetricMath {
    private MetricMath() {}

    public static long[] cpuTimes(String line) {
        if (line == null) return null;
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 5 || !"cpu".equals(parts[0])) return null;
        try {
            long total = 0;
            // guest/guest_nice are already included in user/nice: never count them twice.
            for (int i = 1; i < Math.min(parts.length, 9); i++) total += Long.parseLong(parts[i]);
            long idle = Long.parseLong(parts[4]) + (parts.length > 5 ? Long.parseLong(parts[5]) : 0);
            return new long[]{total, idle};
        } catch (NumberFormatException e) { return null; }
    }

    public static float cpuPercent(long[] before, long[] after) {
        if (before == null || after == null) return Float.NaN;
        long total = after[0] - before[0], idle = after[1] - before[1];
        if (total <= 0 || idle < 0 || idle > total) return Float.NaN;
        return 100f * (total - idle) / total;
    }

    public static float gpuBusy(String value) {
        if (value == null) return Float.NaN;
        try {
            String[] parts = value.trim().split("\\s+");
            if (parts.length != 2) return Float.NaN;
            long busy = Long.parseLong(parts[0]), total = Long.parseLong(parts[1]);
            if (busy < 0 || total <= 0 || busy > total) return Float.NaN;
            return 100f * busy / total;
        } catch (NumberFormatException e) { return Float.NaN; }
    }

    public static float percent(String value) {
        if (value == null) return Float.NaN;
        try {
            float result = Float.parseFloat(value.trim());
            return Float.isFinite(result) && result >= 0 && result <= 100 ? result : Float.NaN;
        } catch (NumberFormatException e) { return Float.NaN; }
    }

    public static float usedRamPercent(long total, long available) {
        if (total <= 0 || available < 0 || available > total) return Float.NaN;
        return (float) ((total - available) * 100.0 / total);
    }

    public static float processCpu(long cpuMs, long wallMs) {
        return wallMs <= 0 || cpuMs < 0 ? Float.NaN : 100f * cpuMs / wallMs;
    }

    public static final class History {
        private final float[] values;
        private int next, size;
        public History(int capacity) { values = new float[capacity]; }
        public void add(float value) {
            values[next] = value;
            next = (next + 1) % values.length;
            size = Math.min(size + 1, values.length);
        }
        public void clear() { next = 0; size = 0; }
        public int size() { return size; }
        public float get(int i) { return values[(next - size + i + values.length) % values.length]; }
    }
}
