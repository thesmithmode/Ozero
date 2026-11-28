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
| Opera Proxy | SOCKS5 TCP | Seleção de região e servidor; VPN ou proxy |
| WARP 2 (MASQUE) | CONNECT-IP / HTTP/3 ou HTTP/2 | Perfil WARP separado com fallback automático |
| Telegram WSS | TLS WebSocket + WARP | Tráfego dos DCs do Telegram por WSS e demais tráfegos por WARP |
| Adaptive VPN | VPN / TCP | Seleção manual de modo, região e transporte |

Cada motor é isolado em um módulo Gradle dedicado e conectado através da interface `EnginePlugin`.

## Arquitetura

- Arquitetura modular: cada transporte é um módulo Gradle dedicado `engine-*`
- Interface `EnginePlugin` unificada — o aplicativo não depende dos detalhes do transporte
- Sistema de plugins extensível
- Kill-switch configurável no modo VPN: bloqueia o tráfego em caso de falha conforme a política de roteamento.
- URnetwork também pode atuar como relay em segundo plano junto a outro motor conectado; o funcionamento e as recompensas dependem das configurações e do serviço.
- As versões são instaladas manualmente. A verificação Ed25519 ainda não está ligada à interface nem a verificações em segundo plano. Veja os [formatos de assinatura](docs/build.md#подписи-опубликованного-обновления) (em russo).
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
- Motores: [WARP](engine-warp/README.md), [URnetwork](engine-urnetwork/README.md), [FPTN](engine-fptn/README.md), [Opera Proxy](engine-opera/README.md), [WARP 2 (MASQUE)](engine-masque/README.md), [Telegram WSS](engine-tgwss/README.md), [Adaptive VPN](engine-gfvpn/README.md)
- [Plano atual](PLAN.md) (em russo)

O desenvolvimento é integrado em `dev`, embora a branch padrão do GitHub seja `main`. Um push para `main` inicia a publicação de um APK.

## Licença

GPLv3 — veja [LICENSE](LICENSE).
