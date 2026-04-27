# Release Readiness — Ozero v1.0.0

> Чек-лист готовности к тегу `v1.0.0`. Заполняется перед `git tag -a v1.0.0`.
> Любой `Pending` блокирует релиз.

---

## 1. Мета

| Поле | Значение |
| --- | --- |
| Версия | `v1.0.0` |
| Дата проверки | 2026-04-27 |
| Git ref | `dev` @ `12137ac787b0b45fda66774a503ab275da07dcba` |
| Последний коммит | `FIX: AntiFridaCheck — явная лямбда для File.useLines` |
| Коммитов с момента отделения от `main` | **97** (`git rev-list --count main..dev`) |
| Ответственный | maintainer |

---

## 2. Артефакты релиза

Собираются workflow `.github/workflows/release.yml` по push тега `v*.*.*`:

- `app-release.apk` — подписанный production APK (release keystore, RSA-4096).
- `app-release.apk.asc` — GPG detached-signature ASCII-armor.
- `app-release.apk.sha256` — контрольная сумма.
- `bootstrap-servers.json` + `bootstrap-servers.json.sig` — bootstrap snapshot и Ed25519 подпись (вкладываются в APK как asset, плюс публикуются отдельно для аудита).
- `source-bundle.tar.gz` — слепок репозитория на теге (`git archive`), sha256.
- `update-pubkey.pem` — публичный Ed25519 (для верификации snapshot/self-update внешними аудиторами).
- SBOM (`sbom.spdx.json`) — генерируется security-audit job, прикрепляется к Release.

Все артефакты публикуются в GitHub Release `v1.0.0` через `softprops/action-gh-release`.

---

## 3. Подпись и доверие

