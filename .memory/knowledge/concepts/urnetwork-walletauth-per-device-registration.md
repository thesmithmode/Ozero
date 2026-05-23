---
title: "URnetwork Per-Device walletAuth Auto-Registration"
aliases: [walletauth, per-device-registration, ed25519-device-identity, urnetwork-solana-auth]
tags: [urnetwork, authentication, crypto, architecture, monetization]
sources:
  - "daily/2026-05-18.md"
  - "daily/2026-05-23.md"
created: 2026-05-18
updated: 2026-05-23
---

# URnetwork Per-Device walletAuth Auto-Registration

Ozero automatically creates a non-guest URnetwork account per device using Ed25519 wallet-based authentication. Each device generates a unique Ed25519 keypair, persisted in `filesDir/urnetwork/device-keypair.bin` encrypted via AES-GCM with an AndroidKeyStore-wrapped key. The public key serves as a Solana-format wallet address for `authLogin(walletAuth)` against the URnetwork server, producing a non-guest `byJwt` that unlocks relay monetization (wallet API access blocked for guest accounts — see [[concepts/urnetwork-guest-mode-relay-blocker]]).

## Key Points

- `RealUrnetworkDeviceIdentity`: BouncyCastle Ed25519 keypair (32-byte seed), AES-GCM encrypted at rest via AndroidKeyStore alias `ozero_urn_dev_v1`
- Server protocol: `authLogin(walletAuth)` → existing user returns `byJwt`; echoed `walletAuth` without JWT → `networkCreate(walletAuth + networkName)` creates new user
- Message format: `"ozero-auth-v1:" + pubkeyBase58`; signature = raw 64-byte Ed25519, Base64.NO_WRAP
- No challenge, no nonce from server — client chooses message, replay-safe forever (server validates signature only)
- 1 pubkey = 1 network (DB unique constraint on server); network names randomized as `n<hex8>` to reduce fingerprinting
- Migration: existing guest `byJwt` → walletAuth upgrade on first engine start; `byClientJwt` invalidated (tied to old guest JWT)
- Fallback: walletAuth failure → graceful fallback to guest `networkCreate` (relay works but no earnings)

## Details

### Architecture

The system comprises four components:

1. **`Base58`** — Pure-Kotlin Bitcoin alphabet encoder/decoder (Solana spec). 7 unit tests cover empty input, leading zeros, golden vectors, roundtrip, alphabet validation.

2. **`UrnetworkDeviceIdentity`** (interface) — `pubkeyBase58(): String` + `sign(message: ByteArray): ByteArray`. Two implementations: `RealUrnetworkDeviceIdentity` (production, AndroidKeyStore-backed) and `InMemoryUrnetworkDeviceIdentity` (test fake, deterministic seed).

3. **`RealUrnetworkAuthService.acquireDeviceWalletJwt()`** — Orchestrates the auth flow: construct walletAuth payload → `authLogin` → check response for JWT (returning user) or echoed walletAuth (new user → `networkCreate`).

4. **`EngineUrnetwork.ensureGuestJwt()`** — Entry point in engine start sequence. Three branches:
   - Existing `byJwt` + `devicePubkey` → return (idempotent, device already registered)
   - Existing `byJwt` without `devicePubkey` → MIGRATION: walletAuth → atomic update `{byJwt, devicePubkey, deviceNetworkName, byClientJwt=null}`
   - No `byJwt` → walletAuth → on error, fallback to guest `networkCreate`

### Server Protocol Details

URnetwork's `authLogin` endpoint accepts a `walletAuth` object containing:
- `blockchain`: `"solana"` (string identifier)
- `wallet`: Base58-encoded Ed25519 public key (32 bytes → 43-44 chars)
- `walletSignature`: Base64.NO_WRAP of raw 64-byte Ed25519 signature
- `walletMessage`: `"ozero-auth-v1:" + pubkeyBase58`
- `terms`: `true` (accept ToS)

The server validates the Ed25519 signature against the public key. Two response patterns:
- **Returning user**: response contains `network.byJwt` with `isGuest=false` — device was previously registered
- **New user**: response echoes `walletAuth` without JWT → client calls `networkCreate(walletAuth + networkName)` which creates the network and returns `byJwt`

Critical server properties discovered during research:
- **No nonce**: server does not issue a challenge; client picks the message freely
- **No proof-of-ownership for payoutWallet**: `addExternalWallet(address, "solana")` accepts any address without proving the caller controls it — payout setup is trivial but ToS-risky for many-to-one aggregation
- **1 pubkey = 1 network**: DB unique constraint prevents one keypair from creating multiple networks

### Key Storage Security

The Ed25519 seed (32 bytes) is encrypted with AES-GCM using a key stored in AndroidKeyStore:

```kotlin
val keySpec = KeyGenParameterSpec.Builder("ozero_urn_dev_v1", PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setRandomizedEncryptionRequired(true)
    .build()
```

