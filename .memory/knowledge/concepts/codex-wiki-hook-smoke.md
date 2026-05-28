---
title: Codex wiki hook smoke validation
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Codex wiki hook smoke validation

## Key Points
- Codex migration smoke is valid when transcript parsing, `flush`, and daily-log append all work through the Codex backend.
- The hook path uses the shared transcript parser and Codex runtime backend, not a separate Claude-only path.
- A Codex-only hook smoke confirms that `transcript -> flush -> daily` remains the operational memory pipeline.
- Smoke success is useful durable knowledge because future hook regressions should compare against this known working baseline.

## Details

On 2026-05-28, memory maintenance sessions confirmed that the wiki migration to Codex had passed a smoke test. The durable claim is not that every wiki command was exhaustively validated, but that the Codex hook path can parse a Codex transcript, run the flush pipeline, and append durable facts into `daily/`.

This matters because `.memory/` depends on hook automation for session capture. If later memory flushes stop appearing, the first diagnostic split is whether the transcript parser/backend path still matches the Codex runtime contract described in [[concepts/wiki-knowledge-base]] and whether the daily append path remains intact.

## Related Concepts
- [[concepts/wiki-knowledge-base]]
- [[concepts/release-regression-evidence-checklist]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-28.md]] records that Codex hooks use the shared transcript parser and Codex backend.
- [[daily/2026-05-28.md]] records that the Codex-only hook smoke test followed the `transcript -> flush -> daily` memory algorithm.
