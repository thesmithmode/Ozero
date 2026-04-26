# Release Signing — Ozero

## Обзор

Release APK подписывается двумя способами:
1. **Android keystore** (RSA 4096, PKCS12) — обязательная подпись для Google Play / прямого распространения.
2. **GPG detached signature** (ed25519) — `.asc` файл рядом с APK для верификации на уровне пользователя.

## GitHub Secrets (заданы один раз)

| Secret | Описание |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | PKCS12 keystore, закодированный в base64 |
| `RELEASE_KEYSTORE_PASSWORD` | Пароль от keystore |
| `RELEASE_KEY_ALIAS` | Алиас ключа (`ozero`) |
| `RELEASE_KEY_PASSWORD` | Пароль ключа (совпадает с `RELEASE_KEYSTORE_PASSWORD` для PKCS12) |
| `RELEASE_GPG_PRIVATE_KEY` | Armored PGP private key (ed25519) |
| `RELEASE_GPG_PASSPHRASE` | Парольная фраза GPG ключа |

## Генерация ключей (первичная настройка)

```bash
# 1. Сгенерировать keystore
tools/keystore-gen.sh /tmp/ozero-release

# 2. Сгенерировать GPG ключ
tools/gpg-gen.sh /tmp/ozero-release

# 3. Загрузить все секреты в GitHub и удалить локальные файлы
tools/upload-release-secrets.sh /tmp/ozero-release thesmithmode/Ozero
```

После `upload-release-secrets.sh` все локальные приватные файлы будут уничтожены через `shred`.

## Ротация ключей

### Ротация keystore
Android keystore нельзя заменить без потери возможности обновления APK через Google Play (подпись должна совпадать). **Менять keystore только если:**
- Ключ скомпрометирован (утечка).
- Приложение переходит с debug на новую distribution схему.

**Процедура:**
1. Сгенерировать новый keystore: `tools/keystore-gen.sh /tmp/new-ks`
2. Загрузить: `tools/upload-release-secrets.sh /tmp/new-ks`
3. Обновить fingerprint в README/SECURITY.md.
4. Уведомить пользователей — им придётся удалить и переустановить APK если у них ручная установка.

### Ротация GPG ключа
GPG ключ можно менять свободно. Нужно обновить публичный fingerprint в `SECURITY.md`.

1. `tools/gpg-gen.sh /tmp/new-gpg`
2. `tools/upload-release-secrets.sh /tmp/new-gpg` (загружает только GPG секреты если нет keystore файла)
3. Экспортировать публичный ключ и обновить `SECURITY.md`.

## Recovery — потерян приватный ключ

### Потерян GPG ключ
Recovery невозможен. Старые `.asc` подписи перестают верифицироваться новым ключом.
**Действия:**
1. Сгенерировать новый GPG ключ: `tools/gpg-gen.sh /tmp/recovery-gpg`
2. Загрузить в GitHub secrets.
3. Обновить публичный fingerprint в `SECURITY.md` с пометкой о ротации и датой.
4. Все новые релизы будут подписаны новым ключом.

### Потерян Android keystore
**Это критическая ситуация для Play Store.** Recovery невозможен без Google Play App Signing backup.

Если включён **Google Play App Signing** (рекомендуется):
- Google хранит upload key + app signing key раздельно.
- Обратиться в Google Play support для смены upload key.

Если keystore хранился только локально и потерян:
- Приложение нельзя обновить в Google Play.
- Нужно создать новый app listing или использовать Play App Signing recovery.
- Для прямого распространения: создать новый keystore, уведомить пользователей об обязательной переустановке.

## Верификация подписи APK (для пользователей)

```bash
# Скачать публичный GPG ключ из SECURITY.md
gpg --import ozero-release-pubkey.asc

# Проверить подпись
gpg --verify ozero-release-v1.0.0.apk.asc ozero-release-v1.0.0.apk
```

## CI/CD

Release workflow `.github/workflows/release.yml` срабатывает при пуше тега `v*.*.*`.
Workflow автоматически:
1. Декодирует keystore из `RELEASE_KEYSTORE_BASE64`.
2. Собирает подписанный APK через Gradle.
3. Создаёт GPG detached signature для каждого APK.
4. Публикует GitHub Release с артефактами: `.apk`, `.sha256`, `.asc`.
