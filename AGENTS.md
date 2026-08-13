# Agent Guide

Android application for the [Zipline Upload Server](https://github.com/diced/zipline).

- `app/` - Android app source
- `gradle/libs.versions.toml` - Library versions
- `Taskfile.yml` - [task](https://github.com/go-task/task) commands

## Commands

ALWAYS use the `task *` commands

| Command        | Purpose                                             |
| -------------- | --------------------------------------------------- |
| `task compile` | Compile Kotlin                                      |
| `task debug`   | Build debug variant (APK)                           |
| `task release` | Build release variant (APK)                         |
| `task bundle`  | Build Android App Bundle (AAB)                      |
| `task lint`    | Prettier check + yamllint + actionlint + shellcheck |
| `task format`  | Prettier write (format non-kotlin files)            |

Do NOT use `-q` or pipe Gradle output through `Select-Object` — both hide progress and make long builds look hung.

## Rules

Do NOT run task compile/debug/release/bundle after making edits unless it is REQUIRED!!!
