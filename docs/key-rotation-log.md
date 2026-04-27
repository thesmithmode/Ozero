# Subscription Ed25519 key rotation log

Append-only лог ротаций subscription signing key (SK). См. процедуру: `docs/key-rotation.md`.

## Формат записи

```
YYYY-MM-DD | reason | sk_old_pubkey_b64_short (first 12) | sk_new_pubkey_b64_short (first 12) | operator_id
```

Где:
- `reason` — `scheduled` (плановая 12 мес) | `emergency` (компрометация) | `initial` (первичная генерация)
- `sk_*_short` — первые 12 символов base64 публичного ключа (для удобства глазами, полный ключ — в `OzeroConfig.kt` соответствующей версии APK)
- `operator_id` — GitHub handle разработчика, выполнившего ротацию

## Записи

<!-- APPEND ONLY. НИКОГДА НЕ РЕДАКТИРОВАТЬ СТАРЫЕ ЗАПИСИ. -->

| Дата | Причина | SK_old | SK_new | Оператор | Примечание |
|------|---------|--------|--------|----------|------------|
| _pending_ | initial | — | _will be added after E0.5_ | maintainer | Первичная генерация ключа до v1.0.0 |
