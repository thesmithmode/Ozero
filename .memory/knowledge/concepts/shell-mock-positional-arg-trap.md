---
title: "Shell Script Mock $0 Positional Argument Trap"
aliases: [printf-argv0-trap, shell-mock-first-arg, binary-path-in-args]
tags: [testing, shell, gotcha, android]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# Shell Script Mock $0 Positional Argument Trap

When writing shell script test mocks that capture arguments via `$@`, the script's own path (`$0`) is NOT included in `$@`. If a test asserts that the first argument is the binary executable path (e.g., `args.first().endsWith("libmtg.so")`), the assertion always fails because `$@` starts from the first explicit argument, not from `$0`. The fix: `printf '%s\n' "$0" "$@"` prints `$0` (the script/binary path) as the first output line, followed by all explicit arguments.

## Key Points

- `$@` in shell = all positional parameters passed to the script, NOT including `$0` (script name/path)
- `printf '%s\n' "$@"` → prints only explicit arguments; binary path never appears in output
- `printf '%s\n' "$0" "$@"` → prints script path first, then all explicit arguments
- Test pattern: fake shell script echoes its invocation; `$0` is what the calling code passes as `argv[0]` (the binary path)
- Symptom: `args.first().endsWith("libmtg.so")` always fails even when binary path is correctly passed by production code

## Details

### The $0 vs $@ Distinction

In POSIX shell, positional parameters have a specific numbering:
- `$0` — the name/path of the script or function being executed
- `$1`, `$2`, ... — explicit arguments passed to the script
- `$@` — expands to all of `$1`, `$2`, ... (NOT `$0`)

When a process launches a script with `execve("/path/to/libmtg.so", ["binary", "arg1", "arg2"], envp)`, the shell inside the script sees `$0="binary"`, `$1="arg1"`, `$2="arg2"`. For shell scripts used as binary mocks in tests, `$0` is the script file path itself — exactly what production code passes as the binary path.

### The Ozero MtgWrapperArgsTest Incident (2026-05-14)

`MtgWrapperArgsTest` verified that `MtgWrapper` correctly sets the binary path as the first element when building the `ProcessBuilder` argument list for `libmtg.so`. The test used a fake shell script as the "binary" mock:

```bash
#!/bin/sh
printf '%s\n' "$@"  # BROKEN: $0 not included in output
```

The test assertion:
```kotlin
assertTrue(args.first().endsWith("libmtg.so"))
```

Since `$@` excludes `$0`, the captured output started with the first explicit argument (e.g., a port number or flag), not the binary path. The test could never pass regardless of how `MtgWrapper` was implemented — the mock script was broken, not the production code.

The fix:
```bash
#!/bin/sh
printf '%s\n' "$0" "$@"  # CORRECT: $0 (binary path) first, then all args
```

### When This Trap Appears

Any shell script test mock that:
1. Captures argument lists for assertion via `$@`
2. Mocks native binary execution where `argv[0]` (the binary path) is checked
3. Logs invocation details where the binary name/path matters

The test mock must mirror what the production process sees — including the binary path at position 0. Production code using `ProcessBuilder` always sets `argv[0]` to the binary path; a `$@`-only mock silently drops this critical element.

## Related Concepts

- [[concepts/byedpi-args-parsing]] — `argv[0]` as program name is the same issue at the ByeDPI level: `getopt_long` skips `argv[0]`, so the first real flag must be at `argv[1]`; double argv[0] trap documented
- [[concepts/byedpi-mock-server-ci-fragility]] — Related: mock test infrastructure design bugs causing tests to always fail or always pass; both are mock correctness issues

## Sources

- [[daily/2026-05-14.md]] — Session 17:xx: `MtgWrapperArgsTest > should use binary from nativeLibraryDir as first arg` always failed; root cause = fake script used `printf '%s\n' "$@"` (no $0); test checked `args.first().endsWith("libmtg.so")`; fix = `printf '%s\n' "$0" "$@"`
