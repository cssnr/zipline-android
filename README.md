[![GitHub Downloads](https://img.shields.io/github/downloads/cssnr/zipline-android/total?logo=android)](https://github.com/cssnr/zipline-android/releases/latest/download/app-release.apk)
[![GitHub Release Version](https://img.shields.io/github/v/release/cssnr/zipline-android?logo=github)](https://github.com/cssnr/zipline-android/releases/latest)
[![GitHub Docs Last Commit](https://img.shields.io/github/last-commit/cssnr/zipline-android-docs?logo=vitepress&logoColor=white&label=docs)](https://zipline-android.cssnr.com/)
[![GitHub Last Commit](https://img.shields.io/github/last-commit/cssnr/zipline-android?logo=github&label=updated)](https://github.com/cssnr/zipline-android/pulse)
[![Lint](https://img.shields.io/github/actions/workflow/status/cssnr/zipline-android/lint.yaml?logo=cachet&label=lint)](https://github.com/cssnr/zipline-android/actions/workflows/lint.yaml)
[![Release](https://img.shields.io/github/actions/workflow/status/cssnr/zipline-android/release.yaml?logo=cachet&label=release)](https://github.com/cssnr/zipline-android/actions/workflows/release.yaml)
[![AGP Version](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fcssnr%2Fzipline-android%2Frefs%2Fheads%2Fmaster%2Fgradle%2Flibs.versions.toml&query=%24.versions.agp&logo=gradle&label=AGP)](https://github.com/cssnr/zipline-android/blob/master/gradle/libs.versions.toml#L2)
[![GitHub Repo Size](https://img.shields.io/github/repo-size/cssnr/zipline-android?logo=bookstack&logoColor=white&label=repo%20size)](https://github.com/cssnr/zipline-android)
[![GitHub Top Language](https://img.shields.io/github/languages/top/cssnr/zipline-android?logo=htmx)](https://github.com/cssnr/zipline-android)
[![GitHub Discussions](https://img.shields.io/github/discussions/cssnr/zipline-android?logo=github)](https://github.com/cssnr/zipline-android/discussions)
[![GitHub Forks](https://img.shields.io/github/forks/cssnr/zipline-android?style=flat&logo=github)](https://github.com/cssnr/zipline-android/forks)
[![GitHub Repo Stars](https://img.shields.io/github/stars/cssnr/zipline-android?style=flat&logo=github)](https://github.com/cssnr/zipline-android/stargazers)
[![GitHub Org Stars](https://img.shields.io/github/stars/cssnr?style=flat&logo=github&label=org%20stars)](https://cssnr.com/)
[![Discord](https://img.shields.io/discord/899171661457293343?logo=discord&logoColor=white&label=discord&color=7289da)](https://discord.gg/wXy6m2X8wY)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-72a5f2?logo=kofi&label=support)](https://ko-fi.com/cssnr)
[![](https://repository-images.githubusercontent.com/963715375/e18a8ea8-f964-4088-852b-98f51631877f)](https://zipline-android.cssnr.com/)

# Zipline Upload

> [!IMPORTANT]  
> **Google Play Testers Needed!**
> See [this discussion](https://github.com/cssnr/zipline-android/discussions/25) for more details.

- [Install](#Install)
  - [Setup](#Setup)
- [Features](#Features)
  - [Planned](#Planned)
  - [Known Issues](#Known-Issues)
  - [Troubleshooting](#troubleshooting)
- [Screenshots](#Screenshots)
- [Support](#Support)
- [Development](#Development)
  - [Building](#Building)
  - [Android Studio](#Android-Studio)
  - [Command Line](#Command-Line)
- [Contributing](#Contributing)

Zipline Android Client Application to Upload, Share, Download and Manage Files and Short URLs
for the [Zipline](https://github.com/diced/zipline) v4 ShareX Upload Server.
Includes a Native File List for Viewing, Editing and Downloading files locally.
Plus User Management to edit Username, Password, Avatar, TOTP, and more...

Native Kotlin Android Application with a Mobile First Design.
Everything is cached and images are not downloaded over metered connections unless enabled.
User profile and stats widget are updated in the background with a user configurable task.

For more information visit the website: https://zipline-android.cssnr.com/

_We are also developing a browser addon for all major browsers including Firefox Android:
[Zipline Web Extension](https://github.com/cssnr/zipline-extension?tab=readme-ov-file#readme)_

## Install

> [!NOTE]  
> Google Play is in Closed Testing. To be included see [this discussion](https://github.com/cssnr/zipline-android/discussions/25).

[![Get on GitHub](https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/android/get80/github.png)](https://github.com/cssnr/zipline-android/releases/latest/download/app-release.apk)
[![Get on Obtainium](https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/android/get80/obtainium.png)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/cssnr/zipline-android)
[![Get on Google Play](https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/android/get80/google-play.png)](https://play.google.com/store/apps/details?id=org.cssnr.zipline)

<details><summary>📲 Click to View QR Codes 📸 Supports Android 8 (API 26) 2017 +</summary>

[![QR Code GitHub](https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/qr-code-github.png)](https://github.com/cssnr/zipline-android/releases/latest/download/app-release.apk)

[![QR Code Obtainium](https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/qr-code-obtainium.png)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/cssnr/zipline-android)

[![QR Code Google Play](https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/qr-code-google.png)](https://play.google.com/store/apps/details?id=org.cssnr.zipline)

</details>

_Note: If installing directly, you may need to allow installation of apps from unknown sources.  
For more information, see [Release through a website](https://developer.android.com/studio/publish#publishing-website)._

<details><summary>View Manual Steps to Install from Unknown Sources</summary>

Note: Downloading and Installing the [apk](https://github.com/cssnr/zipline-android/releases/latest/download/app-release.apk)
should take you to the settings area to allow installation if not already enabled. Otherwise:

1. Go to your device settings.
2. Search for "Install unknown apps" or similar.
3. Choose the app you will install the apk file from.
   - Select your web browser to install directly from it.
   - Select your file manager to open it, locate the apk and install from there.
4. Download the [Latest Release](https://github.com/cssnr/zipline-android/releases/latest/download/app-release.apk).
5. Open the download apk in the app you selected in step #3.
6. Choose Install and Accept any Play Protect notifications.
7. The app is now installed. Proceed to the [Setup](#Setup) section below.

</details>

### Setup

Setup guides available on the website: https://zipline-android.cssnr.com/

1. [Install](#Install) and open the app on your device.
2. Log in as you normally would on the website.
3. Done! You can now share and open files with Zipline.
4. Optionally add a Stats Widget to your Home Screen.

**To use**, share or open any file(s), text, or URL and choose the Zipline app.  
Preview the item(s), set upload options, verify and submit.  
The results will be shown and copied to the clipboard.

The Files List can be used to view, edit, download, or delete any file or files.

The User Page can be used to edit your profile, avatar, and execute server actions if administrator.

> [!TIP]
> Please [let us know](#support) if you run into any
> [issues](https://github.com/cssnr/zipline-extension/issues).
> **All bugs** that can be reproduced, **will be fixed!**

## Features

Features are documented on the website: [https://zipline-android.cssnr.com/](https://zipline-android.cssnr.com/)

- Share or Open any File, Media, Text or URL
- Preview, Edit and set Options before Uploading
- Native File List with Multi-Select, Edit and Delete
- User and Server Management Page with Avatar Cropper
- Home Screen Widget with File Stats and App Shortcuts
- User Configurable Background Update Task for Stats
- Supports Two-Factor Authentication and Custom Headers

### Planned

- Update User Page UX
- Add Short URL Management
- Add AppBar to Replace Bottom Navigation
- Add Remaining Upload Options (ref: [upload-options](https://zipline.diced.sh/docs/guides/upload-options))
- Improve File List
  - Add Grid View Selector
  - Add Remaining File Options (ref: [file](https://zipline.diced.sh/docs/api/models/file))

### Known Issues

- After deleting files and then scrolling in the file list it skips the number of files deleted when loading.
  - _After deleting files, scroll to the top and pull down to refresh._
- If your sessions get deleted you are logged out of the WebView (Home).
  - _The WebView is being deprecated and future updates will rely less on this._
- Android 8 (API 26-27) crashes when downloading a file due to a permissions error.
  - _Single files can be downloaded from the Home WebView. No work around for bulk downloads._
- Please [Open a New Issue](https://github.com/cssnr/zipline-extension/issues) if you don't see yours.

### Troubleshooting

- Most navigation and display issues can be fixed by fully closing the app and restarting it.
  - If you encounter a reproducible issue please [report it as a bug](https://github.com/cssnr/zipline-extension/issues).
- If you encounter issues with the media in the file list, try clearing the application cache.
- If you encounter issues with authentication, try clearing the application data (resets settings and auth).
- If all of the above fail, try re-installing the application and [let us know what happened](https://github.com/cssnr/zipline-extension/issues).

If you are having trouble using the app, support is available via [GitHub](#support) or [Discord](https://discord.gg/wXy6m2X8wY).

## Screenshots

<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/1.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/1.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/2.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/2.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/3.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/3.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/4.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/4.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/5.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/5.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/6.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/6.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/7.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/7.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/8.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/8.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/9.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/9.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/10.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/10.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/11.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/11.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/12.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/12.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/13.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/13.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/14.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/14.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/15.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/15.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/16.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/16.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/17.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/17.jpg"></a>
<a title="Screenshot" href="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/18.jpg">
    <img alt="Screenshot" src="https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/android/screenshots/18.jpg"></a>

## Support

Documentation: https://zipline-android.cssnr.com/

For general help or to request a feature, see:

- Q&A Discussion: https://github.com/cssnr/zipline-android/discussions/categories/q-a
- Request a Feature: https://github.com/cssnr/zipline-android/discussions/categories/feature-requests

If you are experiencing an issue/bug or getting unexpected results, you can:

- Report an Issue: https://github.com/cssnr/zipline-android/issues
- Chat with us on Discord: https://discord.gg/wXy6m2X8wY
- Provide General Feedback: [https://cssnr.github.io/feedback/](https://cssnr.github.io/feedback/?app=Zipline%20Android%20App)

# Development

This section briefly covers running and building in [Android Studio](#Android-Studio) and the [Command Line](#Command-Line).

To update the Website/Docs go here: https://github.com/cssnr/zipline-android-docs

## Building

To build the app you must first add a [Google Services](#Google-Services) file and optionally prepare highlightjs.

1. Building this app requires a valid `app/google-services.json` file. For more info see [Google Services](#Google-Services).

2. To build the text preview run `bash .github/scripts/prepare.sh` or manually add highlightjs to:  
   `assets/preview/dist`

Proceed to [Android Studio](#Android-Studio) or [Command Line](#Command-Line) below.

## Android Studio

1. Download and Install Android Studio: https://developer.android.com/studio

2. Ensure that usb or wifi debugging is enabled in the Android developer settings and verify.

3. Then build or run the app on your device.
   - Import the Project
   - Run Gradle Sync

To Run: Select a device and press Play ▶️

To Build:

- Select the Build Variant (debug or release)
- Build > Generate App Bundles or APK > Generate APKs

## Command Line

> [!WARNING]  
> This section may not be complete!
> For more details see the [release.yaml](.github/workflows/release.yaml).

You will need to have [ADB](https://developer.android.com/tools/adb) installed.

<details><summary>Click Here to Download and Install a Release</summary>

```shell
$ wget https://github.com/cssnr/zipline-android/releases/latest/download/app-release.apk
$ ls
app-release.apk

$ which adb
C:\Users\Shane\Android\sdk\platform-tools\adb.EXE

$ adb devices
List of devices attached
RF9M33Z1Q0M     device

$ adb -s RF9M33Z1Q0M install app-release.apk
Performing Incremental Install
Serving...
All files should be loaded. Notifying the device.
Success
Install command complete in 917 ms
```

See below for more details...

</details>

1. Download and Install the Android SDK Platform Tools.

https://developer.android.com/tools/releases/platform-tools#downloads

Ensure that `adb` is in your PATH.

2. List and verify the device is connected with:

```shell
$ adb devices
List of devices attached
RF9M33Z1Q0M     device
```

3. Build a debug or release apk.

```shell
./gradlew assemble
./gradlew assembleRelease
```

_Note: Use `gradlew.bat` for Windows._

4. Then install the apk to your device with adb.

```shell
$ cd app/build/outputs/apk/debug
$ adb -s RF9M33Z1Q0M install app-debug.apk
```

```shell
$ cd app/build/outputs/apk/release
$ adb -s RF9M33Z1Q0M install app-release-unsigned.apk
```

_Note: you may have to uninstall before installing due to different certificate signatures._

For more details, see the [ADB Documentation](https://developer.android.com/tools/adb#move).

## Google Services

This app uses Firebase Google Services. Building requires a valid `google-services.json` file in the `app` directory.  
You must add `org.cssnr.zipline` to a Firebase campaign here: https://firebase.google.com/

To enable/disable Firebase DebugView use the following commands:

```shell
# set
adb shell setprop debug.firebase.analytics.app org.cssnr.zipline

# unset
adb shell setprop debug.firebase.analytics.app .none.

# check
adb shell getprop debug.firebase.analytics.app
```

Only 1 app can be in debug mode at a time and this must be set every restart.

Note: Firebase is disabled in debug builds.
See the `manifestPlaceholders` in the [build.gradle.kts](app/build.gradle.kts) file debug config.

# Contributing

All contributions are welcome including [bug reports](https://github.com/cssnr/zipline-extension/issues),
[feature requests](https://github.com/cssnr/zipline-android/discussions/categories/feature-requests),
or [pull requests](https://github.com/cssnr/zipline-extension/discussions) (please start a discussion).

### Zipline Projects

- [Zipline Web Extension](https://github.com/cssnr/zipline-extension?tab=readme-ov-file#readme)

[![Screenshot](https://raw.githubusercontent.com/smashedr/repo-images/refs/heads/master/zipline/extension/screenshot.jpg)](https://github.com/cssnr/zipline-extension?tab=readme-ov-file#readme)

- [Zipline CLI](https://github.com/cssnr/zipline-cli?tab=readme-ov-file#readme) - _Only Supports v3_

### Related Projects

You can also star this project on GitHub and support other related projects:

- [Django Files Server](https://github.com/django-files/django-files?tab=readme-ov-file#readme)
- [Django Files iOS App](https://github.com/django-files/ios-client?tab=readme-ov-file#readme)
- [Django Files Android App](https://github.com/django-files/android-client?tab=readme-ov-file#readme)
- [Django Files Web Extension](https://github.com/django-files/web-extension?tab=readme-ov-file#readme)

Additional Android projects:

- [NOAA Weather Android](https://github.com/cssnr/noaa-weather-android?tab=readme-ov-file#readme)
- [Remote Wallpaper Android](https://github.com/cssnr/remote-wallpaper-android?tab=readme-ov-file#readme)
- [Tibs3DPrints Android](https://github.com/cssnr/tibs3dprints-android?tab=readme-ov-file#readme)

Please consider making a donation to support the development of this project
and [additional](https://cssnr.com/) open source projects.

[![Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/cssnr)

For a full list of current projects visit: [https://cssnr.github.io/](https://cssnr.github.io/)
