# Ротация Ed25519 subscription-ключа

## 1. Модель ключей
- **Long-term root key (LTK)** — хранится air-gapped + YubiKey OpenPGP. **Никогда не меняется**, только отзывается в крайнем случае.
- **Signing key (SK)** — рабочий ключ, подписывает `servers.json.sig`. Ротируется каждые 12 месяцев ИЛИ при подозрении на компрометацию.
- **Public SK** — хардкоден в APK в `OzeroConfig.kt` (Base64, 32 байта).

## 2. Зачем ротация
- Ограничение blast radius при компрометации
- Криптографическая гигиена (Ed25519 без известных атак, но ротация — defense in depth)
- Легко отзывать скомпрометированный SK заменой на LTK-подписанный новый SK

## 3. Transition-period подпись
- Новый SK_new генерируется на air-gapped машине
- SK_new подписывается LTK → получаем `sk_new.cert` с Ed25519 over LTK
- В APK публикуется двойной верификатор: проверяет `servers.json.sig` либо SK_old либо SK_new (пока оба активны)
- Transition period = 90 дней: подписываем подписки и SK_old, и SK_new (двойная подпись `.sig` + `.sig2`)
- После 90 дней: новые APK содержат только SK_new, старые APK продолжают работать через SK_old до истечения SK_old

## 4. Формат двойной подписи
- `servers.json` — канонический JSON
- `servers.json.sig` — 64 байта Ed25519 от SK_{active}
- `servers.json.sig2` — 64 байта Ed25519 от SK_{next} (в transition period)
- Клиент: если есть `.sig2` — проверяет тот ключ, который знает

## 5. Emergency rotation (компрометация)
1. Публикуется революкейшн через backend: `revocation.json` подписан LTK
2. Клиенты при старте делают revocation check (1 HTTPS GET)
3. Если revocation получен: клиент НЕ доверяет SK_old, ждёт SK_new (из backend + `sk_new.cert` LTK-signed)
4. Emergency update APK — встроен SK_new хардкодом

## 6. Процедура генерации (пошагово)
1. Air-gapped Linux (USB-boot)
2. `openssl genpkey -algorithm ED25519 -out sk_new.pem`
3. `openssl pkey -in sk_new.pem -pubout -out sk_new.pub`
4. Base64 32 байт публичного ключа → для копирования в APK
5. YubiKey attach → подписать SK_new приватным LTK: `gpg --detach-sign --output sk_new.cert sk_new.pub`
6. Export на encrypted USB → импорт в backend-сервер (только приватный SK_new)
7. SK_old архивируется оффлайн (зашифрован AES-256, пароль в password manager + paper backup)

## 7. Audit trail
- Все ротации логируются в `docs/key-rotation-log.md` (append-only)
- Формат: `YYYY-MM-DD | reason | sk_old_pubkey_b64_short | sk_new_pubkey_b64_short | operator_id`

## 8. Что делать пользователю
- Ничего. Обновление APK (через self-update E12) приносит новый публичный ключ.
- При emergency: уведомление в приложении «Критическое обновление доступно» + канал Telegram.
