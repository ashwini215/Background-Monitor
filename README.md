# Background Monitor

A small, offline Android app for rooted phones. It shows installed app processes that are currently running in the background, lets you select several apps, and asks Android to force-stop the selected packages through `su`.

## What it does

- Reads `dumpsys activity` and the process table through `su`.
- Refreshes every three seconds while the screen is open.
- Groups multiple processes under their app and shows the process count and one PID.
- Uses `am force-stop --user current` for each selected package.
- Has an Exit button that closes the task and terminates this app's process.
- Declares no `INTERNET` permission and includes no networking library or background service.

The first time you press **Connect to root**, approve the request in Magisk, KernelSU, or another `su` manager. If you deny it, use **Retry root access** after granting it in the root manager.

## Build

The project uses Java 17, Android Gradle Plugin 9.3.3, Gradle 9.5.0, `compileSdk 37`, `targetSdk 37`, and `minSdk 26`. The GitHub Actions workflow at `.github/workflows/build-apk.yml` installs the Android 17 SDK, builds a debug APK, and publishes it as the `BackgroundMonitor-debug-apk` artifact. Push this project to a GitHub repository, then run **Actions > Build debug APK > Run workflow**; pushes to `main` or `master` also trigger a build. The build runner needs network access to download the Android tools and Gradle plugin. The installed app itself has no network permission.

You can also open the folder in Android Studio with the Android 17 / API 37 SDK installed and choose **Build > Build APK(s)**. Install the generated `app-debug.apk` on the rooted phone.

## Notes

- Root is required for the process list and force-stop action. Without a working `su` binary and an approved root request, the app shows a root-access error.
- The monitor is intentionally foreground-only: it performs no polling after you leave the screen and has no service, receiver, or scheduled job keeping it alive.
- It groups process names that Android's process table can map to installed packages, and omits unassociated system daemons and the currently resumed app.
- Force stop applies to the app package and all of its processes. Android puts that package in the stopped state; it can run again after a user or system action starts it.
- Package names are taken from Android's package manager and validated before being added to the shell command.
