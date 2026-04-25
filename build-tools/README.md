# build-tools

Скрипты кросс-компиляции нативных ядер Ozero.

## Скрипты

| Файл | Назначение |
|------|-----------|
| `build_xray.sh` | Xray-core -> `engine-xray/libs/libxray.aar` через gomobile bind |
| `build_amnezia.sh` | (будет) AmneziaWG -> `engine-amnezia/libs/libamnezia-wg.aar` |
| `build_tor.sh` | (будет) Tor + obfs4proxy + snowflake -> `engine-tor/src/main/jniLibs/<arch>/*` |
| `build_naive.sh` | (будет) NaiveProxy -> `engine-naive/libs/libnaive.aar` |
| `Dockerfile` | Reproducible env для всех скриптов (F-Droid) |

## Требования

- Go 1.22+
- Android NDK r27+
- JDK 17
- `gomobile` (скрипт поставит сам)

Env:
- `ANDROID_NDK_ROOT=/path/to/android-ndk`
- Optional: `XRAY_VERSION=v25.10.1`

## Использование (local)

```bash
export ANDROID_NDK_ROOT=/opt/android-ndk
./build-tools/build_xray.sh
ls -lh engine-xray/libs/
```

## Использование (docker, рекомендуется для релиза)

```bash
docker build -t ozero-xray-builder -f build-tools/Dockerfile .
docker run --rm -v "$PWD:/workspace" ozero-xray-builder
```

## Reproducibility

Скрипт пишет рядом с AAR:
- `libxray.aar.sha256` — чексумма артефакта
- `libxray.manifest.txt` — метаданные сборки (XRAY_VERSION, upstream commit SHA, Go/NDK/API, timestamp)

При релизе сверяются:
- локальная сборка разработчика
- Docker-сборка (эталон)
- F-Droid CI сборка (итоговая эталонная)

Хэши должны совпадать. Если не совпадают — bisect зависимостей: чаще всего go.sum toolchain drift, NDK patch version.

AAR артефакты **не коммитятся** в Git (см. `engine-xray/libs/.gitignore`). Релизный pipeline подкладывает AAR из защищённого хранилища или пересобирает детерминированно.

## Trust chain

- Xray-core подпись: commit tag `v25.10.1` (sync с upstream XTLS/Xray-core GPG key, see `docs/trust-chain.md` будущий)
- NDK: SHA256 официального Google build pinned в Dockerfile
- Go: `golang:1.22.12-bookworm` digest pinned (не `:latest`)

## Troubleshooting

- `gomobile: command not found` -> `go install golang.org/x/mobile/cmd/gomobile@latest` + ensure `$GOPATH/bin` в PATH
- `ANDROID_NDK_ROOT not set` -> экспортируй путь к NDK (не NDK toolchain, а корень NDK)
- `linker error arm64`: проверь NDK версию >= r27
- Docker build медленный: первая сборка ~800MB download (NDK), последующие — cache

---

## Binary artifact pipeline (RT.1.7)

Native бинари собираются workflow `.github/workflows/binaries.yml` (Docker pinned NDK r27 + cmake), публикуются в GitHub Releases с тегом `binaries-<sha8>`. Convention plugin `ozero.binaries` качает их в Gradle через lock-файл `build-tools/binaries.lock.yaml` с SHA256 verify.

### Когда пересобирать бинари

После изменений в `build-tools/build_*.sh` или Dockerfile, или при bump upstream версии (byedpi tag).

```bash
# 1) push изменения в build-tools/* → workflow триггерится автоматически
git push

# 2) После зелёного workflow → скачай новый manifest и обнови lock:
gh release download <new-tag> -p 'libbyedpi-*.so' -p 'manifest.txt' -D /tmp/bin/ -R thesmithmode/Ozero
python3 build-tools/regen_lock.py \
  --tag <new-tag> --repo thesmithmode/Ozero \
  --manifest /tmp/bin/manifest.txt --out build-tools/binaries.lock.yaml
git add build-tools/binaries.lock.yaml
git commit -m "FEAT: RT.1.7 — обновлён binaries.lock.yaml для <new-tag>"
git push
```

(Auto-PR из workflow требует repo permission «Allow GitHub Actions to create PRs» — пока вручную.)

### Применение в engine-* модулях

```kotlin
plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    artifact("libbyedpi-arm64-v8a.so")
    artifact("libbyedpi-armeabi-v7a.so")
}
```

Hook добавляет `downloadBinaries` task в `preBuild`. Бинари кладутся в `src/main/jniLibs/<abi>/`.

### Файлы

| Файл | Назначение |
|------|-----------|
| `build-tools/binaries.lock.yaml` | source of truth: tag + URL + SHA256 каждого артефакта |
| `build-tools/Dockerfile.byedpi` | Reproducible env для byedpi (debian + NDK + cmake, без Go) |
| `build-tools/build_byedpi.sh` | CMake-сборка libbyedpi.so для 4 ABI |
| `build-tools/regen_lock.py` | manifest.txt → binaries.lock.yaml |
| `.github/workflows/binaries.yml` | Triggered on push to build-tools/* — собирает в Docker, публикует Release |
| `buildSrc/src/main/kotlin/ozero.binaries.gradle.kts` | Convention plugin для engine-* модулей |

### Negative test

Удаление asset из Release → CI fail с сообщением `HTTP 404 for ... Run: gh workflow run binaries.yml --ref dev`. Восстановление = повторный dispatch workflow.
