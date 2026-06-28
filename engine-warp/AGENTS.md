# engine-warp — критические инварианты

## AWG обфускация ОБЯЗАТЕЛЬНА — jc НИКОГДА не должен быть 0 по умолчанию

Vanilla WireGuard заблокирован в РФ на уровне DPI. WARP работает только через AmneziaWG (AWG).

Конфиги от Cloudflare WARP API — стандартный WireGuard формат без jc/jmin/jmax полей.
Это НЕ означает "отключить обфускацию". Это означает "применить дефолтный AWG пресет".

**WarpConfParser.parseAwgParams fallback должен использовать `AwgParams.DEFAULT_JC/JMIN/JMAX` (5/100/200),
а НЕ 0.** jc=0 → vanilla WG → DPI блок → WARP не работает в РФ вообще.

Если конфиг имеет явный jc (напр. кастомный пресет) → он берётся из файла.
Если отсутствует (все API-конфиги) → DEFAULT_JC=5 как минимально необходимый обход DPI.

## DNS resolve перед attachTun

`EngineWarp.resolveEndpointHost()` резолвит hostname → IP до VPN establish только через bootstrap-safe DoH,
иначе system DNS до туннеля раскрывает endpoint во внешнюю сеть, а DNS через туннель который ещё не поднят → loop.

В тестах: использовать IP в peerEndpoint или локальный DoH mock, не hostname с system DNS.

## WarpSdkBridge — AmneziaWG backend

`AwgBackend` — обёртка над libam-go.so (не libwg-go.so). Не путать.
`AbstractBackend` конструктор грузит libam-go.so через ReLinker — не обходить конструктор,
иначе JNI_OnLoad не вызовется и все native вызовы упадут.

## Cooldown авто-регистрации

`ProxyWarpAutoConfig.lastSuccessMs` — in-memory, сбрасывается при kill process.
`COOLDOWN_MS = 5 min` — защита от rate-limit Cloudflare API.
