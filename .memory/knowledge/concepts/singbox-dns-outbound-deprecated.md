---
title: "Sing-box: dns Outbound Type Deprecated in 1.13.0"
aliases: [singbox-dns-outbound, singbox-1.13-breaking, dns-outbound-removed]
tags: [singbox, android, configuration, breaking-change]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-06-13
---

# Sing-box: dns Outbound Type Deprecated in 1.13.0

The `dns` outbound type was deprecated and removed in sing-box 1.13.0. Configurations that include `"type": "dns"` in their outbounds array will fail to load at runtime. This is a breaking change that silently prevents the engine from starting when stale configs (from before 1.13.0) are used.

## Key Points

- `"type": "dns"` in outbounds is invalid from sing-box 1.13.0 onward
- The error surfaces as a startup failure in `:engine_singbox` process — SIGABRT or config parse error
- Fix: remove the `dns` outbound entry; DNS routing in 1.13.0+ uses route rules with `action = "hijack-dns"` instead
- Subscription profiles from before 1.13.0 may still contain `dns` outbounds — these need filtering at parse time
- Found in Ozero v0.2.8/v0.2.9 log analysis session (3 SIGABRTs in `:engine_singbox`)

## Details

### Migration Path

In sing-box versions before 1.13.0, DNS traffic could be handled by adding a `dns` outbound:

```json
{
  "outbounds": [
    {"type": "dns", "tag": "dns-out"}
  ],
  "route": {
    "rules": [
      {"protocol": "dns", "outbound": "dns-out"}
    ]
  }
}
```

From 1.13.0 onward, the DNS handling is inlined into route rules via the `action` field:

```json
{
  "route": {
    "rules": [
      {"protocol": "dns", "action": "hijack-dns"}
    ]
  }
}
```

### Impact on Subscription Profiles

Public subscription profiles from VLESS/VMess providers often contain `dns` outbound entries, especially profiles generated before 2025. The `SingboxSubscriptionParser` should filter or rewrite these entries before constructing the final config:

1. Strip any outbound with `"type": "dns"` from the outbounds array
2. Remove corresponding route rules that reference the stripped outbound by tag
3. Add a `hijack-dns` rule if DNS capture is desired

### CI Evidence

The Ozero v0.2.8/v0.2.9 device test log showed 3 consecutive SIGABRTs in `:engine_singbox`. Log analysis in session 19:44 identified `dns outbound deprecated in 1.13.0` as one of the error causes alongside `unknown transport type: splithttp`.

## Related Concepts

- [[concepts/singbox-splithttp-unsupported]] - Companion deprecation issue found in the same log analysis
- [[concepts/singbox-engine-design]] - Sing-box engine architecture and config generation
- [[concepts/singbox-subscription-architecture]] - Subscription parser that needs to filter deprecated outbound types
- [[connections/singbox-ui-ci-diagnostic-feedback-loop]] - Wider diagnostic loop for parser compatibility, crash evidence, and CI validation

## Sources

- [[daily/2026-05-26.md]] - Session 19:44: log analysis found `dns outbound deprecated in 1.13.0` in `:engine_singbox` SIGABRT trace for v0.2.8/v0.2.9; categorized as T1 in fix priority list (simplest fix); fix: replace dns outbound + route rule with `action: hijack-dns`
