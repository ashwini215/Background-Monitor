<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Build and download the signed release APK

The release workflow signs the APK with your own key. Do not put the keystore or its passwords in the repository. The workflow reads them from GitHub Actions repository secrets.

## 1. Create a signing key on Windows

You need Java's keytool, included with JDK 17. Open PowerShell and run:

~~~powershell
keytool -genkeypair -v -keystore "$env:USERPROFILE\background-monitor-release.p12" -storetype PKCS12 -alias background-monitor -keyalg RSA -keysize 4096 -validity 10000
~~~

Choose and record a strong keystore password. When prompted for the key password, you may use the same password. Keep the alias background-monitor, or record the different alias if you change it.

Make an offline backup of the .p12 file and its passwords. If you lose this key, you will not be able to publish an update that installs over an APK signed with this key.

## 2. Add four repository secrets on GitHub

1. Open your repository and choose **Settings**.
2. Open **Secrets and variables > Actions**.
3. Choose **New repository secret**.
4. Add each secret below. Do not paste the passwords into an issue or source file.

| Secret name | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64 text for the .p12 signing key (instructions below) |
| ANDROID_KEYSTORE_PASSWORD | Password you chose for the keystore |
| ANDROID_KEY_ALIAS | background-monitor |
| ANDROID_KEY_PASSWORD | Password for the key (same as the store password if you chose that) |

To copy the signing key as Base64 from PowerShell:

~~~powershell
$keystore = Join-Path $env:USERPROFILE "background-monitor-release.p12"
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore)) | Set-Clipboard
~~~

Paste the clipboard into the ANDROID_KEYSTORE_BASE64 secret form and save it.

## 3. Confirm the source version

Before building, open **Code > app > build.gradle** in the repository and confirm that `versionName` is `1.3.1` and `versionCode` is `5`. The updated source must be on `main`; there is no need to replace files through GitHub's website for this release.

## 4. Run the release build

1. Open the repository's **Actions** tab.
2. Choose **Build signed release APK**.
3. Choose **Run workflow**, select main, then confirm.
4. When the run completes with a green check, open it and download the BackgroundMonitor-release-apk artifact.
5. Extract the downloaded artifact ZIP to get app-release.apk.

You can also push a tag beginning with v (for example, `v1.3.1`) to run the release workflow.

## 5. Install it

The release APK is signed with your key and can be installed directly. The debug APK you tested was signed with a different debug key, so uninstall that debug copy before installing the first release APK. Later release APKs signed with this same key can update one another.

On the phone, allow installation from the browser or file manager you use, then open app-release.apk. Launch Background Monitor and approve its root request in Magisk, KernelSU, or your root manager.

## Optional: publish a GitHub Release

The workflow creates a downloadable Actions artifact. To publish it on the repository's Releases page, download and extract the artifact, choose **Releases > Draft a new release**, create a tag such as `v1.3.1`, attach `app-release.apk`, and publish the release.
