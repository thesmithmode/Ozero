# engine-urnetwork

URnetwork реализован как `EngineUrnetwork` с `RealUrnetworkSdkBridge`. Production DI в [UrnetworkModule](../app/src/main/java/ru/ozero/app/di/UrnetworkModule.kt) использует реальный bridge.

## SDK и TUN

[UrnetworkSdkBridge](src/main/java/ru/ozero/engineurnetwork/UrnetworkSdkBridge.kt) разделяет `start(..., byClientJwt)`, `attachTun(tunFd)` и `stop()`. Bridge создаёт SDK device, подключает TUN через `Sdk.newIoLoop(device, tunFd, callback)` и управляет выбором локации и состоянием соединения.

Consumer VPN и relay имеют отдельные координаторы. `attachRelayTun()` не включает consumer connect автоматически. JWT bootstrap, device identity, локации и relay-настройки связываются в Hilt-модуле приложения.

## Подготовка SDK

- SDK: `URnetworkSdk.aar`, Java package `com.bringyour.sdk`.
- В engine-модуле SDK используется через `compileOnly`; runtime-зависимость упаковывает `app`.
- [CI](../.github/workflows/ci.yml) и [release](../.github/workflows/release.yml) скачивают `urnetwork-sdk-*` asset в `engine-urnetwork/libs/` и удаляют дублирующие `go/*.class`.
- [app/build.gradle.kts](../app/build.gradle.kts) использует `StripGoClassesFromAarTask`. Общие Go stubs подготавливаются вместе с libbox.
- `userwireguard.aar` не является второй обязательной библиотекой текущего VPN pipeline. Наличие исторического скрипта его сборки не меняет production DI.
- Отсутствующий SDK не переключает production на заглушку; его нужно подготовить до компиляции модулей, использующих SDK types.

Самостоятельная сборка SDK описана в [tools/build-urnetwork-aar.sh](../tools/build-urnetwork-aar.sh). Скрипт по умолчанию использует upstream `main`; для повторяемой сборки выбирайте явную upstream-версию и сохраняйте manifest. Это отдельная операция от сборки APK.

## Процессы и проверка

URnetwork использует Go runtime в основном процессе. WARP загружает свой Go backend в `:engine_warp`; их нельзя переносить в один процесс без пересмотра native isolation.

Изменения bridge проверяются на запуске, выборе локации, передаче TUN-пакетов, остановке, повторном старте и завершении `IoLoop`. Relay проверяется отдельно от consumer VPN.

Общий порядок подготовки артефактов — [docs/build.md](../docs/build.md).
