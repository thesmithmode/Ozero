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
| sing-box | libbox protocols and transports | Profiles, subscriptions, automatic selection and chains |

Each engine is isolated in a dedicated Gradle module and plugged in through the `EnginePlugin` interface.

## Architecture

- Modular architecture: each transport is a dedicated Gradle module `engine-*`
- Unified `EnginePlugin` interface — the app does not depend on transport internals
- Extensible plugin system
- Internal kill-switch: traffic is blocked on engine failure (fail-closed)
- The in-app updater implements Ed25519 verification; publication of the required `.apk.sig` is not configured yet — see [signature formats](docs/build.md#подписи-опубликованного-обновления) (Russian)
- Build hardening: R8 minify + shrink, class obfuscation
- Per-engine UI with settings, no shared configuration screen

## Build

```bash
git clone --branch dev https://github.com/thesmithmode/Ozero.git
cd Ozero
```

Prepare the native libraries and SDK artifacts before building. Release builds also require APK signing variables and an update public key. See the [build guide](docs/build.md) (Russian); cloning alone does not prepare every engine.

## Documentation

- [Architecture and engine lifecycle](docs/architecture.md) (Russian)
- [Builds, native artifacts and APK releases](docs/build.md) (Russian)
- Engines: [WARP](engine-warp/README.md), [URnetwork](engine-urnetwork/README.md), [FPTN](engine-fptn/README.md)
- [Current plan](PLAN.md) (Russian)

Development targets `dev`. GitHub opens `main` by default; target development changes at `dev`. A push to `main` triggers an APK release.

## License

GPLv3 — see [LICENSE](LICENSE).
