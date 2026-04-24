# Trust chain Ozero

Цепочка доверия для сборки Ozero от исходного кода до установленного APK у пользователя. Критична для F-Droid reproducible builds и защиты от supply chain атак.

## 1. Исходный код

| Слой | Источник | Pinned по |
|------|----------|-----------|
| Ozero app | `github.com/thesmithmode/ozero` | commit SHA в release tag |
| Xray-core | `github.com/XTLS/Xray-core` | версионный tag (`v25.10.1` — текущий) + GPG verification upstream maintainer |
| AmneziaWG Android | `github.com/amnezia-vpn/amneziawg-android` | tag + upstream signature (если доступна) |
| Tor | `git.torproject.org/tor.git` | tag + GPG upstream |
| obfs4proxy | `gitlab.com/yawning/obfs4` | tag |
| snowflake | `gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake` | tag |
| NaiveProxy | `github.com/klzgrad/naiveproxy` | tag |
| ByeDPI | `github.com/hufrea/byedpi` | tag |
| hev-socks5-tunnel | `github.com/heiher/hev-socks5-tunnel` | tag |

Все upstream tag'и фиксируются в `build-tools/versions.lock` (будет создан в E0.8/E0.12 вместе с первой реальной сборкой).

## 2. Toolchain

| Инструмент | Версия | SHA256 / image digest |
|-----------|--------|----------------------|
| Go | 1.22.x | `golang:1.22.12-bookworm` image digest pinned в `build-tools/Dockerfile` |
| Android NDK | r27c | SHA256 от Google-сайта, проверяется в Dockerfile до unzip |
| JDK | 17 (temurin/openjdk headless) | apt snapshot |
| Gradle | 8.9 | SHA256 от `gradle-wrapper-validation` action |
| AGP | 8.5.x | Maven Central |
| Kotlin | 2.0.x | Maven Central |

## 3. Зависимости Maven/Gradle

- Все dependencies — через `gradle/libs.versions.toml`
- `dependencyResolutionManagement { repositoriesMode = FAIL_ON_PROJECT_REPOS }` — ни один модуль не может добавить произвольный репозиторий
- Разрешённые репозитории: `google()`, `mavenCentral()`
- Lock: `./gradlew --write-locks` генерирует `*.lockfile` при enablement (включим в E0.11)

## 4. Подпись APK

- **Release keystore** — RSA-4096, 25 лет, генерация оффлайн (air-gapped)
- Backup: AES-256-зашифрованный файл на 2 оффлайн носителях + 1 hardware token (YubiKey)
- Passwords: GitHub encrypted secrets (`KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`)
- Release build в GitHub Actions использует keystore из secrets
- Debug keystore — стандартный Android debug.keystore, commit-able (bundled для CI)

## 5. Ed25519 subscription key

См. `docs/key-rotation.md`. Публичный ключ хардкоден в APK. Приватный — на air-gapped машине + YubiKey.

## 6. Reproducibility

F-Droid требует reproducible build: одинаковый source + toolchain → одинаковый APK bit-for-bit.

Наш контракт:
- Все non-determinism минимизирован (no timestamps, fixed file ordering in ZIP)
- Docker-билд (через `build-tools/Dockerfile`) — эталонная сборка
- Локальный билд разработчика сверяется с Docker через SHA256 AAR-артефактов
- F-Droid CI сборка (в `fdroiddata` metadata) — финальный эталон; его hash должен совпасть с нашим Docker-hash

Отклонения → bisect по:
- Go toolchain patch version (`go.sum` drift)
- NDK patch version
- gomobile версия
- Любые тайм-зависимые include (например `time.Now()` в генерированном коде)

## 7. Release workflow

```
git tag v1.0.0
  → GitHub Actions pipeline
    → fetch upstream-проекты по tag'ам из versions.lock
    → docker build build-tools/Dockerfile (reproducible env)
    → docker run build_xray.sh / build_amnezia.sh / build_tor.sh
    → ./gradlew assembleRelease с keystore из secrets
    → sign APK через jarsigner + apksigner
    → generate SHA256 + GPG detached signature (разработчика)
    → upload в GitHub Release
```

Разработчик подписывает финальный APK своим GPG key (pubkey в `SECURITY.md` / README). Пользователь может проверить: скачан APK + `.asc` → `gpg --verify`.

## 8. Verification пользователем

- SHA256 hash из GitHub Release сравнить с локальным после скачивания
- GPG signature проверить: `gpg --verify ozero-v1.0.0.apk.asc ozero-v1.0.0.apk`
- Сверить APK signing certificate SHA256 (доступно через `apksigner verify --print-certs`) с публичным известным fingerprint (будет в README)

## 9. Compromise scenarios

| Компромeтация | Отклик |
|---------------|--------|
| Upstream Xray (malicious commit) | Pin на last-known-good tag, откатить `versions.lock`, hotfix релиз |
| NDK Google distribution | Проверить официальные channels, паузнуть релизы до выяснения |
| GitHub Actions runner | Реsign всех в диапазоне compromise — security advisory + re-release |
| Разработческий GPG key | См. `SECURITY.md` — announce revocation + новый key |
| Subscription SK | См. `docs/key-rotation.md` — emergency rotation |
| Release keystore | Game-over для 1.x — migrate users к 2.x с новым signing key через self-update + упреждающее уведомление |

## 10. Аudit-лог версий

Финальные SHA256 каждого релиза: `docs/release-hashes.md` (append-only, создаётся начиная с v1.0.0).
