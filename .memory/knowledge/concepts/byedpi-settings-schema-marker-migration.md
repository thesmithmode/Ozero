---
title: ByeDPI settings schema marker migration
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# ByeDPI settings schema marker migration

## Summary
ByeDPI settings JSON needs an explicit schema marker so legacy migration can distinguish old defaults from new user-selected values and preserve round-trip semantics.

## Key Points
- Exact JSON equality is fragile as a legacy migration trigger because new explicit user choices can look like old defaults.
- `toJson()` should write a schema marker when serializing the current settings format.
- Legacy migration should apply only to JSON payloads without the marker.
- The explicit `desyncUdp=true` setting must survive serialize/deserialize round trips.

## Details
On 2026-05-30, code review found that a newly explicit ByeDPI UDP desync setting could be overwritten by legacy migration logic. The issue was not the UDP value itself, but the absence of a durable schema signal separating old JSON from current JSON.

The fix pattern is to add a marker in `ByeDpiUiSettings.toJson()` and scope migration to marker-less legacy payloads. That preserves user intent while keeping compatibility with older saved settings. The same discipline supports [[concepts/byedpi-udp-quic-routing]], because UDP behavior is a real runtime contract, not just UI state.

This is also a settings-versioning lesson for [[concepts/core-backup-module]] and [[concepts/byedpi-cmd-verbatim-pipeline]]: when serialized engine settings evolve, migration must be explicit enough to avoid reinterpreting a current user choice as a historical default.

## Related Concepts
- [[concepts/byedpi-udp-quic-routing]]
- [[concepts/byedpi-cmd-verbatim-pipeline]]
- [[concepts/core-backup-module]]
- [[concepts/byedpi-args-parsing]]

## Sources
- [[daily/2026-05-30]]: Review findings required a schema marker so `desyncUdp=true` round-trip preserves the new explicit setting.
- [[daily/2026-05-30]]: The accepted decision was to apply legacy migration only to old JSON without the marker.
