---
title: "WARP awgTurnOn Reference Diagnostic Loop"
aliases: [warp-awgturnon-diagnostic-loop, awgturnon-reference-loop]
tags: [warp, amneziawg, debugging, connections]
sources:
  - "daily/2026-05-06.md"
created: 2026-06-12
updated: 2026-06-12
---

# WARP awgTurnOn Reference Diagnostic Loop

`awgTurnOn=-1` diagnosis is a loop across runtime evidence, reference parity, artifact identity, and cleanup state. No single symptom distinguishes invalid INI, wrong API version, fd contract drift, stale UAPI sockets, or DNS bootstrap problems.

## Key Points

- Reference parity removes impossible hypotheses but does not replace device logs.
- Masked INI logging is required before changing dependency versions or AWG parameters.
- Artifact identity must be verified at API/signature level before using downgrade or replacement as a fix.
- Stale socket cleanup and fd mode are independent checks because both can produce `-1`.
- DNS pre-resolution is adjacent: it may not cause `-1`, but it can make the same WARP startup appear broken after tunnel establishment.

## Details

The 2026-05-06 sessions show the full loop. PORTAL WG decompilation proved that Ozero's basic `awgTurnOn` call shape, wg-quick format, and `uapiPath` were plausible. The next valid move was not another speculative dependency edit; it was runtime logging of the actual INI, fd validity, uapiPath existence, and socket state on the failing device.

The loop also separates root-cause classes that share symptoms. A stale `ozero-warp.sock` can make native bind return `EADDRINUSE` and surface as `-1`. A wrong API version can fail at compile time because the signature changed. A DNS loop can pass `awgTurnOn` and then fail readiness. Keeping those layers separate avoids treating all WARP startup failures as one native-library problem.

## Related Concepts

- [[concepts/amneziawg-turnon-minus-one]] - Central symptom and diagnosis checklist for negative handles.
- [[concepts/portal-wg-reference-oracle]] - Reference comparison used to rule out call-shape and config-format hypotheses.
- [[concepts/amneziawg-artifact-identity-boundary]] - Dependency identity and API signature checks before version changes.
- [[concepts/warp-uapi-stale-socket-cleanup]] - Stale socket cleanup as an independent `-1` root cause.
- [[concepts/warp-dns-routing-loop]] - Adjacent startup failure after tunnel establishment when endpoint DNS is resolved too late.

## Sources

- [[daily/2026-05-06.md]] - Sessions 09:30 and 10:18: reference decompilation narrowed hypotheses; masked INI/fd/uapi logging was required before further diagnosis; v1.2.2 downgrade failed because of API signature mismatch.
- [[daily/2026-05-06.md]] - Sessions 13:16 and 15:01: stale socket cleanup, final `blocking=true` parity, and endpoint pre-resolution were all part of the WARP startup recovery batch.
