package dev.pocketpulse.monitor;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int BG = 0xFF000000, CARD = 0xFF000000, TEXT = 0xFFF0F4F8;
    private static final int MUTED = 0xFFA4B2C3, TEAL = 0xFF5DE4C7, CORAL = 0xFFFFB08D, PURPLE = 0xFFB8A3FF;
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler worker;
    private TelemetrySampler sampler;
    private TelemetrySampler.Sample last;
    private SharedPreferences preferences;
    private LinearLayout root;
    private GridLayout grid;
    private TextView status, device, footer;
    private Button intervalButton, pauseButton;
    private Card cpu, gpu, ram, battery, thermal, self;
    private Card[] cards;
    private volatile boolean visible, paused, screenOn = true;
    private volatile int generation, interval = 2000;
    private boolean compact, keepAwake = true, dim;
    private int columns = -1;
    private int arrangedCardHeight = -1;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            screenOn = !Intent.ACTION_SCREEN_OFF.equals(intent.getAction());
            restartSampling(true);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        interval = preferences.getInt("interval", 2000);
        if (interval != 1000 && interval != 2000 && interval != 5000 && interval != 10000) interval = 2000;
        paused = state != null ? state.getBoolean("paused") : getIntent().getBooleanExtra("paused", false);
        keepAwake = preferences.getBoolean("awake", true);
        dim = preferences.getBoolean("dim", false);
        sampler = new TelemetrySampler(this);
        thread = new HandlerThread("PocketPulse-sampler", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        worker = new Handler(thread.getLooper());
        createUi();
        applyScreenOptions();
    }

    private void createUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), 0, dp(16), 0);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top; bottom = bars.bottom; left = bars.left; right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            root.setPadding(dp(16) + left, dp(8) + top, dp(16) + right, bottom);
            return insets;
        });
        root.requestApplyInsets();

        LinearLayout heading = row();
        TextView title = text("POCKET PULSE", 21, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(.06f);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1));
        status = text(paused ? "PAUSED" : "● LIVE", 11, TEAL);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(4), dp(12), dp(4));
        status.setBackground(background(0xFF17322F, 20, 0));
        heading.addView(status, new LinearLayout.LayoutParams(-2, dp(28)));
        root.addView(heading);
        device = text(Build.MANUFACTURER + " " + Build.MODEL, 12, MUTED);
        device.setSingleLine(true);
        device.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(device, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout controls = row();
        intervalButton = button("2s refresh", this::chooseInterval);
        pauseButton = button(paused ? "Resume" : "Pause", () -> {
            paused = !paused;
            updateControls();
            restartSampling(true);
        });
        Button displayButton = button("Display", this::chooseDisplay);
        Button moreButton = button("Options", this::showOptions);
        Button[] buttons = {intervalButton, pauseButton, displayButton, moreButton};
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) params.setMarginStart(dp(6));
            controls.addView(buttons[i], params);
        }
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(-1, -2);
        controlParams.bottomMargin = dp(10);
        root.addView(controls, controlParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        grid = new GridLayout(this);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        cpu = new Card("CPU", CORAL, true);
        gpu = new Card("GPU", PURPLE, true);
        ram = new Card("SYSTEM RAM", TEAL, true);
        battery = new Card("BATTERY", 0xFFE5D38D, false);
        thermal = new Card("THERMAL STATUS", 0xFF95C4F2, false);
        self = new Card("THIS APP", MUTED, false);
        cards = new Card[]{cpu, gpu, ram, battery, thermal, self};
        for (Card card : cards) grid.addView(card);
        cpu.set("—", "Checking availability", "System utilization");
        gpu.set("—", "Checking availability", "System utilization");
        ram.set("—", "System memory", "Used / total");
        battery.set("—", "Battery temperature", "Waiting for first sample");
        thermal.set("—", "Android thermal status", "Waiting for first sample");
        self.set("—", "Pocket Pulse overhead", "Waiting for first sample");
        footer = text("Local only · No ads · No background service", 11, MUTED);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(30)));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> arrangeCards());
        updateControls();
    }

    private void arrangeCards() {
        int width = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
        if (width <= 0) return;
        int nextColumns = width >= dp(540) ? 3 : width >= dp(290) ? 2 : 1;
        int viewport = ((View) grid.getParent()).getHeight();
        if (viewport <= 0) return;
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        int rows = (cards.length + nextColumns - 1) / nextColumns;
        int rowSpace = viewport / rows - dp(10);
        boolean nextCompact = preferences.getBoolean("compact", false) || rowSpace < dp(145 * fontScale);
        int nextHeight = Math.max(dp(110 * fontScale), Math.min(dp((nextCompact ? 133 : 184) * fontScale), rowSpace));
        if (columns == nextColumns && compact == nextCompact && arrangedCardHeight == nextHeight) return;
        columns = nextColumns;
        compact = nextCompact;
        arrangedCardHeight = nextHeight;
        // Remove before changing the column count: old GridLayout specs may exceed the new count.
        grid.removeAllViews();
        grid.setColumnCount(columns);
        for (int i = 0; i < cards.length; i++) {
            Card card = cards[i];
            card.chart.setVisibility(card.hasChart && !compact ? View.VISIBLE : View.GONE);
            card.value.setTextSize(compact ? 25 : 31);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(i / columns), GridLayout.spec(i % columns, 1f));
            params.width = 0;
            params.height = nextHeight;
            params.setMargins(i % columns == 0 ? 0 : dp(5), 0, i % columns == columns - 1 ? 0 : dp(5), dp(10));
            grid.addView(card, params);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        screenOn = ((PowerManager) getSystemService(POWER_SERVICE)).isInteractive();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, filter);
        updateDeviceLabel();
        restartSampling(true);
    }
    // A game on another display can pause this Activity while it stays visible.
    // Stop sampling onStop, not onPause, so the lower screen keeps updating.
    @Override protected void onStop() {
        visible = false;
        unregisterReceiver(screenReceiver);
        generation++;
        worker.removeCallbacksAndMessages(null);
        main.removeCallbacksAndMessages(null);
        super.onStop();
    }
    @Override protected void onDestroy() {
        generation++;
        worker.removeCallbacksAndMessages(null);
        main.removeCallbacksAndMessages(null);
        thread.quitSafely();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("paused", paused);
        super.onSaveInstanceState(state);
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        columns = -1;
        root.requestApplyInsets();
        root.requestLayout();
        updateDeviceLabel();
    }

    private void restartSampling(boolean clearHistory) {
        final int token = ++generation;
        worker.removeCallbacksAndMessages(null);
        if (clearHistory) for (Card card : cards) card.chart.clear();
        if (!visible || paused || !screenOn) return;
        worker.post(() -> {
            sampler.resetBaseline();
            if (token == generation) collect(token);
        });
    }

    private void collect(int token) {
        if (!visible || paused || !screenOn || token != generation) return;
        try {
            TelemetrySampler.Sample sample = sampler.sample();
            main.post(() -> {
                if (visible && !paused && screenOn && token == generation) render(sample);
            });
        } catch (RuntimeException e) {
            main.post(() -> {
                if (visible && token == generation) footer.setText("A reading failed · Retrying next refresh");
            });
        }
        if (visible && !paused && screenOn && token == generation) worker.postDelayed(() -> collect(token), interval);
    }

    private void render(TelemetrySampler.Sample s) {
        last = s;
        cpu.set(percent(s.cpu), s.cpuClock, s.cpuReadable ? "System load · all cores" : "Usage restricted by Android");
        gpu.set(percent(s.gpu), s.gpuClock, s.gpuReadable ? "Device GPU activity" : "Usage not exposed by device");
        ram.set(percent(s.ram), f("%.1f / %.1f GiB used", (s.totalRam - s.availableRam) / 1073741824.0, s.totalRam / 1073741824.0), f("%.1f GiB available", s.availableRam / 1073741824.0));
        battery.set(s.battery >= 0 ? s.battery + "%" : "—", Float.isFinite(s.batteryC) ? f("%.1f °C · battery", s.batteryC) : "Temperature unavailable", s.charging ? "Charging / external power" : "On battery");
        String[] thermalNames = {"None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown"};
        thermal.set(s.thermal >= 0 && s.thermal < thermalNames.length ? thermalNames[s.thermal] : "—", s.thermal >= 0 ? "Throttling severity reported" : "Requires Android 10+", "Firmware report · not CPU °C");
        thermal.value.setTextColor(s.thermal >= 3 ? CORAL : 0xFF95C4F2);
        self.set(Float.isFinite(s.selfCpu) ? f("%.1f%% CPU", s.selfCpu) : "Warming up", f("%.1f MiB RAM · PSS", s.appPssKb / 1024.0), "CPU relative to one core");
        self.value.setTextSize(compact ? 22 : 27);
        cpu.chart.add(s.cpu); gpu.chart.add(s.gpu); ram.chart.add(s.ram);
        footer.setText(f("%ds refresh · %.1f ms to collect · 90-sample history", interval / 1000, s.collectionMs));
    }

    private void updateControls() {
        intervalButton.setText(interval / 1000 + "s refresh");
        pauseButton.setText(paused ? "Resume" : "Pause");
        status.setText(paused ? "PAUSED" : "● LIVE");
        status.setTextColor(paused ? MUTED : TEAL);
        if (paused) footer.setText("Paused · Readings frozen · Sampling stopped");
    }

    private void chooseInterval() {
        final int[] times = {1000, 2000, 5000, 10000};
        final String[] names = {"1 second · responsive", "2 seconds · balanced", "5 seconds · low power", "10 seconds · lowest activity"};
        int selected = 1;
        for (int i = 0; i < times.length; i++) if (times[i] == interval) selected = i;
        new AlertDialog.Builder(this).setTitle("Refresh interval").setSingleChoiceItems(names, selected, (dialog, which) -> {
            interval = times[which];
            preferences.edit().putInt("interval", interval).apply();
            updateControls();
            restartSampling(true);
            dialog.dismiss();
        }).setNegativeButton("Close", null).show();
    }

    private void chooseDisplay() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display current = getWindowManager().getDefaultDisplay();
        List<Display> candidates = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Display display : manager.getDisplays()) {
            if ((display.getFlags() & Display.FLAG_PRIVATE) != 0 && display.getDisplayId() != current.getDisplayId()) continue;
            candidates.add(display);
            names.add((display.getDisplayId() == current.getDisplayId() ? "✓ " : "") + display.getName() + " · " + display.getDisplayId());
        }
        if (candidates.size() <= 1) {
            new AlertDialog.Builder(this).setTitle("One display detected").setMessage("Connect or enable the second screen, then open Display again. Some handhelds require their launcher or screen-switch shortcut to move an app. Pocket Pulse can also run in split screen.").setPositiveButton("Got it", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Move Pocket Pulse to…").setItems(names.toArray(new String[0]), (dialog, which) -> {
            Display display = candidates.get(which);
            if (display.getDisplayId() == current.getDisplayId()) return;
            Intent intent = new Intent(this, MainActivity.class).putExtra("paused", paused);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            ActivityManager activities = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= 29 && !activities.isActivityStartAllowedOnDisplay(this, display.getDisplayId(), intent)) {
                displayMoveFailed(); return;
            }
            try {
                startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(display.getDisplayId()).toBundle());
                finish();
            } catch (RuntimeException e) { displayMoveFailed(); }
        }).setNegativeButton("Cancel", null).show();
    }

    private void displayMoveFailed() {
        new AlertDialog.Builder(this).setTitle("Use the handheld’s screen controls").setMessage("This firmware did not allow Pocket Pulse to launch on that display. Move it with your device’s launcher, app switcher, or screen-switch shortcut.").setPositiveButton("Got it", null).show();
    }

    private void showOptions() {
        String[] labels = {"Keep this screen awake", "Dim this screen to 25%", "Compact cards"};
        boolean[] checked = {keepAwake, dim, preferences.getBoolean("compact", false)};
        new AlertDialog.Builder(this).setTitle("Pocket Pulse").setMultiChoiceItems(labels, checked, (dialog, which, value) -> {
            if (which == 0) { keepAwake = value; preferences.edit().putBoolean("awake", value).apply(); }
            if (which == 1) { dim = value; preferences.edit().putBoolean("dim", value).apply(); }
            if (which == 2) { preferences.edit().putBoolean("compact", value).apply(); columns = -1; arrangeCards(); }
            applyScreenOptions();
        }).setPositiveButton("Done", null).setNeutralButton("About / readings", (dialog, which) -> showAbout()).show();
    }

    private void applyScreenOptions() {
        if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = dim ? .25f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(attributes);
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("Pocket Pulse · 0.1.0").setMessage(
            "A quiet dashboard for a second screen.\n\n" +
            "CPU and GPU: system-wide readings only where firmware permits them. A dash means unavailable or waiting for a valid sample; clocks are not utilization.\n\n" +
            "RAM: total minus Android’s available memory. Includes the system, games, apps, and some cache; it is not a game’s RAM allocation.\n\n" +
            "Thermals: Android’s reported severity, not a chip thermometer. ‘None’ means no throttling reported; firmware support varies. Battery °C is not CPU/GPU temperature.\n\n" +
            "This app: CPU relative to one core, and process PSS sampled every 15 seconds. Collection time covers telemetry reads, not drawing or total game impact.\n\n" +
            "No root, network permission, telemetry, ads, overlay, boot task, wake lock, or background service. Reads stop when hidden, paused, or the screen turns off.\n\n" +
            "Move the app to the lower display, then open your game above. The display picker depends on firmware. Game FPS and per-game CPU/GPU/RAM are not available in this no-setup version."
        ).setPositiveButton("Close", null).setNeutralButton("Copy device report", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Pocket Pulse device report", deviceReport()));
            Toast.makeText(this, "Device report copied", Toast.LENGTH_SHORT).show();
        }).show();
    }

    private String deviceReport() {
        String report = "Pocket Pulse 0.1.0\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\nRefresh: " + interval + " ms\n";
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        for (Display display : manager.getDisplays()) report += "Display " + display.getDisplayId() + ": " + display.getName() + "\n";
        if (last != null) report += "CPU source: " + last.cpuSource + "\nCPU load: " + percent(last.cpu) + "\nCPU clock: " + last.cpuClock + "\nGPU source: " + last.gpuSource + "\nGPU load: " + percent(last.gpu) + "\nGPU clock: " + last.gpuClock + "\nRAM used: " + percent(last.ram) + "\nThermal status: " + last.thermal + "\nApp PSS: " + last.appPssKb + " KiB\nApp CPU: " + last.selfCpu + "% of one core\n";
        return report;
    }

    private void updateDeviceLabel() {
        Display display = getWindowManager().getDefaultDisplay();
        device.setText(Build.MODEL + "  ·  Display " + display.getDisplayId() + "  ·  Android " + Build.VERSION.RELEASE);
    }
    private final class Card extends LinearLayout {
        final TextView value, detail, note;
        final Sparkline chart;
        final boolean hasChart;
        Card(String label, int color, boolean hasChart) {
            super(MainActivity.this);
            this.hasChart = hasChart;
            setOrientation(VERTICAL);
            setPadding(dp(13), dp(12), dp(13), dp(10));
            setBackground(background(CARD, 16, 0xFF253140));
            TextView title = text(label, 10, color);
            title.setLetterSpacing(.09f);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            addView(title);
            value = text("—", 31, color);
            value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            value.setSingleLine(true);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(value, new LinearLayout.LayoutParams(-1, -2));
            detail = text("", 12, TEXT);
            detail.setMaxLines(2);
            addView(detail, new LinearLayout.LayoutParams(-1, -2));
            note = text("", 10, MUTED);
            note.setMaxLines(2);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
            noteParams.topMargin = dp(3);
            addView(note, noteParams);
            chart = new Sparkline(MainActivity.this, color);
            LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, 0, 1);
            chartParams.topMargin = dp(8);
            addView(chart, chartParams);
            chart.setVisibility(hasChart ? View.VISIBLE : View.GONE);
        }
        void set(String number, String detailText, String noteText) {
            value.setText(number); detail.setText(detailText); note.setText(noteText);
        }
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label); view.setTextColor(TEXT); view.setTextSize(11); view.setAllCaps(false);
        view.setMinWidth(0); view.setMinimumWidth(0);
        view.setPadding(dp(4), 0, dp(4), 0);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, background(0xFF294C48, 12, TEAL));
        states.addState(new int[]{android.R.attr.state_pressed}, background(0xFF294C48, 12, TEAL));
        states.addState(new int[]{}, background(BG, 12, 0xFF2A394B));
        view.setBackground(states);
        view.setOnClickListener(v -> action.run());
        return view;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private GradientDrawable background(int color, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String percent(float value) { return Float.isFinite(value) ? f("%.0f%%", value) : "—"; }
    private static String f(String template, Object... args) { return TelemetrySampler.format(template, args); }
}
