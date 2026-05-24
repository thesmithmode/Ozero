---
title: "Xray Engine Design"
aliases: [engine-xray, xray-core-android, xtls-android]
tags: [android, engine, xray, go-runtime, process-isolation]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Xray Engine Design

Xray-core (XTLS/xray-core) is the chosen universal proxy engine for Ozero, selected over sing-box at the end of the 2026-05-24 design session. It supports VLESS, VMess, Trojan, Shadowsocks, and WireGuard via a unified JSON configuration.

## Key Points

- xray-core is Go-based → same `libgojni.so` naming conflict and SIGABRT constraints as sing-box
- Mutually exclusive with URnetwork engine: `GoRuntimeGuard` acquire/release pattern prevents concurrent Go-runtimes
- Reference implementations: v2rayNG (Android JNI integration) and xtls/xray-core upstream
- Process isolation strategy: `:engine_xray` Android process (like WARP) or `ProcessBuilder` (like MasterDNS) — not yet finalized
- `engine-xray` module does not exist in Ozero yet; must be created as new `engine-*` Gradle module

## Details

Xray-core was chosen over sing-box as the target engine for Ozero's new proxy capability. The architectural constraints identified during the sing-box design session carry over completely: Go-runtime exclusivity, process isolation, Hilt DI boundary, and per-engine UI requirement.

The `libgojni.so` naming issue is critical: if both xray and another Go-engine (URnetwork, WARP) load their respective Go runtimes in the same process, SIGABRT occurs in `gcWriteBarrier`. The GoRuntimeGuard singleton enforces sequential ownership — only one Go-engine can hold the runtime at a time. Switching engines requires: stop current engine → release GoRuntimeGuard → acquire → start new engine.

Process isolation options: (1) `:engine_xray` named process in AndroidManifest, giving VPN service its own process scope, prevents any `app/` Hilt DI from crossing the boundary; (2) `ProcessBuilder` subprocess approach (like `engine-masterdns`), where xray-core runs as a child process receiving config via stdin or file. Option 1 is consistent with the WARP engine pattern; Option 2 avoids JNI complexity.

The v2rayNG Android app is the primary reference implementation — it shows how xray-core Go bindings are compiled for Android (gomobile bind), how config JSON is passed to the runtime, and how TUN fd is transferred across the JNI boundary.

## Acceptance Criteria (planned)

- Switch from URnetwork → xray → back to URnetwork: no SIGABRT
- VLESS, Trojan, Shadowsocks connections established through TUN
- Subscription import from URL populates server list
- Per-engine settings screen in `ui/settings/engines/xray`

## Related Concepts

- [[concepts/singbox-engine-design]] - The sing-box design session this pivoted from; all constraints carry over
- [[concepts/go-runtime-process-isolation]] - GoRuntimeGuard SIGABRT prevention
- [[concepts/hilt-cross-process-injection]] - VPN process DI boundary
- [[concepts/engine-ownership-boundary]] - Engine contract pattern
- [[concepts/xray-aar-build-research]] - Prior AAR build research for xray

## Sources

- [[daily/2026-05-24.md]] - User pivot decision at end of Session 14:50: "Нам не нужен sing-box. Нам нужен xray."; action items: rewrite spec, verify JNI API (v2rayNG/xtls), finalize process isolation strategy
