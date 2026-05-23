[Русский](README.md) | [English](README.en.md) | Español | [Português](README.pt.md)

# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Cliente VPN gratuito para Android con soporte para múltiples protocolos de transporte bajo una interfaz unificada.

## Requisitos

- Android 7.0+ (API 24); se recomienda Android 10+
- ABI: `arm64-v8a`

## Arquitectura

- Arquitectura modular: cada transporte está aislado en un módulo Gradle independiente
- Interfaz `Engine` unificada — la aplicación no depende de los detalles del transporte
- Sistema de plugins de motores extensible
- Kill-switch interno: el tráfico se bloquea ante un fallo del motor (fail-closed)
- Suscripciones de servidores verificadas con Ed25519
- Compilación reforzada: R8 minify + shrink

## Compilación

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew assembleRelease
```

La compilación de release requiere variables de entorno para la firma del APK y la clave pública de actualizaciones.

## Licencia

GPLv3 — ver [LICENSE](LICENSE).
