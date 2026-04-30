# ByeByeDPI auto-strategy testing — research findings (W3.5.1)

## Источник

`io.github.romanvht.byedpi.activities.TestActivity` (ByeByeDPI v1.7.4).

Path: `.claude/Контекст/ByeByeDPI-v.1.7.4/app/src/main/java/io/github/romanvht/byedpi/activities/TestActivity.kt`.

## Подход в ByeByeDPI

### 1. Strategies — 75 hardcoded

Asset `app/src/main/assets/proxytest_strategies.list` — ровно 75 строк, каждая строка = ByeDPI args. Поддерживает `{sni}` placeholder (default `google.com`).

### 2. Sites — категоризированные

`assets/proxytest_*.sites` — general/youtube/discord/telegram/social/cloudflare/googlevideo. По default 6 generic sites (rutracker, nyaa, speedtest и т.д.).

### 3. Test cycle (sequential)

```
for strategy in strategies:
    updateCmdArgs(strategy.command)
    ServiceManager.start(Mode.Proxy)             // SOCKS5-only, без TUN
    waitForProxyStatus(Running, timeout=3000ms)
    delay(500ms)
    siteChecker.checkSitesAsync(sites, requestsCount=1, timeout=5s, concurrent=20)
    ServiceManager.stop()
    waitForProxyStatus(Halted)
    delay(500ms)
```

После всех 75 strategies — sortByPercentage.

### 4. Success criteria — content-length check

```kotlin
proxy = Proxy(SOCKS, InetSocketAddress(127.0.0.1, port))
connection = URL("https://$site").openConnection(proxy) as HttpURLConnection
declaredLength = connection.contentLengthLong
read inputStream до declaredLength

if (declaredLength <= 0 || actualLength >= declaredLength) → SUCCESS
else → BLOCK   // ТСПУ обрезал tcp stream
```

**Ключевой insight**: success ≠ HTTP 200, а полный response received. ТСПУ block обрезает stream — actualLength < declaredLength → BLOCK.

## Адаптация для Ozero

### Копируем 1:1

- `proxytest_strategies.list` → `app/src/main/assets/byedpi_strategies.list` (75 строк).
- `proxytest_general.sites` → `app/src/main/assets/byedpi_test_sites_general.list` (6 default).
- SiteCheckUtils success criteria.

License: ByeByeDPI = GPL-3.0. Нужен NOTICE attribute или совместимая лицензия Ozero.

### Адаптируем

- ByeDpiEnginePlugin.start/stop вместо ServiceManager.
- DataStore key `byedpi_winning_args` (уже есть в SettingsRepository).
- {sni} default `google.com`.
- Compose UI — progress dialog вместо RecyclerView.

### Подзадачи (W3.5.2-W3.5.6)

- W3.5.2: ByeDpiStrategy data class + Strategies75 asset reader + tests
- W3.5.3: SocksProbeClient (HttpURLConnection + SOCKS Proxy + content-length check)
- W3.5.4: AutoStrategyPicker orchestrator
- W3.5.5: UI — Auto-test button в ByeDpiEngineSettings
- W3.5.6: DI wiring

### Нюансы

1. **SOCKS5-only mode** — ByeDpiEnginePlugin сейчас expect TUN context. Нужен `startSocksOnly()` API. Возможно требует рефакторинг ByeDpiEnginePlugin (проверить).

2. **Sequential test cycle** — 75 × ~5s/strategy = ~6 минут. UI должен показывать progress.

3. **Success criteria fragility** — site без content-length → false-positive SUCCESS. Acceptable risk.

4. **Не conflict с активным VPN** — auto-test требует SOCKS port 1080 свободен. Disable button если VPN connected.

## License note

GPL-3.0 от ByeByeDPI. Ozero LICENSE требует проверки на совместимость. Если копируем 1:1 — атрибутируем в NOTICE.md.
