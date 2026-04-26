# Cert Pinning Rotation Playbook (RT.12.1)

> **Статус (PLAN v4, апрель 2026): не применяется в server-less архитектуре.**
> Документ оставлен как playbook на случай возможного приватного backend в
> будущем (private subscription для бета-каналов, например). Текущая архитектура
> — server-less: source конфигов = публичные GitHub-репо (TLS GitHub),
> self-update = GitHub Releases (TLS GitHub). Pinning GitHub'а — антипаттерн
> (их CA rotation вне нашего контроля → brick).
>
> Конфиг: `app/src/main/res/xml/network_security_config.xml`.

## TL;DR

Никогда не выкатываем APK с одним pin'ом. Минимум **два**: текущий
production-сертификат + backup (next-cert). Когда current истекает или
скомпрометирован — backup становится current, а в config попадает новый
backup. Так старые установленные APK не превращаются в кирпичи в момент
ротации сертификата.

## Конфиг pin-set

`app/src/main/res/xml/network_security_config.xml`:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- RT.12.1: pin-set для subscription домена. -->
    <domain-config>
        <domain includeSubdomains="false">sub.ozero.app</domain>
        <pin-set expiration="2027-04-26">
            <!-- current: SubjectPublicKeyInfo SHA-256 от prod-сертификата -->
            <pin digest="SHA-256">BASE64_PIN_CURRENT==</pin>
            <!-- backup: SPKI SHA-256 от next-cert (выпущен заранее, в HSM) -->
            <pin digest="SHA-256">BASE64_PIN_BACKUP==</pin>
        </pin-set>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </domain-config>
</network-security-config>
```

`expiration` — дата за которой pin'ы перестают применяться (fallback на
system trust). Должна быть **позже** валидности backup-сертификата, иначе
после неё клиент примет любой валидный по system trust сертификат.

## Как считается pin

```bash
openssl s_client -servername sub.ozero.app -connect sub.ozero.app:443 \
  </dev/null 2>/dev/null | \
openssl x509 -pubkey -noout | \
openssl pkey -pubin -outform DER | \
openssl dgst -sha256 -binary | \
openssl base64
```

Pin — это base64(SHA-256(SubjectPublicKeyInfo)). Не fingerprint цельного
сертификата (тот меняется при ре-issue даже если ключ тот же).

Для backup (cert ещё не задеплоен на сервере) — считаем тот же hash от
SPKI выпущенного заранее cert'а:

```bash
openssl x509 -in next-cert.pem -pubkey -noout | \
openssl pkey -pubin -outform DER | \
openssl dgst -sha256 -binary | \
openssl base64
```

## Процедура ротации (overlap window)

**Цель:** в любой момент времени установленные APK имеют как минимум один
валидный pin под текущий cert на сервере. Минимальное окно — 90 дней
overlap'а перед выкаткой нового cert'а.

### Шаги

1. **T-90: выпустить next-cert.** Новая пара ключей в HSM, CSR подписать
   через CA (Let's Encrypt автоматически перевыпустит за 30 дней).
   Пока **не** деплоить на сервер.
2. **T-90: посчитать BASE64_PIN_BACKUP** из next-cert SPKI (см. выше).
3. **T-90: обновить network_security_config.xml** — `BACKUP_PIN`
   = новый pin. Старые `current` и `backup` сохраняем — теперь у нас
   два pin'а. Поднимаем `expiration` на ≥1 год вперёд.
4. **T-90: выкатить APK** в release-канал. Дать время старым клиентам
   обновиться (Play/F-Droid, минимум 30 дней).
5. **T-30: убедиться** что ≥95% активной базы на новой версии (метрика
   из Play Console / F-Droid analytics).
6. **T-0: перевыпустить cert на сервере** — деплоить next-cert как
   primary. Старые APK всё ещё работают через `BACKUP_PIN`, новые APK —
   через `PIN_CURRENT` который теперь соответствует серверу.
7. **T+30: обновить config** — старый `current` удаляем, `BACKUP_PIN`
   становится `current`, генерим следующий backup из next-next-cert.
   Выкатываем APK.

### Аварийная ротация (compromise)

Если private key скомпрометирован:

1. **Немедленно** revoke текущий cert через CA.
2. Деплоить backup-cert на сервере как primary.
3. Выкатить hotfix APK с обновлённым pin-set'ом (новый backup).
4. Старые APK с одним только current-pin'ом потеряют доступ к
   `sub.ozero.app` — но это намеренно: компромисс лучше, чем подмена
   подписки.
5. Пользователи без обновления APK останутся со старым списком серверов
   (Room cache) до ручного обновления приложения.

## Тестирование ротации

### Локально

1. Поднять `nginx` с self-signed cert (валидно 1 день).
2. Заменить в network_security_config pin на SPKI этого cert'а.
3. Запустить APK в эмуляторе → проверить sync.
4. Перевыпустить cert (другой ключ) **не меняя конфиг** → APK должен
   отказаться от подключения с `SSLHandshakeException: cert pin
   mismatch`.
5. Добавить новый SPKI как backup → APK снова подключается.

### CI/CD

`network_security_config_test.xml` в `app/src/androidTest/res/xml/`:
два pin'а от mock-серверов в Espresso instrumentation. См. `RobolectricTest`
в `core-subscriptions` — pinning проверяется через `OkHttp.CertificatePinner`
который читает тот же конфиг.

## Чеклист перед выкатом

- [ ] В config'е минимум **два** pin'а
- [ ] `expiration` ≥ срок валидности backup-cert'а
- [ ] `BASE64_PIN_CURRENT` сверен с продом (`openssl s_client` выше)
- [ ] `BASE64_PIN_BACKUP` сверен с next-cert.pem из HSM
- [ ] Тест на локальном mock-сервере прошёл (см. выше)
- [ ] CHANGELOG отметка про ротацию pin'ов
- [ ] План отзыва старого cert'а через ≥30 дней после выката

## Ссылки

- [Android Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [OWASP MASTG: Pinning](https://mas.owasp.org/MASTG/tests/android/MASVS-NETWORK/)
- E10.5 (источник pin-set дизайна) — в `docs/security-todo.md`

## Текущий статус (2026-04-26)

`network_security_config.xml` пока **без** `domain-config` для
`sub.ozero.app` — pin-set реально подключим вместе с E10.5
(когда backend `sub.ozero.app` выйдет из stub-режима и появится prod
сертификат). Этот документ — playbook для того момента, не описание
текущего состояния.
