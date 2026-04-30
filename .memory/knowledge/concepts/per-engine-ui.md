---
title: "Per-Engine UI Architecture"
aliases: [engine-settings-screens, per-engine-settings]
tags: [ui, architecture, engine]
sources:
  - "daily/2026-04-29.md"
created: 2026-04-29
updated: 2026-04-29
---

# Per-Engine UI Architecture

Each VPN engine in Ozero has its own dedicated settings screen, located in `ui/settings/engines/`. This architectural decision ensures that engine-specific configuration (subscription URLs, server pickers, command-line arguments, bridge settings) is presented through purpose-built UI rather than a generic form.

## Key Points

- Settings screens live in `app/src/main/java/.../ui/settings/engines/`
- Each engine (Xray, Hy2, Awg, Naive, Tor, ByeDpi) has its own screen
- Screens are backed by per-engine ViewModels for state management
- This pattern was established as a project rule in CLAUDE.md
- Introduced in v1.0.5 as part of the D6 deliverable

## Details

The per-engine UI pattern was a deliberate architectural choice to avoid a one-size-fits-all settings screen. Different engines have fundamentally different configuration surfaces: ByeDpi needs DPI bypass arguments, Tor needs bridge configuration, Xray needs subscription URLs, and Amnezia WireGuard needs key management. A generic form would either be too complex or too limited.

Each screen follows the same structural pattern — a Jetpack Compose screen backed by a ViewModel — but the content is engine-specific. The ViewModels handle validation, persistence, and interaction with the engine pipeline. This was implemented as part of the D1-D6 batch in v1.0.5, alongside the manual engine wiring in `VpnEnginePipeline`.

The pattern is enforced as a project convention: CLAUDE.md explicitly states that every engine must have a settings screen in the designated directory. This prevents regressions where new engines are added without user-facing configuration.

## Related Concepts

- [[concepts/vpn-engine-pipeline]] - The pipeline that uses engine configurations from these UI screens
- [[concepts/ci-workflow-discipline]] - CI process that validated these screens before release

## Sources

- [[daily/2026-04-29.md]] - Per-engine UI screens created as D6 deliverable, each engine received its settings screen