| Назначение | Алгоритм | Где приватник | Где паблик |
| --- | --- | --- | --- |
| Bootstrap snapshot + self-update payload | Ed25519 | GH Secret `ED25519_UPDATE_PRIVATE_KEY` | `app/src/main/assets/update-pubkey.pem` (in-APK, immutable) |
| APK signing | RSA-4096 (PKCS#12 keystore) | GH Secret `RELEASE_KEYSTORE_B64` (+ `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`) | Fingerprint в `docs/keystore-setup.md` и Release notes |
| Release artefacts (`*.asc`) | GPG (RSA-4096) | GH Secret `GPG_PRIVATE_KEY` (+ `GPG_PASSPHRASE`) | Публичный ключ на keys.openpgp.org + `docs/release-signing.md` |

Перечень GH Secrets, обязательных для релиза:

```
ED25519_UPDATE_PRIVATE_KEY
RELEASE_KEYSTORE_B64
RELEASE_STORE_PASSWORD
RELEASE_KEY_PASSWORD
RELEASE_KEY_ALIAS
GPG_PRIVATE_KEY
GPG_PASSPHRASE
```

Перед тегом проверить `gh secret list` — все семь должны присутствовать.

---

## 4. CI gates passed (автоматические)

Статусы зафиксированы на 2026-04-27 через `gh run list --workflow=<name> --limit 1`.

| Workflow | Триггер | Последний статус | Run ID / Branch |
| --- | --- | --- | --- |
| `ci.yml` | push/PR | ✅ success | `24977946382` / `dev` (2026-04-27) |
| `gradle-wrapper-validation.yml` | push/PR | ✅ success | `24977946393` / `dev` (2026-04-27) |
| `urnetwork-aar.yml` | push (paths) | ❌ failure | `24964911205` / `feat/e15-urnetwork` (2026-04-26) — **требует фикса перед релизом** |
| `security-audit.yml` | manual + cron 04:00 UTC | ⚠️ Pending — workflow закоммичен (8842830), но ни одного run пока не зарегистрировано | — |
| `throughput-bench.yml` | manual (`workflow_dispatch`) | ⚠️ Pending — manual gate, см. §5 | — |
| `soak.yml` | manual (`workflow_dispatch`) | ⚠️ Pending — manual gate, см. §5 | — |
| `ru-probe.yml` | manual (`workflow_dispatch`) | ⚠️ Pending — manual gate, см. §5 | — |

**Блокеры:**
- `urnetwork-aar.yml` красный → починить до релиза (см. §6).
- `security-audit.yml` ни разу не запускался → запустить вручную (`gh workflow run security-audit.yml`) и приложить SARIF.

---

## 5. Manual gates — E13 (производительность и доступность)

Workflow’ы `workflow_dispatch` запускаются вручную перед каждым релизом и логируются здесь.

| Gate | Workflow | Критерий приёмки | Статус |
| --- | --- | --- | --- |
| iperf3 throughput baseline | `throughput-bench.yml` | ≥ 80% от direct-link throughput на эмуляторе CI matrix | ⚠️ Pending — запустить `gh workflow run throughput-bench.yml` за ≤24ч до тега |
| RU vantage probe | `ru-probe.yml` | ozero-домены доступны через RU SOCKS, заблокированные сайты деблокируются | ⚠️ Pending — запустить `gh workflow run ru-probe.yml` |
| Soak (1h smoke в CI) | `soak.yml` | drop-rate < 0.5%, нет memory leak, нет ANR | ⚠️ Pending — запустить `gh workflow run soak.yml -f requests=...` |

Обоснование Pending: workflow’ы триггерятся вручную и должны запускаться непосредственно перед релизом, чтобы данные отражали текущий коммит. Для каждого gate сюда вписать Run ID + ссылку на артефакты.

---

## 6. Manual gates — E15 (URnetwork engine)

| Gate | Где проверяется | Критерий | Статус |
| --- | --- | --- | --- |
| URnetwork AAR build | `urnetwork-aar.yml` | gomobile bind зелёный, AAR ≤ 30 MB | ❌ Failed (последний run на `feat/e15-urnetwork`) — починить и запустить на `dev` |
| `engine-urnetwork` unit tests | `ci.yml` (Gradle module) | Coverage ≥ 90% реальный | ⚠️ Зависит от слияния E15 в `dev` |
| E2E: connect через URnetwork | Instrumented test (`androidTest`) | `cloudflare.com/cdn-cgi/trace` отвечает 200 через URnetwork tunnel | ⚠️ Pending |

---

## 7. Security review

- Workflow: `security-audit.yml` (Gradle `dependencyCheck` + `mobsfscan` + SARIF upload в GitHub Security tab).
- Последний прогон: **отсутствует** (workflow закоммичен, но ещё не выполнялся).
- Действие до тега: запустить вручную `gh workflow run security-audit.yml --ref dev`, дождаться, прикрепить ссылку на SARIF (`https://github.com/thesmithmode/Ozero/security/code-scanning?query=branch:dev`) и убедиться: 0 High/Critical.
- Дополнительно проверить `docs/security-todo.md` и `docs/threat-model.md` — нет открытых пунктов уровня MUST.

---

## 8. Known issues

<!-- Заполнить вручную перед тегом. Каждая запись: симптом → impact → workaround/fix-by. -->

- 
- 
- 

---

## 9. Release procedure

После того как все gates выше переведены в ✅:

```bash
# 1. Финальная синхронизация
git checkout dev && git pull --ff-only origin dev

# 2. Убедиться что CI на dev зелёный
gh run list --branch dev --limit 5

# 3. Обновить CHANGELOG.md (раздел v1.0.0 — дата 2026-04-27)
$EDITOR CHANGELOG.md
git add CHANGELOG.md
git commit -m "DOCS: CHANGELOG v1.0.0"
git push origin dev

# 4. Fast-forward main ← dev (единственное прямое касание main, по приказу пользователя)
git checkout main
git pull --ff-only origin main
git merge --ff-only dev

# 5. Тег + push
git tag -a v1.0.0 -m "Ozero v1.0.0 — server-less GA"
git push origin main v1.0.0

# 6. release.yml автоматически:
#    - assembleRelease (signed APK)
#    - GPG-armor .asc
#    - sha256
#    - source bundle
#    - публикация GitHub Release v1.0.0

# 7. Пост-релиз: вернуться на dev
git checkout dev
```

Откат при провале release workflow:

```bash
git push --delete origin v1.0.0
git tag -d v1.0.0
# main уже fast-forward на dev — оставляем, ничего не теряется.
```
