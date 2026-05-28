---
title: sing-box auto-chain validator parity
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# sing-box auto-chain validator parity

## Key Points
- sing-box auto-chain must apply the same supported-bean filters as standalone auto-select.
- Unsupported transports such as `splithttp` must be filtered before config reaches libbox.
- Unsupported VLESS flows like `xtls-rprx-vision-udp443` should be normalized or excluded before config validation.
- Chain-mode DNS should use the same `hijack-dns` pipeline as the main path, not a legacy `dns-out` route.

## Details

The 2026-05-28 review found that sing-box fixes were incomplete if they only addressed the observed `unsupported flow: xtls-rprx-vision-udp443` error. Standalone auto-select already had a supported-bean filtering path, but `buildAutoChainConfig()` did not consistently share that invariant. This allowed subscription profiles with unsupported transports to enter chain configs even when the normal path would reject them.

The owning-layer fix belongs in `ConfigBuilder`, not in UI selection controls, because the config builder is the shared boundary before libbox validation. The same review also identified a chain-mode DNS divergence: legacy `dns-out` routing could survive in chain config while the main mode had moved to route-rule DNS hijacking. This connects directly to [[concepts/singbox-dns-outbound-deprecated]] and [[concepts/singbox-splithttp-unsupported]].

## Related Concepts
- [[concepts/singbox-dns-outbound-deprecated]]
- [[concepts/singbox-splithttp-unsupported]]
- [[concepts/singbox-subscription-fetch-robustness]]
- [[concepts/release-regression-evidence-checklist]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-28.md]] records the trace error `unsupported flow: xtls-rprx-vision-udp443`.
- [[daily/2026-05-28.md]] records the review finding that auto-chain did not filter unsupported transports like auto-select.
- [[daily/2026-05-28.md]] records the decision to replace legacy chain DNS behavior with the shared `hijack-dns` pipeline and add regression coverage.
