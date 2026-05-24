---
title: "URnetwork NetworkSpace env=main and Bundle Fields Requirement"
aliases: [urnetwork-env-main, networkspace-bundle-fields, urnetwork-sigabrt-guest-network]
tags: [urnetwork, sdk, gotcha, sigabrt, native]
sources:
  - "daily/2026-05-05.md"
created: 2026-05-05
updated: 2026-05-05
---

# URnetwork NetworkSpace env=main and Bundle Fields Requirement

The URnetwork Go SDK requires the `NetworkSpace` to be constructed with `env_name=main` (not `prod`) and with all required bundle fields set via `updateNetworkSpace` before calling `networkCreate`. Using `env=prod` or omitting bundle fields (`linkHostName`, `migrationHostName`, `wallet`) causes SIGABRT at guest network auto-creation time — a crash that surfaces silently long after the misconfigured initialization.

## Key Points

- `env_name` in the network space JSON must be `"main"` — using `"prod"` causes SIGABRT when the Go runtime tries to auto-create a guest network
- Bundle fields (`linkHostName`, `migrationHostName`, `wallet`) must be set via `updateNetworkSpace(...)` before `networkCreate` is called
- The failure manifests as SIGABRT with log message "auto-creating guest network" — the crash appears in the Go runtime layer, not in Kotlin
- The correct values for bundle fields come from the reference implementation in `.claude/Контекст/android/` (URnetwork reference project)
- This bug was unmasked when a `NubiaGuard` workaround was removed (commit 47d0156) — the guard had been incidentally preventing the code path that triggers the misconfigured fields

## Details

### The env=prod Trap

The URnetwork SDK's Go runtime uses `env_name` to determine which environment it connects to. The production environment identifier is `"main"`, not `"prod"`. Using `"prod"` sends the SDK into an undefined configuration path that eventually SIGABRTs when the SDK attempts to auto-create a guest network during initialization.

The name mismatch is counterintuitive: developers naturally assume production = `"prod"`. The URnetwork SDK's naming convention comes from the Go service layer where the main deployment environment is called `main`. Inspecting the reference project (`.claude/Контекст/android/`) reveals the correct value.

### Bundle Fields via updateNetworkSpace

After constructing the `NetworkSpace` (via `importNetworkSpaceFromJson`), the SDK requires additional host and wallet parameters to be injected via `updateNetworkSpace`. These fields map to Go struct fields:

- `linkHostName` — hostname for deep-link handling
- `migrationHostName` — hostname for migration endpoints
- `wallet` — wallet address (required even for guest mode; may be empty string but must be set)

Omitting these fields leaves the Go runtime in a partially initialized state. When the runtime subsequently attempts to establish a guest network (which happens automatically on first connect), it dereferences nil pointers from the unset fields — SIGABRT.

### Why the Bug Was Hidden

The SIGABRT was latent for several sessions because a `NubiaGuard` (ROM-specific workaround for Nubia/RedMagic VPN throttling) was incidentally preventing the code path that triggered `networkCreate`. When the guard was removed in commit 47d0156 to fix an unrelated issue, the unmasked `networkCreate` call hit the misconfigured NetworkSpace and crashed.

This is a classic symptom-masking pattern: a workaround for problem A accidentally prevents problem B from manifesting. Removing the workaround appears to cause a regression, but the actual root cause predates the workaround.

### Reference Source for Correct Values

The correct `env_name`, `linkHostName`, `migrationHostName`, and `wallet` defaults are obtained from the URnetwork reference implementation in `.claude/Контекст/android/`. The `UrnetworkDefaults` object in Ozero should match these reference values exactly. Do not infer the correct values from naming conventions.

## Related Concepts

- [[concepts/urnetwork-networkspace-init]] - First-run NetworkSpace initialization (null handling and importNetworkSpaceFromJson); complementary to this article
- [[concepts/urnetwork-networkspace-bundle-fields]] - Bundle field serialization requirements for NetworkSpace
- [[concepts/android-silent-crash-diagnosis]] - General patterns for diagnosing SIGABRT and silent native crashes
- [[concepts/nubia-rom-permission-enforcement]] - The Nubia guard whose removal unmasked this bug

## Sources

- [[daily/2026-05-05.md]] - Session 22:08: URnetwork SIGABRT at "auto-creating guest network" after Nubia-guard removal; root cause = env=`prod` instead of `main` + missing updateNetworkSpace bundle fields; fix confirmed from reference project in `.claude/Контекст/android/`
