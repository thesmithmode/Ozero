# Сборка и выпуск APK

## Ветки и готовые артефакты

Разработка ведётся в `dev`, а default branch GitHub — `main`. Рабочую ветку создавайте от `dev` и направляйте изменения туда. Push в `main` или тега `v*.*.*` запускает [release.yml](../.github/workflows/release.yml), поэтому `main` не используется для проверки документации или пробной сборки.

[CI](../.github/workflows/ci.yml) запускается для рабочих веток и PR в `dev`/`main`. При открытом PR push-run может пропускать проверки, чтобы не дублировать PR-run; оценивайте jobs полного прогона на нужном SHA. Debug APK публикуется как artifact `debug-apk`. Готовые release APK находятся в [Releases](https://github.com/thesmithmode/Ozero/releases).

## Окружение

- JDK 17.
- Android SDK: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 24`.
- Gradle Wrapper из репозитория. Текущие Gradle/AGP/Kotlin закреплены в [wrapper properties](../gradle/wrapper/gradle-wrapper.properties) и [version catalog](../gradle/libs.versions.toml).
- Для подготовки артефактов: Git, GitHub CLI, Python 3, `unzip`, `zip`, `jar`; доступ к GitHub Releases и Maven/Google repositories.
- Для HEV в release workflow используется NDK r27c. Собственная сборка FPTN использует отдельное окружение из [Dockerfile.fptn](../build-tools/Dockerfile.fptn).

Production APK рассчитан на `arm64-v8a`. Свойство `ozero.soak.x86_64` служит Android soak; оно не означает поддержку всех движков на x86_64.

```bash
git clone --branch dev https://github.com/thesmithmode/Ozero.git
cd Ozero
```

Задайте путь Android SDK через `ANDROID_HOME` или локальный `local.properties`. Не коммитьте локальные пути, keystore, пароли или private keys.

## Подготовка зависимостей

Не все SDK и `.so` скачиваются при обычном запуске Gradle. Для сборки из чистого clone воспроизведите этапы подготовки из `assembleDebug` в [ci.yml](../.github/workflows/ci.yml), а для полноценного release — из `build-apk` в [release.yml](../.github/workflows/release.yml).

| Компонент | Фактическая подготовка |
|-----------|-----------------------|
| ByeDPI и MasterDNS | Gradle-задачи `:engine-byedpi:downloadBinaries` и `:engine-masterdns:downloadBinaries`, lock URL/SHA256, зависимость `preBuild` |
| WARP | `libam.so`, `libam-go.so`, `libam-quick.so` уже отслеживаются в `engine-warp/src/main/jniLibs/arm64-v8a`; release проверяет их наличие и идентичность backend |
| URnetwork | Workflow выбирает `urnetwork-sdk-*` release, скачивает SDK в `engine-urnetwork/libs`, удаляет дублирующие `go/*.class`; приложение дополнительно использует `StripGoClassesFromAarTask` |
| sing-box | libbox 1.13.12: download/verify scripts, извлечение `classes.jar` и JNI, выделение и patch общих `go` stubs, удаление `go/*.class` из libbox.jar |
| FPTN | Release скачивает `.so` и manifest по записи `fptn` в [binaries.lock.yaml](../build-tools/binaries.lock.yaml), проверяет source metadata, SHA256 и размер |
| HEV | Release собирает `libhev-ozero-socks5-tunnel.so` из закреплённого upstream commit, с `APP_CFLAGS=-DPKGNAME=hev` и 16 KB alignment |

Для download scripts libbox вне GitHub Actions задайте `GITHUB_REPOSITORY=thesmithmode/Ozero`; аргумент назначения должен сохранять имя asset `libbox.aar`. Полный порядок извлечения и patch Go stubs берите из workflow. Простое добавление исходного AAR в runtime dependencies дублирует классы Go.

`assembleDebug` проверяет компиляцию и упаковку, но не воспроизводит все этапы native-подготовки release и не подтверждает работу каждого транспорта на устройстве. Для installable APK со всеми движками нужны все соответствующие библиотеки из таблицы.

После подготовки зависимостей debug-сборка выполняется командой:

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/`. Эта команда не выполняет подготовку всех внешних артефактов из таблицы.

## Release-конфигурация

Обязательные переменные окружения:

| Переменная | Значение |
|------------|----------|
| `OZERO_KEYSTORE_PATH` | Путь к keystore подписи APK |
| `OZERO_KEYSTORE_PASSWORD` | Пароль keystore |
| `OZERO_KEY_ALIAS` | Alias ключа подписи |
| `OZERO_KEY_PASSWORD` | Пароль ключа |
| `UPDATE_PUBLIC_KEY_HEX` | 64 hex-символа публичного Ed25519-ключа проверки APK, без нулевого placeholder |

Для встроенного обновления также задаются `UPDATE_GITHUB_OWNER` и `UPDATE_GITHUB_REPO`; их пустые defaults не указывают репозиторий обновлений. В release workflow эти значения берутся из текущего GitHub repository.

`VERSION_CODE` задаётся в [gradle.properties](../gradle.properties). `versionName` берётся из `OZERO_VERSION_NAME` или последнего Git-тега `v*.*.*`. Workflow вычисляет версию отдельно и передаёт её сборке.

После native-подготовки и настройки переменных:

```bash
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/`. Для публикации используйте существующий release workflow: он также проверяет подпись APK, состав DEX и native-библиотек. Производится один APK; dynamic features не подключены.

### Подписи опубликованного обновления

Android-подпись APK, GPG-подпись файла и Ed25519-подпись встроенного обновления — разные форматы. Release workflow создаёт APK и `.apk.sha256`, а при наличии `RELEASE_GPG_PRIVATE_KEY` — также `.apk.asc`.

[GithubReleaseFetcher](../app/src/main/java/ru/ozero/app/selfupdate/GithubReleaseFetcher.kt) ожидает один APK и один asset `.apk.sig`; [ApkUpdateVerifier](../app/src/main/java/ru/ozero/app/selfupdate/ApkUpdateVerifier.kt) проверяет его как 64-байтовую Ed25519-подпись. Текущий release workflow не создаёт `.apk.sig`. Одного `UPDATE_PUBLIC_KEY_HEX`, checksum или GPG `.asc` недостаточно, чтобы такой release был доступен встроенному updater. Подготовка `.apk.sig` требует отдельного шага публикации; здесь описано существующее поведение, без изменения release pipeline.

## Native source и готовая библиотека

[binaries.yml](../.github/workflows/binaries.yml) собирает и публикует отдельные native-артефакты. Обновление Kotlin/C++ исходников, публикация `.so` и обновление lock — разные операции. Обычная APK-сборка FPTN использует готовую библиотеку: изменение C++ в `engine-fptn/src/main/cpp` не запускает CMake из Gradle.

Текущая идентичность FPTN release tag вычисляется из Dockerfile, build script и `regen_lock.py`; wrapper C++ в хеш не включён. Поэтому SHA256 из lock доказывает целостность выбранного asset, но не его соответствие текущему wrapper source. При native-изменениях явно проверяйте Ozero revision сборки, JNI ABI и новый lock; документирование этого ограничения не меняет действующий pipeline.

## Проверки

Правила проекта в [AGENTS.md](../AGENTS.md) требуют выполнять тесты, сборки и lint через CI, если пользователь отдельно не разрешил локальный запуск. Команды выше описывают сборку для разработчика и не отменяют это правило для автоматических агентов.

Для изменения транспорта к CI добавляется проверка на Android ARM64: подключение, готовность, передача трафика, reconnect, stop/start и переключение движков. Успешный sing-box soak не является проверкой native FPTN или WARP.
