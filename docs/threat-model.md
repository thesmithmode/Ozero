# Ozero — Threat Model

**Версия**: 1.0  
**Дата**: 2026-04-24  
**Статус**: Draft  
**Аудитория**: core team, security reviewer, F-Droid maintainer

---

## 1. Scope и Trust Boundaries

### 1.1 Описание системы

Ozero — Android-приложение без облачного backend. Клиент сам выбирает движок, сам проверяет серверы, сам устанавливает TUN. Единственный серверный компонент — `sub.ozero.app`: публичный Go-сервис, который отдаёт подписку серверов, подписанную Ed25519 на air-gapped машине. Приватный ключ никогда не покидает офлайн-носитель. Обновления APK публикуются на GitHub Releases и зеркалируются в F-Droid.

### 1.2 Trust Zones и ASCII-диаграмма

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  ZONE 0 — DEVELOPER INFRA (highest trust, air-gapped)                       ║
║  YubiKey (release signing) + offline machine (Ed25519 private key)          ║
║  → подписанный APK → GitHub Releases                                         ║
╚══════════════════════════════════════════════════════════════════════════════╝
                         │ HTTPS + SHA256 verify + Ed25519 APK sig
                         ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  ZONE 1 — UPDATE CHANNEL (low trust, public)                                ║
║  GitHub Releases (APK + .sig) │ F-Droid repo                                ║
╚══════════════════════════════════════════════════════════════════════════════╝
                         │ download APK
                         ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  ZONE 2 — ANDROID DEVICE (untrusted environment)                            ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  App process (ru.ozero.app)                                         │    ║
║  │  ┌──────────────────┐   ┌───────────────────────────────────────┐  │    ║
║  │  │  UI (Compose)    │   │  Orchestrator + StrategyEngine         │  │    ║
║  │  │  MainScreen      │   │  Ed25519 verify, SubscriptionManager   │  │    ║
║  │  └──────────────────┘   └───────────────────────────────────────┘  │    ║
║  │  ┌──────────────────────────────────────────────────────────────┐  │    ║
║  │  │  Engine layer (JNI / gomobile)                               │  │    ║
║  │  │  libbyedpi.so │ libxray.aar │ libamnezia.aar │ libtor.so     │  │    ║
║  │  └──────────────────────────────────────────────────────────────┘  │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                │                                             ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  VpnService (OzeroVpnService)                                       │    ║
║  │  TUN fd + hev-socks5-tunnel (loopback SOCKS)                        │    ║
║  │  route 0.0.0.0/0 + ::/0 → fail-closed kill-switch                  │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
╚══════════════════════════════════════════════════════════════════════════════╝
        │ Loopback 127.0.0.1:SOCKS_PORT (trust boundary: OS loopback)
        ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  ZONE 3 — NETWORK EGRESS (zero trust)                                       ║
║  ISP / ТСПУ / DPI / CGNAT                                                   ║
║  Мобильный оператор (MNO) — полная видимость metadata + SNI + QUIC initial  ║
╚══════════════════════════════════════════════════════════════════════════════╝
        │ encrypted tunnel (VLESS+Reality / Hysteria2 / AmneziaWG / etc.)
        ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  ZONE 4 — ENTRY VPS (РФ или нейтральная юрисдикция)                        ║
║  Xray inbound (VLESS+Reality+XHTTP)                                         ║
║  Выглядит как TLS 1.3 HTTPS сайт для DPI                                    ║
║  В double-hop режиме — только relay, не видит расшифрованный трафик         ║
╚══════════════════════════════════════════════════════════════════════════════╝
        │ внутренний hop (Xray proxySettings chain)
        ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  ZONE 5 — EXIT VPS (иностранная юрисдикция)                                 ║
║  Xray outbound → интернет                                                   ║
║  Чистый IP (не VPN-listed), принимается западными сайтами                   ║
╚══════════════════════════════════════════════════════════════════════════════╝
        │ open internet (HTTPS, etc.)
        ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  ZONE 6 — SUBSCRIPTION BACKEND (sub.ozero.app)                              ║
║  Публичный Go HTTP-сервер. Отдаёт servers.json + Ed25519 подпись            ║
║  Не хранит пользовательские данные. Подписи генерируются офлайн             ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### 1.3 Trust Boundaries — перечень

