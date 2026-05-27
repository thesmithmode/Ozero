---
title: "Windows GitHub Runner gradlew Shell Trap"
aliases: [windows-gradlew, pwsh-gradlew, windows-runner-shell]
tags: [github-actions, windows, gradle, ci]
sources:
  - "daily/2026-05-27.md"
created: 2026-05-27
updated: 2026-05-27
---

# Windows GitHub Runner gradlew Shell Trap

The default shell on Windows GitHub runners is PowerShell Core (`pwsh`). In PowerShell, `./gradlew` does not automatically resolve to `gradlew.bat` — it attempts to execute a file with no extension, which fails. Linux and macOS runners use `bash` by default where `./gradlew` correctly invokes the shell script. The fix is to add `shell: bash` to any step that calls `./gradlew` on a Windows runner.

## Key Points

- Windows GitHub runner default shell: `pwsh` (PowerShell Core 7+)
- `./gradlew` in pwsh: looks for a file named literally `gradlew` without extension — not found
- `./gradlew` in bash: invokes `gradlew` shell script — works correctly
- Fix: add `shell: bash` to the step (Git Bash is available on all Windows GitHub runners)
- `.\gradlew.bat` also works as an alternative if staying in PowerShell
- Linux/macOS steps using `./gradlew` do NOT need `shell: bash` — it's already the default

## Details

When a GitHub Actions workflow builds an Android project on both Linux and Windows, the `./gradlew assembleRelease` command works on Linux/macOS steps without any `shell:` declaration because those runners default to bash. The same syntax fails on Windows because the PowerShell resolver does not apply the OS-level `PATHEXT`-style extension resolution for `./path` syntax.

The failure manifests as `./gradlew : The term './gradlew' is not recognized` or a "file not found" error, depending on the PowerShell version. This is distinct from a Gradle build failure — it happens before Gradle starts at all.

The most portable fix is `shell: bash` on the Windows step. All Windows GitHub runners include Git Bash as part of the Git for Windows installation, so `shell: bash` is always available. The alternative of using `.\gradlew.bat` in PowerShell syntax requires changing the command itself and scattering platform-specific logic throughout the workflow.

Note that `shell: bash` on Windows runner runs commands through Git Bash (`C:\Program Files\Git\bin\bash.exe`), which has its own path handling quirks — notably, Windows paths must use forward slashes or escaped backslashes. For simple Gradle invocations this is not an issue.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - platform-specific CI configuration discipline
- [[concepts/gradle-continue-full-failures]] - other Gradle CI configuration patterns
- [[concepts/gradle-r8-oom-github-runners]] - companion OOM issue on Windows/macOS runners
- [[concepts/release-process]] - Windows build is part of the multi-platform release

## Sources

- [[daily/2026-05-27.md]] - run 5 of release pipeline: Windows job failed because `./gradlew` in pwsh step could not find the executable; fix: `shell: bash` added to Windows Gradle step
