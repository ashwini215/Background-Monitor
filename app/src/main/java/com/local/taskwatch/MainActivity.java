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
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private MaterialButton rootButton;
    private MaterialButton forceStopButton;
    private MaterialButton selectAllButton;
    private TextInputEditText searchInput;
    private TextView helpDetails;
    private boolean helpExpanded;

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
            if (resumed && rootConnected && !rootBusy && !forceStopBusy) {
                readProcesses();
            }
            if (resumed) {
                mainHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        loadInstalledApps();
        buildInterface();
        renderState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (rootConnected) {
            readProcesses();
        }
        mainHandler.removeCallbacks(pollRunnable);
        mainHandler.postDelayed(pollRunnable, REFRESH_INTERVAL_MS);
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
        ACCENT = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, ACCENT);
        PALE_BLUE = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, PALE_BLUE);
        RED = MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, RED);
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean lightSystemBars = nightMode != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int systemBarFlags = lightSystemBars
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(22), dp(16), dp(22), dp(12));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = 0;
            int bottom = 0;
            int left = 0;
            int right = 0;
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
            view.setPadding(dp(22) + left, dp(12) + top, dp(22) + right, dp(12) + bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout titleStack = textStack("Background Monitor", "Root-powered · local only", 22, 14);
        header.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton exitButton = smallButton("Exit");
        exitButton.setOnClickListener(v -> exitMonitor());
        header.addView(exitButton);
        root.addView(header, marginParams(-1, -2, 0, 0, 0, 16));

        LinearLayout statusCard = panel(18);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusTitle = makeText("Root access is needed", 22, INK, true);
        statusDetail = makeText("Connect once to see app processes that are running outside this monitor.", 14, MUTED, false);
        statusDetail.setLineSpacing(dp(2), 1f);
        statusCard.addView(statusTitle);
        statusCard.addView(statusDetail, marginParams(-1, -2, 0, 8, 0, 0));
        rootButton = new MaterialButton(this);
        rootButton.setAllCaps(false);
        rootButton.setTextColor(0xFFFFFFFF);
        rootButton.setTextSize(15);
        rootButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        rootButton.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        rootButton.setOnClickListener(v -> readProcesses());
        statusCard.addView(rootButton, marginParams(-1, dp(48), 0, 12, 0, 0));
        root.addView(statusCard, marginParams(-1, -2, 0, 0, 0, 10));

        LinearLayout infoCard = panel(14);
        TextView info = makeText(
                "Refreshes every 3 seconds while open. Force stop uses Android's package manager through root; apps may start again after you launch them or the system requests them.",
                13, MUTED, false);
        info.setLineSpacing(dp(2), 1f);
        infoCard.addView(info);
        root.addView(infoCard, marginParams(-1, -2, 0, 0, 0, 14));

        TextInputLayout searchLayout = new TextInputLayout(this);
        searchLayout.setHint("Search running apps");
        searchLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        searchLayout.setStartIconDrawable(android.R.drawable.ic_menu_search);
        searchLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        searchInput = new TextInputEditText(searchLayout.getContext());
        searchInput.setSingleLine(true);
        searchInput.setContentDescription("Search by app name or package");
        searchLayout.addView(searchInput, new LinearLayout.LayoutParams(-1, -2));
        root.addView(searchLayout, marginParams(-1, -2, 0, 0, 0, 12));
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

        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView sectionTitle = makeText("Running apps", 18, INK, true);
        sectionHeader.addView(sectionTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        selectAllButton = smallButton("Select visible");
        selectAllButton.setOnClickListener(v -> toggleSelectAll());
        sectionHeader.addView(selectAllButton);
        root.addView(sectionHeader, marginParams(-1, -2, 2, 0, 0, 6));

        updatedLabel = makeText("Waiting for root access", 12, MUTED, false);
        root.addView(updatedLabel, marginParams(-1, -2, 2, 0, 0, 8));

        processScroll = new ScrollView(this);
        processScroll.setFillViewport(false);
        processRows = new LinearLayout(this);
        processRows.setOrientation(LinearLayout.VERTICAL);
        processScroll.addView(processRows, new ScrollView.LayoutParams(-1, -2));
        root.addView(processScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout helpCard = panel(14);
        helpCard.setOrientation(LinearLayout.VERTICAL);
        TextView helpTitle = makeText("How it works & what you need", 15, INK, true);
        TextView helpSummary = makeText("Tap to show requirements and force-stop details", 12, MUTED, false);
        helpDetails = makeText(
                "A rooted phone and a root manager such as Magisk or KernelSU are required. "
                        + "Approve the su request when prompted. The list refreshes every 3 seconds while open. "
                        + "Force stop asks Android to stop each selected package; Android can start it again later. "
                        + "The app has no internet permission.",
                13, MUTED, false);
        helpDetails.setVisibility(View.GONE);
        helpCard.addView(helpTitle);
        helpCard.addView(helpSummary, marginParams(-1, -2, 0, 4, 0, 0));
        helpCard.addView(helpDetails, marginParams(-1, -2, 0, 10, 0, 0));
        helpCard.setClickable(true);
        helpCard.setFocusable(true);
        helpCard.setOnClickListener(v -> {
            helpExpanded = !helpExpanded;
            helpDetails.setVisibility(helpExpanded ? View.VISIBLE : View.GONE);
            helpSummary.setText(helpExpanded ? "Tap to hide details" : "Tap to show requirements and force-stop details");
        });
        root.addView(helpCard, marginParams(-1, -2, 0, 8, 0, 8));

        LinearLayout footer = panel(14);
        footer.setOrientation(LinearLayout.VERTICAL);
        selectedLabel = makeText("0 selected", 13, MUTED, true);
        footer.addView(selectedLabel);
        forceStopButton = new MaterialButton(this);
        forceStopButton.setAllCaps(false);
        forceStopButton.setTextColor(0xFFFFFFFF);
        forceStopButton.setTextSize(15);
        forceStopButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        forceStopButton.setBackgroundTintList(ColorStateList.valueOf(RED));
        forceStopButton.setOnClickListener(v -> confirmForceStop());
        footer.addView(forceStopButton, marginParams(-1, dp(48), 0, 8, 0, 0));
        root.addView(footer, marginParams(-1, -2, 0, 10, 0, 0));

        setContentView(root);
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
            statusTitle.setText(runningApps.size() + (runningApps.size() == 1 ? " app running" : " apps running"));
            statusDetail.setText(processCount + (processCount == 1 ? " app process" : " app processes")
                    + " detected outside Background Monitor.");
            rootButton.setText(forceStopBusy ? "Stopping selected apps…" : (rootBusy ? "Refreshing…" : "Refresh now"));
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
                addEmptyMessage(query.isEmpty()
                        ? "No background app processes found."
                        : "No running apps match “" + query + "”.");
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
        selectedLabel.setText(selectedCount == 0 ? "Select apps to force stop"
                : selectedCount + (selectedCount == 1 ? " app selected" : " apps selected"));
        forceStopButton.setText(forceStopBusy ? "Force stopping…" : "Force stop selected" + (selectedCount > 0 ? " (" + selectedCount + ")" : ""));
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

    private void confirmForceStop() {
        if (selectedPackages.isEmpty() || forceStopBusy || !rootConnected) return;
        List<RunningApp> chosen = new ArrayList<>();
        for (RunningApp app : runningApps) {
            if (selectedPackages.contains(app.packageName)) chosen.add(app);
        }
        if (chosen.isEmpty()) return;
        StringBuilder names = new StringBuilder();
        for (RunningApp app : chosen) {
            if (names.length() > 0) names.append('\n');
            names.append("• ").append(app.label);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Force stop " + chosen.size() + (chosen.size() == 1 ? " app?" : " apps?"))
                .setMessage(names + "\n\nAndroid may start these apps again when you launch them or when the system needs them.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Force stop", (dialog, which) -> forceStop(chosen))
                .show();
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
        rootWorker.execute(() -> {
            StringBuilder script = new StringBuilder("id; ");
            for (int i = 0; i < packages.size(); i++) {
                String packageName = packages.get(i);
                script.append("/system/bin/am force-stop --user current ").append(packageName)
                        .append("; echo __STOP_RESULT_").append(i).append("=$?; ");
            }
            String error = "";
            int successCount = 0;
            try {
                RootResult result = runSu(script.toString());
                if (!result.output.matches("(?s).*\\buid=0(?:\\b|\\().*")) {
                    throw new IOException("Root permission was denied.");
                }
                Matcher matcher = Pattern.compile("__STOP_RESULT_(\\d+)=(\\d+)").matcher(result.output);
                while (matcher.find()) {
                    if ("0".equals(matcher.group(2))) successCount++;
                }
                if (successCount < packages.size()) {
                    error = "Android accepted " + successCount + " of " + packages.size() + " force-stop requests.";
                }
            } catch (Exception exception) {
                error = readableRootError(exception);
            }
            int success = successCount;
            String operationError = error;
            mainHandler.post(() -> {
                forceStopBusy = false;
                if (!operationError.isEmpty()) {
                    Toast.makeText(this, operationError, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Force-stop requested for " + success + " app(s).", Toast.LENGTH_SHORT).show();
                }
                readProcesses();
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

    private LinearLayout textStack(String title, String subtitle, int titleSize, int subtitleSize) {
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.addView(makeText(title, titleSize, INK, true));
        stack.addView(makeText(subtitle, subtitleSize, MUTED, false), marginParams(-1, -2, 0, 3, 0, 0));
        return stack;
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

