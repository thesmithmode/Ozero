# Архитектура Ozero

Документ описывает текущую реализацию в `dev`. Контракты Kotlin, Gradle-конфигурация и workflow определяют фактическое поведение; исторический план хранится отдельно в [архиве](archive/PLAN-2026-05.md).

## Модули

| Область | Модули и назначение |
|---------|--------------------|
| Приложение | `app`: Compose UI, Hilt composition root, настройки, диагностика и обновление APK |
| Контракты движков | `engines-core`: `EnginePlugin`, конфигурации, capabilities, readiness и socket protection |
| VPN | `common-vpn`: сервис, TUN, запуск, остановка, переключение, watchdog и состояние туннеля |
| Общие компоненты | `common-dns`, `common-net`, `common-crypto`, `core-storage`, `core-backup` |
| Транспорты | `engine-byedpi`, `engine-warp`, `engine-fptn`, `engine-urnetwork`, `engine-masterdns`, `engine-singbox`, `engine-opera`, `engine-masque`, `engine-tgwss`, `engine-gfvpn` |
| WARP | `shared-warp-settings`: общая модель конфигурации и AWG-параметры |
| sing-box | `singbox-process`, `singbox-core`, `singbox-fmt`, `singbox-config`, `singbox-room`, `singbox-subscription` |

Полный список модулей — [settings.gradle.kts](../settings.gradle.kts). Наличие старого binary build script или записи в lock само по себе не означает, что соответствующий движок подключён в приложение.

## Подключение и запуск движка

Движки реализуют [EnginePlugin](../engines-core/src/main/java/ru/ozero/enginescore/EnginePlugin.kt) и регистрируются Hilt-модулями приложения через `@IntoSet`. Общая VPN-логика использует контракт плагина; настройки UI и DI связывают его с конкретным модулем.

`start(config, upstream)` подготавливает транспорт. Для движков с собственным TUN используются `tunSpec()` и отдельный контракт `TunFdAcceptor.attachTun()`. `awaitReady()` подтверждает готовность. Возврат `StartResult.Success` сам по себе не заменяет подтверждение handshake/readiness. Локальные SOCKS-движки подключаются к TUN через HEV.

[StartSequenceCoordinator](../common-vpn/src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt) выбирает конфигурацию и запускает путь TUN или proxy. [ChainOrchestrator](../engines-core/src/main/java/ru/ozero/enginescore/ChainOrchestrator.kt) управляет цепочкой; один движок — частный случай цепочки длины один. Допустимые комбинации ограничены capabilities и реализацией движков. Telegram WSS доступен при ручном выборе. gfVPN доступен при ручном и автоматическом выборе в режиме TUN. Оба движка запускаются самостоятельно.

[TunnelController](../common-vpn/src/main/java/ru/ozero/commonvpn/TunnelController.kt) хранит состояние и переходы. Ошибки работающего движка передаются через `RuntimeFailureRouter`. Изменения настроек обрабатываются `EngineSettingsRestartObserver` и `RuntimeConfigRestartCoordinator`; fingerprint должен включать параметры, которые действительно меняют runtime. Для длительного запуска Opera, MASQUE, Telegram WSS и gfVPN общий [EngineStartupBudget](../common-vpn/src/main/java/ru/ozero/commonvpn/EngineStartupBudget.kt) согласует сроки запуска цепочки, ожидания restart и индикатора переключения; это предельные бюджеты, а не типичное время подключения.

## Нативные библиотеки и процессы

