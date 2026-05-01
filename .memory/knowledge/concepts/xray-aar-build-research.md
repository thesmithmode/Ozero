# Xray-core AAR build pipeline — W4.1 research

**Status:** research-only. Implementation требует device verify + reproducible build infrastructure.

## Источники

- Xray-core: github.com/xtls/Xray-core (Go, MPL-2.0)
- v2rayNG paths: github.com/2dust/v2rayNG (reference Android integration)
- gomobile: golang.org/x/mobile/cmd/gomobile

## Текущая infrastructure

build_*.sh уже существуют для:
- build_amneziawg.sh (gomobile bind amnezia-vpn/amneziawg-go) — AmneziaWG2
- build_hysteria2.sh (gomobile bind apernet/hysteria) — Hysteria2 client
- build_byedpi.sh (NDK CMake) — ByeDPI native shared lib
- build_naive.sh (Chromium build) — NaiveProxy (Android NDK build = блокер)
- build_tor.sh (Briar fork) — Tor binary

Все используют:
- `Dockerfile` с pinned Go + NDK r27 + gomobile commit
- ANDROID_NDK_ROOT, ANDROID_TARGETS env
- Output AAR + SHA256

## Xray AAR — pattern

Скрипт `build_xray.sh` создан по образцу build_amneziawg.sh:
1. gomobile init
2. clone xtls/Xray-core @ pinned version (v25.10.5 — последний stable 2026-04)
3. go mod tidy
4. gomobile bind -target android/arm64,android/arm,android/amd64 -androidapi 24
5. SHA256

ABI: 3 (без x86 для consistency с :app abiFilters W5.8 alignment).

Output: `out/xray/libxray.aar` + sources jar + sha256.

## Известные ловушки

### 1. Размер AAR

Xray-core полный — ~30-40 MB. Сравнение:
- libgojni.so в КИБЕРЩИТ-X v1.0.4 (Xray) = 40 MB монолит
- AmneziaWG AAR = ~10 MB
- ByeDPI .so = ~500 KB

Это ~10× размер base APK. Для distribution через Play Store — 100MB warning.
Для F-Droid / sideload — норм.

### 2. JNI export pattern

gomobile генерирует `Xray.kt` из exported Go функций. v2rayNG pattern:
- `xray.startV2Ray(configJson: String, statsManager: ...)` — start
- `xray.stopV2Ray()` — stop
- `xray.queryStats()` — traffic counters

Ozero `XrayEnginePlugin` будет использовать эти функции через `@Inject LibXrayDelegate`.

### 3. gomobile cache в CI

Полный gomobile bind = 5-10 минут. Без cache CI = 10 мин per release.

Pattern из release.yml для libhev:
- actions/cache@v4 на jniLibs/ output
- key: "xray-${XRAY_VERSION}-ndk-r27c-${HASH(go.mod)}-v1"
- Cache hit пропускает gomobile bind

Для AAR — cache на `out/xray/libxray.aar` + SHA256 verify.

### 4. Reproducible builds

Phase E13 release-checklist требует diffoscope verify:
- 2 builds на разных машинах должны давать identical AAR
- gomobile с фиксированным GOFLAGS + ldflags `-s -w -trimpath` дают reproducible
- Уже used в build_amneziawg.sh

### 5. Конфликт с ByeDPI

ByeDPI в base APK (~500KB). Xray AAR (~30MB) увеличит APK ~30MB.

Решения:
- **Dynamic Feature Module** :dynamic-xray (PlayCore SplitInstall) — on-demand download. Pattern из PRD §5.4 для Tor.
- **Один универсальный APK** — простейший, +30MB на base.
- **AAB split** — Play Store сделает per-ABI APK с ~10MB Xray.

Для v0.0.x — один APK, accept overhead. DFM — после Phase 3 stable.

## Следующие шаги (W4.2)

После build_xray.sh validated в CI с green AAR:
1. `:engine-xray` модуль с `LibXrayDelegate` interface
2. `XrayEnginePlugin: EnginePlugin` impl
3. `XrayConfigBuilder` — VLESS+Reality+XHTTP base config
4. EnginesModule provideXrayPlugin @IntoSet
5. UI: ManualEngine selection включает Xray
6. Tests: XrayEngineTest mock LibXrayDelegate
7. Smoke: real XRAY URI from bootstrap-servers.json через emulator

## Decision

**W4.1 = build_xray.sh + Dockerfile reuse + binaries.yml workflow extension. Pure infra, верифицируется через CI artifact build.**

W4.2 (real engine integration) требует device verify и AAR из W4.1. Sequential.

## Lic compliance

Xray-core MPL-2.0 → совместимо с Ozero GPL-3.0 (MPL-2.0 weaker, can be GPL'd in combination).
NOTICE.md: добавить attribution для Xray-core upstream.
