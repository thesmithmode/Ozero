[Русский](README.md) | [English](README.en.md) | Español | [Português](README.pt.md)

# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Cliente VPN open-source para Android con soporte para múltiples motores de transporte y ofuscación de tráfico bajo una interfaz unificada.

## Requisitos

- Android 7.0+ (API 24); se recomienda Android 10+
- ABI: `arm64-v8a`

## Motores soportados

| Motor | Transporte | Propósito |
|-------|------------|-----------|
| ByeDPI | proxy TCP local | Fragmentación SNI, ofuscación del handshake TLS |
| WARP (AmneziaWG) | WireGuard/UDP | Cloudflare WARP con campos extendidos junk/S1-S2/H1-H4 |
| FPTN | HTTPS + SNI Reality | Ofuscación del handshake TLS imitando dominios populares |
| URnetwork | mesh P2P | Anonimización mediante una red peer de proveedores |
| MasterDNS | DNS sobre UDP | Fallback de emergencia, desplegado en tu propio VPS en un clic |

Cada motor está aislado en un módulo Gradle dedicado y se conecta mediante la interfaz `EnginePlugin`.

## Arquitectura

- Arquitectura modular: cada transporte es un módulo Gradle dedicado `engine-*`
- Interfaz `EnginePlugin` unificada — la aplicación no depende de los detalles del transporte
- Sistema de plugins extensible
- Kill-switch interno: el tráfico se bloquea ante un fallo del motor (fail-closed)
- Suscripciones de servidores verificadas con Ed25519
- Compilación reforzada: R8 minify + shrink, ofuscación de clases
- UI por motor con sus propios ajustes

## Compilación

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew assembleRelease
```

La compilación de release requiere variables de entorno para la firma del APK y la clave pública de actualizaciones.

## Documentación

- [`docs/architecture.md`](docs/architecture.md) — capas, módulos, DI
- [`docs/engines.md`](docs/engines.md) — detalles por motor
- [`docs/runtime-flow.md`](docs/runtime-flow.md) — flujo VPN de inicio a parada
- [`docs/masterdns-server-setup.md`](docs/masterdns-server-setup.md) — desplegar el servidor MasterDNS en 15 minutos

## Licencia

GPLv3 — ver [LICENSE](LICENSE).
