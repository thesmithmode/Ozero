---
title: "URnetwork NetworkSpace Bundle Fields and env=main Requirement"
aliases: [urnetwork-env-main, networkspace-bundle-fields, urnetwork-sigabrt-fix]
tags: [urnetwork, sdk, integration, gotcha, native]
sources:
  - "daily/2026-05-05.md"
created: 2026-05-05
updated: 2026-05-05
---

# URnetwork NetworkSpace Bundle Fields and env=main Requirement

The URnetwork Go SDK requires a fully populated `NetworkSpace` before `networkCreate` is called. Two distinct gaps cause SIGABRT during guest network auto-creation: using `env="prod"` instead of `env="main"`, and omitting the bundle fields (`linkHostName`, `migrationHostName`, `wallet`) that must be set via `updateNetworkSpace` before network initialization. Both are required simultaneously.

## Key Points

- `env="prod"` is incorrect — the production URnetwork environment name is `"main"`, not `"prod"`
- Bundle fields (`linkHostName`, `migrationHostName`, `wallet`) must be set via `updateNetworkSpace(networkSpace)` after `importNetworkSpaceFromJson` but before `networkCreate`
- Omitting either (wrong env OR missing bundle fields) → SIGABRT in Go runtime during guest network auto-creation
- The canonical values for these fields come from reference implementations in `.claude/Контекст/android` — not from URnetwork documentation
- SIGABRT happens after Nubia-guard removal (guard was masking the underlying misconfiguration)

## Details

### The env=main Discovery

`NetworkSpace` in the URnetwork SDK carries an environment name that routes SDK calls to the correct backend. The URnetwork production environment identifier is `"main"` — a Go naming convention from their internal backend. Using `"prod"` (the common convention in most SaaS products) routes to a non-existent or incorrect environment, causing the Go SDK to abort when attempting to create a guest network.

This naming is non-obvious: `"main"` looks like a branch name or development environment, but it is the canonical production identifier. The correct value was found only by examining the reference Android implementation in `.claude/Контекст/android`.

### Bundle Fields Requirement

After `NetworkSpace` is created via `importNetworkSpaceFromJson`, three bundle fields must be explicitly set:

- `linkHostName` — the main API hostname for connecting to URnetwork infrastructure
- `migrationHostName` — hostname for migration operations
- `wallet` — wallet address for P2P compensation model

These fields are not part of the initial JSON used in `importNetworkSpaceFromJson` — they are application-level configuration values set on the `NetworkSpace` object via `updateNetworkSpace`. The sequence:

```kotlin
// 1. Create or retrieve NetworkSpace
val networkSpace = getNetworkSpace("default")
    ?: importNetworkSpaceFromJson(defaultJson)

// 2. Set bundle fields from reference values
networkSpace.linkHostName = UrnetworkDefaults.LINK_HOST_NAME
networkSpace.migrationHostName = UrnetworkDefaults.MIGRATION_HOST_NAME
networkSpace.wallet = UrnetworkDefaults.WALLET

// 3. Persist the updated NetworkSpace
updateNetworkSpace(networkSpace)

// 4. Only now call networkCreate
networkCreate(...)
```

Calling `networkCreate` without step 2-3 causes the Go SDK to encounter null or empty host fields during guest network setup, triggering a SIGABRT in the Go runtime.

### Relationship to SIGABRT After Guard Removal

In commit `47d0156`, a Nubia-specific guard was removed that had been preventing `networkCreate` from running on Nubia devices. After guard removal, URnetwork engine started reaching `networkCreate` for the first time on those devices — and immediately aborted. This looked like a regression from the guard removal, but was actually exposing a pre-existing misconfiguration (wrong env + missing bundle fields) that had been masked.

The reference implementation in `.claude/Контекст/android` contains the authoritative values for all three bundle fields and correctly sets `env="main"`.

## Related Concepts

- [[concepts/urnetwork-networkspace-init]] - The earlier discovery of getNetworkSpace null on first run; bundle fields are a subsequent initialization step
- [[concepts/urnetwork-sdk-integration]] - Full integration journey including this fix
- [[concepts/android-silent-crash-diagnosis]] - SIGABRT diagnosis methodology applicable here

## Sources

- [[daily/2026-05-05.md]] - Session 22:08: URnetwork SIGABRT after guard removal; root cause: env="prod" + missing bundle fields (linkHostName, migrationHostName, wallet); reference impl in .claude/Контекст/android contained canonical values
