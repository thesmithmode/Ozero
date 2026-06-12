---
title: "PORTAL WG Reference Oracle Discipline"
aliases: [portal-wg-reference-oracle, warp-reference-oracle, decompiled-reference-oracle]
tags: [warp, amneziawg, reference, debugging]
sources:
  - "daily/2026-05-06.md"
created: 2026-06-12
updated: 2026-06-12
---

# PORTAL WG Reference Oracle Discipline

PORTAL WG v1.4.3 is a WARP/AmneziaWG reference oracle for Ozero only when its decompiled behavior is mapped to concrete call signatures, config format, and lifecycle order. It should be used to prove or reject hypotheses, not as a source for wholesale copying.

## Key Points

- Reference comparison must verify call signature, config format, fd handling, uapiPath, and library loading path.
- PORTAL WG uses the same 4-parameter `awgTurnOn(name, fd, config, uapiPath)` shape and wg-quick config format observed in Ozero.
- PORTAL WG's `protect()` call happens after successful `awgTurnOn`, matching Ozero's lifecycle order.
- Reference-only AWG fields such as S3/S4/I1/I2/I5 are parity gaps, but absence alone did not prove the immediate `-1` root cause.
- Differences in native library loading path are evidence to log and compare, not automatic proof of failure.

## Details

The 2026-05-06 WARP investigation decompiled and read PORTAL WG files including `GoBackend`, interface config generation, AWG parameter mapping, parser/config generator code, and native library loading. This showed that Ozero's core call shape was not obviously wrong: both sides used wg-quick config text, a 4-parameter `awgTurnOn`, and a data-directory uapi path.

The useful outcome was negative evidence. Library loading, uapiPath, race with `onDestroy`, and basic config format were removed from the primary suspect list. Remaining work moved to directly logging what Ozero passed to native code: masked INI, fd validity, uapiPath existence, socket cleanup, and AWG version. That is the core discipline: reference code narrows the investigation, but runtime evidence still owns the final diagnosis.

## Related Concepts

- [[concepts/amneziawg-turnon-minus-one]] - The `awgTurnOn=-1` diagnosis used PORTAL WG to rule out several API-shape hypotheses.
- [[concepts/amneziawg-artifact-identity-boundary]] - Reference artifacts and Maven/gomobile artifacts must be separated before dependency changes.
- [[concepts/warp-awg-field-preservation-contract]] - Reference AWG fields matter for full config parity and must not be silently dropped.
- [[concepts/warp-awgturnon-blocking-fd]] - Final fd parity with official AmneziaWG behavior belongs to the same reference discipline.

## Sources

- [[daily/2026-05-06.md]] - Session 09:30: PORTAL WG v1.4.3 files were decompiled and compared; same 4-parameter `awgTurnOn`, same wg-quick format, same dataDir uapiPath, and post-success `protect()` order were observed.
- [[daily/2026-05-06.md]] - Session 09:30: PORTAL WG AWG params S3, S4, I1, I2, and I5 were identified as missing in Ozero and marked as likely parity work rather than confirmed immediate `-1` cause.
