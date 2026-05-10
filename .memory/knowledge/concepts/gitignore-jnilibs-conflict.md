---
title: "Global *.so gitignore Conflicts with Android jniLibs"
aliases: [gitignore-so-conflict, jnilibs-gitignore, so-files-tracked]
tags: [android, native, git, gotcha, build]
sources:
  - "daily/2026-05-08.md"
created: 2026-05-08
updated: 2026-05-08
---

# Global *.so gitignore Conflicts with Android jniLibs

Python project templates commonly add `*.so` to `.gitignore` to exclude compiled extension modules. When an Android project inherits this pattern (e.g., from a monorepo or template that combines Python and Android), the rule silently prevents `engine-*/src/main/jniLibs/**/*.so` files from being tracked, even though checked-in SO files are required for engines that ship their native binaries directly.

## Key Points

- Global or root-level `*.so` in `.gitignore` blocks all `.so` files including `jniLibs/` — no warning, files silently untracked
- Fix: add exception rule `!engine-warp/src/main/jniLibs/**/*.so` (or `!**/jniLibs/**/*.so`) after the `*.so` rule
- `git status` will not show missing SO files as untracked — they are silently ignored, not reported
- Verify with `git ls-files --error-unmatch path/to/file.so` to confirm tracking
- This only matters when SO files are checked in directly (vs Maven/AAR); checked-in SO is the pattern for verified native binaries from reference implementations

## Details

### The Python Template Origin

Python C extensions compile to `.so` files (on Linux/Mac) that should not be version-controlled — they are build artifacts. The convention to ignore them is so standard it appears in GitHub's Python `.gitignore` template and many project generators. When an Android project reuses such a template or shares a root `.gitignore` with a Python project, the `*.so` rule applies globally, including to Android's `jniLibs/` directory.

The problem is silent: `git add engine-warp/src/main/jniLibs/arm64-v8a/libam-go.so` appears to succeed but the file is never staged. `git status` shows clean working tree. The committed code references the SO path, but the file is absent from the repository. Anyone cloning the repo gets a build that fails at link time.

### Detection

```bash
git ls-files --error-unmatch engine-warp/src/main/jniLibs/arm64-v8a/libam-go.so
```

If this returns exit code 1 with "did not match any file(s) known to git", the file is not tracked — either it was never added, or a `.gitignore` rule is blocking it.

### Fix Pattern

In `.gitignore`, exceptions use `!` prefix and must appear AFTER the blocking rule:

```gitignore
# Python artifacts
*.so

# Android checked-in native libraries (exception)
!engine-warp/src/main/jniLibs/**/*.so
```

The exception is path-specific to avoid accidentally tracking other `.so` files. A broader exception `!**/jniLibs/**/*.so` works but is less precise.

### When Checked-In SO Is Appropriate

Checking SO files directly into the repository is appropriate when:
- The binary is from a trusted reference implementation with a verified SHA256
- The Maven/AAR source cannot be trusted to produce the same binary (as in the `amneziawg` case where Maven and PORTAL_WG binaries diverged)
- The build pipeline for the SO is complex or platform-constrained (e.g., requires specific Go version + NDK)

In all other cases, prefer Maven/AAR or building in CI via documented script. Checked-in SO bypasses the normal dependency management chain and requires manual updates.

## Related Concepts

- [[concepts/amneziawg-so-binary-integrity]] - The migration that required checking in SO files and triggered this gitignore issue
- [[concepts/release-stub-gate]] - Another case where a file's presence/absence in the APK requires explicit verification
- [[connections/native-aar-build-convergence]] - Alternative to checked-in SO: build pipeline producing AARs in CI

## Sources

- [[daily/2026-05-08.md]] - Session 12:18: global `*.so` gitignore (Python template) silently blocked engine-warp jniLibs SO files; fix = `!engine-warp/src/main/jniLibs/**/*.so` exception added
