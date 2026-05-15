# engine-warp

Cloudflare WARP engine (F2). WireGuard-based, конфиг получает через `ProxyWarpAutoConfig`
— список из ~78 публичных serverless-зеркал (Netlify/Vercel/Cloudflare Workers),
которые проксируют запрос к Cloudflare и возвращают готовый `[Interface]/[Peer]`
.conf text. Прямой `api.cloudflareclient.com` блокируется ТСПУ в РФ — список
зеркал и schema запроса/ответа извлечены из CYBERPORTAL_X-v1.0.2 и подтверждены
bundled sample КИБЕРЩИТ-X/assets/bundled/str_warp_2.conf.

Проксирующие зеркала генерируют пару ключей серверной стороной → клиент НЕ
посылает свой public key, и `WarpConfig.publicKey` / `accountLicense` опциональны.
Регистрация: shuffle списка → race по 8 параллельных POST, общий бюджет 240s,
первый успех отменяет остальные.

## Bridge layer

`WarpSdkBridge` — контракт `start(WarpConfig) → StartResult / stop() / isRunning()`.

Реализации:

- `StubWarpSdkBridge` — default DI binding (`WarpModule.provideWarpSdkBridge`).
  Любой `start()` возвращает `Failed("WireGuard AAR not yet built ...")`. Активна
  пока `RealWarpSdkBridge` не включён в DI.
- `RealWarpSdkBridge` — обёртка над `com.wireguard.android.backend.GoBackend` +
  `Tunnel` API. Maven artifact `com.wireguard.android:tunnel:1.0.20230706` уже
  подключён в `engine-warp/build.gradle.kts`, кастомный AAR build не требуется.

## DI wiring

В `app/src/main/java/ru/ozero/app/di/WarpModule.kt`:

`RealWarpSdkBridge(RemoteAwgRuntime(context, WarpEngineService))` — bridge
делегирует AWG-вызовы в isolated process `:engine_warp` через AIDL. Singleton
гарантируется Hilt. Process-isolation защищает от dual-Go-runtime SIGABRT
(libam-go в :engine_warp, libgojni в main). См. `OzeroAppProcessIsolationTest`.

## Open TODO в RealWarpSdkBridge

Помечены `// TODO link via DI`:

- Backend lifecycle — сейчас lazy field, при switch → переехать в `@Provides
  GoBackend` отдельно (полезно для тестов).
- DNS provider — WARP advertises 1.1.1.1 / 2606:4700:4700::1111, сейчас полагаемся
  на системный resolver через VpnService. Поднять в `WarpConfig.dnsServers` если
  WarpAutoConfig начнёт их возвращать.
- Persistent keepalive — WARP рекомендует 25s на CGNAT/мобильных. Поле появится
  в `WarpConfig.keepaliveSeconds` после соответствующей правки.

## Sanity-check артефакта

```
./gradlew :engine-warp:dependencies --configuration releaseRuntimeClasspath \
    | grep com.wireguard
```

Должен показать `com.wireguard.android:tunnel:1.0.20230706` (resolved).
