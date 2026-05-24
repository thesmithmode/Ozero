---
title: "Android Build-Tools Dockerfile ENV Propagation Trap"
aliases: [dockerfile-sha256-env, build-tools-env-missing, android-cmdline-tools-sha]
tags: [android, ci, docker, build, gotcha]
sources:
  - "daily/2026-05-01.md"
created: 2026-05-01
updated: 2026-05-01
---

# Android Build-Tools Dockerfile ENV Propagation Trap

The `build-tools/Dockerfile` in Ozero's native binary build pipeline requires `ANDROID_CMDLINE_TOOLS_SHA256` to be passed explicitly as a build argument or environment variable. If the variable is not forwarded from the CI workflow context, the Dockerfile either uses a wrong/empty value, fails to verify the downloaded SDK, or uses a stale cached layer — causing the AAR build job to fail with a non-obvious error.

## Key Points

- `ANDROID_CMDLINE_TOOLS_SHA256` is not hardcoded in the Dockerfile — it must be passed by the caller (GitHub Actions workflow or local `docker build --build-arg`)
- Missing env = silent wrong behavior: Docker may use a stale cache layer or fail SHA256 verification, depending on the fallback
- The failure manifests in the AAR build job, not in the Dockerfile build step itself — making the root cause non-obvious
- Fix: explicitly forward the variable in the workflow step that invokes `docker build` or `docker run`
- This was discovered during the URnetwork SDK AAR build attempt in Session 15:24 (2026-05-01)

## Details

### The Propagation Gap

`build-tools/Dockerfile` is designed to download and verify Android command-line tools using a SHA256 checksum. The SHA256 value varies by SDK version and must be sourced from the workflow configuration or a shared constants file. The Dockerfile does not provide a default value for this variable.

When invoked from GitHub Actions without the explicit `--build-arg ANDROID_CMDLINE_TOOLS_SHA256=...` or `env:` block, Docker receives an empty string. The typical failure modes:

1. **SHA256 verification fails**: Docker `RUN` command computes SHA256 of the downloaded archive, compares to empty string, fails immediately
2. **Stale cache hit**: If a prior layer is cached with a different (previously correct) SHA256, Docker uses the cached layer. The Dockerfile appears to succeed but the installed SDK version is wrong

### Detection Pattern

The bug surfaces not in the Dockerfile build output but in subsequent AAR compilation:
- `gomobile bind` or `ndkBuild` may reference SDK tools from the wrong version
- Error messages reference missing or incompatible `build-tools/` subdirectories
- The failed job is the AAR generation job, not `docker build`

This indirection is what makes the trap non-obvious: developers look for the problem in the Go build or gomobile command, not in SDK installation.

### Fix Pattern

In the GitHub Actions workflow:

```yaml
- name: Build native AAR
  env:
    ANDROID_CMDLINE_TOOLS_SHA256: ${{ vars.ANDROID_CMDLINE_TOOLS_SHA256 }}
  run: docker build --build-arg ANDROID_CMDLINE_TOOLS_SHA256=$ANDROID_CMDLINE_TOOLS_SHA256 build-tools/
```

Or by storing the SHA256 as a repository variable/secret and referencing it consistently across all jobs that invoke the Dockerfile.

## Related Concepts

- [[concepts/native-binary-auto-update-pipeline]] - The broader native AAR build pipeline that uses this Dockerfile
- [[concepts/gomobile-bind-gotchas]] - gomobile bind failures that surface after the SDK is (mis)installed
- [[concepts/ci-workflow-discipline]] - CI workflow structure where env propagation gaps hide failures

## Sources

- [[daily/2026-05-01.md]] - Session 15:24: URnetwork AAR build: `build-tools/Dockerfile` fell because `ANDROID_CMDLINE_TOOLS_SHA256` env not forwarded → fix applied; prior 3 gomobile iterations had masked this as a different failure
