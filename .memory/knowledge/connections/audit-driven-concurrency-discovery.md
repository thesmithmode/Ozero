---
title: "Connection: Audit-Driven Concurrency Bug Discovery"
connects:
  - "concepts/gene-memory-concurrency-traps"
  - "concepts/backup-awg-field-roundtrip-loss"
  - "concepts/warp-slot-corrupt-json-resilience"
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# Connection: Audit-Driven Concurrency Bug Discovery

## The Connection

Three categories of silent data integrity bugs — concurrency races in GeneMemory/SavedStrategyStore, field omission in BackupWarpSerializer, and input validation gaps in AppBackupSerializer — were all invisible to unit tests and CI. They were discovered only through a dedicated adversarial audit using 6 parallel subagents. This is the same pattern as the WARP slot corruption discovery ([[concepts/warp-slot-corrupt-json-resilience]]), which was also found via adversarial review, not tests.

## Key Insight

Unit tests verify expected behavior; they rarely exercise concurrent access patterns or enumerate serialization field completeness. The common thread across all findings is that correctness properties spanning multiple files or execution contexts (thread A writes while thread B reads; serializer must match config struct field-by-field) are structurally invisible to per-function unit tests. Adversarial audit — systematically asking "what happens if X fails / races / is incomplete" — catches what tests miss.

The 22 findings from 6 subagents in one session produced more data integrity fixes than weeks of normal development, reinforcing that periodic audit sessions are high-ROI for shared-state and persistence code.

## Evidence

- `GeneMemory.scores` HashMap race: no unit test exercises concurrent read+write; only audit found it
- `BackupWarpSerializer` missing 5 fields: no test compared serialized field count against WarpConfig property count; only audit found it
- `AppBackupSerializer.deserialize` no size limit: no test provides a large input; only audit found the OOM vector
- `warp-slot-corrupt-json-resilience` (2026-05-05): same discovery pattern — adversarial review, not test failure

## Related Concepts

- [[concepts/gene-memory-concurrency-traps]] - Concurrency bugs found by audit
- [[concepts/backup-awg-field-roundtrip-loss]] - Field completeness bug found by audit
- [[concepts/warp-slot-corrupt-json-resilience]] - Prior adversarial review finding with same pattern
