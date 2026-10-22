# engine-fptn

FPTN реализует `EnginePlugin` и `TunFdAcceptor`. Production DI — [FptnModule](../app/src/main/java/ru/ozero/app/di/FptnModule.kt).

## Запуск

1. `FptnToken` разбирает токен и список серверов.
2. `FptnEngine.start()` выбирает кандидатов, выполняет HTTPS login и резолвит адрес выбранного сервера в IPv4.
3. `attachTun()` создаёт native WebSocket, задаёт callback защиты сокета и подключает обмен пакетами с TUN.
4. `awaitReady()` проверяет готовность WebSocket до объявления соединения готовым.

Ручной режим работает только с явно выбранным сервером: до двух попыток в общем auth-бюджете 20 секунд. Auto использует первые четыре сервера токена, до четырёх параллельных auth-запросов и общий бюджет 5 секунд; возвращается первый успешный результат. Серверы после четвёртого сейчас автоматически не проверяются.

Readiness имеет отдельный бюджет 30 секунд. Таймаут ожидания runtime restart для FPTN — 55 секунд, чтобы покрывать ручную авторизацию и readiness. Эти значения определены в [FptnEngine](src/main/java/ru/ozero/enginefptn/FptnEngine.kt) и [RuntimeConfigRestartCoordinator](../app/src/main/java/ru/ozero/app/vpn/RuntimeConfigRestartCoordinator.kt).

## Настройки

Конфигурация хранится в DataStore. Токен шифруется через `PreferenceAtRest`; старое plaintext-значение мигрируется при чтении или записи.

Runtime fingerprint учитывает токен, bypass method, SNI domain и режим выбора. Имя выбранного сервера учитывается в ручном режиме. Изменения во время запуска могут быть повторно применены после readiness через runtime-config observer.

## JNI и сборка

Kotlin-обвязки HTTPS/WebSocket используют `libfptn_native_lib.so`. [build_fptn.sh](../build-tools/build_fptn.sh) собирает локальные JNI wrappers с закреплёнными upstream-исходниками. Release APK использует готовый asset по [binaries.lock.yaml](../build-tools/binaries.lock.yaml).

Gradle-модуль не запускает CMake автоматически. Изменение C++ wrapper требует отдельной пересборки библиотеки и обновления lock; проверка checksum старого asset не подтверждает наличие нового native-кода. Подробности — [native source и готовая библиотека](../docs/build.md#native-source-и-готовая-библиотека).

При проверке на Android нужны auth/readiness, трафик через TUN, смена сети, reconnect и повторный stop/start. Kotlin tests с fake native client не заменяют такую проверку.
