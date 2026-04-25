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

## Binary artifact pipeline (RT.1.7 / RT.1.7.1)

Native бинари собираются workflow `.github/workflows/binaries.yml`, публикуются в GitHub Releases с **per-engine** тегом (`byedpi-<sha8>`, `xray-<sha8>`). Convention plugin `ozero.binaries` качает их в Gradle через единый lock-файл `build-tools/binaries.lock.yaml` с SHA256 verify.

### Tag scheme

`<engine>-<sha8>`, где `sha8` = первые 8 hex от sha256 списка build-входов engine'а (Dockerfile + build script + regen_lock). Per-engine изоляция: bump xray не инвалидирует byedpi tag.

### Когда пересобирать бинари

После изменений в `build-tools/build_<engine>.sh`, `Dockerfile*`, или при bump upstream версии.

```bash
# 1) Запусти workflow вручную для нужного engine:
gh workflow run binaries.yml --ref <branch> -f artifact=xray   # или byedpi

# 2) Дождись зелёного → workflow откроет PR с обновлённым lock.
#    Если permission disabled — обнови lock локально:
gh release download <new-tag> -p 'libxray.aar' -p 'manifest.txt' -D /tmp/bin/ -R thesmithmode/Ozero
python3 build-tools/regen_lock.py \
  --engine xray --tag <new-tag> --repo thesmithmode/Ozero \
  --manifest /tmp/bin/manifest.txt --out build-tools/binaries.lock.yaml
git add build-tools/binaries.lock.yaml
git commit -m "FEAT: RT.1.7 — binaries.lock.yaml для <new-tag>"
git push
```

`regen_lock.py` сохраняет artifacts других engine-ов нетронутыми — обновляет только указанный `--engine`.

### Применение в engine-* модулях

```kotlin
plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    // byedpi — 4 .so в jniLibs/<abi>/
    artifact("libbyedpi-arm64-v8a.so")
    artifact("libbyedpi-armeabi-v7a.so")
}
```

Hook добавляет `downloadBinaries` task в `preBuild`. Destination определяется lock-файлом (jniLibs или libs).

### Статус движков

| Engine | Status | Notes |
|--------|--------|-------|
| byedpi | ✅ Production | 4 ABI .so в jniLibs, lock entries actual |
| xray | 🚧 Pending RT.1.3 | Infrastructure ready (Dockerfile, build_xray.sh skeleton, matrix workflow), `gomobile bind` blocked: `golang.org/x/mobile/bind` package разрушен в актуальных pin'ах. Engine-xray plugin apply отложен до RT.1.3 |
| naive | Pending RT.1.7.2 | После RT.1.3 |
| amnezia | Pending RT.1.7.3 | После RT.1.2 |
| tor + PT | Pending RT.1.7.4 | После RT.1.6 |

### Файлы

| Файл | Назначение |
|------|-----------|
| `build-tools/binaries.lock.yaml` | Source of truth: per-engine artifacts + URL + SHA256 |
| `build-tools/Dockerfile.byedpi` | Reproducible env для byedpi (NDK + cmake, без Go) |
| `build-tools/Dockerfile` | Reproducible env для xray (NDK + Go 1.22 + gomobile pinned) |
| `build-tools/build_byedpi.sh` | CMake → libbyedpi.so для 4 ABI |
| `build-tools/build_xray.sh` | gomobile bind → libxray.aar (multi-ABI) |
| `build-tools/regen_lock.py` | manifest.txt → binaries.lock.yaml (multi-engine merge) |
| `build-tools/tests/test_regen_lock.py` | Unit tests для regen_lock |
| `.github/workflows/binaries.yml` | Build matrix: byedpi/xray, upload Release, lock-PR |
| `buildSrc/src/main/kotlin/ozero.binaries.gradle.kts` | Convention plugin для engine-* модулей |

### Negative test

Удаление asset из Release → CI fail с сообщением `HTTP 404 for ... Run: gh workflow run binaries.yml`. Восстановление = повторный dispatch workflow.
