# Политика приватности Ozero

> Версия: 2026-04-26 · Действует с момента установки приложения

## TL;DR

Ozero не отправляет ваши данные на сервер. Никаких аналитик, crash-reporting'а,
рекламных идентификаторов, фингерпринтинга. Всё что приложение делает с данными —
делается локально, на вашем устройстве.

## Что собирает Ozero

**Ничего не отправляется наружу.** Вообще. Все данные ниже хранятся **только**
в локальном файловом хранилище приложения (`/data/data/ru.ozero.app/`) и
никогда не передаются на наши серверы — потому что у нас их нет для этой цели.

| Данные | Где хранится | Зачем |
|---|---|---|
| Список серверов из подписки | Room SQLite DB | подключение к VPN |
| Настройки (split-tunnel, IPv6, движок) | DataStore | поведение приложения |
| Crash-логи (RT.11.1) | `filesDir/crashes/<ts>.txt` | диагностика, ручной экспорт по выбору пользователя |
| Diagnostics результаты | оперативная память | UI экрана Diagnostics |

Статистика подключений, телеметрия, идентификаторы устройства — **не собираем**.

## Что собирает наш backend

**Никакого backend нет.** Архитектура server-less (PLAN v4):
- Источник серверов — публичные GitHub-репо живых прокси (`mahdibland/V2RayAggregator`,
  `Epodonios/v2ray-configs`, `MhdiTaheri/V2rayCollector_Py`, `freefq/free`, `Pawdroid/Free-servers`).
  HTTP-запросы идут на `raw.githubusercontent.com` через стандартный TLS GitHub.
- Self-update — `api.github.com/repos/thesmithmode/Ozero/releases/latest`.
- URnetwork — P2P через `urnetwork/sdk`, peers волонтёров.

Что **видит GitHub** при запросах от вашего устройства:
- IP клиента (стандартное логирование GitHub)
- User-Agent: `Ozero/<версия> Android/<sdk>`
- TLS Server Name (`raw.githubusercontent.com` / `api.github.com`)

Это политика GitHub, не наша. Никаких собственных серверов и никаких
access-логов с нашей стороны (поскольку соответствующих серверов нет).

## Crash reporting и аналитика — RT.11.1

**Принципиально отказались** от внешних сервисов. Наш список «никогда»:
- Sentry, Bugsnag, Firebase Crashlytics — не подключены
- Google Analytics, Firebase Analytics, Amplitude, Mixpanel — не подключены
- Кастомный telemetry beacon на наш сервер — нет
- AppsFlyer, Adjust, любой attribution SDK — не подключены

Crash-логи пишутся в `filesDir/crashes/<timestamp>.txt` локально через
`Thread.setDefaultUncaughtExceptionHandler`. Экспорт — **только** по явной
кнопке «Экспорт crash log» в Diagnostics. Файл уходит через системный
`ACTION_SEND` — куда вы сами его отправите.

## Permissions и зачем они нужны

| Permission | Зачем |
|---|---|
| `INTERNET` | сетевой стек VPN (без него подключаться некуда) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` | VpnService требует foreground-сервис на Android 14+ |
| `BIND_VPN_SERVICE` | реализация VPN-туннеля Android |
| `ACCESS_NETWORK_STATE` | определение типа сети (Wi-Fi/cellular/CGNAT) для probe-логики |
| `RECEIVE_BOOT_COMPLETED` | опциональный auto-start после загрузки (выкл. по умолчанию) |
| `POST_NOTIFICATIONS` (Android 13+) | persistent VPN-нотификация (требование системы) |
| `REQUEST_INSTALL_PACKAGES` | self-update через PackageInstaller (RT.6) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (опц.) | один раз предложить whitelist от Doze (RT.7.3) |

Никаких `ACCESS_FINE_LOCATION`, `READ_PHONE_STATE`, `READ_CONTACTS` и подобного
— приложение их не требует, а значит не запрашивает.

## Storage permission и backup

`android:allowBackup="false"` + `dataExtractionRules`: данные приложения
**не попадают** в Google Drive backup, Auto Backup, и в `adb backup`.
VPN-конфиги и сессии Tor никогда не покидают устройство.

## Сетевая безопасность

- `usesCleartextTraffic="false"` — http:// запросы блокируются на уровне OS
- TLS 1.3 only для запросов к GitHub (system trust). Pinning GitHub'а
  не используется — их CA rotation вне нашего контроля → риск brick'а APK.
- Engines используют свои собственные обфусцированные протоколы
  (Reality, Hysteria2, etc.).

## Третьи стороны

OSS-зависимости перечислены в `LICENSE` и AboutScreen. Ни одна из них не
является аналитической SDK. Полный список — в `app/build.gradle.kts` и
`gradle/libs.versions.toml`.

## Дети

Приложение не предназначено для детей младше 13 лет (политика COPPA), но
никаких возрастных ограничений мы не применяем — потому что не собираем
персональные данные ни с кого.

## Изменения политики

Любое изменение этого документа коммитится в репозиторий
[github.com/thesmithmode/Ozero](https://github.com/thesmithmode/Ozero) с явным
сообщением `DOCS: privacy — <что изменилось>`. Подписаться на изменения можно
через GitHub watch.

## Контакты

Вопросы по приватности → issue с тегом `privacy` в репозитории.