| Граница | Описание | Механизм защиты |
|---------|----------|-----------------|
| TB-1 | Интернет → App process | TLS 1.3, certificate pinning (`sub.ozero.app`), Ed25519 payload verify |
| TB-2 | App process → VpnService | Binder IPC (Android OS-level isolation, same UID) |
| TB-3 | VpnService → Engine (loopback SOCKS) | OS loopback (127.0.0.1), не выходит в сеть, port изолирован |
| TB-4 | Engine → Entry VPS | Зашифрованный протокол (VLESS+Reality / Hysteria2 / AmneziaWG) |
| TB-5 | Entry VPS → Exit VPS | Xray chain outbound (Mutual TLS / VLESS внутри) |
| TB-6 | App process → Subscription backend | HTTPS + cert pinning + Ed25519 signature on payload |
| TB-7 | Developer → GitHub Releases | YubiKey подпись APK + Ed25519 `.sig` файл |
| TB-8 | OS → App | Android sandbox, `android:allowBackup="false"`, `networkSecurityConfig` |

---

## 2. Assets

### 2.1 Что защищается

| Asset | Описание | Классификация |
|-------|----------|---------------|
| **A-1 Конфиденциальность трафика** | Содержимое HTTP(S)-запросов пользователя от приложения до exit-ноды | Критично |
| **A-2 Анонимность перед ISP/государством** | Метаданные: с кем соединяется, когда, сколько байт | Критично |
| **A-3 Идентичность серверов** | Клиент должен подключиться именно к валидному серверу, а не к honeypot | Высокий |
| **A-4 Целостность APK** | Подлинность и неизменность бинарного кода, который устанавливает пользователь | Критично |
| **A-5 Availability обхода** | Сервис должен продолжать работать после новых блокировок | Высокий |
| **A-6 Приватный ключ Ed25519** | Ключ подписи подписок; компрометация — атака на всю базу серверов | Критично |
| **A-7 Release keystore** | Ключ подписи APK; компрометация — возможность выпустить вредоносный APK под видом Ozero | Критично |
| **A-8 Конфигурация серверов (rawConfig)** | UUID, public key Reality, SNI, shortId — достаточно для попытки fingerprint сервера | Средний |
| **A-9 Локальные логи подключений** | connection_log в Room — метаданные о сессиях пользователя | Средний |

---

## 3. Attackers (STRIDE Actors)

### A01 — РКН Passive DPI (ТСПУ)

- **Тип**: государственный пассивный наблюдатель
- **Capabilities**: глубокая инспекция пакетов на магистральных каналах РФ, ML-классификаторы трафика (JA3/JA4 fingerprint, энтропия payload, packet timing, flow statistics), база сигнатур всех известных VPN-протоколов
- **Motivation**: подчинение закону о суверенном интернете, исполнение реестра блокировок
- **Budget**: государственный, неограниченный
- **Access level**: passively sees all packets traversing Russian backbone ISPs; не может модифицировать трафик сам по себе, но может направлять команды ISP
- **Ограничения**: не видит payload зашифрованного трафика; не может вскрыть TLS 1.3 без private key

### A02 — РКН Active MITM / ISP enforcement

- **Тип**: государственный активный, действует через обязанных ISP
- **Capabilities**: инъекция TCP RST, selective blackhole (silent drop), DNS-подмена, BGP null-routing отдельных IP-префиксов, QUIC blocking (Drop UDP 443)
- **Motivation**: блокировка сервисов из реестра, обеспечение исполнения приказов
- **Budget**: государственный
- **Access level**: активное вмешательство в транзитный трафик через ТСПУ-оборудование у операторов

### A03 — ISP / Мобильный оператор

- **Тип**: коммерческий, потенциально принуждаемый государством
- **Capabilities**: полная видимость metadata пользователя (IP src/dst, порты, объём, время), SNI из ClientHello, QUIC Initial packet analysis, логи CGNAT-трансляций
- **Motivation**: соответствие законодательству, избежание штрафов
- **Budget**: высокий (инфраструктура оператора)
- **Access level**: все пакеты от устройства до первой внешней точки обмена трафиком; в случае CGNAT — знает реальный номер SIM-карты за NAT-IP

