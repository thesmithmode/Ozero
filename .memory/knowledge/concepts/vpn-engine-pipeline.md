---
title: "VPN Engine Pipeline"
aliases: [engine-pipeline, vpn-pipeline, strategy-engine]
tags: [architecture, vpn, engine]
sources:
  - "daily/2026-04-29.md"
created: 2026-04-29
updated: 2026-04-29
---

# VPN Engine Pipeline

Ozero uses a pipeline architecture for VPN engine selection and execution. The pipeline routes traffic through different proxy engines (Xray, Hysteria2, Amnezia WireGuard, Naive, Tor, ByeDpi) based on configuration. In v1.0.5, a manual branch was added to the pipeline, allowing users to specify engine and server settings directly rather than relying on automatic strategy selection.

## Key Points

- `VpnEnginePipeline` is the central orchestrator that decides which engine handles traffic
- `ManualEngineSource` was introduced in v1.0.5 to support user-specified engine configuration
- `StrategyEngine` handles automatic engine selection based on strategy rules
- The pipeline supports a manual branch alongside the automatic strategy branch
- Each engine (Xray, Hy2, Awg, Naive, Tor, ByeDpi) is a pluggable component in the pipeline

## Details

The VPN engine pipeline follows a strategy pattern where `VpnEnginePipeline` acts as the dispatcher. Prior to v1.0.5, the pipeline only supported automatic engine selection through `StrategyEngine`, which chose the appropriate proxy engine based on preset rules.

The v1.0.5 release introduced `ManualEngineSource`, enabling a manual branch in the pipeline. This allows users to override automatic selection and directly specify which engine to use along with its configuration (server address, ports, protocol-specific arguments). The manual and automatic branches coexist within the same pipeline, with the user's choice determining which path is taken.

The wiring between `ManualEngineSource` and `StrategyEngine` is handled at the pipeline level, where the configuration source determines which branch activates. This architecture keeps individual engine implementations decoupled from the selection logic.

## Related Concepts

- [[concepts/per-engine-ui]] - UI screens that configure each engine's manual settings
- [[concepts/release-process]] - The release in which manual engine wiring was shipped

## Sources

- [[daily/2026-04-29.md]] - ManualEngineSource + StrategyEngine wiring introduced as part of D1-D6 implementation in v1.0.5
