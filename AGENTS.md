# Agent Guide

Android application for the [Zipline Upload Server](https://github.com/diced/zipline).

- `app/` - Android app source (Kotlin, Gradle)
- `Taskfile.yml` - task commands (go-task/task)

## Commands

ALWAYS use the `task *` commands

| Command        | Purpose                                             |
| -------------- | --------------------------------------------------- |
| `task compile` | Compile Kotlin (quick check)                        |
| `task build`   | Build all variants (APKs)                           |
| `task release` | Build release variant (APK)                         |
| `task lint`    | Prettier check + yamllint + actionlint + shellcheck |
| `task format`  | Prettier write (format non-kotlin files)            |

Do NOT use `-q` or pipe Gradle output through `Select-Object` — both hide progress and make long builds look hung.
