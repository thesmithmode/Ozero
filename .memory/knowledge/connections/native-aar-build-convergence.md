---
title: "Connection: Native AAR Build Pipeline Convergence"
connects:
  - "concepts/gomobile-bind-gotchas"
  - "concepts/urnetwork-sdk-integration"
  - "concepts/xray-aar-build-research"
sources:
  - "daily/2026-05-01.md"
created: 2026-05-01
updated: 2026-05-01
---

# Connection: Native AAR Build Pipeline Convergence

## The Connection

Ozero integrates multiple Go-based VPN engines (AmneziaWG, Hysteria2, Xray, URnetwork) through a shared `gomobile bind` → AAR pipeline. All engines share the same Dockerfile infrastructure, the same gomobile toolchain, and encounter the same class of build failures. Lessons learned from one engine's build directly prevent failures in subsequent engines.

## Key Insight

The non-obvious relationship is that build infrastructure knowledge is transferable across engines but the gotchas are not self-documenting. The URnetwork SDK build encountered three failures (dependency miss, wrapper export trap, javac encoding) that the Xray AAR build research had already partially anticipated (reproducible builds, gomobile cache, size concerns) but from a different angle. The Dockerfile `ANDROID_CMDLINE_TOOLS_SHA256` undocumented requirement affects all engines equally but was only discovered during the URnetwork build.

This means that each new engine integration serves as a regression test for the shared build infrastructure. The `build-tools/Dockerfile` with its undocumented env requirements, the gomobile version pinning, and the AAR caching strategy in CI are all shared failure surfaces. A fix to the Dockerfile for URnetwork benefits Xray, Hysteria2, and AmneziaWG builds simultaneously.

## Evidence

- `build_amneziawg.sh`, `build_hysteria2.sh`, and the new URnetwork SDK build all use the same Dockerfile base image with Go + NDK r27 + gomobile
- The `ANDROID_CMDLINE_TOOLS_SHA256` env requirement was undocumented and broke the URnetwork build — it would have broken any other AAR build on a fresh CI runner
- The wrapper-vs-direct-bind lesson from URnetwork (bind the real package, not a thin wrapper) applies directly to the Xray build where the binding target selection is equally critical
- All engines output to the same `engine-*/libs/` directory pattern with SHA256 verification

## Related Concepts

- [[concepts/gomobile-bind-gotchas]] - The common failure patterns shared across all gomobile-based engine builds
- [[concepts/urnetwork-sdk-integration]] - The most recent engine to traverse the pipeline, revealing new failure modes
- [[concepts/xray-aar-build-research]] - The next engine planned for this pipeline, benefiting from URnetwork lessons
