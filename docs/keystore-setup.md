# Release keystore — генерация, backup, использование

Процедура создания и хранения release-keystore для подписи APK Ozero. Ключ действителен 25 лет — подмена ключа в середине цикла приложения означает миграцию пользователей на новую signing identity (game-over-like, см. `docs/threat-model.md` §6).

## 1. Требования

- **Linux live-USB** (Tails или Debian в live-mode) — air-gapped
- **OpenJDK 17** (keytool)
- **YubiKey 5** (OpenPGP applet для backup-кей)
- **2 USB-накопителя** для оффлайн-бекапа, зашифрованные LUKS/VeraCrypt (AES-256)
- **1 листок бумаги** (paper backup) — для passphrase в сейф

## 2. Генерация keystore

### 2.1 Boot в air-gapped Linux
- Wi-Fi/Bluetooth отключены физически (airplane mode + BIOS-toggle если возможно)
- Нет сетевых кабелей
- Сгенерировать только после полного boot live-системы

### 2.2 Команда генерации
```bash
keytool -genkeypair -v \
  -keystore ozero-release.jks \
  -alias ozero \
  -keyalg RSA \
  -keysize 4096 \
  -validity 9125 \
  -storetype JKS \
  -dname "CN=Ozero, OU=Release, O=Ozero Project, L=Unknown, ST=Unknown, C=XX"
```

Параметры:
- `-keysize 4096` — RSA-4096 (не 2048, Android поддерживает)
- `-validity 9125` — 25 лет
- `CN=Ozero` — generic identity, без PII разработчика
- `C=XX` — нейтрально (не раскрывает юрисдикцию)

Keytool запросит:
- `keystore password` — 24+ символа случайной строки (diceware или `openssl rand -base64 24`)
- `key password` — **отличный** от keystore password, 24+ символа
- alias confirmation

### 2.3 Verify
```bash
keytool -list -v -keystore ozero-release.jks -alias ozero
# Проверить: Signature algorithm = SHA256withRSA, key size 4096, validity 25 лет
```

Запомнить / сохранить **SHA-256 fingerprint** keystore — он пойдёт в native signature check (`security/cpp/signature_check.c`) и в README:
```
Certificate fingerprints:
	 SHA256: XX:XX:XX:...:XX
```

## 3. Backup

### 3.1 Три копии, разные носители
- **Копия 1:** зашифрованный USB-1 (LUKS + AES-256) → сейф 1 (домашний)
- **Копия 2:** зашифрованный USB-2 (VeraCrypt + AES-256) → сейф 2 (не дома — банковская ячейка / у доверенного лица)
- **Копия 3:** YubiKey 5 с PIV-applet — импорт приватного ключа через:
  ```bash
  openssl pkcs12 -in ozero-release.p12 -nocerts -nodes -out private_key.pem
  yubico-piv-tool -s 9c -a import-key -i private_key.pem
  yubico-piv-tool -s 9c -a verify-pin
  ```
  Результат — YubiKey со слотом 9c (digital signature). Physical-touch требуется для каждой подписи.

### 3.2 Passphrase backup
- Keystore password + key password → в password manager (KeePass/Bitwarden) с master-password офлайн
- **Paper backup**: passwords записываются (BIP39-style mnemonic если паранойя) и хранятся в физическом сейфе отдельно от USB-носителей

### 3.3 НЕ БЭКАПИТЬ
- В облако (Google Drive / Dropbox / iCloud) — **никогда**
- На устройство разработчика напрямую (`~/keystores/`) — только временно при подписи, затем shred
- В git — **категорически нет**

## 4. GitHub secrets

В репозитории `github.com/thesmithmode/ozero` → Settings → Secrets and variables → Actions:

| Secret | Значение |
|--------|----------|
| `KEYSTORE_BASE64` | `base64 -w 0 ozero-release.jks` → весь output |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password |
| `KEY_ALIAS` | `ozero` |

Workflow в `.github/workflows/release.yml` распакует keystore:
```yaml
- name: Decode keystore
  env:
    KS_B64: ${{ secrets.KEYSTORE_BASE64 }}
  run: |
    echo "$KS_B64" | base64 -d > app/ozero-release.jks

- name: Build signed APK
  env:
    KS_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
  run: ./gradlew assembleRelease -PstoreFile=app/ozero-release.jks ...

- name: Wipe keystore
  if: always()
  run: shred -u app/ozero-release.jks
```

## 5. Local signing (альтернатива CI)

Если релиз подписывается локально (не через CI):
```bash
# Mount encrypted USB с keystore
cryptsetup open /dev/sdb1 ozero-ks
mount /dev/mapper/ozero-ks /mnt/ozero-ks

# Build
./gradlew assembleRelease \
    -PstoreFile=/mnt/ozero-ks/ozero-release.jks \
    -PstorePassword="$KS_PW" \
    -PkeyAlias=ozero \
    -PkeyPassword="$KEY_PW"

# Unmount + close
umount /mnt/ozero-ks
cryptsetup close ozero-ks
```

**Никогда** не копировать `.jks` на рабочую файловую систему.

## 6. Debug keystore

Debug keystore (`debug.keystore`) — стандартный Android debug key:
- Храним в репо: `buildSrc/debug.keystore` (commit-able)
- Password: `android`, key: `androiddebugkey` (default)
- Используется только для `debug` build type
- **Не путать** с release keystore

## 7. Ротация / компрометация

При компрометации release keystore:
1. Немедленно остановить CI pipeline (disable Secrets)
2. Security advisory в GitHub Security Advisories
3. Сгенерировать новый keystore (повтор шагов 2–3)
4. **Все существующие пользователи** не смогут обновиться на новый APK (сменилась signing identity) → миграция через self-update (E12) требует specific flow:
   - Выпустить переходный APK подписанный старым ключом, который warn user «критическое обновление — переустановка требуется»
   - Новый APK (с новым ключом) — только через manual install (скачать + uninstall + install)
5. Update `docs/release-hashes.md` с отметкой smene signing identity

Компромeтация release keystore — **катастрофический** сценарий. Предотвратить лучше чем восстановить.

## 8. Audit

- Место хранения USB-копий — записать в сейфе на бумаге (`keystore-locations.paper`)
- Каждое использование YubiKey (физический touch) — логируется
- Review backup раз в 6 месяцев: USB-1 и USB-2 читаются, контрольная сумма сравнивается
