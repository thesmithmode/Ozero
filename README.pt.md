[Русский](README.md) | [English](README.en.md) | [Español](README.es.md) | Português

# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Cliente VPN gratuito para Android com suporte a múltiplos protocolos de transporte sob uma interface unificada.

## Requisitos

- Android 7.0+ (API 24); recomendado Android 10+
- ABI: `arm64-v8a`

## Arquitetura

- Arquitetura modular: cada transporte é isolado em um módulo Gradle separado
- Interface `Engine` unificada — a aplicação não depende dos detalhes do transporte
- Sistema extensível de plugins de motores
- Kill-switch interno: o tráfego é bloqueado em caso de falha do motor (fail-closed)
- Assinaturas de servidores verificadas com Ed25519
- Compilação protegida: R8 minify + shrink

## Compilação

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew assembleRelease
```

A compilação de release requer variáveis de ambiente para assinatura do APK e a chave pública de atualizações.

## Licença

GPLv3 — ver [LICENSE](LICENSE).
