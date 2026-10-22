# Архитектура Ozero

Документ описывает текущую реализацию в `dev`. Контракты Kotlin, Gradle-конфигурация и workflow определяют фактическое поведение; исторический план хранится отдельно в [архиве](archive/PLAN-2026-05.md).

## Модули

| Область | Модули и назначение |
|---------|--------------------|
| Приложение | `app`: Compose UI, Hilt composition root, настройки, диагностика и обновление APK |
| Контракты движков | `engines-core`: `EnginePlugin`, конфигурации, capabilities, readiness и socket protection |
| VPN | `common-vpn`: сервис, TUN, запуск, остановка, переключение, watchdog и состояние туннеля |
| Общие компоненты | `common-dns`, `common-net`, `common-crypto`, `core-storage`, `core-backup` |
| Транспорты | `engine-byedpi`, `engine-warp`, `engine-fptn`, `engine-urnetwork`, `engine-masterdns`, `engine-singbox` |
| WARP | `shared-warp-settings`: общая модель конфигурации и AWG-параметры |
| sing-box | `singbox-process`, `singbox-core`, `singbox-fmt`, `singbox-config`, `singbox-room`, `singbox-subscription` |

Полный список модулей — [settings.gradle.kts](../settings.gradle.kts). Наличие старого binary build script или записи в lock само по себе не означает, что соответствующий движок подключён в приложение.

## Подключение и запуск движка

Движки реализуют [EnginePlugin](../engines-core/src/main/java/ru/ozero/enginescore/EnginePlugin.kt) и регистрируются Hilt-модулями приложения через `@IntoSet`. Общая VPN-логика использует контракт плагина; настройки UI и DI связывают его с конкретным модулем.

`start(config, upstream)` подготавливает транспорт. Для движков с собственным TUN используются `tunSpec()` и отдельный контракт `TunFdAcceptor.attachTun()`. `awaitReady()` подтверждает готовность. Возврат `StartResult.Success` сам по себе не заменяет подтверждение handshake/readiness. Локальные SOCKS-движки подключаются к TUN через HEV.

[StartSequenceCoordinator](../common-vpn/src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt) выбирает конфигурацию и запускает путь TUN или proxy. [ChainOrchestrator](../engines-core/src/main/java/ru/ozero/enginescore/ChainOrchestrator.kt) управляет цепочкой; один движок — частный случай цепочки длины один. Допустимые комбинации ограничены capabilities и реализацией движков.

[TunnelController](../common-vpn/src/main/java/ru/ozero/commonvpn/TunnelController.kt) хранит состояние и переходы. Ошибки работающего движка передаются через `RuntimeFailureRouter`. Изменения настроек обрабатываются `EngineSettingsRestartObserver` и `RuntimeConfigRestartCoordinator`; fingerprint должен включать параметры, которые действительно меняют runtime.

## Нативные библиотеки и процессы

- WARP использует `RealWarpSdkBridge` → `RemoteAwgRuntime` → `WarpEngineService` в процессе `:engine_warp`. Библиотеки `libam*.so` загружаются в этом процессе.
- URnetwork использует `RealUrnetworkSdkBridge` и SDK `DeviceLocal`/`IoLoop` для передачи пакетов через TUN. Самостоятельный `userwireguard.aar` не входит в текущий app release pipeline.
- sing-box использует runtime-модуль `singbox-process`, libbox и сервис в процессе `:engine_singbox`. URnetwork и sing-box используют общую подготовку `go.Seq`; классы `go/*` удаляются из дублирующих SDK-артефактов.
- FPTN использует Kotlin-обвязку HTTPS/WebSocket и JNI-библиотеку `libfptn_native_lib.so`.
- MasterDNS запускает нативный клиент как subprocess.

Изоляция WARP нужна для совместимости Go runtimes. Native-библиотеки загружаются лениво; правила загрузки и порядка остановки сохраняются в [AGENTS.md](../AGENTS.md).

## Настройки и безопасность

Настройки движков хранятся в DataStore; для секретов FPTN, WARP и URnetwork реализованы шифрование через Android Keystore и миграция прежних plaintext-значений. Встроенный backup имеет отдельные форматы и поведение — шифрование DataStore не является обещанием шифрования каждого экспорта.

Kill-switch и IPv6 routing принадлежат общей VPN-логике; их поведение зависит от режима, настроек и поддержки транспорта. Fail-closed и отсутствие обхода туннеля требуют проверки при изменениях маршрутизации и lifecycle.

Встроенное обновление APK проверяет Ed25519-подпись через `ApkUpdateVerifier`. Подписки sing-box имеют отдельный HTTP/TLS pipeline; проверка APK не означает Ed25519-подпись произвольной импортированной подписки.

## Где проверять изменения

- [CI](../.github/workflows/ci.yml): стиль, unit tests, coverage, обнаружение непустых JUnit-отчётов и debug APK.
- [Android soak](../.github/workflows/soak.yml): отдельная проверка sing-box на Android; её результат не заменяет проверку остальных движков.
- [Release](../.github/workflows/release.yml): подготовка native-зависимостей, сборка и проверки APK.

Сборка и происхождение бинарников описаны в [руководстве](build.md). При изменениях native-кода проверяются и исходники, и библиотека, которая фактически упаковывается в APK.
