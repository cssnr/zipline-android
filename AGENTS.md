# Agent Guide

Android client for the [Zipline Upload Server](https://github.com/diced/zipline).

- `app/` - Android app source
- `gradle/libs.versions.toml` - Library versions
- `Taskfile.yml` - [task](https://github.com/go-task/task) commands

## Android

- applicationId = org.cssnr.zipline.dev
- minSdk = 26
- targetSdk = 36
- compileSdk = 37

## Commands

ALWAYS use the `task *` commands

| Command        | Purpose                                  |
| -------------- | ---------------------------------------- |
| `task lint`    | Gradle Lint - DO NOT RUN                 |
| `task compile` | Compile Kotlin                           |
| `task debug`   | Build debug variant (APK)                |
| `task release` | Build release variant (APK)              |
| `task bundle`  | Build Android App Bundle (AAB)           |
| `task check`   | Prettier check (check non-kotlin files)  |
| `task format`  | Prettier write (format non-kotlin files) |

Do NOT run task lint/compile/debug/release/bundle every turn unless it is REQUIRED!!!

## Testing

To test on a device use the `adb` command. If no devices are running and attached, ask the user to do this!

DO NOT uninstall the application to clear data, use: `adb shell pm clear`