### A04 — Malicious Root (скомпрометированное устройство)

- **Тип**: localy privileged attacker; root-kit, Xposed/Magisk-модули, Frida
- **Capabilities**: полный доступ к памяти приложения через `/proc/self/mem`, перехват JNI-вызовов, hook Java-методов через Xposed, динамический анализ через Frida, дамп keystore при наличии root
- **Motivation**: реверс-инжиниринг алгоритма выбора серверов, кража server configs, обход anti-tamper
- **Budget**: низкий-средний (инструменты публичные)
- **Access level**: полный контроль устройства при root; может обойти sandbox

### A05 — Honeypot Server

- **Тип**: злоумышленник в цепочке подписки; может быть государственным актором или частным
- **Capabilities**: предоставляет работающий VLESS/Hysteria2 прокси, который логирует весь проходящий трафик (src IP пользователя, DNS-запросы, TLS SNI, timing)
- **Motivation**: деанонимизация пользователей, сбор IP-адресов, profiling
- **Budget**: низкий (один VPS)
- **Access level**: видит весь незашифрованный (decrypt VLESS) трафик до HTTPS-слоя приложений, src IP клиента

### A06 — Скомпрометированный канал подписки

- **Тип**: атакующий, захвативший контроль над публичным агрегатором подписок (freefq, V2RayAggregator и др.) или создавший поддельный sub.ozero.app
- **Capabilities**: внедрить вредоносные серверные конфиги в autopull-источники; если захвачен sub.ozero.app — подменить весь signed pool
- **Motivation**: масштабный honeypot, деанонимизация всей пользовательской базы
- **Budget**: средний
- **Access level**: network-level, не имеет доступа к устройству пользователя

### A07 — Supply Chain

- **Тип**: компрометация инструментальной цепочки сборки
- **Capabilities**: модифицированный Go toolchain → вредоносный `libxray.aar`; скомпрометированный gomobile / NDK; вредоносный Gradle plugin (Paranoid); modified byedpi source
- **Motivation**: скрытое внедрение бэкдора в тысячи копий APK
- **Budget**: высокий (nation-state level или APT)
- **Access level**: pre-build; атака происходит до подписания APK

### A08 — GitHub Account Takeover

- **Тип**: атакующий, захвативший GitHub-аккаунт разработчика
- **Capabilities**: публикация поддельного Release с вредоносным APK, замена существующего Release asset, модификация CI/CD workflow
- **Motivation**: mass malware distribution под именем Ozero
- **Budget**: низкий-средний
- **Access level**: GitHub repository level; не имеет release signing key если YubiKey изолирован

---

## 4. STRIDE Таблица Угроз

