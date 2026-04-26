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

**Только метаданные подписок.** При обновлении списка серверов
(`https://sub.ozero.app/<channel>.json.sig`) HTTP-запрос содержит:
- IP-адрес клиента (видит CDN/CloudFlare для маршрутизации)
- User-Agent: `Ozero/<версия> Android/<sdk>` — никакого device-ID
- TLS Server Name (`sub.ozero.app`) — стандартный TLS handshake

Логи запросов к `sub.ozero.app` — стандартные nginx access-логи (IP, время,
путь). Ротация 7 суток. Не связываются с пользователем (нет аккаунтов).

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
- Certificate pinning для `sub.ozero.app` (см. `network_security_config.xml`)
- TLS 1.3 only для backend; engine'ы используют свои собственные обфусцированные
  протоколы (Reality, Hysteria2, etc.)

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

Вопросы по приватности → issue с тегом `privacy` в репозитории, либо
канал в Telegram (ссылка из AboutScreen).
