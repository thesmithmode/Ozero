# PLAN — финал перед v1.0.0

> Source-of-truth для оставшейся работы. Все шаги выполняются автономно.
> Дата: 2026-04-26.

## Этап 0 — Гигиена веток (перед стартом)

- [ ] Дождаться зелёного CI на `feat/e16-server-less`
- [ ] Squash-merge в `dev`, удалить ветку (локал + remote)
- [ ] `git worktree prune`, проверить отсутствие висящих веток

## Этап 1 — Bootstrap snapshot реальных URI (`feat/bootstrap-snapshot`)

Ветка от `dev`. Цель: заменить placeholder в `app/src/main/assets/bootstrap-servers.json` живыми URI.

1. Скрипт `tools/harvest_snapshot.py` — поднять `PublicProxyHarvester` логику локально (Python-репликация: free-proxy-list, proxyscrape, github gists), либо instrumented run на эмуляторе через `gradle :app:harvestSnapshot` task.
2. **Поиск RU vantage**: задействовать публичные RU SOCKS/HTTP списки (proxyscrape `country=RU`, spys.one, hidemy.name free) → отфильтровать живые через TCP+TLS probe к `cloudflare.com`.
3. LiveProber matrix: каждый кандидат прогнать через ByeDpiStrategyGenerator → отобрать топ-50 по latency+stability.
4. Сериализовать в `bootstrap-servers.json` (формат: `{uri, strategy, score, harvested_at}`).
5. Подписать snapshot Ed25519 ключом из Этапа 2 → `bootstrap-servers.json.sig`.
6. Тест: `BootstrapServersAssetTest` парсит, проверяет ≥30 валидных, signature OK.
7. CI зелёный → squash в `dev`.

## Этап 2 — Ed25519 self-update keypair (`feat/ed25519-keys`)

Ветка от `dev`.

1. `tools/keygen-ed25519.sh`: `openssl genpkey -algorithm ed25519` → `private.pem`, `public.pem`.
2. Приватный ключ → GH Secret `ED25519_UPDATE_PRIVATE_KEY` (через `gh secret set` из stdin).
3. Публичный ключ → `app/src/main/assets/update-pubkey.pem` (commit).
4. `BuildConfig.UPDATE_PUBKEY` загружается из asset на старте, верифицирует подпись `bootstrap-servers.json.sig` и будущих self-update payload.
5. Unit test: `Ed25519VerifierTest` (good/bad signature, malformed key).
6. CI Release workflow: подписывает payload приватным ключом из secret.

## Этап 3 — Release keystore + GPG (`chore/release-signing`)

Ветка от `dev`.

1. `tools/keystore-gen.sh`: `keytool -genkey -alias ozero -keyalg RSA -keysize 4096 -validity 10000 -keystore release.keystore`.
2. Base64 keystore → GH Secret `RELEASE_KEYSTORE_B64`. Пароли → `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`.
3. GPG: `gpg --batch --gen-key` со script-config → экспорт private (`gpg --export-secret-keys --armor`) → GH Secret `GPG_PRIVATE_KEY`, passphrase → `GPG_PASSPHRASE`.
4. `app/build.gradle.kts`: `signingConfigs.release` читает из env, подключается только в CI release.
5. `.github/workflows/release.yml`: декодирует keystore, собирает `assembleRelease`, подписывает APK + GPG-detached `.asc`.
6. Smoke: `gradle :app:assembleRelease` проходит локально с фейковыми env.

## Этап 4 — E13 автоматизация (`feat/e13-automation`)

Ветка от `dev`. Делим на под-задачи; каждое CI-зелёное мерджится сразу.

### 4.1 iperf3 harness
- `tools/iperf3-runner.sh` + Gradle task `:app:throughputBench` (instrumented test через эмулятор matrix в CI).
- Выход: JSON metrics → артефакт CI.

### 4.2 RU vantage proxy probe
- Поднять Python скрипт `tools/ru-vantage-probe.py`: использует найденные в Этапе 1 RU SOCKS как exit, запускает test-suite (ozero domains, blocked sites).
- Cron в CI (manual trigger workflow).

### 4.3 Soak harness
- Adb-driven gradle task `:app:soakTest` (24h reduced до 1h в CI smoke).
- Метрики: connection drop rate, memory leak, ANR count.

### 4.4 Security audit automation
- `gradle :app:dependencyCheck` + `mobsfscan` Docker step в `.github/workflows/security-audit.yml`.
- SARIF upload → GitHub Security tab.

## Этап 5 — E15 URnetwork integration (`feat/e15-urnetwork`)

Большая ветка. Под-этапы — отдельные коммиты, мердж после каждого зелёного CI блока.

### 5.1 gomobile bind upstream
- Fork upstream `urnetwork/sdk` (gomobile-friendly fork нашего org или skin).
- `tools/build-urnetwork-aar.sh`: `gomobile bind -target=android -androidapi=21 -o engine-urnetwork/libs/urnetwork.aar github.com/urnetwork/sdk/mobile`.
- AAR коммитится через Git LFS или собирается в CI (предпочтение: CI build, LFS только для release artefacts).

### 5.2 engine-urnetwork module
- Скелет `engine-urnetwork/` по паттерну `engine-amnezia` (`Engine` interface impl, lifecycle, config DTO).
- Unit тесты на конфиг-парсинг.

### 5.3 Orchestrator integration
- Регистрация в `core-orchestrator` (ConnectionStrategy, fallback chain).
- Конфиг-флаг `ENABLE_URNETWORK` в Remote Config.

### 5.4 E2E
- Instrumented test: connect через URnetwork → http probe `cloudflare.com/cdn-cgi/trace`.

## Этап 6 — Tag v1.0.0

После всех squash-merge `dev` → проверить:
- Все E13/E15 manual gates passed (логи в `docs/release-readiness-v1.0.md`).
- CI на `dev` зелёный.
- `CHANGELOG.md` обновлён.

Команда:
```
git checkout main && git merge --ff-only dev
git tag -a v1.0.0 -m "Ozero v1.0.0 — server-less GA"
git push origin main v1.0.0
```

Release workflow собирает APK, подписывает, GPG-armor, публикует GitHub Release.

---

## Правила выполнения

- Каждый этап = отдельная ветка от `dev`, имя короткое (`feat/...`, `chore/...`).
- TDD: падающий тест → реализация. Coverage ≥90% реальный.
- CI зелёный → squash в `dev` → удалить ветку (локал+remote) сразу.
- Параллельно через `git worktree` где независимо.
- Секреты создаются `gh secret set` из stdin, никогда не коммитятся.
- main не трогать до Этапа 6.