| ID | Actor | Угроза | STRIDE | Likelihood | Impact | Митигация |
|----|-------|--------|--------|:----------:|:------:|-----------|
| T-01 | A01 РКН Passive DPI | Fingerprint VLESS+Reality по JA3/JA4 паттернам ClientHello | I | High | Critical | uTLS Chrome fingerprint (Xray `"fingerprint": "chrome"`); XHTTP transport маскирует под HTTP/2 CDN трафик |
| T-02 | A01 РКН Passive DPI | ML-классификация flow по entropy, packet size distribution, IAT | I | Medium | High | Port hopping Hysteria2 разбивает flow-ассоциацию; double-hop меняет точку наблюдения |
| T-03 | A02 РКН Active | TCP RST injection на соединение с Entry VPS | D | High | High | TLS 1.3 с Reality — RST не уберёт TLS session; fallback chain в Orchestrator переключает движок |
| T-04 | A02 РКН Active | Selective blackhole Entry VPS IP | D | High | High | StrategyEngine имеет ≥3 Entry кандидата; probe параллельно; автопереключение |
| T-05 | A02 РКН Active | DNS override — подмена A/AAAA для Entry VPS hostname | S | Medium | High | Ozero использует DoH (Xray internal resolver); не полагается на системный DNS для серверных адресов |
| T-06 | A02 РКН Active | Active probe на Entry VPS: запрос без валидного VLESS handshake | S | High | Medium | Reality fallback server: сервер отвечает как обычный HTTPS сайт для неавторизованных соединений |
| T-07 | A03 ISP | Перехват SNI из TLS ClientHello для логирования и блокировки | I | High | High | ESNI/ECH где возможно; Reality не использует реальный SNI цели; QUIC скрывает SNI от DPI |
| T-08 | A03 ISP | Timing correlation: сравнение времени запросов user ↔ exit VPS | I | Medium | Medium | Tor режим для активистов; double-hop добавляет задержку и разрывает прямую корреляцию |
| T-09 | A03 ISP | Metadata leak: объём трафика, время сессий, активность паттерн | I | High | Medium | Без митигации на уровне протокола; Tor randomizes timing; для массового пользователя — не приоритет |
| T-10 | A03 MNO CGNAT | Hysteria2 port hopping ломается при CGNAT (src port ротируется NAT) | D | High | Medium | CgnatDetector → удаляет Hysteria2 из кандидатов при обнаружении CGNAT; fallback на VLESS |
| T-11 | A04 Malicious Root | Frida injection в App process: перехват движка, кража server configs | T | Medium | High | Anti-Frida в JNI_OnLoad (scan `/proc/self/maps`); при детекте `_exit(0)` |
| T-12 | A04 Malicious Root | TracerPid attach (gdb/lldb): отладка native кода для понимания алгоритма | T | Medium | Medium | `is_debugger_attached()` проверяет TracerPid в `/proc/self/status`; при pid≠0 — `_exit(0)` |
| T-13 | A04 Malicious Root | Патч `/proc/self/mem`: runtime замена кода anti-tamper проверок | T | Low | High | ptrace guard (`prctl(PR_SET_DUMPABLE, 0)`) + `install_ptrace_guard()` блокирует внешний доступ к памяти |
| T-14 | A04 Malicious Root | Xposed/Magisk hook Java методов: перехват SubscriptionVerifier.verify() | T | Medium | High | Критичная верификация реализована в native (C), не в JVM; R8 obfuscation затрудняет hooking |
| T-15 | A04 Malicious Root | Дамп Room DB через root file access: извлечение server configs и connection logs | I | High | Medium | `android:allowBackup="false"` + `dataExtractionRules` пустой; при root — game over (out of scope) |
| T-16 | A05 Honeypot | Вредоносный сервер в публичном пуле подписок логирует src IP пользователя | I | High | High | Ed25519 verify отклоняет неподписанные источники; автопул фильтруется `isLiveIn2026()`; community revocation |
| T-17 | A05 Honeypot | Honeypot видит DNS-запросы пользователя через SOCKS (DNS через прокси) | I | High | High | DNS только через Xray internal DoH resolver; DNS-запросы не идут напрямую через SOCKS к серверу |
| T-18 | A05 Honeypot | Корреляция: honeypot entry знает src IP, honeypot exit знает dst — double deanon | I | Medium | High | Double-hop разделяет эту информацию между двумя серверами; оба должны быть скомпрометированы одновременно |
| T-19 | A06 Subscription channel | Подмена серверного пула: атакующий заменяет servers.json на sub.ozero.app | S | Low | Critical | Certificate pinning на `sub.ozero.app`; Ed25519 sig верифицируется клиентом; private key offline |
| T-20 | A06 Subscription channel | Компрометация публичного агрегатора (freefq, V2RayAggregator): вброс honeypot серверов | S | High | High | Публичные источники не получают Ed25519-подпись; `sourceVerified=false`; используются с меньшим приоритетом и доп. probe |
| T-21 | A06 Subscription channel | DDoS на sub.ozero.app: пользователи не получают обновление подписки | D | Medium | Medium | Клиент кеширует последнюю валидную подписку локально; fallback на публичные источники |
| T-22 | A07 Supply Chain | Бэкдор в libxray.aar (gomobile build): вредоносная Go-библиотека | T | Low | Critical | Сборка из исходников с пин-хэшем; не использовать prebuilt без верификации SHA256 |
| T-23 | A07 Supply Chain | Скомпрометированный Paranoid Gradle plugin: строки "шифруются" с известным ключом | T | Low | High | Использовать только верифицированную версию плагина с pin; рассмотреть самописную реализацию |
| T-24 | A08 GitHub Account | Публикация поддельного Release APK под видом новой версии Ozero | S | Medium | Critical | AppUpdater верифицирует Ed25519 sig APK (отдельный ключ, хардкод в app); SHA256 из trusted источника |
| T-25 | A08 GitHub Account | Модификация CI workflow для кражи release keystore secret | E | Low | Critical | Keystore в GitHub encrypted secrets; workflow pinned на конкретный SHA; branch protection на main |
| T-26 | A04 Malicious Root | Обход anti-emulator: запуск в реальном устройстве с патченным `/proc/self/status` | T | Low | Medium | Многоуровневая эвристика: build.prop проверки + `/dev/qemu` + timing attack на qemu-специфичные инструкции |
| T-27 | A01/A02 РКН | DNS leak через системный resolver при race condition в TUN setup | I | Medium | High | TUN устанавливает `addDnsServer("127.0.0.1")` до первого пакета; DNS-защита активна с момента `establish()` |
| T-28 | A01/A02 РКН | IPv6 leak: трафик уходит мимо TUN по IPv6 если не заблокирован | I | Medium | High | `addRoute("::", 0)` blackhole если движок не поддерживает IPv6; явная конфигурация в VpnService |
| T-29 | A04 Malicious Root | Патч APK (переподпись): установка модифицированного APK с backdoor | T | Medium | Critical | native `verify_apk_signature()`: SHA256 signing cert эмбеддится в .so; мисматч → `_exit(0)` |
| T-30 | A03/A05 | Packet-length/timing fingerprint VLESS+Reality по характерным паттернам | I | Medium | Medium | XHTTP transport паддингом и chunked encoding маскирует длины; Hysteria2 UDP рандомизирует |
| T-31 | A04 Malicious Root | Намеренный обход VPN: Intent injection через exported Activity/Service — запуск без TUN | E | Low | High | Все компоненты `exported="false"` или защищены `signature` permission; VpnService не exported |
| T-32 | A04 Malicious Root | Binder attack на VpnService: вызов внутренних методов через Binder reflection | E | Low | Medium | Binder interface не published; VpnService не реализует публичный AIDL; только internal Intent actions |
| T-33 | A06 Subscription | Компрометация Ed25519 private key: атакующий подписывает вредоносные серверы | S | Low | Critical | Private key на air-gapped machine + hardware token; key rotation procedure → новый APK с новым pub key |
| T-34 | A08 GitHub | Удаление/замена существующего GitHub Release asset: замена APK на вредоносный | T | Medium | High | Ed25519 sig APK: замена APK без нового sig → верификация провалится; sig создаётся только с YubiKey |
| T-35 | A03 ISP | Блокировка QUIC (UDP 443): Hysteria2 перестаёт работать | D | High | High | Fallback chain: VLESS+Reality+XHTTP (TCP); ByeDPI как последний рубеж |

