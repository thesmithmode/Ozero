---
title: "Shell printf: $@ Excludes $0 (Binary Path)"
aliases: [printf-argv0, shell-dollar-zero, printf-positional-args]
tags: [shell, testing, gotcha]
sources:
  - "daily/2026-05-14 (1).md"
created: 2026-05-27
updated: 2026-05-27
---

# Shell printf: $@ Excludes $0 (Binary Path)

In shell scripts, `$@` expands to positional arguments only (i.e., `$1 $2 $3 ...`). It does NOT include `$0` — the script path or binary name. A fake shell script using `printf '%s\n' "$@"` will never print the binary path as the first line, making any test that checks `args.first().endsWith("binary")` fail permanently.

## Key Points

- `$@` = positional arguments ($1, $2, …) — does NOT include $0
- `$0` = path to the script/binary itself
- Fake scripts that echo all arguments for test verification must use `printf '%s\n' "$0" "$@"` to include the binary path
- A test checking `args.first().endsWith("libX.so")` will always fail if the fake script uses `"$@"` only
- This class of bug is undetectable by inspection if you don't know the $0/$@ distinction

## Details

### The MtgWrapperArgsTest Incident (2026-05-14)

`MtgWrapperArgsTest > should use binary from nativeLibraryDir as first arg` failed every run. The test created a fake shell script:

```bash
#!/bin/sh
printf '%s\n' "$@"
```

Then checked:
```kotlin
val lines = runProcess(fakeBinary, args).outputLines()
assertThat(lines.first()).endsWith("libmtg.so")
```

The first line of output was always the first actual argument (not the binary path), so `lines.first()` was never a path ending in `.so`. The test could never pass regardless of argument ordering or binary path.

Fix:
```bash
#!/bin/sh
printf '%s\n' "$0" "$@"
```

With `"$0"` included, the first line of output is the path to the fake script. Since the test passes the binary path as `$0` via `ProcessBuilder`, the assertion `endsWith("libmtg.so")` succeeds.

### Application Pattern

When writing fake scripts for test verification of argument ordering, always include `$0` explicitly:

```bash
#!/bin/sh
# Prints all arguments including script path, one per line
printf '%s\n' "$0" "$@"
```

This pattern is used when:
- Testing that a binary path is passed as the first argument to `ProcessBuilder`
- Verifying command-line argument assembly in wrappers like `MtgWrapper`, `ByeDpiLauncher`, etc.
- Any test that asserts on the contents or ordering of `ProcessBuilder` command list

## Related Concepts

- [[concepts/byedpi-args-parsing]] - Related: ByeDPI argument parsing where argv[0] is set by native-lib.c; Kotlin must NOT prepend a binary path
- [[concepts/byedpi-mock-server-ci-fragility]] - Related test infrastructure trap in the same ByeDPI/MTProxy test suite

## Sources

- [[daily/2026-05-14 (1).md]] - Session 17:xx: `MtgWrapperArgsTest > should use binary from nativeLibraryDir as first arg` always failed; fake script `printf '%s\n' "$@"` excluded $0; fix = `printf '%s\n' "$0" "$@"`
