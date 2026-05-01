# engine-urnetwork

URnetwork P2P bandwidth-sharing engine (F7). Использует **два** Go-AAR:

1. `URnetworkSdk.aar` — API-уровень (account, connect URL discovery, gateway
   selection). Source: `github.com/urnetwork/sdk`. Build: `tools/build-urnetwork-aar.sh`.
2. `userwireguard.aar` — tunnel-уровень (форк wireguard-go от org urnetwork).
   Source: `github.com/urnetwork/userwireguard`. Build: `scripts/build_wireguard_android.sh`.

Оба AAR кладутся в `engine-urnetwork/libs/` и подхватываются `fileTree(...)` в
`build.gradle.kts`. Если папка пуста — fileTree резолвится в пустой набор и
конфигурация не падает; Stub bridge остаётся активным.

## Bridge layer

`UrnetworkSdkBridge` — контракт `start(walletAddress, apiUrl, connectUrl) →
StartResult / stop() / isRunning()`.

Реализации:

- `StubUrnetworkSdkBridge` — default DI binding (`UrnetworkModule.provideUrnetworkSdkBridge`).
  `start()` возвращает `Failed("URnetwork SDK AAR not yet built ...")`.
- `RealUrnetworkSdkBridge` — TBD когда оба AAR доступны. Должен:
  - инициализировать `URnetworkSdk` (Java package `com.bringyour`),
  - получить connect URL по walletAddress,
  - построить inline INI config для userwireguard,
  - вызвать `userwgbind.StartUserWg(configIni)`.

## Сборка AAR

### URnetwork SDK

```
RUNNER_TEMP=/tmp/urnetwork bash tools/build-urnetwork-aar.sh
```

См. blockers в шапке скрипта (Go 1.25, GOEXPERIMENT=greenteagc, replace
директивы для urnetwork/connect и urnetwork/glog).

### userwireguard tunnel

```
bash scripts/build_wireguard_android.sh
```

Идемпотентен — пропускает работу если `userwireguard.aar` существует и SHA в
`userwireguard.aar.sha256` совпадает (иначе пересобирает). Force: `FORCE_REBUILD=1`.

CI: `binaries.yml` workflow_dispatch с input `artifact: userwireguard` собирает
AAR в Docker (Go + gomobile + NDK) и публикует prerelease с тегом
`userwireguard-<sha8>`.

## Switch Stub → Real

В `app/src/main/java/ru/ozero/app/di/UrnetworkModule.kt`:

1. Убедиться что оба AAR лежат в `engine-urnetwork/libs/`.
2. Реализовать `RealUrnetworkSdkBridge(context)` (TBD).
3. Удалить `provideUrnetworkSdkBridge()` со `StubUrnetworkSdkBridge()`.
4. Раскомментировать соседний блок с `RealUrnetworkSdkBridge(context)`.

## Sanity-check артефактов

```
ls -lh engine-urnetwork/libs/
cat engine-urnetwork/libs/userwireguard.manifest.txt
cat engine-urnetwork/libs/manifest.txt
```