---

## 5. Митигации — Детализация

### M-01 Ed25519 верификация подписки

Публичный ключ (32 байта, Ed25519) захардкоден в `OzeroConfig.kt` как base64-константа. При получении `servers.json` от `sub.ozero.app` клиент вызывает `SubscriptionVerifier.verify(raw: ByteArray, signature: ByteArray)` через BouncyCastle `Ed25519Signer`. Если подпись не валидна — payload отбрасывается полностью, сервера не добавляются в пул. Серверы из публичных источников (freefq и др.) получают `sourceVerified=false` и используются с пониженным приоритетом. Приватный ключ генерируется на air-gapped машине и никогда не покидает офлайн-носитель.

Покрывает: T-19, T-20, T-33.

### M-02 APK Signature Check (native)

В `JNI_OnLoad` модуля `security` вызывается `verify_apk_signature(JavaVM *vm)`. Функция через JNI читает `PackageManager.getPackageInfo()` с флагом `GET_SIGNING_CERTIFICATES`, извлекает SHA256-хэш сертификата и сравнивает с эмбеддированной константой в `.so`. При несовпадении — `_exit(0)`. Эмбеддированный хэш компилируется в нативный код, а не хранится в Java/Kotlin, что делает его недоступным через стандартный Xposed hook.

Покрывает: T-29.

### M-03 Certificate Pinning на sub.ozero.app

