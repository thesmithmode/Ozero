# engine-warp

Cloudflare WARP engine (F2). WireGuard-based, ключи получает через автозапись
(`CloudflareWarpAutoConfig`), peer endpoint статический (`engage.cloudflareclient.com`),
JWT account license — anonymous free tier.

## Bridge layer

`WarpSdkBridge` — контракт `start(WarpConfig) → StartResult / stop() / isRunning()`.

Реализации:

- `StubWarpSdkBridge` — default DI binding (`WarpModule.provideWarpSdkBridge`).
  Любой `start()` возвращает `Failed("WireGuard AAR not yet built ...")`. Активна
  пока `RealWarpSdkBridge` не включён в DI.
- `RealWarpSdkBridge` — обёртка над `com.wireguard.android.backend.GoBackend` +
  `Tunnel` API. Maven artifact `com.wireguard.android:tunnel:1.0.20230706` уже
  подключён в `engine-warp/build.gradle.kts`, кастомный AAR build не требуется.

## Switch Stub → Real

В `app/src/main/java/ru/ozero/app/di/WarpModule.kt`:

1. Удалить `provideWarpSdkBridge()` со `StubWarpSdkBridge()`.
2. Раскомментировать соседний блок с `RealWarpSdkBridge(context)`.
3. Убедиться что `@ApplicationContext context: Context` инжектится — Hilt уже
   умеет его предоставлять, импорт уже есть.
4. `RealWarpSdkBridge` владеет одним `GoBackend(context)` per process — Hilt
   `@Singleton` гарантирует это.

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
