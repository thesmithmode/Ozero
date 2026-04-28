# Сборка Ozero

Локальная сборка Android APK + опциональная регенерация native-бинарей.

---

## 1. Требования

| Что | Версия | Откуда |
|-----|--------|--------|
| JDK | 17 | https://adoptium.net/ |
| Android SDK | API 35 (compile), API 24 (min) | Android Studio Iguana+ или command-line tools |
| Android NDK | 27.2.12479018 | через `sdkmanager "ndk;27.2.12479018"` |
| Git | любой современный | — |
| Gradle | wrapper включён, ничего не нужно | `./gradlew` сам подтянет |

Опционально (только если пересобираем native):
- Docker 24+ (для byedpi/xray/etc через build-tools/)
- Go 1.22+ + gomobile (только если запускаем без Docker)

## 2. Первый клон

```bash
git clone https://github.com/thesmithmode/Ozero.git
cd Ozero
./gradlew help          # проверка wrapper
./gradlew assembleDebug # debug APK в app/build/outputs/apk/debug/
```

Native-бинари (libxray.aar, libbyedpi.so, …) скачиваются автоматически по `binaries.lock.yaml` через `ozero.binaries` plugin при первом билде. Sha256 верифицируется — невалидный артефакт сразу валит билд.

## 3. Структура билда

- `:app` — base APK + все engine модули как `implementation`
- Tor включается в релизные артефакты статически, без on-demand модулей PlayCore.
- `:engine-byedpi/xray/amnezia/hysteria2/naive/tor` — обёртки native + `Engine` реализация
- `:core-*`, `:common-*`, `:security`, `:buildSrc` — shared

См. `architecture.md` §2 для полной карты.

## 4. Полезные таски

```bash
./gradlew assembleDebug              # debug APK
./gradlew assembleRelease            # release APK (требует keystore — docs/keystore-setup.md)
./gradlew test                       # unit-тесты всех модулей
./gradlew :app:lint                  # Android lint
./gradlew ktlintCheck                # формат Kotlin
./gradlew detekt                     # статанализ
./gradlew --continue check           # всё разом, без раннего exit (CI flag)
./gradlew downloadBinaries           # форс-перекачать native артефакты по lock
```

CI запускает `check` с `--continue` чтобы видеть все проблемы за один прогон, не цепочку красных коммитов на каждую отдельную lint-violation.

## 5. Native-бинари вручную

Обычно НЕ нужно — артефакты приходят из GitHub Releases. Регенерация требуется при:
- Бамп upstream версии (`XRAY_VERSION=v25.10.1` и т.д.)
- Изменение Dockerfile / build script

Полный поток — `binaries-pipeline.md`. Минимум:

```bash
# byedpi (требует Docker)
docker build -t ozero-byedpi -f build-tools/Dockerfile.byedpi build-tools/
docker run --rm -v "$PWD:/src" -w /src \
    -e REPO_ROOT=/src -e OUT_DIR=/src/out/byedpi \
    ozero-byedpi bash build-tools/build_byedpi.sh

# xray (Go + gomobile, через Docker)
docker build -t ozero-xray -f build-tools/Dockerfile build-tools/
docker run --rm -v "$PWD:/src" -w /src \
    -e REPO_ROOT=/src -e OUTPUT_DIR=/src/out/xray \
    ozero-xray bash build-tools/build_xray.sh
```

Аналогично для amneziawg / hysteria2 / naive / tor — см. `build-tools/build_<engine>.sh`.

## 6. Подпись release APK

Release-сборка требует release keystore. Процедура: `docs/keystore-setup.md` (24-летний key, GPG-encrypted backup).

Self-update APK дополнительно подписывается **Ed25519 поверх APK signing** — `ApkUpdateVerifier` отвергает APK без signature файла. Подробнее: `docs/key-rotation.md`.

## 7. Запуск тестов

```bash
./gradlew test                       # JVM unit (быстро)
./gradlew :app:connectedDebugAndroidTest  # instrumented (требует устройство/эмулятор)
```

Юнит-тесты не должны зависеть от Android runtime. Где Android-API неизбежен (Hilt graph) — Robolectric или androidTest.

## 8. CI

Workflow в `.github/workflows/ci.yml`:
- Validate Gradle Wrapper
- Build and Test (`./gradlew --continue check assembleDebug`)
- Security Scan (CodeQL)
- Build per ABI (arm64-v8a / armeabi-v7a / x86_64)

Native-бинари для CI приходят с GitHub Releases по `binaries.lock.yaml` — CI НЕ пересобирает их. Это делает отдельный workflow `binaries.yml` (manual trigger или push в feat/** с изменением `build_*.sh`).

## 9. Распространение

| Канал | Статус |
|-------|--------|
| GitHub Releases | живой (debug-APK на каждый dev push, signed на tag `v*.*.*`) |