`network_security_config.xml` содержит `<pin-set>` с SHA256 хэшем листового сертификата и backup pin для промежуточного CA. OkHttp клиент настроен через `CertificatePinner` с теми же пинами. Любое соединение с `sub.ozero.app`, где сертификат не совпадает, разрывается до передачи данных. Помимо этого — payload всегда верифицируется Ed25519 (M-01), создавая двойную защиту.

Покрывает: T-19, T-06.

### M-04 Anti-Frida / Anti-Debug

При `JNI_OnLoad` последовательно выполняются:
1. `is_debugger_attached()`: чтение `TracerPid` из `/proc/self/status`; ненулевое значение → `_exit(0)`
2. `is_frida_in_memory()`: сканирование `/proc/self/maps` на строки `"frida"`, `"gadget"`, `"linjector"`; найдено → `_exit(0)`
3. `install_ptrace_guard()`: вызов `prctl(PR_SET_DUMPABLE, 0)` + попытка самоприкрепления через ptrace, блокирующая внешний ptrace
Проверки выполняются при каждом запуске нативного модуля, не только при первой загрузке.

Покрывает: T-11, T-12, T-13.

### M-05 Anti-Emulator

`is_emulator()` проверяет:
- `android.os.Build.FINGERPRINT` содержит `"generic"`, `"vbox"`, `"genymotion"`, `"sdk_`
- `android.os.Build.MODEL` содержит `"Emulator"`, `"Android SDK"`
- наличие `/dev/qemu_pipe` или `/dev/vboxguest`
- timing-based эвристика: `RDTSC` инструкция на qemu возвращает характерные значения

При детекте эмулятора приложение не запускается. Это дополнительный барьер для автоматизированного reverse-engineering в эмуляторных sandbox.

Покрывает: T-26.

### M-06 R8 Full Mode + Obfuscator-LLVM

R8 запускается с `-optimizationpasses 5` и `overloadaggressively`. Все символы переименовываются по словарю (`obfuscation-dict.txt`). Нативные `.so` компилируются с Obfuscator-LLVM флагами `-mllvm -fla` (control flow flattening), `-mllvm -bcf` (bogus control flow), `-mllvm -sub` (instruction substitution). Результат: реверс-инжиниринг требует значительных ресурсов; структура кода неочевидна без выполнения.

Покрывает: T-11, T-12, T-14, T-23.

### M-07 String Encryption (AES-128)

Paranoid Gradle plugin на этапе компиляции заменяет строковые константы (особенно URL, ключи, пути) шифрованными байт-массивами. Дешифрование происходит лениво в runtime через native функцию. Строки не появляются в DEX в открытом виде. Ключ дешифрования эмбеддирован в native `.so`, не в Java/Kotlin classpath.

Покрывает: T-11 (утечка server configs через string scanning).

### M-08 Internal Kill-Switch (Fail-Closed TUN)

`OzeroVpnService` устанавливает TUN-интерфейс с `addRoute("0.0.0.0", 0)` + `addRoute("::", 0)` до запуска Engine. Если Engine упал или SOCKS backend стал недоступен, новые TCP-соединения через TUN получают `Connection Refused`. Трафик не уходит мимо VPN (не leak в открытый интернет). TUN не пересоздаётся при перезапуске Engine — только Engine + hev-socks5-tunnel рестартуют. Это устраняет race condition window между уничтожением и пересозданием TUN.

Покрывает: T-27, T-28, T-03.

### M-09 DNS через Xray Internal Resolver (No-Leak)

`VpnService.Builder.addDnsServer("127.0.0.1")` направляет все DNS-запросы приложений на loopback. Xray internal DNS resolver обрабатывает их через DoH (DNS-over-HTTPS) к Cloudflare `1.1.1.1` или Google `8.8.8.8` через уже-зашифрованный туннель. Системный DNS провайдера (ISP) не получает ни одного DNS-запроса пользователя.

Покрывает: T-17, T-27.

### M-10 IPv6 Blackhole

