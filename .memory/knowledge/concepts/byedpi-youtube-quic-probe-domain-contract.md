---
title: ByeDPI YouTube QUIC probe domain contract
sources:
  - daily/2026-05-30.md
created: 2026-05-31
updated: 2026-06-09
---
# ByeDPI YouTube QUIC probe domain contract
## Summary
ByeDPI YouTube acceptance must prove video-delivery paths such as googlevideo and QUIC/UDP without silently changing explicit runtime argument semantics.

## Key Points
- YouTube page load success does not prove video playback success.
- Video playback can still fail through QUIC/UDP or googlevideo routing even when TCP page content works.
- HEV UDP mode and verbatim CMD args should not be changed without evidence.
- Probe and acceptance coverage should include `manifest.googlevideo.com` and default googlevideo domain lists.
- Runtime fixes must keep explicit `desyncUdp` semantics instead of silently adding `-Ku`.
- This connects [[concepts/byedpi-udp-quic-routing]] and [[concepts/byedpi-cmd-verbatim-pipeline]].

## Details

The 2026-05-30 runtime diagnosis separated a user-visible ByeDPI improvement from remaining failure: YouTube pages started loading, but videos still did not play. This is a distinct network-path signal because page HTML/API traffic and media segment delivery can traverse different protocol and domain paths.

The investigation confirmed that the correct fix direction was not to mutate runtime CMD arguments or force automatic UDP suffix behavior. HEV was already in UDP mode, CMD args remained verbatim, and `desyncUdp` only adds `-Ku` when explicitly enabled by UI. The useful change was to improve probe/acceptance coverage by adding `manifest.googlevideo.com` and related googlevideo defaults.

The durable contract is that acceptance must model video delivery, not only page reachability. A green page probe can be a false positive for YouTube if googlevideo manifests or QUIC/UDP paths are not covered.

The same session reinforced the ByeDPI settings boundary. UI-selected `desyncUdp` may add `-Ku`, but CMD mode remains verbatim and probe coverage should not mutate runtime args as a side effect. This keeps the YouTube acceptance fix from regressing the manual-configuration contract.

## Related Concepts
- [[concepts/byedpi-udp-quic-routing]]
- [[concepts/byedpi-cmd-verbatim-pipeline]]
- [[concepts/byedpi-settings-schema-marker-migration]]
- [[concepts/byedpi-argv-grammar-aware-validation]]
- [[concepts/byedpi-proxy-lane-test-race-synchronization]]

## Sources
- [[daily/2026-05-30]]: User reported YouTube pages loaded through ByeDPI while videos still failed.
- [[daily/2026-05-30]]: Diagnosis pointed to QUIC/UDP and HEV/TUN routing because pages and video use different network paths.
- [[daily/2026-05-30]]: Subagent confirmed runtime UDP/CMD behavior should stay unchanged and the fix belonged in probe/acceptance domains.
- [[daily/2026-05-30]]: `manifest.googlevideo.com` was added to YouTube/googlevideo coverage.
- [[daily/2026-05-30]]: The runtime rule kept CMD args verbatim and only allowed UI `desyncUdp` to add `-Ku` explicitly.
