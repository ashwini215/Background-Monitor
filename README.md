<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Background Monitor

Background Monitor is a small, offline Android utility for rooted phones. It shows app processes currently running in the background, lets you search and multi-select apps, and asks Android to force stop the selected packages through root access.

## What the app does

- Shows installed apps with detected background processes, app icons, process counts, and process IDs.
- Refreshes automatically every three seconds while the app is open; the toolbar also has a manual refresh action.
- Searches by app label or package name.
- Filters the process list to all apps, user-installed apps, or system apps; search and Select visible follow the chosen filter.
- Selects all visible search results or individual apps, then sends a force-stop request with one tap.
- Keeps the running-app list as the largest area of the screen and provides a dedicated Settings screen.
- Offers System, Light, Dark, and wallpaper-based Dynamic color themes.
- Shows app version, developer, package ID, GPL v3 license, source repository, requirements, and force-stop behavior in Settings.
- Checks processes only while the screen is open. It does not run a service or keep polling after you leave.
- Has no internet permission, network client, analytics, or remote service.
- Includes an Exit action that closes the task and terminates the monitor process.

The process list is a live snapshot, not a permanent list of every Android service. It maps process names to installed app packages and omits the app currently in the foreground and system processes that cannot be mapped to an installed app.

## What you need

- A rooted Android device running Android 8.0 / API 26 or newer.
- A working su root manager, such as Magisk or KernelSU.
- Root permission approved for Background Monitor when its first request appears.
- Android 12 or newer for wallpaper-based Dynamic colors. System, Light, and Dark themes work on all supported Android versions.

The app declares QUERY_ALL_PACKAGES so it can match process names against installed apps. It does not declare INTERNET.

Force stop asks Android to stop each selected package for the current user and put it in the stopped state. On Android 15 and newer, it should stay stopped until a user action opens or interacts with it, such as launching the app or using one of its widgets. This monitor does not keep running after you exit to block future launches. Vendor-modified Android builds may behave differently.

## Build a signed release APK on GitHub

See [GITHUB_RELEASE_GUIDE.md](GITHUB_RELEASE_GUIDE.md) for the signing-key setup, repository secrets, workflow run, and APK download steps. The release workflow is .github/workflows/release-apk.yml.

GitHub Actions builds version 1.3.0 with Java 17, Gradle 9.5, Android Gradle Plugin 9.3.3, and the Android 17 preview SDK (API 37). The Android 17 SDK requires the preview channel during SDK installation. The APK itself runs offline.

## Source and acknowledgement

This is an independent, minimal implementation. Its feature concept was inspired by [App Manager](https://github.com/MuntashirAkon/AppManager) by Muntashir Al-Islam, a much larger Android package-management application. No App Manager source files or binaries are included in this repository.

## License

Copyright (C) 2026 Ashwini215

This project is licensed under the GNU General Public License, version 3 only (GPL-3.0-only). See [LICENSE](LICENSE) for the complete terms. Third-party dependency notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