Если активный Engine не поддерживает IPv6 (`EngineCapabilities.supportsIpv6 == false`), `VpnService` добавляет `addRoute("::", 0)` без соответствующего IPv6 backend. Весь IPv6-трафик уходит в TUN но не может быть доставлен → эффективный blackhole. Пользователь не имеет IPv6-связности, но и leak невозможен. Для движков с IPv6-поддержкой IPv6 включается явно через `addAddress("fd00:ozero::1", 128)`.

Покрывает: T-28.

### M-11 uTLS Fingerprint (JA3/JA4 Protection)

Все TLS-соединения Xray-core используют `"fingerprint": "chrome"` в `tlsSettings`. Это заставляет Xray использовать utls-библиотеку, которая воспроизводит точный ClientHello Chrome (extensions порядок, cipher suites, GREASE values). JA3/JA4 fingerprint идентичен браузерному Chrome → DPI не может отличить Ozero-трафик от Chrome HTTPS по fingerprint.

Покрывает: T-01.

### M-12 Port Hopping Hysteria2

Hysteria2 в Xray-core поддерживает `portRange` в конфиге outbound. При каждой новой UDP-сессии клиент случайно выбирает порт из диапазона (например, 20000-50000). Это разрывает flow-ассоциацию DPI: сигнатура «Hysteria2 на порту X» не применима, когда порт меняется. Дополнительно усложняет port-based blacklisting.

Покрывает: T-02, T-35.

### M-13 Double-Hop Architecture

Xray `proxySettings.tag` в outbound-конфиге создаёт цепочку: App → Entry VPS (РФ) → Exit VPS (иностранный). Entry VPS видит src IP пользователя и зашифрованный VLESS трафик, но не знает dst (только Exit знает). Exit VPS знает dst (итоговый сайт), но видит src = Entry VPS, не пользователя. Для корреляционной атаки необходимо скомпрометировать оба VPS одновременно.

Покрывает: T-08, T-18.

### M-14 Key Rotation + Revocation

При компрометации Ed25519 signing key процедура:
1. Генерируется новая пара ключей на air-gapped машине
2. Выпускается экстренный APK с новым `SUBSCRIPTION_PUBLIC_KEY_B64` в `OzeroConfig.kt`
3. Переходный период (90 дней, см. `docs/key-rotation.md`): `sub.ozero.app` отдаёт подписки, подписанные обоими ключами; клиент принимает любую валидную
4. По истечении периода — старый ключ полностью отзывается
5. Security advisory публикуется через GitHub Security Advisories

Аналогичная процедура для release keystore: новый keystore → новое приложение (смена package name или пересмотр distribution). Backup keystore хранится на двух физически разделённых зашифрованных офлайн-носителях.

Покрывает: T-33, T-07.

### M-15 Ed25519 APK Verification (Self-Update)

`AppUpdater.downloadAndVerify()` после скачивания APK-файла проверяет:
1. SHA256 файла совпадает с `UpdateInfo.apkSha256` (получен через GitHub Releases API по HTTPS)
2. Ed25519 подпись `.sig` файла верифицируется отдельным hardcoded public key для APK-подписания (отличным от subscription key)

Если верификация не прошла — файл удаляется, установка не запускается. Это защищает от T-24, T-34 даже при компрометации GitHub Releases страницы.

Покрывает: T-24, T-34.

---

## 6. Residual Risks

### R-01 WebRTC Leak в браузере

WebRTC в браузере (Chrome/Firefox) может раскрыть реальный IP пользователя через STUN-запросы, которые обходят системный VPN на некоторых Android-версиях. Это outside of control приложения: WebRTC работает на уровне браузерного движка. **Митигация**: экран Diagnostics показывает предупреждение «Браузер может раскрывать ваш реальный IP через WebRTC. Используйте браузер с отключённым WebRTC» с инструкцией для Firefox.

### R-02 ML Traffic Analysis (ТСПУ)

Если РКН применяет статистический ML-анализ всего flow (не только fingerprint заголовков), то даже уникальный трафик Reality+XHTTP может быть классифицирован как «не похожий на обычный HTTPS» по паттернам объёма и timing. **Митигация**: регулярная смена транспортов (pivot) при появлении новых блокировок; CDN fronting как опция для XHTTP. Полностью не устранимо без Tor.

### R-03 Honeypot в Подписке (Community)

