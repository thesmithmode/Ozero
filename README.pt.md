[Русский](README.md) | [English](README.en.md) | [Español](README.es.md) | Português

# Ozero

[![CI](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/thesmithmode/Ozero/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/thesmithmode/Ozero?include_prereleases&sort=semver)](https://github.com/thesmithmode/Ozero/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Cliente VPN open-source para Android com suporte a múltiplos motores de transporte e ofuscação de tráfego sob uma interface unificada.

## Requisitos

- Android 7.0+ (API 24); recomendado Android 10+
- ABI: `arm64-v8a`

## Motores suportados

| Motor | Transporte | Finalidade |
|-------|------------|------------|
| ByeDPI | proxy TCP local | Fragmentação SNI, ofuscação do handshake TLS |
| WARP (AmneziaWG) | WireGuard/UDP | Cloudflare WARP com campos estendidos junk/S1-S2/H1-H4 |
| FPTN | HTTPS + SNI Reality | Ofuscação do handshake TLS imitando domínios populares |
| URnetwork | mesh P2P | Anonimização através de uma rede peer de provedores |
| MasterDNS | DNS sobre UDP | Fallback de emergência, implantado no seu próprio VPS em um clique |
| sing-box | protocolos e transportes do libbox | Perfis, assinaturas, seleção automática e cadeias |

Cada motor é isolado em um módulo Gradle dedicado e conectado através da interface `EnginePlugin`.

## Arquitetura

- Arquitetura modular: cada transporte é um módulo Gradle dedicado `engine-*`
- Interface `EnginePlugin` unificada — o aplicativo não depende dos detalhes do transporte
- Sistema de plugins extensível
- Kill-switch interno: o tráfego é bloqueado em caso de falha do motor (fail-closed)
- Atualizações de APK no aplicativo verificadas com Ed25519
- Compilação protegida: R8 minify + shrink, ofuscação de classes
- UI por motor com ajustes próprios

## Compilação

```bash
git clone --branch dev https://github.com/thesmithmode/Ozero.git
cd Ozero
```

Antes de compilar, prepare as bibliotecas nativas e os artefatos dos SDKs. A compilação de release também exige variáveis de assinatura do APK e uma chave pública de atualizações. Consulte o [guia de compilação](docs/build.md) (em russo); clonar o repositório não prepara todos os motores.

## Documentação

- [Arquitetura e ciclo de vida](docs/architecture.md) (em russo)
- [Compilação e publicação de APK](docs/build.md) (em russo)
- Motores: [WARP](engine-warp/README.md), [URnetwork](engine-urnetwork/README.md), [FPTN](engine-fptn/README.md)
- [Plano atual](PLAN.md) (em russo)

O desenvolvimento é integrado em `dev`, embora a branch padrão do GitHub seja `main`. Um push para `main` inicia a publicação de um APK.

## Licença

GPLv3 — veja [LICENSE](LICENSE).
