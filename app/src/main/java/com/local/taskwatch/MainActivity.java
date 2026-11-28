package com.local.taskwatch;

/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Ashwini215
 */
import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final String PREFS_NAME = "background_monitor_settings";
    private static final String PREF_THEME = "theme_mode";
    private static final String STATE_SETTINGS = "showing_settings";
    private static final String STATE_ROOT_CONNECTED = "root_connected";
    private static final String STATE_SELECTED_PACKAGES = "selected_packages";
    private static final String STATE_APP_FILTER = "app_filter";
    private static final int FILTER_ALL = 0;
    private static final int FILTER_USER = 1;
    private static final int FILTER_SYSTEM = 2;
    private int BG = 0xFFF4F6FA;
    private int PANEL = 0xFFFFFFFF;
    private int INK = 0xFF162337;
    private int MUTED = 0xFF6D7B8E;
    private int ACCENT = 0xFF5265E8;
    private int PALE_BLUE = 0xFFEEF0FF;
    private int RED = 0xFFB43D50;
    private static final long REFRESH_INTERVAL_MS = 3_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService rootWorker = Executors.newSingleThreadExecutor();
    private final Set<String> selectedPackages = new HashSet<>();
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private volatile java.lang.Process activeRootProcess;

    private LinearLayout processRows;
    private ScrollView processScroll;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView updatedLabel;
    private TextView selectedLabel;
    private TextView sectionTitle;
    private MaterialButton rootButton;
    private MaterialButton forceStopButton;
    private MaterialButton selectAllButton;
    private MaterialButtonToggleGroup appFilterGroup;
    private TextInputEditText searchInput;
    private boolean showingSettings;
    private int appFilter = FILTER_ALL;

    private List<ApplicationInfo> installedApps = new ArrayList<>();
    private List<RunningApp> runningApps = new ArrayList<>();
    private boolean resumed;
    private boolean rootConnected;
    private boolean rootBusy;
    private boolean forceStopBusy;
    private String rootError = "";

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (resumed && !showingSettings && rootConnected && !rootBusy && !forceStopBusy) {
                readProcesses();
            }
            if (resumed) {
                mainHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String themeMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_THEME, "system");
        if ("light".equals(themeMode)) {
            setTheme(R.style.AppThemeLight);
        } else if ("dark".equals(themeMode)) {
            setTheme(R.style.AppThemeDark);
        }
        super.onCreate(savedInstanceState);
        if ("dynamic".equals(themeMode)) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        if (savedInstanceState != null) {
            showingSettings = savedInstanceState.getBoolean(STATE_SETTINGS, false);
            rootConnected = savedInstanceState.getBoolean(STATE_ROOT_CONNECTED, false);
            appFilter = savedInstanceState.getInt(STATE_APP_FILTER, FILTER_ALL);
            ArrayList<String> restoredSelection = savedInstanceState.getStringArrayList(STATE_SELECTED_PACKAGES);
            if (restoredSelection != null) selectedPackages.addAll(restoredSelection);
        }
        configureSystemBars();
        loadInstalledApps();
        if (showingSettings) buildSettingsInterface();
        else buildInterface();
        renderState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_SETTINGS, showingSettings);
        outState.putBoolean(STATE_ROOT_CONNECTED, rootConnected);
        outState.putInt(STATE_APP_FILTER, appFilter);
        outState.putStringArrayList(STATE_SELECTED_PACKAGES, new ArrayList<>(selectedPackages));
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (showingSettings) {
            showMainScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (rootConnected && !showingSettings) {
            readProcesses();
        }
        mainHandler.removeCallbacks(pollRunnable);
        if (!showingSettings) mainHandler.postDelayed(pollRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        resumed = false;
        mainHandler.removeCallbacks(pollRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        rootWorker.shutdownNow();
        java.lang.Process process = activeRootProcess;
        if (process != null) process.destroyForcibly();
        super.onDestroy();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        BG = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, BG);
        PANEL = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, PANEL);
        INK = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, INK);
        MUTED = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, MUTED);
        ACCENT = MaterialColors.getColor(this, android.R.attr.colorAccent, ACCENT);
        PALE_BLUE = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, PALE_BLUE);
        RED = MaterialColors.getColor(this, android.R.attr.colorError, RED);
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean lightSystemBars = nightMode != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String themeMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_THEME, "system");
        boolean useLightSystemBars = "light".equals(themeMode)
                || (!"dark".equals(themeMode) && lightSystemBars);
        int systemBarFlags = useLightSystemBars
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : 0;
        window.getDecorView().setSystemUiVisibility(systemBarFlags);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        if (Build.VERSION.SDK_INT >= 33) {
            installedApps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0));
        } else {
            //noinspection deprecation
            installedApps = pm.getInstalledApplications(0);
        }
        Collections.sort(installedApps, (a, b) -> a.packageName.compareToIgnoreCase(b.packageName));
    }

    private void buildInterface() {
        clearScreenReferences();
        LinearLayout root = pageRoot();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView pageTitle = makeText("Background Monitor", 21, INK, true);
        header.addView(pageTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton settingsButton = smallButton("Settings");
        settingsButton.setContentDescription("Open settings and app details");
        settingsButton.setOnClickListener(v -> showSettingsScreen());
        header.addView(settingsButton);
        root.addView(header, marginParams(-1, -2, 0, 0, 0, 10));

        LinearLayout statusCard = panel(18);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout statusText = new LinearLayout(this);
        statusText.setOrientation(LinearLayout.VERTICAL);
        statusTitle = makeText("Root access needed", 17, INK, true);
        statusDetail = makeText("Connect to view live background apps.", 12, MUTED, false);
        statusDetail.setMaxLines(2);
        statusText.addView(statusTitle);
        statusText.addView(statusDetail, marginParams(-1, -2, 0, 3, 0, 0));
        statusCard.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1f));
        rootButton = new MaterialButton(this);
        rootButton.setAllCaps(false);
        rootButton.setTextColor(0xFFFFFFFF);
        rootButton.setTextSize(13);
        rootButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        rootButton.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        rootButton.setMinWidth(0);
        rootButton.setMinimumWidth(0);
        rootButton.setPadding(dp(8), 0, dp(8), 0);
        rootButton.setOnClickListener(v -> readProcesses());
        statusCard.addView(rootButton, marginParams(-2, dp(42), 8, 0, 0, 0));
        root.addView(statusCard, marginParams(-1, -2, 0, 0, 0, 8));

        TextInputLayout searchLayout = new TextInputLayout(this);
        searchLayout.setHint("Search running apps");
        searchLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        searchLayout.setStartIconDrawable(android.R.drawable.ic_menu_search);
        searchLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        searchInput = new TextInputEditText(searchLayout.getContext());
        searchInput.setSingleLine(true);
        searchInput.setContentDescription("Search by app name or package");
        searchLayout.addView(searchInput, new LinearLayout.LayoutParams(-1, -2));
        root.addView(searchLayout, marginParams(-1, -2, 0, 0, 0, 8));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderRows();
                renderSelectionControls();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        appFilterGroup = new MaterialButtonToggleGroup(this);
        appFilterGroup.setSingleSelection(true);
        appFilterGroup.setSelectionRequired(true);
        appFilterGroup.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton allAppsButton = filterButton("All apps");
        MaterialButton userAppsButton = filterButton("User apps");
        MaterialButton systemAppsButton = filterButton("System apps");
        allAppsButton.setId(View.generateViewId());
        userAppsButton.setId(View.generateViewId());
        systemAppsButton.setId(View.generateViewId());
        LinearLayout.LayoutParams filterButtonParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        appFilterGroup.addView(allAppsButton, filterButtonParams);
        appFilterGroup.addView(userAppsButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        appFilterGroup.addView(systemAppsButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        int checkedFilterId = appFilter == FILTER_USER ? userAppsButton.getId()
                : appFilter == FILTER_SYSTEM ? systemAppsButton.getId() : allAppsButton.getId();
        appFilterGroup.check(checkedFilterId);
        appFilterGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            appFilter = checkedId == userAppsButton.getId() ? FILTER_USER
                    : checkedId == systemAppsButton.getId() ? FILTER_SYSTEM : FILTER_ALL;
            renderRows();
            renderSelectionControls();
        });
        root.addView(appFilterGroup, marginParams(-1, dp(44), 0, 0, 0, 7));

        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        sectionTitle = makeText("Running apps", 17, INK, true);
        sectionHeader.addView(sectionTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        selectAllButton = smallButton("Select visible");
        selectAllButton.setOnClickListener(v -> toggleSelectAll());
        sectionHeader.addView(selectAllButton);
        root.addView(sectionHeader, marginParams(-1, -2, 2, 0, 0, 2));

        updatedLabel = makeText("Waiting for root access", 11, MUTED, false);
        root.addView(updatedLabel, marginParams(-1, -2, 2, 0, 0, 5));

        processScroll = new ScrollView(this);
        processScroll.setFillViewport(true);
        processRows = new LinearLayout(this);
        processRows.setOrientation(LinearLayout.VERTICAL);
        processScroll.addView(processRows, new ScrollView.LayoutParams(-1, -2));
        root.addView(processScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout footer = panel(10);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        selectedLabel = makeText("0 selected", 13, MUTED, true);
        footer.addView(selectedLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        forceStopButton = new MaterialButton(this);
        forceStopButton.setAllCaps(false);
        forceStopButton.setTextColor(0xFFFFFFFF);
        forceStopButton.setTextSize(15);
        forceStopButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        forceStopButton.setBackgroundTintList(ColorStateList.valueOf(RED));
        forceStopButton.setOnClickListener(v -> forceStopSelected());
        footer.addView(forceStopButton, marginParams(-2, dp(48), 8, 0, 0, 0));
        root.addView(footer, marginParams(-1, -2, 0, 6, 0, 0));

        setContentView(root);
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(10), dp(16), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            int left;
            int right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets system = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = system.top;
                bottom = system.bottom;
                left = system.left;
                right = system.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            view.setPadding(dp(16) + left, dp(8) + top, dp(16) + right, dp(6) + bottom);
            return insets;
        });
        return root;
    }

    private void clearScreenReferences() {
        processRows = null;
        processScroll = null;
        statusTitle = null;
        statusDetail = null;
        updatedLabel = null;
        selectedLabel = null;
        sectionTitle = null;
        rootButton = null;
        forceStopButton = null;
        selectAllButton = null;
        appFilterGroup = null;
        searchInput = null;
    }

    private void showSettingsScreen() {
        showingSettings = true;
        mainHandler.removeCallbacks(pollRunnable);
        clearScreenReferences();
        buildSettingsInterface();
    }

    private void showMainScreen() {
        showingSettings = false;
        clearScreenReferences();
        buildInterface();
        renderState();
        if (rootConnected) readProcesses();
        if (resumed) {
            mainHandler.removeCallbacks(pollRunnable);
            mainHandler.postDelayed(pollRunnable, REFRESH_INTERVAL_MS);
        }
    }

    private void buildSettingsInterface() {
        LinearLayout root = pageRoot();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton backButton = smallButton("Back");
        backButton.setContentDescription("Return to running apps");
        backButton.setOnClickListener(v -> showMainScreen());
        header.addView(backButton);
        TextView title = makeText("Settings", 21, INK, true);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);
        root.addView(header, marginParams(-1, -2, 0, 0, 0, 10));

        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        content.addView(makeText("Appearance", 18, INK, true), marginParams(-1, -2, 2, 4, 0, 7));
        String currentTheme = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_THEME, "system");
        LinearLayout appearance = panel(12);
        appearance.setOrientation(LinearLayout.VERTICAL);
        addThemeOption(appearance, currentTheme, "system", "System default", "Follow your phone's light or dark setting.", true);
        addThemeOption(appearance, currentTheme, "light", "Light", "Always use the light appearance.", true);
        addThemeOption(appearance, currentTheme, "dark", "Dark", "Always use the dark appearance.", true);
        addThemeOption(appearance, currentTheme, "dynamic", "Dynamic colors", "Use colors from your wallpaper (Android 12 or newer).", Build.VERSION.SDK_INT >= 31);
        content.addView(appearance, marginParams(-1, -2, 0, 0, 0, 14));

        content.addView(makeText("App details", 18, INK, true), marginParams(-1, -2, 2, 0, 0, 7));
        LinearLayout about = panel(16);
        about.setOrientation(LinearLayout.VERTICAL);
        addDetail(about, "Background Monitor", "Root-powered background app manager");
        addDetail(about, "Version", appVersionText());
        addDetail(about, "Developer", "Ashwini215");
        addDetail(about, "Package", getPackageName());
        addDetail(about, "License", "GNU General Public License v3.0");
        addDetail(about, "Source code", "github.com/ashwini215/Background-Monitor");
        content.addView(about, marginParams(-1, -2, 0, 0, 0, 14));

        content.addView(makeText("What force stop does", 18, INK, true), marginParams(-1, -2, 2, 0, 0, 7));
        LinearLayout forceStopInfo = panel(16);
        TextView explanation = makeText(
                "Force stop asks Android to stop the selected app and put it in the stopped state. "
                        + "On Android 15 and newer, it should stay stopped until you open it or take another action "
                        + "that launches it, such as using one of its widgets. This monitor does not keep running "
                        + "after you exit to block future launches. If an app returns to the list without you "
                        + "opening or interacting with it, your device may be handling the request differently.",
                13, MUTED, false);
        explanation.setLineSpacing(dp(3), 1f);
        forceStopInfo.addView(explanation);
        content.addView(forceStopInfo, marginParams(-1, -2, 0, 0, 0, 14));

        content.addView(makeText("Requirements & privacy", 18, INK, true), marginParams(-1, -2, 2, 0, 0, 7));
        LinearLayout requirements = panel(16);
        TextView requirementText = makeText(
                "Requires Android 8.0 or newer and root access through Magisk, KernelSU, or a compatible su manager. "
                        + "Approve the root request when prompted. Background Monitor has no internet permission "
                        + "and checks processes only while it is open.",
                13, MUTED, false);
        requirementText.setLineSpacing(dp(3), 1f);
        requirements.addView(requirementText);
        content.addView(requirements, marginParams(-1, -2, 0, 0, 0, 14));

        MaterialButton exitButton = smallButton("Exit Background Monitor");
        exitButton.setOnClickListener(v -> exitMonitor());
        content.addView(exitButton, marginParams(-1, dp(48), 0, 0, 0, 12));

        settingsScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void addThemeOption(LinearLayout parent, String currentTheme, String key,
                                String title, String description, boolean enabled) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        MaterialRadioButton radio = new MaterialRadioButton(this);
        radio.setChecked(key.equals(currentTheme));
        radio.setEnabled(enabled);
        row.addView(radio, new LinearLayout.LayoutParams(dp(42), dp(48)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = makeText(title, 14, INK, true);
        TextView detail = makeText(enabled ? description : "Available on Android 12 and newer.", 12, MUTED, false);
        detail.setLineSpacing(dp(1), 1f);
        text.addView(name);
        text.addView(detail, marginParams(-1, -2, 0, 2, 0, 0));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setEnabled(enabled);
        if (enabled) {
            View.OnClickListener chooseTheme = v -> {
                if (!key.equals(currentTheme)) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_THEME, key).apply();
                    recreate();
                }
            };
            row.setOnClickListener(chooseTheme);
            radio.setOnClickListener(chooseTheme);
        }
        parent.addView(row, marginParams(-1, -2, 0, 0, 0, 2));
    }

    private void addDetail(LinearLayout parent, String label, String value) {
        TextView title = makeText(label, 11, MUTED, true);
        TextView detail = makeText(value, 14, INK, false);
        detail.setTextIsSelectable(true);
        parent.addView(title, marginParams(-1, -2, 0, 5, 0, 0));
        parent.addView(detail, marginParams(-1, -2, 0, 1, 0, 4));
    }

    private String appVersionText() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + versionCode + ")";
        } catch (PackageManager.NameNotFoundException exception) {
            return "Unknown";
        }
    }

    private void renderState() {
        if (statusTitle == null) return;
        if (rootBusy && !rootConnected) {
            statusTitle.setText("Connecting to root…");
            statusDetail.setText("Approve the request in your root manager if it appears.");
            rootButton.setText("Checking root access…");
            rootButton.setEnabled(false);
        } else if (rootConnected) {
            int processCount = 0;
            for (RunningApp app : runningApps) processCount += app.processes.size();
            statusTitle.setText(runningApps.size() + (runningApps.size() == 1 ? " app found" : " apps found"));
            statusDetail.setText(processCount + (processCount == 1 ? " background process" : " background processes")
                    + " · refreshes every 3 sec");
            rootButton.setText(forceStopBusy ? "Stopping…" : (rootBusy ? "Reading…" : "Refresh"));
            rootButton.setEnabled(!forceStopBusy && !rootBusy);
        } else {
            statusTitle.setText("Root access is needed");
            statusDetail.setText(rootError.isEmpty()
                    ? "Connect once to see app processes that are running outside this monitor."
                    : rootError);
            rootButton.setText(rootError.isEmpty() ? "Connect to root" : "Retry root access");
            rootButton.setEnabled(true);
        }
        updateTimestamp();
        renderRows();
        renderSelectionControls();
    }

    private void renderRows() {
        if (processRows == null) return;
        int scrollY = processScroll.getScrollY();
        processRows.removeAllViews();
        if (!rootConnected) {
            addEmptyMessage(rootBusy ? "Waiting for the root manager…" : "Connect to root to view live app processes.");
        } else {
            List<RunningApp> visibleApps = filteredApps();
            if (visibleApps.isEmpty()) {
                String query = searchInput == null || searchInput.getText() == null
                        ? "" : searchInput.getText().toString().trim();
                String category = appFilter == FILTER_USER ? "user" : appFilter == FILTER_SYSTEM ? "system" : "";
                addEmptyMessage(query.isEmpty()
                        ? "No " + (category.isEmpty() ? "background" : category) + " apps found."
                        : "No " + (category.isEmpty() ? "" : category + " ") + "apps match “" + query + "”.");
            }
            for (RunningApp app : visibleApps) {
                processRows.addView(makeAppRow(app), marginParams(-1, -2, 0, 0, 0, 8));
            }
        }
        processRows.post(() -> processScroll.scrollTo(0, scrollY));
    }

    private List<RunningApp> filteredApps() {
        String query = searchInput == null || searchInput.getText() == null
                ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<RunningApp> filtered = new ArrayList<>();
        for (RunningApp app : runningApps) {
            boolean systemApp = isSystemApp(app.applicationInfo);
            if (appFilter == FILTER_USER && systemApp) continue;
            if (appFilter == FILTER_SYSTEM && !systemApp) continue;
            if (query.isEmpty()
                    || app.label.toLowerCase(Locale.ROOT).contains(query)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(app);
            }
        }
        return filtered;
    }

    private View makeAppRow(RunningApp app) {
        LinearLayout row = panel(12);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(round(selectedPackages.contains(app.packageName) ? PALE_BLUE : PANEL, 16));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(loadAppIcon(app.applicationInfo));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, dp(4), 0);
        TextView name = makeText(app.label, 15, INK, true);
        TextView details = makeText(processDetails(app), 12, MUTED, false);
        names.addView(name);
        names.addView(details, marginParams(-1, -2, 0, 3, 0, 0));
        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialCheckBox checkbox = new MaterialCheckBox(this);
        checkbox.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        checkbox.setChecked(selectedPackages.contains(app.packageName));
        checkbox.setContentDescription("Select " + app.label);
        checkbox.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selectedPackages.add(app.packageName);
            else selectedPackages.remove(app.packageName);
            row.setBackground(round(checked ? PALE_BLUE : PANEL, 16));
            renderSelectionControls();
        });
        row.addView(checkbox);
        row.setOnClickListener(v -> checkbox.performClick());
        return row;
    }

    private String processDetails(RunningApp app) {
        StringBuilder details = new StringBuilder();
        details.append(app.processes.size()).append(app.processes.size() == 1 ? " process" : " processes");
        if (!app.processes.isEmpty()) {
            details.append(" · PID ").append(app.processes.get(0).pid);
            String name = app.processes.get(0).name;
            if (!name.equals(app.packageName)) details.append(" · ").append(name);
        }
        if (app.processes.size() > 1) details.append(" · +").append(app.processes.size() - 1).append(" more");
        return details.toString();
    }

    private Drawable loadAppIcon(ApplicationInfo info) {
        Drawable cached = iconCache.get(info.packageName);
        if (cached != null) return cached;
        Drawable icon;
        try {
            icon = getPackageManager().getApplicationIcon(info);
        } catch (RuntimeException exception) {
            icon = getDrawable(android.R.drawable.sym_def_app_icon);
        }
        iconCache.put(info.packageName, icon);
        return icon;
    }

    private void addEmptyMessage(String message) {
        LinearLayout card = panel(18);
        TextView text = makeText(message, 14, MUTED, false);
        text.setGravity(Gravity.CENTER);
        text.setMinHeight(dp(88));
        card.addView(text);
        processRows.addView(card, marginParams(-1, -2, 0, 0, 0, 8));
    }

    private void renderSelectionControls() {
        if (selectedLabel == null) return;
        int selectedCount = selectedPackages.size();
        if (sectionTitle != null) sectionTitle.setText("Running apps · " + filteredApps().size());
        selectedLabel.setText(selectedCount == 0 ? "Select apps to force stop"
                : selectedCount + (selectedCount == 1 ? " app selected" : " apps selected"));
        forceStopButton.setText(forceStopBusy ? "Stopping…" : "Force stop" + (selectedCount > 0 ? " (" + selectedCount + ")" : ""));
        forceStopButton.setEnabled(rootConnected && selectedCount > 0 && !forceStopBusy && !rootBusy);
        List<RunningApp> visibleApps = filteredApps();
        selectAllButton.setEnabled(rootConnected && !visibleApps.isEmpty() && !forceStopBusy && !rootBusy);
        boolean allSelected = !visibleApps.isEmpty();
        for (RunningApp app : visibleApps) {
            if (!selectedPackages.contains(app.packageName)) {
                allSelected = false;
                break;
            }
        }
        selectAllButton.setText(allSelected ? "Clear visible" : "Select visible");
    }

    private void updateTimestamp() {
        if (updatedLabel == null) return;
        if (rootBusy) {
            updatedLabel.setText("Reading process list…");
        } else if (!rootConnected) {
            updatedLabel.setText("Root access not connected");
        } else {
            updatedLabel.setText("Live snapshot · " + new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new java.util.Date()));
        }
    }

    private void toggleSelectAll() {
        if (!rootConnected) return;
        List<RunningApp> visibleApps = filteredApps();
        boolean allSelected = !visibleApps.isEmpty();
        for (RunningApp app : visibleApps) {
            if (!selectedPackages.contains(app.packageName)) {
                allSelected = false;
                break;
            }
        }
        for (RunningApp app : visibleApps) {
            if (allSelected) {
                selectedPackages.remove(app.packageName);
            } else {
                selectedPackages.add(app.packageName);
            }
        }
        renderRows();
        renderSelectionControls();
    }

    private boolean isSystemApp(ApplicationInfo applicationInfo) {
        int systemFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        return (applicationInfo.flags & systemFlags) != 0;
    }

    private void readProcesses() {
        if (rootBusy || forceStopBusy) return;
        rootBusy = true;
        rootError = "";
        renderState();
        rootWorker.execute(() -> {
            Snapshot snapshot = null;
            String error = "";
            try {
                snapshot = readRootSnapshot();
            } catch (Exception exception) {
                error = readableRootError(exception);
            }
            Snapshot resultSnapshot = snapshot;
            String resultError = error;
            mainHandler.post(() -> {
                rootBusy = false;
                if (resultSnapshot == null) {
                    rootConnected = false;
                    rootError = resultError;
                    runningApps = new ArrayList<>();
                    selectedPackages.clear();
                } else {
                    rootConnected = true;
                    rootError = "";
                    runningApps = resultSnapshot.apps;
                    selectedPackages.retainAll(packagesIn(runningApps));
                }
                renderState();
            });
        });
    }

    private Snapshot readRootSnapshot() throws Exception {
        String command = "id; printf '\\n__ACTIVITY__\\n'; /system/bin/dumpsys activity activities 2>&1; "
                + "printf '\\n__PROCESSES__\\n'; /system/bin/ps -A -w -o PID,NAME 2>&1";
        RootResult result = runSu(command);
        if (!result.output.matches("(?s).*\\buid=0(?:\\b|\\().*")) {
            throw new IOException("Root was not granted. Approve Background Monitor in your su manager and retry.");
        }
        int activityMarker = result.output.indexOf("__ACTIVITY__");
        int processMarker = result.output.indexOf("__PROCESSES__");
        if (processMarker < 0) {
            throw new IOException("Root connected, but Android did not return a process list.");
        }
        String activityDump = activityMarker >= 0 && processMarker > activityMarker
                ? result.output.substring(activityMarker, processMarker) : "";
        String processDump = result.output.substring(processMarker + "__PROCESSES__".length());
        Set<String> foregroundPackages = findForegroundPackages(activityDump);
        List<RunningApp> apps = parseProcesses(processDump, foregroundPackages);
        return new Snapshot(apps);
    }

    private RootResult runSu(String command) throws Exception {
        java.lang.Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        activeRootProcess = process;
        try {
            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = input.readLine()) != null) output.append(line).append('\n');
                } catch (IOException ignored) {
                    // The process may be terminated after the timeout or app exit.
                }
            }, "root-output-reader");
            reader.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                throw new IOException("The root command timed out. Check your root manager and try again.");
            }
            reader.join(1_000L);
            String result = output.toString();
            if (process.exitValue() != 0 && result.trim().isEmpty()) {
                throw new IOException("The su command failed. Confirm that a root manager is installed.");
            }
            return new RootResult(result, process.exitValue());
        } finally {
            activeRootProcess = null;
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private Set<String> findForegroundPackages(String activityDump) {
        Set<String> packages = new HashSet<>();
        Pattern pattern = Pattern.compile("(?m)(?:mResumedActivity|topResumedActivity|mFocusedApp|ResumedActivity)[^\\n]*?\\bu\\d+\\s+([A-Za-z0-9_.]+)/");
        Matcher matcher = pattern.matcher(activityDump);
        while (matcher.find()) packages.add(matcher.group(1));
        return packages;
    }

    private List<RunningApp> parseProcesses(String processDump, Set<String> foregroundPackages) {
        Map<String, RunningApp> byPackage = new LinkedHashMap<>();
        String[] lines = processDump.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.toUpperCase(Locale.ROOT).startsWith("PID ")) continue;
            String[] fields = trimmed.split("\\s+", 3);
            if (fields.length < 2) continue;
            int pid;
            try {
                pid = Integer.parseInt(fields[0]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            String processName = fields[1];
            String packageName = packageForProcess(processName);
            if (packageName == null || packageName.equals(getPackageName()) || foregroundPackages.contains(packageName)) continue;
            ApplicationInfo info = applicationForPackage(packageName);
            if (info == null) continue;
            RunningApp app = byPackage.get(packageName);
            if (app == null) {
                String label;
                try {
                    label = getPackageManager().getApplicationLabel(info).toString();
                } catch (RuntimeException exception) {
                    label = packageName;
                }
                app = new RunningApp(packageName, label, info);
                byPackage.put(packageName, app);
            }
            app.processes.add(new RunningProcess(pid, processName));
        }
        List<RunningApp> result = new ArrayList<>(byPackage.values());
        for (RunningApp app : result) {
            Collections.sort(app.processes, Comparator.comparingInt(item -> item.pid));
        }
        Collections.sort(result, (a, b) -> {
            int labels = a.label.compareToIgnoreCase(b.label);
            return labels != 0 ? labels : a.packageName.compareToIgnoreCase(b.packageName);
        });
        return result;
    }

    private String packageForProcess(String processName) {
        ApplicationInfo best = null;
        int bestLength = -1;
        for (ApplicationInfo info : installedApps) {
            String packageName = info.packageName;
            String declaredProcess = info.processName == null ? packageName : info.processName;
            boolean match = processName.equals(packageName) || processName.startsWith(packageName + ":")
                    || processName.equals(declaredProcess) || processName.startsWith(declaredProcess + ":");
            if (match && packageName.length() > bestLength) {
                best = info;
                bestLength = packageName.length();
            }
        }
        return best == null ? null : best.packageName;
    }

    private ApplicationInfo applicationForPackage(String packageName) {
        for (ApplicationInfo info : installedApps) {
            if (info.packageName.equals(packageName)) return info;
        }
        return null;
    }

    private Set<String> packagesIn(List<RunningApp> apps) {
        Set<String> packages = new HashSet<>();
        for (RunningApp app : apps) packages.add(app.packageName);
        return packages;
    }

    private void forceStopSelected() {
        if (selectedPackages.isEmpty() || forceStopBusy || rootBusy || !rootConnected) return;
        List<RunningApp> chosen = new ArrayList<>();
        for (RunningApp app : runningApps) {
            if (selectedPackages.contains(app.packageName)) chosen.add(app);
        }
        if (chosen.isEmpty()) return;
        forceStop(chosen);
    }

    private void forceStop(List<RunningApp> chosen) {
        if (forceStopBusy) return;
        List<String> packages = new ArrayList<>();
        for (RunningApp app : chosen) {
            if (app.packageName.matches("[A-Za-z0-9_.]+")) packages.add(app.packageName);
        }
        if (packages.isEmpty()) {
            Toast.makeText(this, "No valid app packages selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        forceStopBusy = true;
        renderState();
        Toast.makeText(this, "Sending force-stop request…", Toast.LENGTH_SHORT).show();
        rootWorker.execute(() -> {
            StringBuilder script = new StringBuilder("id; ");
            for (int i = 0; i < packages.size(); i++) {
                String packageName = packages.get(i);
                script.append("/system/bin/am force-stop --user current ").append(packageName)
                        .append("; echo __STOP_RESULT_").append(i).append("=$?; ");
            }
            String error = "";
            int successCount = 0;
            Set<String> successfulPackages = new HashSet<>();
            try {
                RootResult result = runSu(script.toString());
                if (!result.output.matches("(?s).*\\buid=0(?:\\b|\\().*")) {
                    throw new IOException("Root permission was denied.");
                }
                Matcher matcher = Pattern.compile("__STOP_RESULT_(\\d+)=(\\d+)").matcher(result.output);
                while (matcher.find()) {
                    if ("0".equals(matcher.group(2))) {
                        int packageIndex = Integer.parseInt(matcher.group(1));
                        if (packageIndex >= 0 && packageIndex < packages.size()) {
                            successfulPackages.add(packages.get(packageIndex));
                            successCount++;
                        }
                    }
                }
                if (successCount < packages.size()) {
                    error = "Android accepted " + successCount + " of " + packages.size() + " force-stop requests.";
                }
            } catch (Exception exception) {
                error = readableRootError(exception);
            }
            int success = successCount;
            String operationError = error;
            Set<String> stoppedPackages = successfulPackages;
            mainHandler.post(() -> {
                forceStopBusy = false;
                selectedPackages.removeAll(stoppedPackages);
                if (!operationError.isEmpty()) {
                    Toast.makeText(this, operationError, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Android accepted " + success + " force-stop request(s). Refreshing…", Toast.LENGTH_SHORT).show();
                }
                renderState();
                mainHandler.postDelayed(() -> {
                    if (resumed && !showingSettings) readProcesses();
                }, 800L);
            });
        });
    }

    private String readableRootError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return "Could not read the root process list. Check su access and retry.";
        return message;
    }

    private void exitMonitor() {
        finishAndRemoveTask();
        Process.killProcess(Process.myPid());
    }

    private LinearLayout panel(int padding) {
        LinearLayout view = new LinearLayout(this);
        view.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        view.setBackground(round(PANEL, 18));
        view.setElevation(dp(1));
        return view;
    }

    private MaterialButton smallButton(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(ACCENT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(PALE_BLUE));
        return button;
    }

    private MaterialButton filterButton(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setCheckable(true);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class RunningApp {
        final String packageName;
        final String label;
        final ApplicationInfo applicationInfo;
        final List<RunningProcess> processes = new ArrayList<>();

        RunningApp(String packageName, String label, ApplicationInfo applicationInfo) {
            this.packageName = packageName;
            this.label = label;
            this.applicationInfo = applicationInfo;
        }
    }

    private static final class RunningProcess {
        final int pid;
        final String name;

        RunningProcess(int pid, String name) {
            this.pid = pid;
            this.name = name;
        }
    }

    private static final class Snapshot {
        final List<RunningApp> apps;

        Snapshot(List<RunningApp> apps) {
            this.apps = apps;
        }
    }

    private static final class RootResult {
        final String output;
        final int exitCode;

        RootResult(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }
    }
}