`setRandomizedEncryptionRequired(true)` auto-generates a unique IV per encryption. The IV is read from `cipher.iv` after `init(ENCRYPT_MODE)` and persisted alongside the ciphertext. File write uses atomic rename (`.tmp` → final) to prevent partial-write corruption. `Mutex` guards lazy load to prevent concurrent keypair generation.

### Migration Flow

Existing Ozero users have a guest `byJwt` from `acquireGuestJwt()`. On first engine start with walletAuth-capable code:

1. `ensureGuestJwt()` detects `byJwt` exists but `devicePubkey` is null
2. Generates Ed25519 keypair, calls `acquireDeviceWalletJwt(identity, networkName)`
3. On success: atomic `configStore.update { byJwt=new, devicePubkey=pubkey, deviceNetworkName=name, byClientJwt=null }`
4. `byClientJwt=null` invalidation is critical — old `byClientJwt` was derived from guest `byJwt` and is no longer valid

On migration failure: the guest `byJwt` remains untouched, relay continues in guest mode (working but no earnings).

### Payout Wallet Binding

After successful walletAuth and `bridge.start()`, the existing `UrnetworkPayoutWalletSetup.configure()` flow runs:
- `addExternalWallet(PRESET_WALLET, "solana")` — registers the developer's Solana address
- `updatePayoutWallet(PRESET_WALLET)` — sets it as the active payout destination

With a non-guest JWT, these wallet API calls now succeed (previously blocked by server-side 401 for guest accounts).

### Payout Pipeline Telemetry (2026-05-23)

`WALLET_ADD_TIMEOUT_MS` raised from 10 s to 30 s (commit b86fb16f). The original 10 s budget was insufficient when the backend takes longer to process `addExternalWallet` — `updatePayoutWallet` would not be called if the first call timed out, leaving the device with no payout wallet set for that session.

`configure()` now returns `Boolean` and emits explicit log events:
- `relay sharing: endpoint bound` — `addExternalWallet` succeeded within timeout
- `relay sharing: endpoint deferred` — timeout hit, `updatePayoutWallet` not called; will retry on next engine start
- `relay sharing: traffic forwarded — accumulated_bytes=N` — direct proof of mesh forwarding to a peer

Six sentinel-tests in `UrnetworkPayoutPipelineSentinelTest` gate the WALLET_ADD_TIMEOUT_MS value, the `configure()` return type, and the three log message patterns.

### ToS Risk Assessment

The user explicitly accepted ToS risk: "На ToS похуй, вроде ни разу бана никому не давали." Design mitigations:
- Network names randomized as `n<hex8>` (no `ozero` prefix) — reduces bulk detection
- Each device has a unique pubkey/network — not a single shared account
- No private key logging (sentinel test enforces)
- `PRESET_WALLET` is a single payout address across many networks — the primary fingerprinting risk

### Test Coverage

- `Base58Test` (7): empty, leading zeros, golden, roundtrip, Solana 32-byte, alphabet, invalid char
- `InMemoryDeviceIdentityTest` (6): signature length, deterministic seed, different seed→different key, Ed25519 verify, invalid seed size, Base58 check
- `DeviceWalletJwtSentinelTest` (12): source-inspection for WALLET_MESSAGE_PREFIX, blockchain=solana, authLogin before networkCreate, terms=true, Base64.NO_WRAP, no privkey logging, AndroidKeyStore, atomic update, migration flow, byClientJwt invalidation
- `EngineUrnetworkDeviceJwtTest` (8): Success/Error/idempotent/legacy migration/migration error fallback/cached networkName/identity null/new networkName
- `UrnetworkConfigStoreTest` extension (7): devicePubkey + deviceNetworkName persistence

## Related Concepts

- [[concepts/urnetwork-guest-mode-relay-blocker]] - The monetization blocker that walletAuth solves; guest JWT → 401 on wallet API
- [[concepts/urnetwork-relay-always]] - Relay architecture that benefits from non-guest JWT; walletAuth enables actual earnings
- [[concepts/urnetwork-runtime-release-lifecycle]] - Go-runtime singleton lifecycle; walletAuth runs within the same SDK instance
- [[concepts/core-backup-module]] - Device keypair is NOT backed up (AndroidKeyStore-bound); new device = new identity = new network

## Sources

- [[daily/2026-05-18.md]] - Session 18:25: walletAuth research — server endpoints, no-nonce protocol, payout trivial but ToS risk; Session 18:52: research continued — message format, uniqueness constraint; Session 19:07: review fixes + protocol details saved to memory; Session 19:30: full implementation — Base58, RealUrnetworkDeviceIdentity (AES-GCM + AndroidKeyStore), RealUrnetworkAuthService.acquireDeviceWalletJwt, migration flow, 40 tests, commit 0ef16e3a