Серверы из публичных агрегаторов (`freefq`, `V2RayAggregator`) не имеют Ed25519-подписи Ozero. Даже с фильтрацией `isLiveIn2026()` honeypot-сервер может пройти probe-проверку (он работает, он просто логирует). **Митигация**: Community-репорт через GitHub Issues → ручная проверка → добавление в blocklist следующей версии подписки. Signed pool от Ozero является основным источником; публичные — вспомогательными.

### R-04 Root User (Game Over)

Пользователь с root-доступом или злоумышленник, получивший root на устройстве, имеет полный контроль: может обойти любую native защиту через прямую запись в `/proc/self/mem`, отключить SELinux, дампить Keystore. Anti-frida и anti-debug создают барьер для автоматизированных атак, но не для мотивированного атакующего с root. **Это out of scope**: приложение не может защититься от владельца устройства.

### R-05 Cold Boot / Физическое Изъятие

При изъятии незашифрованного устройства содержимое Room DB (server configs, connection logs) доступно. **Митигация частичная**: `android:allowBackup="false"` блокирует adb backup; полная защита требует шифрования БД на уровне приложения (SQLCipher) — рекомендуется для v1.1. При включённом FDE/FBE Android защита обеспечивается ОС.

### R-06 Coercion Разработчика

Принудительное требование к разработчику подписать вредоносное обновление или раскрыть private key. **Out of scope**: частично смягчается тем, что Ed25519 private key хранится на air-gapped machine без сетевого доступа и потребует физического присутствия для использования. Ключ не может быть получён удалённо.

### R-07 Correlation Attack через Timing (Tor)

Глобальный наблюдатель (который контролирует как вход в Tor-сеть, так и exit) теоретически может выполнить timing correlation attack даже через Tor. Для пользователей персоны A03 (активисты) это residual risk. **Митигация**: рекомендация использовать Tor только для высокочувствительных операций; awareness в документации.

---

## 7. Мониторинг и Incident Response

### 7.1 Каналы обратной связи

- **GitHub Issues** (публичный): сообщения о проблемах с работоспособностью и non-security баги
- **GitHub Security Advisories** (приватный): responsible disclosure канал для security researcher-ов
- **SECURITY.md** (создать): PGP-ключ разработчика для шифрованного disclosure, email для конфиденциальных репортов
- **Telegram-канал** (только push-уведомления): экстренные security advisory для пользователей

### 7.2 Security Advisory Pipeline

1. Researcher находит уязвимость → отправляет на email/PGP из SECURITY.md
2. Разработчик подтверждает получение в течение 24 часов
3. Оценка severity по CVSS 3.1
4. Если Critical/High: hotfix branch → сборка → тестирование → релиз
5. Публикация GitHub Security Advisory с CVE (при наличии) после релиза fix

### 7.3 SLA на Critical Vulnerabilities

| Severity | Время до патча | Время до disclosure |
|----------|:--------------:|:-------------------:|
| Critical | 48 часов | После патча (max 7 дней) |
| High | 7 дней | После патча (max 30 дней) |
| Medium | 30 дней | После патча |
| Low | Следующий релиз | Публично |

### 7.4 Процедура Компрометации Ключей

**Ed25519 subscription key скомпрометирован**:
1. Немедленно: sub.ozero.app перестаёт раздавать подписанные пулы (503)
2. Air-gapped machine: генерация новой пары ключей
3. Экстренный APK с новым pub key — сборка и подпись через YubiKey
4. Публикация GitHub Release + Security Advisory
5. Переходный период 90 дней с двойной подписью (см. `docs/key-rotation.md`)
6. Уведомление пользователей через Telegram

**Release keystore скомпрометирован**:
1. Это критически: злоумышленник может выпускать APK под именем Ozero
2. Смена package name: `ru.ozero.app` → `ru.ozero.v2` (разрыв с предыдущими установками)
3. Максимально широкое публичное оповещение о компрометации
4. F-Droid удаляет старый package

### 7.5 Responsible Disclosure

Принимается coordinated disclosure с embargo до 90 дней для Critical уязвимостей. PGP fingerprint разработчика и контакт для disclosure публикуются в `SECURITY.md` (файл создаётся в фазе 0 разработки). Bug bounty не предусмотрен в MVP, планируется при наборе финансирования.
