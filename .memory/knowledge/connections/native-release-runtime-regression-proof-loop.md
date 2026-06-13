---
title: "Native Release Runtime Regression Proof Loop"
aliases: [native-release-proof-loop, runtime-regression-proof-loop, ci-release-runtime-loop]
tags: [native, release, ci, runtime, diagnostics]
sources:
  - "daily/2026-05-23.md"
created: 2026-06-12
updated: 2026-06-12
---

# Native Release Runtime Regression Proof Loop

The 2026-05-23 work shows one recurring pattern across WARP, FPTN, URnetwork, MasterDNS, and release packaging: runtime regressions are only proven by connecting symptom logs to the owning native/build/release layer, then adding a sentinel or workflow assertion at that layer. A green CI run alone was repeatedly insufficient because failures came from filesystem residue, native linker namespaces, release checkout depth, or SDK identity state.

## Key Points

- WARP failures required filesystem evidence (`sockets/*.sock`) plus UAPI selection behavior, not a timeout-only fix.
- FPTN failures required protocol evidence (`sniDomain` versus server IP) and native build evidence (Conan/CMake, Brotli, `c++_static`).
- URnetwork relay failures required separating billing JWT identity from mesh `provideSecretKeys` identity.
- MasterDNS required deploy-script idempotency, SSH exec diagnostics, firewall ownership markers, and R8 dependency guards.
- Release install failures required APK `versionCode` proof from the release workflow, not advice to uninstall the previous APK.

## Details

The loop starts with a user-visible symptom such as WARP timeout, FPTN HTTP 608, URnetwork 0 relay bytes, MasterDNS deploy failure, or Android install error. The useful next step is not a generic timeout increase. It is to identify which layer owns the symptom: native socket state, TLS/SNI protocol, SDK local identity, remote deploy script, Android package metadata, or R8 release minification.

Once the owning layer is found, the fix must be guarded where the regression can recur. WARP got cleanup and newest-socket selection sentinels; FPTN got SNI-domain flow and build assertions; URnetwork got provider/JWT sentinels; MasterDNS got deploy-state tests and release R8 guards; release packaging got a versionCode assertion. The common property is that each guard checks the contract directly, not a proxy such as "CI completed" or "engine button turned green".

## Related Concepts

- [[concepts/warp-uapi-stale-socket-cleanup]] - Filesystem residue caused WARP runtime timeouts.
- [[concepts/fptn-sni-bypass-method]] - FPTN protocol failures were caused by invalid SNI, not generic connectivity.
- [[concepts/urnetwork-provide-secret-keys-identity]] - Relay identity required SDK contract parity, not only JWT presence.
- [[concepts/masterdns-deploy-hardening]] - Remote deploy correctness needed idempotent scripts and diagnostic state.
- [[concepts/versioncode-git-history-rewrite-regression]] - APK update failures require monotonic release metadata.

## Sources

- [[daily/2026-05-23.md]] - WARP stale socket regression, FPTN SNI/Reality timeout, URnetwork provider/JWT identity fixes, MasterDNS deploy hardening, sshj/R8 trap, and release `versionCode=1` shallow-checkout regression all required layer-specific evidence and sentinels rather than symptom-only fixes.
