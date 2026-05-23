[Русский](README.md) | English | [Español](README.es.md) | [Português](README.pt.md)

# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

A free Android VPN client that supports multiple transport protocols under a unified interface.

## Requirements

- Android 7.0+ (API 24); Android 10+ recommended
- ABI: `arm64-v8a`

## Architecture

- Modular architecture: each transport is isolated in a separate Gradle module
- Unified `Engine` interface — the application has no knowledge of transport internals
- Extensible engine plugin system
- Internal kill-switch: traffic is blocked on engine failure (fail-closed)
- Server subscriptions verified with Ed25519
- Build hardening: R8 minify + shrink

## Build

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew assembleRelease
```

The release build requires environment variables for APK signing and the update public key.

## License

GPLv3 — see [LICENSE](LICENSE).
