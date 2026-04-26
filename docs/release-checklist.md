# Release Checklist

> Финальное QA перед v1.0.0. Все пункты должны быть зелёными для прохода Gate E13.
> При красных — fix в `dev`, повторный прогон.

## E13.1 — Провайдеры РФ (manual)

Запустить APK на реальном устройстве (не эмулятор) с активным SIM каждого
провайдера. Mobile + Home broadband для каждого если возможно.

| Провайдер | Mobile | Home | YouTube | Discord | ChatGPT | Engine выбран | Заметки |
|---|---|---|---|---|---|---|---|
| МТС | ☐ | ☐ | ☐ | ☐ | ☐ | | |
| МегаФон | ☐ | ☐ | ☐ | ☐ | ☐ | | |
| Билайн | ☐ | ☐ | ☐ | ☐ | ☐ | | |
| Ростелеком | ☐ | ☐ | ☐ | ☐ | ☐ | | |
| Yota | ☐ | ☐ | ☐ | ☐ | ☐ | | |
| T2 | ☐ | ☐ | ☐ | ☐ | ☐ | | |

**Критерий прохода:** все 6 провайдеров → автовыбор работает на YouTube/
Discord/ChatGPT без ручного вмешательства.

**Ручной fallback test:** на каждом провайдере по очереди форсировать
engine через Settings → manual engine — все engines должны хотя бы один
раз отработать.

## E13.2 — Soak test (8h)

- [ ] Устройство: реальный midrange (Snapdragon 6/7-серия)
- [ ] Сеть: Wi-Fi домашняя
- [ ] Сценарий: YouTube streaming 1080p непрерывно 8 часов
- [ ] VPN ON, no manual reconnect
- [ ] Результаты:
  - Battery drain ≤ 40% за 8 часов
  - Heap memory не растёт линейно (Android Profiler)
  - Нет ANR / crash в logcat
  - HealthMonitor switches: ≤ 3 за 8h (косвенно — нестабильный engine)

## E13.3 — Security audit

### Decompile resistance (R8 obfuscation)

```bash
# JADX decompile
jadx -d /tmp/ozero-decompile app/build/outputs/apk/release/app-release.apk
grep -r "ozero\|VLESS\|reality" /tmp/ozero-decompile/sources/ru/ozero/ | wc -l
```

- [ ] Имена классов в `sources/ru/ozero/` — нечитаемы (a, b, c... после R8)
- [ ] Strings зашифрованы или хотя бы не содержат ключевых слов в plaintext
- [ ] `BuildConfig.UPDATE_PUBLIC_KEY_HEX` — реальный ключ (не нули)
- [ ] `proguard-rules.pro` keeps только то что нужно для reflection / native bridge

### Compromise detection (E10)

- [ ] Frida server attach → app определяет → kill (CompromiseDetector)
- [ ] Эмулятор (Android Studio AVD) → app определяет → kill
- [ ] Подмена signature (apksigner с другим keystore) → SignatureCheck → kill
- [ ] Root detection: Magisk → app поведение (kill / warn)

### Network security

- [ ] cleartext HTTP запросы блокируются (`usesCleartextTraffic="false"`)
- [ ] (n/a в server-less архитектуре PLAN v4) ~~Certificate pinning для `sub.ozero.app`~~
- [ ] User CA не доверяется (mitmproxy с пользовательским CA не работает)

### Permissions audit

- [ ] Manifest содержит ровно permissions из `docs/privacy.md` (без лишних)
- [ ] `allowBackup="false"` + `dataExtractionRules` — данные не утекают в backup
- [ ] FOREGROUND_SERVICE_CONNECTED_DEVICE для Android 14+

## E13.4 — Performance

### Throughput

Тестовый сервер с iperf3 в той же сети что VPN-exit:

```bash
# С устройства через ADB
adb shell am start -W -a android.intent.action.VIEW -d "vless://..."
adb shell run-as ru.ozero.app /data/data/ru.ozero.app/files/iperf3 -c <server> -t 60
```

- [ ] Topовое устройство (SD 8 Gen 3): ≥ 100 Mbps
- [ ] Midrange (SD 7s Gen 2): ≥ 60 Mbps
- [ ] Xray overhead: < 15% (измерить bypass vs VPN на той же сети)
- [ ] ByeDPI overhead: < 5% (фрагментация на TCP-handshake'е)

### Latency

- [ ] Ping без VPN ↔ через VPN — delta ≤ 30ms (RTT to nearest server)

### Battery

- [ ] 1 час idle с активным VPN: drain ≤ 3%

## Gate E13

Все чекбоксы выше — зелёные. Если что-то красное — fix в `dev`,
не двигаемся в E14.

## Pre-release финальная проверка

- [ ] `versionName` поднят (semver)
- [ ] `versionCode` поднят (monotonic)
- [ ] CHANGELOG.md обновлён
- [ ] `docs/privacy.md` актуален (если permissions менялись)
- [ ] Release keystore доступен в GitHub Secrets (`KEYSTORE_BASE64`,
      `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
- [ ] GPG-ключ для подписи SHA256 в Secrets (`GPG_PRIVATE_KEY`,
      `GPG_PASSPHRASE`)
- [ ] `BuildConfig.UPDATE_PUBLIC_KEY_HEX` — продакшен Ed25519 ключ
- [ ] Tag создаётся через `git tag -s v1.0.0 -m "..."`