- WARP использует `RealWarpSdkBridge` → `RemoteAwgRuntime` → `WarpEngineService` в процессе `:engine_warp`. Библиотеки `libam*.so` загружаются в этом процессе.
- ByeDPI загружает закреплённый JNI-бинарник; native-владелец закрывает listening socket, остановка использует отдельный управляющий дескриптор и не освобождает guard до завершения владельца.
- URnetwork использует `RealUrnetworkSdkBridge` и SDK `DeviceLocal`/`IoLoop` для передачи пакетов через TUN. Самостоятельный `userwireguard.aar` не входит в текущий app release pipeline. Consumer VPN маршрутизирует собственный трафик, а отдельный [relay-координатор](../app/src/main/java/ru/ozero/app/relay/UrnetworkRelayCoordinator.kt) поддерживает фоновую передачу при подключении другого движка. Relay прекращается при отключении туннеля; при выборе URnetwork второй экземпляр не запускается. Выплаты не гарантируются локальным состоянием VPN.
- sing-box использует runtime-модуль `singbox-process`, libbox и сервис в процессе `:engine_singbox`. URnetwork и sing-box используют общую подготовку `go.Seq`; классы `go/*` удаляются из дублирующих SDK-артефактов.
- FPTN использует Kotlin-обвязку HTTPS/WebSocket и JNI-библиотеку `libfptn_native_lib.so`.
- MasterDNS запускает нативный клиент как subprocess.
- Opera Proxy запускает закреплённый ARM64 subprocess с локальным TCP SOCKS5. Для TUN используется libbox в отдельном процессе `:engine_opera_tun`: DNS через DoH и Opera, TCP через Opera, прочий UDP отклоняется.
- MASQUE запускает отдельный usque subprocess с SOCKS5, H2/H3 и проверкой трафика; профиль хранится зашифрованным.
- Telegram WSS использует TLS WebSocket для известных TCP-адресов Telegram DC и WARP SOCKS для остальных TCP/UDP; смена транспорта после передачи MTProto-заголовка не переигрывает уже начатую сессию.
- gfVPN загружает проверенный встроенный runtime в отдельном процессе с UID приложения (не sandbox), использует авторизованный SOCKS и собственный TUN-адаптер. На API 29+ отдельный HEV с mapdns выдаёт синтетические адреса `198.19.0.0/16`, которые остаются внутри TUN при обходе LAN; на API 24–28 используется прежний HEV. IPv6 и proxy-only не поддерживаются. Проверка готовности включает дескриптор, native-состояние и SOCKS, но не заменяет проверку трафика внешнего UID.

Изоляция WARP нужна для совместимости Go runtimes. Native-библиотеки загружаются лениво; правила загрузки и порядка остановки сохраняются в [AGENTS.md](../AGENTS.md).

## Настройки и безопасность

Настройки движков хранятся в DataStore; для секретов FPTN, WARP и URnetwork реализованы шифрование через Android Keystore и миграция прежних plaintext-значений. Встроенный backup имеет отдельные форматы и поведение — шифрование DataStore не является обещанием шифрования каждого экспорта.

Kill-switch и IPv6 routing принадлежат общей VPN-логике; их поведение зависит от режима, настроек и поддержки транспорта. Fail-closed и отсутствие обхода туннеля требуют проверки при изменениях маршрутизации и lifecycle.

Обновления APK устанавливаются вручную. Код проверки подписи Ed25519 (`ApkUpdateVerifier`) сейчас не подключён к приложению. Подписки sing-box загружаются через отдельный HTTP/TLS pipeline. При сетевой ошибке возможна одна резервная попытка через SOCKS5 на loopback-порту активного движка с тем же TLS-клиентом. При отсутствии SOCKS-порта, переключении движка, ошибке сертификата, HTTP-ошибке или явном insecure retry резервный маршрут не используется. Маршрутизация TUN и kill-switch не изменяются.

## Где проверять изменения

- [CI](../.github/workflows/ci.yml): стиль, unit tests, coverage, обнаружение непустых JUnit-отчётов и debug APK.
- [Android soak](../.github/workflows/soak.yml): x86_64-эмулятор проверяет VLESS, VMess, Trojan, VLESS Reality, автоматический выбор, переключение Sing-box/ByeDPI и восстановление Wi-Fi с HTTP-проверкой из отдельного UID. MASQUE CLI проверяется без регистрации. Это не подтверждает работу всех движков на ARM64.
- [Release](../.github/workflows/release.yml): подготовка native-зависимостей, сборка и проверки APK.

Сборка и происхождение бинарников описаны в [руководстве](build.md). При изменениях native-кода проверяются и исходники, и библиотека, которая фактически упаковывается в APK.
