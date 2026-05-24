---
title: "URnetwork NetworkSpace Initialization Flow"
aliases: [networkspace-init, urnetwork-first-run, importNetworkSpaceFromJson]
tags: [urnetwork, sdk, integration, gotcha]
sources:
  - "daily/2026-05-02.md"
created: 2026-05-02
updated: 2026-05-02
---

# URnetwork NetworkSpace Initialization Flow

The URnetwork SDK's `NetworkSpaceManager` distinguishes between looking up an existing network space (`getNetworkSpace(key)`) and creating one (`importNetworkSpaceFromJson(json)`). On first app launch, `getNetworkSpace` returns null because no space has been persisted yet. The initial Ozero integration treated this null as an error, causing `RealUrnetworkAuthService` to log "NetworkSpace null" and the engine to fail on every first run.

## Key Points

- `NetworkSpaceManager.getNetworkSpace(key)` is a lookup — returns null when no space exists (first run), not an SDK error
- `NetworkSpaceManager.importNetworkSpaceFromJson(json): NetworkSpace` is the creation method — must be called on first run with default configuration JSON
- `NetworkSpaceManager` does NOT have a `createNetworkSpace` method — this was confirmed via SDK AAR bytecode introspection (`jar tf`)
- Default JSON must include `host_name` and `env_name` fields (Go struct tags from SDK source: `json:"host_name"`, `json:"env_name"`)
- Correct init flow: `getNetworkSpace(key)` → if null → `importNetworkSpaceFromJson(defaultJson)` → proceed with returned `NetworkSpace`

## Details

### The Null NetworkSpace Problem

The v0.0.2-4 device test revealed the failure: `ozero.log` lines 64-65 showed `RealUrnetworkAuthService: NetworkSpace null`. The `RealUrnetworkSdkBridge.start()` called `Sdk.newNetworkSpaceManager()` and then `getNetworkSpace("default")`, receiving null. The code treated null as a fatal error and propagated it as an engine failure state.

The root cause was a misunderstanding of the SDK API surface. `getNetworkSpace` is a pure lookup against local persistent storage — it returns whatever was previously saved under that key. On a fresh install, nothing has been saved, so null is the expected return value. The SDK expects the caller to handle this by creating a new space via `importNetworkSpaceFromJson`.

### Bytecode Introspection Discovery

The correct API was discovered through SDK AAR bytecode introspection rather than documentation (which does not exist for the URnetwork Go SDK's Android bindings). The team ran `jar tf` on the AAR to list all classes and methods, finding:

- `NetworkSpaceManager.getNetworkSpace(String): NetworkSpace` — lookup
- `NetworkSpaceManager.importNetworkSpaceFromJson(String): NetworkSpace` — create from JSON

There is no `createNetworkSpace` convenience method. The JSON import is the only creation path, which makes sense for the SDK's design: network spaces are portable configurations that can be exported/imported across devices.

### Go Source Confirmation

The Go source files for the URnetwork SDK confirmed the JSON structure. The `NetworkSpace` struct uses Go JSON tags: `host_name` (maps to `hostName` in the JSON), `env_name` (maps to `envName`). The `UrnetworkDefaults` object in Ozero provides the default JSON with these fields populated for the production environment.

### Fix Pattern

The fix in `RealUrnetworkSdkBridge`:

1. Call `getNetworkSpace("default")`
2. If null: call `importNetworkSpaceFromJson(UrnetworkDefaults.DEFAULT_NETWORK_SPACE_JSON)`
3. Use the returned `NetworkSpace` for subsequent SDK operations (auth, tunnel start)
4. If both fail: propagate error with diagnostic logging to `BootFileLogger`

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] - Parent article covering the full URnetwork integration journey
- [[concepts/urnetwork-networkspace-env-bundle-fields]] - After creating the NetworkSpace, `env_name=main` and bundle fields must be set via `updateNetworkSpace` before `networkCreate` — a distinct failure mode (SIGABRT vs null)
- [[concepts/gomobile-bind-gotchas]] - The AAR that exposes these SDK methods was built through the gomobile pipeline with its own set of traps

## Sources

- [[daily/2026-05-02.md]] - Session 11:40: `getNetworkSpace` null on first run diagnosed via device logs; bytecode introspection revealed `importNetworkSpaceFromJson` as creation method; Go source confirmed JSON field names
