package com.local.taskwatch;

/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Ashwini215
 */
import android.app.Application;

import com.google.android.material.color.DynamicColors;

/** Applies the user's Android wallpaper colors to Material 3 components on Android 12+. */
public final class BackgroundMonitorApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}

