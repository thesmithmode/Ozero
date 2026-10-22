# engine-warp

WARP использует AmneziaWG backend и реализует `EnginePlugin` через `EngineWarp`. Настройки слотов и AWG-полей находятся также в `shared-warp-settings`.

## Bridge и отдельный процесс

Production DI в [WarpModule](../app/src/main/java/ru/ozero/app/di/WarpModule.kt):

```text
EngineWarp
  → RealWarpSdkBridge
    → RemoteAwgRuntime
      → WarpEngineService (:engine_warp)
```

[WarpSdkBridge](src/main/java/ru/ozero/enginewarp/WarpSdkBridge.kt) принимает TUN fd, INI-конфигурацию, UAPI path и socket protector через `attachTun()`. Отдельный контракт существует для proxy-режима. Остановка выполняется через `detachTun()` или `stopProxy()`.

[WarpEngineService](../app/src/main/java/ru/ozero/app/warp/WarpEngineService.kt) объявлен в [манифесте app](../app/src/main/AndroidManifest.xml) с `android:process=":engine_warp"`. AIDL связывает процесс приложения с native backend. При смерти процесса вызывается runtime failure routing.

## Нативные артефакты

В Git отслеживаются ARM64-библиотеки `libam.so`, `libam-go.so` и `libam-quick.so` в `src/main/jniLibs/arm64-v8a/`. Release workflow проверяет их наличие в APK и SHA256 backend.

Текущий engine-модуль не использует Maven `com.wireguard.android:tunnel` и не требует переключения DI с Stub на Real. Историческая сборка отдельного AmneziaWG AAR не описывает активный runtime.

`libam-go` загружается только в `:engine_warp`, отдельно от Go runtime URnetwork/sing-box. См. [общие правила загрузки](../AGENTS.md) и [инварианты WARP и AWG defaults](AGENTS.md).

## Конфигурация

`ProxyWarpAutoConfig` получает конфигурации через зеркала. Также поддерживаются импорт конфигураций и слоты. Состав зеркал, retry-бюджет и defaults определены кодом; старые фиксированные количества зеркал не являются контрактом.

AWG-поля из конфигурации должны сохраняться. Если их нет в обычном WARP-конфиге, parser применяет AWG defaults; отсутствие полей не означает отключение обфускации.

## Проверка изменений

Проверяются импорт и сохранение слотов, DNS endpoint до установления TUN, handshake/readiness, передача трафика, смена движка и остановка удалённого процесса. ABI production APK — ARM64; произвольная подмена backend или добавление ABI требует отдельной native-валидации.

Порядок сборки — [docs/build.md](../docs/build.md).
