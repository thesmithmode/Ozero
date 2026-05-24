[Русский](README.md) | English | [Español](README.es.md) | [Português](README.pt.md)

# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Open-source Android VPN client supporting multiple transport engines and traffic obfuscation under a unified interface.

## Requirements

- Android 7.0+ (API 24); Android 10+ recommended
- ABI: `arm64-v8a`

## Supported engines

| Engine | Transport | Purpose |
|--------|-----------|---------|
| ByeDPI | local TCP proxy | SNI fragmentation, TLS handshake obfuscation |
| WARP (AmneziaWG) | WireGuard/UDP | Cloudflare WARP with extended junk/S1-S2/H1-H4 fields |
| FPTN | HTTPS + SNI Reality | TLS handshake obfuscation mimicking popular domains |
| URnetwork | P2P mesh | Anonymisation through a peer provider network |
| MasterDNS | DNS-over-UDP | Emergency fallback, deployed on your own VPS in one click |

Each engine is isolated in a dedicated Gradle module and plugged in through the `EnginePlugin` interface.

## Architecture

- Modular architecture: each transport is a dedicated Gradle module `engine-*`
- Unified `EnginePlugin` interface — the app does not depend on transport internals
- Extensible plugin system
- Internal kill-switch: traffic is blocked on engine failure (fail-closed)
- Server subscriptions verified with Ed25519
- Build hardening: R8 minify + shrink, class obfuscation
- Per-engine UI with settings, no shared configuration screen

## Build

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew assembleRelease
```

The release build requires environment variables for APK signing and the update public key. Details — `.claude/Контекст/Architect.md`.

## License

GPLv3 — see [LICENSE](LICENSE).
