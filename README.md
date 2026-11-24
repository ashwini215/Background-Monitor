<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Background Monitor

Background Monitor is a small, offline Android utility for rooted phones. It shows app processes currently running in the background, lets you search and multi-select apps, and asks Android to force stop the selected packages through root access.

## What the app does

- Shows installed apps with detected background processes, app icons, process counts, and process IDs.
- Refreshes automatically every three seconds while the app is open; the toolbar also has a manual refresh action.
- Searches by app label or package name.
- Selects all visible search results or individual apps, then confirms before sending force-stop requests.
- Uses Material 3 components and Android dynamic colors on Android 12 and newer.
- Checks processes only while the screen is open. It does not run a service or keep polling after you leave.
- Has no internet permission, network client, analytics, or remote service.
- Includes an Exit action that closes the task and terminates the monitor process.

The process list is a live snapshot, not a permanent list of every Android service. It maps process names to installed app packages and omits the app currently in the foreground and system processes that cannot be mapped to an installed app.

## What you need

- A rooted Android device running Android 8.0 / API 26 or newer.
- A working su root manager, such as Magisk or KernelSU.
- Root permission approved for Background Monitor when its first request appears.
- Android 12 or newer for wallpaper-based Material You colors. Earlier supported Android versions use the theme fallback palette.

The app declares QUERY_ALL_PACKAGES so it can match process names against installed apps. It does not declare INTERNET.

Force stop asks Android to stop each selected package for the current user. Android may start a package again after you launch it, receive a system request, or reboot the phone. Force stop is not an uninstall, disable, or permanent background restriction.

## Build a signed release APK on GitHub

See [GITHUB_RELEASE_GUIDE.md](GITHUB_RELEASE_GUIDE.md) for the signing-key setup, repository secrets, workflow run, and APK download steps. The release workflow is .github/workflows/release-apk.yml.

GitHub Actions builds with Java 17, Gradle 9.5, Android Gradle Plugin 9.3.3, and the Android 17 preview SDK (API 37). The Android 17 SDK requires the preview channel during SDK installation. The APK itself runs offline.

## Source and acknowledgement

This is an independent, minimal implementation. Its feature concept was inspired by [App Manager](https://github.com/MuntashirAkon/AppManager) by Muntashir Al-Islam, a much larger Android package-management application. No App Manager source files or binaries are included in this repository.

## License

Copyright (C) 2026 Ashwini215

This project is licensed under the GNU General Public License, version 3 only (GPL-3.0-only). See [LICENSE](LICENSE) for the complete terms. Third-party dependency notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

