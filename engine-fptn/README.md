# engine-fptn

FPTN реализует `EnginePlugin` и `TunFdAcceptor`. Production DI — [FptnModule](../app/src/main/java/ru/ozero/app/di/FptnModule.kt).

## Запуск

1. `FptnToken` разбирает `fptn:`/`fptnb:` и оба списка: `servers` и `censored_zone_servers`. Дополнительные серверы добавляются после обычных; полностью совпадающие записи объединяются.
2. `FptnEngine.start()` выбирает кандидатов, выполняет HTTPS login и резолвит адрес выбранного сервера в IPv4.
3. `attachTun()` создаёт native WebSocket, задаёт callback защиты сокета и подключает обмен пакетами с TUN.
4. `awaitReady()` проверяет готовность WebSocket до объявления соединения готовым.

Ручной режим работает только с явно выбранным сервером: до двух попыток в общем auth-бюджете 20 секунд. Auto проверяет все серверы токена, до 16 auth-запросов одновременно, с таймаутом запроса до 10 секунд и общим бюджетом 25 секунд; возвращается первый успешный результат. После неудачного прохода допускается один повтор в оставшемся бюджете. Отказ в доступе прекращает повторы.

Readiness имеет отдельный бюджет 30 секунд. Таймаут ожидания runtime restart для FPTN — 60 секунд, чтобы покрывать авторизацию и readiness. Эти значения определены в [FptnEngine](src/main/java/ru/ozero/enginefptn/FptnEngine.kt) и [RuntimeConfigRestartCoordinator](../app/src/main/java/ru/ozero/app/vpn/RuntimeConfigRestartCoordinator.kt).

## Настройки

Конфигурация хранится в DataStore. Токен шифруется через `PreferenceAtRest`; старое plaintext-значение мигрируется при чтении или записи.

Runtime fingerprint учитывает токен, bypass method, SNI domain и режим выбора. Имя выбранного сервера учитывается в ручном режиме. Изменения во время запуска могут быть повторно применены после readiness через runtime-config observer.

## JNI и сборка

Kotlin-обвязки HTTPS/WebSocket используют `libfptn_native_lib.so`. [build_fptn.sh](../build-tools/build_fptn.sh) собирает локальные JNI wrappers с закреплёнными upstream-исходниками. Release APK и workflow `FPTN transport` собирают библиотеку через общий action `build-fptn` из закреплённых исходников; Gradle download для FPTN в этих сборках отключён. Обычная локальная Gradle-сборка использует готовый asset по [binaries.lock.yaml](../build-tools/binaries.lock.yaml).

Gradle-модуль не запускает CMake автоматически. Изменение C++ wrapper требует пересборки библиотеки; проверка checksum старого asset не подтверждает наличие нового native-кода. Release workflow проверяет совпадение упакованной библиотеки с результатом текущей сборки. Подробности — [native source и готовая библиотека](../docs/build.md#native-source-и-готовая-библиотека).

При проверке на Android нужны auth/readiness, трафик через TUN, смена сети, reconnect и повторный stop/start. Kotlin tests с fake native client не заменяют такую проверку.

## Upstream transport

Android wrappers и protocol library закреплены отдельно в `build_fptn.sh`. Protocol revision включает фоновую NTP-синхронизацию и TCP probe с таймаутом 1500 мс: первая TLS-операция не должна ждать недоступный NTP. Manifest сборки содержит обе ревизии. Workflow `FPTN transport` проверяет NTP-регрессию на настоящем protocol-коде с подставным медленным NTP, затем собирает Android native library и APK с этой библиотекой.
