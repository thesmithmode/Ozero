---
title: Runtime API Contract Review Release Loop
sources:
  - daily/2026-05-02.md
created: 2026-06-12
updated: 2026-06-12
---
# Runtime API Contract Review Release Loop

## Key Points
- Runtime regressions on 2026-05-02 were resolved by proving API contracts rather than padding timeouts alone.
- URnetwork, WARP, and Android fd cleanup each failed at a boundary where assumed API shape differed from real API shape.
- Code/security review supplied the initial risk list, but fixes still required local evidence from logs, bytecode, source, and CI.
- Release confidence came from merging fixes, watching the correct CI workflow, tagging, and then checking release build output.

## Details

The day connected several different-looking failures through one pattern: the implementation assumed APIs that were not real. URnetwork assumed `getNetworkSpace` created or returned a first-run space; bytecode showed creation required `importNetworkSpaceFromJson`. WARP assumed mirrors returned raw WireGuard config; the real contract returned JSON with Base64 content. Android cleanup assumed `Os.close(Int)` existed; the correct Java-layer pattern was `ParcelFileDescriptor.adoptFd(rawFd).close()`.

Review findings were useful as triage, but they were not sufficient by themselves. The durable loop was evidence first, fix at the owning boundary, update audit/backlog for non-blockers, then prove through the intended CI and release workflows. This links runtime API grounding with CI watcher discipline and avoids confusing review severity, release readiness, and runtime proof.

## Related Concepts
- [[concepts/urnetwork-networkspace-init]]
- [[concepts/warp-config-generator-api]]
- [[concepts/android-parcelfiledescriptor-close-trap]]
- [[concepts/ci-workflow-anchor-and-auto-release-watchers]]

## Sources
- [[daily/2026-05-02]]: Sessions 11:40 and 12:26 record URnetwork bytecode/source grounding and WARP API contract correction.
- [[daily/2026-05-02]]: Session 13:20 records the Android fd API compile failure and the release watcher/tag recovery loop.
