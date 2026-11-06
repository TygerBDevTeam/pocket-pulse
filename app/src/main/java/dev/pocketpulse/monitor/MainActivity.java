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
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
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
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
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
    private LinearLayout headingRow, footerPanel;
    private int chromeHeight = -1;
    private GridLayout grid;
    private ScrollView dashboardScroll;
    private TextView status, device, footer;
    private Button intervalButton, pauseButton, muteButton;
    private AudioManager audio;
    private Card cpu, gpu, ram, battery, optional, fan, power, self;
    private LinearLayout storageBody;
    private View storageDivider;
    private TextView storageHint;
    private StorageView internalStorageView, sdStorageView;
    private boolean showThermal;
    private int gpuCardMode;
    private UpdateChecker updateChecker;
    private Thread updateThread;
    private AlertDialog updateDialog;
    private int updateToken;
    private Card[] cards;
    private volatile boolean visible, paused, screenOn = true;
    private volatile int generation, interval = 2000;
    private boolean compact, keepAwake = true, dim;
    private int columns = -1;
    private int arrangedCardHeight = -1;
    private String arrangedGeometry;
    private DashboardLayout dashboardLayout;
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
        showThermal = preferences.getBoolean("thermalCard", false);
        gpuCardMode = preferences.getInt("gpuCard", 0);
        if (gpuCardMode < 0 || gpuCardMode > 2) gpuCardMode = 0;
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
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
        headingRow = heading;
        device = text(Build.MANUFACTURER + " " + Build.MODEL, 12, MUTED);
        device.setSingleLine(true);
        device.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(device, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout volume = row();
        Button lower = button("Volume −", () -> adjustVolume(AudioManager.ADJUST_LOWER));
        Button raise = button("Volume +", () -> adjustVolume(AudioManager.ADJUST_RAISE));
        muteButton = button("Mute", () -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE));
        lower.setContentDescription("Media volume down"); raise.setContentDescription("Media volume up");
        Button[] volumeButtons = {lower, raise, muteButton};
        for (int i = 0; i < volumeButtons.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) params.setMarginStart(dp(6));
            volume.addView(volumeButtons[i], params);
        }
        LinearLayout.LayoutParams volumeParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        volumeParams.setMarginStart(dp(6));
        heading.addView(volume, volumeParams);
        root.addView(heading);

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
        dashboardScroll = scroll;
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        grid = new GridLayout(this);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> arrangeCards());
        cpu = new Card("CPU", CORAL, true);
        gpu = new Card("GPU", PURPLE, true);
        ram = new Card("SYSTEM RAM", TEAL, true);
        battery = new Card("BATTERY", 0xFFE5D38D, false);
        optional = new Card("STORAGE", 0xFF95C4F2, false);
        storageBody = new LinearLayout(this);
        storageBody.setOrientation(LinearLayout.VERTICAL);
        storageHint = text("Free / total", 10, MUTED);
        storageHint.setVisibility(View.GONE);
        storageBody.addView(storageHint);
        internalStorageView = new StorageView("Internal");
        sdStorageView = new StorageView("microSD");
        storageBody.addView(internalStorageView);
        View divider = new View(this);
        storageDivider = divider;
        divider.setBackgroundColor(0xFF253140);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(5); dividerParams.bottomMargin = dp(5);
        storageBody.addView(divider, dividerParams);
        storageBody.addView(sdStorageView);
        optional.addView(storageBody);
        fan = new Card("FAN SPEED", 0xFF95C4F2, false);
        power = new Card("BATTERY DRAW", 0xFFE5D38D, false);
        self = new Card("THIS APP", MUTED, false);
        cards = new Card[]{cpu, gpu, ram, battery, optional, fan, power, self};
        for (Card card : cards) grid.addView(card);
        cpu.set("—", "Checking availability", "System utilization");
        gpu.set("—", "Checking availability", "System utilization");
        ram.set("—", "System memory", "Used / total");
        battery.set("—", "Battery temperature", "Waiting for first sample");
        optional.set("—", "Android thermal status", "Waiting for first sample");
        fan.set("—", "Checking fan RPM", "Device support varies");
        power.set("—", "Battery power", "Checking current + voltage");
        self.set("—", "Pocket Pulse overhead", "Waiting for first sample");
        LinearLayout footerRow = row();
        footerPanel = footerRow;
        footerRow.addView(text("Pocket Pulse", 11, MUTED), new LinearLayout.LayoutParams(-2, -1));
        footer = text("Waiting for readings", 11, MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setMaxLines(2);
        footerRow.addView(footer, new LinearLayout.LayoutParams(0, -1, 1));
        status = text(paused ? "Paused" : "● Live", 11, TEAL);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footerRow.addView(status, new LinearLayout.LayoutParams(-2, -1));
        root.addView(footerRow, new LinearLayout.LayoutParams(-1, dp(34)));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> arrangeCards());
        updateOptionalCard();
        updateControls();
    }

    private void arrangeCards() {
        int width = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
        if (width <= 0) return;
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        boolean shortWindow = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom() < dp(430 * fontScale);
        int nextChromeHeight = dp(shortWindow ? 44 : 48);
        if (chromeHeight != nextChromeHeight) {
            chromeHeight = nextChromeHeight;
            ViewGroup.LayoutParams header = headingRow.getLayoutParams(); header.height = chromeHeight; headingRow.setLayoutParams(header);
            for (int i = 0; i < headingRow.getChildCount(); i++) {
                View child = headingRow.getChildAt(i);
                ViewGroup.LayoutParams params = child.getLayoutParams(); params.height = chromeHeight; child.setLayoutParams(params);
            }
            ViewGroup.LayoutParams footerParams = footerPanel.getLayoutParams(); footerParams.height = dp(shortWindow ? 30 : 34); footerPanel.setLayoutParams(footerParams);
        }
        int viewport = ((View) grid.getParent()).getHeight();
        if (viewport <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        boolean autoFit = preferences.getBoolean("autoFit", true);
        boolean forceCompact = preferences.getBoolean("compact", false);
        String geometry = width + ":" + viewport + ":" + density + ":" + fontScale + ":" + autoFit + ":" + forceCompact + ":" + showThermal + ":" + gpuCardMode;
        if (geometry.equals(arrangedGeometry)) return;
        boolean landscape = width > root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
        int nextColumns = DashboardLayout.columns(width, density, fontScale, landscape, autoFit);
        int[] minimumHeights = new int[4];
        for (int profile = 0; profile < minimumHeights.length; profile++) {
            applyCardProfile(profile);
            int needed = 0;
            for (Card card : cards) needed = Math.max(needed, card.requiredHeight(profile));
            minimumHeights[profile] = needed;
        }
        // Preserve the existing full-size look on screens with enough space.
        minimumHeights[0] = Math.max(minimumHeights[0], dp(170 * fontScale));
        dashboardLayout = DashboardLayout.choose(width, viewport, density, nextColumns, autoFit, forceCompact, minimumHeights);
        applyCardProfile(dashboardLayout.profile);
        columns = dashboardLayout.columns;
        compact = dashboardLayout.profile > 0;
        arrangedCardHeight = dashboardLayout.cardHeight;
        arrangedGeometry = geometry;
        // Remove before changing the column count: old GridLayout specs may exceed the new count.
        grid.removeAllViews();
        grid.setColumnCount(columns);
        for (int i = 0; i < cards.length; i++) {
            Card card = cards[i];
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(i / columns), GridLayout.spec(i % columns, 1f));
            params.width = 0;
            params.height = arrangedCardHeight;
            int gap = dashboardLayout.gap;
            params.setMargins(i % columns == 0 ? 0 : gap / 2, 0,
                    i % columns == columns - 1 ? 0 : gap - gap / 2,
                    i / columns == dashboardLayout.rows - 1 ? 0 : gap);
            grid.addView(card, params);
        }
        if (!dashboardLayout.scrolling) dashboardScroll.scrollTo(0, 0);
        if (gpuCardMode != 0) renderGpuCard(last);
    }

    private void applyCardProfile(int profile) {
        boolean dense = profile >= 2;
        for (Card card : cards) {
            card.setPadding(dp(dense ? 8 : 13), dp(profile == 3 ? 3 : dense ? 6 : 12), dp(dense ? 8 : 13), dp(profile == 3 ? 3 : dense ? 6 : 10));
            card.value.setTextSize(card == self ? (profile == 0 ? 25 : profile == 1 ? 20 : 18) : (profile == 0 ? 31 : profile == 1 ? 25 : 22));
            card.detail.setTextSize(dense ? 11 : 12);
            card.note.setVisibility(profile == 3 ? View.GONE : View.VISIBLE);
            card.chart.setVisibility(card.hasChart && (card != gpu || gpuCardMode == 0) && profile == 0 ? View.VISIBLE : View.GONE);
            card.setClickable(dense); card.setFocusable(dense);
        }
        storageHint.setVisibility(profile == 3 ? View.VISIBLE : View.GONE);
        internalStorageView.applyProfile(profile);
        sdStorageView.applyProfile(profile);
        LinearLayout.LayoutParams divider = (LinearLayout.LayoutParams) storageDivider.getLayoutParams();
        divider.topMargin = dp(profile == 3 ? 1 : dense ? 2 : 5);
        divider.bottomMargin = divider.topMargin;
        storageDivider.setLayoutParams(divider);
        optional.value.setVisibility(showThermal ? View.VISIBLE : View.GONE);
        optional.detail.setVisibility(showThermal ? View.VISIBLE : View.GONE);
        optional.note.setVisibility(showThermal && profile != 3 ? View.VISIBLE : View.GONE);
    }

    private void showReading(Card card) {
        String message;
        if (card == optional && !showThermal) {
            message = last == null ? "Waiting for readings" : "Internal: " + storageSummary(last.internalStorage) + "\n\nmicroSD: "
                    + (last.sdStorage == null ? last.sdStatus : storageSummary(last.sdStorage));
        } else message = card.value.getText() + "\n" + card.detail.getText() + "\n\n" + card.note.getText();
        new AlertDialog.Builder(this).setTitle(card.title.getText()).setMessage(message).setPositiveButton("Close", null).show();
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
        updateVolumeButton();
        restartSampling(true);
    }
    // A game on another display can pause this Activity while it stays visible.
    // Stop sampling onStop, not onPause, so the lower screen keeps updating.
    @Override protected void onStop() {
        visible = false;
        cancelUpdate();
        unregisterReceiver(screenReceiver);
        generation++;
        worker.removeCallbacksAndMessages(null);
        main.removeCallbacksAndMessages(null);
        super.onStop();
    }
    @Override protected void onDestroy() {
        cancelUpdate();
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
        renderGpuCard(s);
        ram.set(percent(s.ram), f("%.1f / %.1f GiB used", (s.totalRam - s.availableRam) / 1073741824.0, s.totalRam / 1073741824.0), f("%.1f GiB available", s.availableRam / 1073741824.0));
        battery.set(s.battery >= 0 ? s.battery + "%" : "—", Float.isFinite(s.batteryF) ? f("%.1f °F · battery", s.batteryF) : "Temperature unavailable", s.charging ? "Charging / external power" : "On battery");
        renderOptional(s);
        fan.set(Float.isFinite(s.fanRpm) ? f("%.0f RPM", s.fanRpm) : "—", Float.isFinite(s.fanRpm) ? "Measured fan speed" : "RPM unavailable", "Requires readable fan sensor");
        power.set(Float.isFinite(s.batteryWatts) ? f("%.1f W", Math.abs(s.batteryWatts)) : "—",
                Float.isFinite(s.batteryWatts) ? (s.batteryWatts > 0 ? "Charging · into battery" : s.batteryWatts < 0 ? "Discharging · battery" : "No net battery current") : "Power unavailable",
                "Net battery power · not wall draw");
        power.title.setText(Float.isFinite(s.batteryWatts) && s.batteryWatts > 0 ? "BATTERY CHARGE" : "BATTERY DRAW");
        updateVolumeButton();
        self.set(Float.isFinite(s.selfCpu) ? f("%.1f%% CPU", s.selfCpu) : "Warming up", f("%.1f MiB RAM · PSS", s.appPssKb / 1024.0), "CPU relative to one core");
        cpu.chart.add(s.cpu); if (gpuCardMode == 0) gpu.chart.add(s.gpu); ram.chart.add(s.ram);
        footer.setText(f("%ds refresh · %.1f ms to collect · 90-sample history", interval / 1000, s.collectionMs));
    }

    private void renderGpuCard(TelemetrySampler.Sample sample) {
        if (gpuCardMode == 1) {
            Display display = getWindowManager().getDefaultDisplay();
            float hz = display.getRefreshRate();
            gpu.title.setText("DISPLAY");
            gpu.set(hz > 0 && Float.isFinite(hz) ? f("%.0f Hz", hz) : "—",
                    f("%d × %d px window", root.getWidth(), root.getHeight()), "Screen refresh · not game FPS");
        } else if (gpuCardMode == 2) {
            long minutes = SystemClock.elapsedRealtime() / 60000;
            gpu.title.setText("DEVICE UPTIME");
            gpu.set(minutes >= 1440 ? f("%dd %dh", minutes / 1440, minutes / 60 % 24)
                    : minutes >= 60 ? f("%dh %dm", minutes / 60, minutes % 60) : f("%dm", minutes),
                    "Since device restart", "Includes sleep time");
        } else {
            gpu.title.setText("GPU");
            if (sample != null) gpu.set(percent(sample.gpu), sample.gpuClock, sample.gpuReadable ? "Device GPU activity" : "Usage not exposed by device");
        }
    }

    private void renderOptional(TelemetrySampler.Sample s) {
        if (!showThermal) {
            internalStorageView.set(s.internalStorage, "Space unavailable");
            sdStorageView.set(s.sdStorage, s.sdStatus);
            return;
        }
        String[] thermalNames = {"None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown"};
        optional.set(s.thermal >= 0 && s.thermal < thermalNames.length ? thermalNames[s.thermal] : "—", s.thermal >= 0 ? "Throttling severity reported" : "Requires Android 10+", "Firmware report · not CPU °F");
        optional.value.setTextColor(s.thermal >= 3 ? CORAL : 0xFF95C4F2);
    }

    private void updateOptionalCard() {
        optional.title.setText(showThermal ? "THERMAL STATUS" : "STORAGE");
        optional.value.setVisibility(showThermal ? View.VISIBLE : View.GONE);
        optional.detail.setVisibility(showThermal ? View.VISIBLE : View.GONE);
        optional.note.setVisibility(showThermal ? View.VISIBLE : View.GONE);
        storageBody.setVisibility(showThermal ? View.GONE : View.VISIBLE);
        if (last != null) renderOptional(last);
        arrangedGeometry = null;
        if (dashboardScroll != null) dashboardScroll.post(this::arrangeCards);
    }

    private void updateControls() {
        intervalButton.setText(interval / 1000 + "s refresh");
        pauseButton.setText(paused ? "Resume" : "Pause");
        status.setText(paused ? "Paused" : "● Live");
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
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(4), dp(20), dp(8));
        String[] labels = {"Keep this screen awake", "Dim this screen to 25%", "Compact cards", "Auto-fit dashboard"};
        boolean[] checked = {keepAwake, dim, preferences.getBoolean("compact", false), preferences.getBoolean("autoFit", true)};
        for (int i = 0; i < labels.length; i++) {
            final int setting = i;
            CheckBox check = new CheckBox(this);
            check.setText(labels[i]); check.setTextColor(TEXT); check.setTextSize(14);
            check.setMinHeight(dp(44)); check.setChecked(checked[i]);
            check.setOnCheckedChangeListener((view, value) -> {
                if (setting == 0) { keepAwake = value; preferences.edit().putBoolean("awake", value).apply(); }
                if (setting == 1) { dim = value; preferences.edit().putBoolean("dim", value).apply(); }
                if (setting == 2) { preferences.edit().putBoolean("compact", value).apply(); arrangedGeometry = null; arrangeCards(); }
                if (setting == 3) { preferences.edit().putBoolean("autoFit", value).apply(); arrangedGeometry = null; arrangeCards(); }
                applyScreenOptions();
            });
            panel.addView(check);
        }
        panel.addView(text("Bottom-left card", 12, MUTED));
        Spinner choice = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Storage", "Thermal status"});
        choice.setAdapter(adapter); choice.setSelection(showThermal ? 1 : 0);
        panel.addView(choice, new LinearLayout.LayoutParams(-1, dp(44)));
        choice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showThermal = position == 1;
                preferences.edit().putBoolean("thermalCard", showThermal).apply();
                updateOptionalCard();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        panel.addView(text("GPU card", 12, MUTED));
        Spinner gpuChoice = new Spinner(this);
        gpuChoice.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"GPU activity", "Display info", "Device uptime"}));
        gpuChoice.setSelection(gpuCardMode);
        panel.addView(gpuChoice, new LinearLayout.LayoutParams(-1, dp(44)));
        gpuChoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == gpuCardMode) return;
                gpuCardMode = position;
                preferences.edit().putInt("gpuCard", gpuCardMode).apply();
                gpu.chart.clear();
                renderGpuCard(last);
                arrangedGeometry = null;
                dashboardScroll.post(MainActivity.this::arrangeCards);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        panel.addView(text("Temperature units · Fahrenheit (°F)", 11, MUTED), new LinearLayout.LayoutParams(-1, dp(28)));
        Button update = button("Check for updates", () -> {});
        panel.addView(update, new LinearLayout.LayoutParams(-1, dp(48)));
        panel.addView(text("GitHub Releases · Installed " + versionName(), 11, MUTED), new LinearLayout.LayoutParams(-1, dp(26)));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Options").setView(scroll)
                .setPositiveButton("Done", null).setNeutralButton("About / readings", (d, which) -> showAbout()).create();
        update.setOnClickListener(v -> { dialog.dismiss(); checkForUpdates(); });
        dialog.show();
    }

    private void adjustVolume(int direction) {
        try {
            if (audio.isVolumeFixed()) { Toast.makeText(this, "This audio output has fixed volume", Toast.LENGTH_SHORT).show(); return; }
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            updateVolumeButton();
        } catch (RuntimeException e) { Toast.makeText(this, "Android did not allow this volume change", Toast.LENGTH_SHORT).show(); }
    }

    private void updateVolumeButton() {
        try {
            boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
            muteButton.setText(muted ? "Unmute" : "Mute");
            muteButton.setTextColor(muted ? TEAL : TEXT);
            muteButton.setContentDescription(muted ? "Unmute media audio" : "Mute media audio");
        } catch (RuntimeException ignored) { muteButton.setText("Mute"); }
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "0.2.1"; }
    }

    private void checkForUpdates() {
        cancelUpdate();
        final int token = ++updateToken;
        final UpdateChecker checker = new UpdateChecker();
        updateChecker = checker;
        final String installed = versionName();
        updateDialog = new AlertDialog.Builder(this).setTitle("Check for updates")
                .setMessage("Checking published GitHub releases…")
                .setNegativeButton("Cancel", (dialog, which) -> cancelUpdate()).create();
        updateDialog.setOnCancelListener(dialog -> cancelUpdate());
        updateDialog.show();
        updateThread = new Thread(() -> {
            try {
                UpdateChecker.Release release = checker.check(installed);
                main.post(() -> {
                    if (!visible || isFinishing() || token != updateToken) return;
                    finishUpdateDialog();
                    if (release == null) {
                        new AlertDialog.Builder(this).setTitle("No newer APK found")
                                .setMessage("Installed: " + installed + "\n\nNo newer published APK is available. Checks include test releases.")
                                .setPositiveButton("OK", null).setNeutralButton("Open GitHub", (d, w) -> openUrl(UpdateChecker.REPOSITORY + "/releases")).show();
                    } else {
                        String message = "Installed: " + installed + "\nAvailable: " + release.tag
                                + (release.prerelease ? " · test release" : "")
                                + "\n\nDownload the APK, then open it to install. Android will ask you to confirm."
                                + (release.notes.isEmpty() ? "" : "\n\n" + release.notes);
                        new AlertDialog.Builder(this).setTitle("Update available").setMessage(message)
                                .setPositiveButton("Download APK", (d, w) -> openUrl(release.apk))
                                .setNeutralButton("Release notes", (d, w) -> openUrl(release.page)).setNegativeButton("Later", null).show();
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!visible || isFinishing() || token != updateToken) return;
                    finishUpdateDialog();
                    new AlertDialog.Builder(this).setTitle("Could not check for updates")
                            .setMessage("Check your connection and try again. You can also open the GitHub releases page.")
                            .setPositiveButton("Retry", (d, w) -> checkForUpdates())
                            .setNeutralButton("Open GitHub", (d, w) -> openUrl(UpdateChecker.REPOSITORY + "/releases")).setNegativeButton("Close", null).show();
                });
            }
        }, "PocketPulse-update-check");
        updateThread.start();
    }

    private void finishUpdateDialog() {
        updateChecker = null; updateThread = null;
        if (updateDialog != null) { updateDialog.dismiss(); updateDialog = null; }
    }

    private void cancelUpdate() {
        ++updateToken;
        if (updateChecker != null) updateChecker.cancel();
        if (updateThread != null) updateThread.interrupt();
        finishUpdateDialog();
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (RuntimeException e) { Toast.makeText(this, "No browser is available to open GitHub", Toast.LENGTH_LONG).show(); }
    }

    private void applyScreenOptions() {
        if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = dim ? .25f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(attributes);
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("Pocket Pulse · " + versionName()).setMessage(
            "A quiet dashboard for a second screen.\n\n" +
            "CPU and GPU: system-wide readings only where firmware permits them. A dash means unavailable or waiting for a valid sample; clocks are not utilization.\n\n" +
            "RAM: total minus Android’s available memory. Includes the system, games, apps, and some cache; it is not a game’s RAM allocation.\n\n" +
            "All temperatures use Fahrenheit. Battery °F is not CPU/GPU temperature. Optional thermals show Android’s reported severity, not a chip thermometer. ‘None’ means no severity reported; firmware support varies.\n\n" +
            "Auto-fit sizes cards to the usable window, accounting for display density, system bars and text size. Short windows hide charts and use tighter cards. Tap a tight card for its full reading. Extremely small windows or large accessibility fonts retain scrolling so text stays readable.\n\n" +
            "GPU card can instead show this display’s refresh rate and window resolution, or device uptime since restart (including sleep). Display refresh rate is not a game’s FPS. The card choice is saved.\n\n" +
            "Storage: free and total usable space for internal storage and a mounted microSD. Updated every 15 seconds. Adopted storage can be included in internal space.\n\n" +
            "Fan speed: actual RPM only when readable; not fan mode or PWM. Battery power is current × voltage: net charging/discharging power at the battery, not wall power or a game’s individual power use.\n\n" +
            "This app: CPU relative to one core, and process PSS sampled every 15 seconds. Collection time covers telemetry reads, not drawing or total game impact.\n\n" +
            "Media volume buttons control Android’s music/game audio stream. Network access is used only for manual GitHub update checks. No root, analytics, ads, overlay, boot task, wake lock, or background service. Reads stop when hidden, paused, or the screen turns off.\n\n" +
            "Move the app to the lower display, then open your game above. The display picker depends on firmware. Game FPS and per-game CPU/GPU/RAM are not available in this no-setup version."
        ).setPositiveButton("Close", null).setNeutralButton("Copy device report", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Pocket Pulse device report", deviceReport()));
            Toast.makeText(this, "Device report copied", Toast.LENGTH_SHORT).show();
        }).show();
    }

    private String deviceReport() {
        String report = "Pocket Pulse " + versionName() + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\nRefresh: " + interval + " ms\n";
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        report += "Window: " + root.getWidth() + " x " + root.getHeight() + " px\nDensity: " + getResources().getDisplayMetrics().densityDpi
                + " dpi\nFont scale: " + getResources().getConfiguration().fontScale + "\nDashboard viewport: " + dashboardScroll.getWidth() + " x " + dashboardScroll.getHeight() + " px\n";
        if (dashboardLayout != null) report += "Layout: " + dashboardLayout.columns + " columns; profile " + dashboardLayout.profile
                + "; card height " + dashboardLayout.cardHeight + " px; scrolling " + dashboardLayout.scrolling + "\n";
        report += "Auto-fit: " + preferences.getBoolean("autoFit", true) + "\nGPU card mode: " + gpuCardMode + "\n";
        for (Display display : manager.getDisplays()) report += "Display " + display.getDisplayId() + ": " + display.getName() + "\n";
        if (last != null) report += "CPU source: " + last.cpuSource + "\nCPU load: " + percent(last.cpu) + "\nCPU clock: " + last.cpuClock + "\nGPU source: " + last.gpuSource + "\nGPU load: " + percent(last.gpu) + "\nGPU clock: " + last.gpuClock + "\nRAM used: " + percent(last.ram) + "\nThermal status: " + last.thermal + "\nApp PSS: " + last.appPssKb + " KiB\nApp CPU: " + last.selfCpu + "% of one core\n";
        if (last != null) report += "Battery temperature: " + (Float.isFinite(last.batteryF) ? f("%.1f °F", last.batteryF) : "Unavailable")
                + "\nBattery net power: " + (Float.isFinite(last.batteryWatts) ? f("%.2f W (positive = charging)", last.batteryWatts) : "Unavailable")
                + "\nFan: " + (Float.isFinite(last.fanRpm) ? f("%.0f RPM", last.fanRpm) : "Unavailable") + "\nFan source: " + last.fanSource
                + "\nInternal storage: " + storageReport(last.internalStorage) + "\nmicroSD: " + (last.sdStorage == null ? last.sdStatus : storageReport(last.sdStorage)) + "\n";
        return report;
    }

    private String storageReport(TelemetrySampler.StorageReading reading) {
        return reading == null ? "Unavailable" : f("%.1f / %.1f GiB free; source: %s", reading.available / 1073741824.0, reading.total / 1073741824.0, reading.source);
    }

    private String storageSummary(TelemetrySampler.StorageReading reading) {
        return reading == null ? "Unavailable" : f("%.1f GiB free of %.1f GiB", reading.available / 1073741824.0, reading.total / 1073741824.0);
    }

    private void updateDeviceLabel() {
        Display display = getWindowManager().getDefaultDisplay();
        device.setText(Build.MODEL + "  ·  Display " + display.getDisplayId() + "  ·  Android " + Build.VERSION.RELEASE);
    }
    private final class Card extends LinearLayout {
        final TextView title, value, detail, note;
        final Sparkline chart;
        final boolean hasChart;
        Card(String label, int color, boolean hasChart) {
            super(MainActivity.this);
            this.hasChart = hasChart;
            setOrientation(VERTICAL);
            setPadding(dp(13), dp(12), dp(13), dp(10));
            setBackground(background(CARD, 16, 0xFF253140));
            title = text(label, 10, color);
            title.setLetterSpacing(.09f);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setSingleLine(true);
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
            setOnClickListener(v -> showReading(this));
        }
        void set(String number, String detailText, String noteText) {
            value.setText(number); detail.setText(detailText); note.setText(noteText);
        }
        int requiredHeight(int profile) {
            int height = getPaddingTop() + getPaddingBottom() + title.getLineHeight();
            if (this == optional && !showThermal) {
                LinearLayout.LayoutParams divider = (LinearLayout.LayoutParams) storageDivider.getLayoutParams();
                return height + internalStorageView.requiredHeight() + sdStorageView.requiredHeight()
                        + divider.height + divider.topMargin + divider.bottomMargin
                        + (profile == 3 ? storageHint.getLineHeight() : 0) + dp(2);
            }
            height += value.getLineHeight() + detail.getLineHeight() * 2;
            if (profile != 3) height += note.getLineHeight() * 2 + dp(3);
            if (hasChart && (this != gpu || gpuCardMode == 0) && profile == 0) height += dp(24 + 8);
            return height + dp(2);
        }
    }

    private final class StorageView extends LinearLayout {
        final TextView label, number, capacity;
        private boolean minimal;
        private TelemetrySampler.StorageReading reading;
        private String unavailable = "Waiting for reading";
        StorageView(String label) {
            super(MainActivity.this);
            setOrientation(VERTICAL);
            this.label = text(label, 11, TEXT);
            addView(this.label);
            number = text("—", 24, 0xFF95C4F2);
            number.setSingleLine(true);
            number.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(number);
            capacity = text("Waiting for reading", 10, MUTED);
            capacity.setMaxLines(2);
            capacity.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(capacity);
        }
        void set(TelemetrySampler.StorageReading reading, String unavailable) {
            this.reading = reading; this.unavailable = unavailable;
            number.setText(reading == null ? (minimal && unavailable.equals("No microSD detected") ? "Not detected" : "—")
                    : minimal ? f("%.0f / %.0f GiB", reading.available / 1073741824.0, reading.total / 1073741824.0)
                    : f("%.0f GiB", reading.available / 1073741824.0));
            capacity.setText(reading == null ? unavailable : f("free of %.0f GiB", reading.total / 1073741824.0));
            setContentDescription(label.getText() + ": " + (reading == null ? unavailable : f("%.1f GiB free of %.1f GiB", reading.available / 1073741824.0, reading.total / 1073741824.0)));
        }
        void applyProfile(int profile) {
            minimal = profile == 3;
            number.setTextSize(minimal ? 14 : profile == 2 ? 18 : profile == 1 ? 20 : 24);
            label.setTextSize(profile >= 2 ? 10 : 11);
            capacity.setVisibility(minimal ? View.GONE : View.VISIBLE);
            capacity.setMaxLines(profile >= 2 ? 1 : 2);
            set(reading, unavailable);
        }
        int requiredHeight() {
            return label.getLineHeight() + number.getLineHeight() + (minimal ? 0 : capacity.getLineHeight() * capacity.getMaxLines());
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
