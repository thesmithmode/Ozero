# Binary artifact pipeline (RT.1.7)

Native-бинари (libxray.aar, libbyedpi.so, и т.д.) собираются в CI и публикуются в GitHub Releases с per-engine тегом. Локальный билд app скачивает их по sha256 из `binaries.lock.yaml`. F-Droid build-farm сможет повторить с reproducible Docker.

---

## 1. Зачем не submodule / не локальная сборка

| Проблема submodule | Решение пайплайна |
|--------------------|-------------------|
| Каждый dev должен иметь Go + NDK + 30+ мин на полный билд | Бинари уже собраны, dev качает готовое |
| `git clone --recursive` тащит десятки MB исходников движков | submodule больше не нужен после миграции (RT.1.7.1) |
| Подмена бинаря на CI = риск supply chain | sha256 в lock-файле, любая подмена ломает build |
| Несовместимые версии у разных feature-веток | Lock версионируется в git, всегда воспроизводимо |

## 2. Поток

```
PR / push → workflow_dispatch → binaries.yml
                                  ↓
                  build_<engine>.sh (Docker isolation)
                                  ↓
                   out/<engine>/  (libfoo.so + manifest.txt + sha256)
                                  ↓
              softprops/action-gh-release → GitHub Release tag <engine>-<sha8>
                                  ↓
               (manual) regen_lock.py --engine X --tag X-<sha8>
                                  ↓
                  build-tools/binaries.lock.yaml (commit на dev)
                                  ↓
       Gradle :app build → ozero.binaries plugin → DownloadBinaryTask
                                  ↓
               sha256 verify → place в jniLibs/<abi>/ или libs/
```

## 3. Per-engine изоляция

Тег формируется как `<engine>-<sha8>` где sha8 — хэш build-inputs **только этого engine** (Dockerfile + build script + regen_lock.py). Бамп xray не инвалидирует тег byedpi.

```
case "$ARTIFACT" in
  byedpi)    FILES="build-tools/Dockerfile.byedpi build-tools/build_byedpi.sh ..." ;;
  xray)      FILES="build-tools/Dockerfile build-tools/build_xray.sh ..." ;;
  ...
esac
HASH=$(cat $FILES | sha256sum | cut -c1-8)
TAG="${ARTIFACT}-${HASH}"
```

## 4. Lock-файл

`build-tools/binaries.lock.yaml` — единственный источник правды для Gradle. Полная схема — `buildSrc/src/main/kotlin/binaries/LockFile.kt`. Пример entry:

```yaml
artifacts:
  - name: libxray.aar
    engine: xray
    destination: libs
    download_url: https://github.com/thesmithmode/Ozero/releases/download/xray-deadbeef/libxray.aar
    sha256: 39e1e4b1cd15c0436c57c934f0de17f3a0aa7ba88cfa4b2e9a5084c194e2ff85
    size_bytes: 12345678
    source_repo: https://github.com/XTLS/Xray-core
    source_commit: v25.10.1
```

Поля: `name` / `engine` / `abi?` (для .so) / `destination` (`jniLibs` или `libs`) / `download_url` / `sha256` / `size_bytes` / `source_repo` / `source_commit`. Каждый engine изолирован — `merge_engine_artifacts` в `regen_lock.py` заменяет только entries своего engine.

## 5. Регенерация lock-файла

После CI выкладывает release:

```bash
gh release download <engine>-<sha8> -p '*'
python3 build-tools/regen_lock.py \
    --engine <engine> \
    --tag <engine>-<sha8> \
    --repo thesmithmode/Ozero \
    --manifest manifest.txt \
    --out build-tools/binaries.lock.yaml
git add build-tools/binaries.lock.yaml
git commit -m "FEAT: RT.1.7.X — <engine> lock"
```

Workflow `binaries.yml` НЕ автоматизирует этот шаг — ранее был peter-evans/create-pull-request, но он плодил orphan `bot/binaries-lock-*` ветки при выключенной permission «Allow GH Actions to create PRs». Сейчас manual + squash-merge dev flow.

## 6. ozero.binaries Gradle plugin

```kotlin
// engine-xray/build.gradle.kts
plugins {
    id("ozero.android.library")
    id("ozero.binaries")
}

ozeroBinaries {
    artifact("libxray.aar")
}
```

Plugin парсит lock, генерирует `DownloadBinaryTask` per artifact, кладёт в `build/binaries/<module>/libs|jniLibs/`. Sha256 проверяется при каждом билде — расхождение = build fail.

## 7. Tor — два engine, один tag

`tor-<sha8>` тег несёт **оба** набора .so:
- `libtor-<abi>.so` (engine=`tor`, source=`guardianproject/tor-android`)
- `libiptproxy-<abi>.so` (engine=`iptproxy`, source=`tladesignz/IPtProxy`)

`build_tor.sh` пишет два manifest'а (`manifest-tor.txt` + `manifest-iptproxy.txt`), `regen_lock.py` запускается дважды — каждый передаёт свой manifest, чтобы получить правильный `source_repo` per engine. В lock они хранятся как два разных engine, но скачиваются из одного release.

## 8. Reproducibility

- Все native билды запускаются в Docker (`build-tools/Dockerfile`, `Dockerfile.byedpi`)
- Версии upstream pinned (envvars `XRAY_VERSION`, `TOR_VERSION`, `IPTPROXY_VERSION`)
- Sha256 коммитится в git — F-Droid bot повторяет тот же build, сверяет sha
- `gomobile bind` детерминистичен с фиксированным Go + NDK

## 9. UI hygiene (Releases)

Каждый успешный build публикует **prerelease** в GitHub Releases:
- `prerelease: true` — релиз не маячит как «Latest» на главной странице репо.
- После upload workflow удаляет старые prerelease того же движка, оставляя последние **3 версии** (откат возможен в течение 3 циклов).
- Cleanup использует `gh release delete --cleanup-tag` — git tag удаляется вместе с релизом.

Эти релизы — build-кэш, **не** пользовательские релизы продукта. Релизы Ozero APK выпускаются отдельно через RT.6 self-update flow.

## 10. Upstream version watcher

Workflow `.github/workflows/upstream-check.yml` запускается раз в неделю (или вручную через workflow_dispatch). Скрипт `build-tools/check_upstream.py`:

1. Парсит `_VERSION` переменные из `build_*.sh`.
2. Дёргает `releases/latest` каждого upstream-репо (с fallback на `tags` API для репо без GitHub Releases).
3. Если pinned ≠ latest — пишет markdown отчёт `build-tools/upstream-bumps.md` (gitignored).
4. `peter-evans/create-pull-request` открывает PR с этим отчётом в описании.

Человек смотрит changelog upstream, проверяет breaking changes, bump'ает `_VERSION` в build-скрипте, запускает `binaries.yml --ref <branch>` для пересборки. После публикации release — `regen_lock.py` обновляет `binaries.lock.yaml`.

API rate limit GitHub: 5000 req/h с токеном (есть в workflow), 60 без. У нас ~5 запросов раз в неделю — лимит неактуален.
