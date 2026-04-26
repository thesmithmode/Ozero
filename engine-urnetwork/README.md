# engine-urnetwork

URnetwork P2P engine для Ozero. Использует децентрализованную P2P сеть
[urnetwork.com](https://urnetwork.com) как fallback когда все primary engines недоступны.

**Upstream SDK**: https://github.com/urnetwork/sdk  
**Java package**: `com.bringyour` (gomobile bind)  
**Priority**: `PRIORITY_URNETWORK = 2` (выше TOR=1, ниже ByeDPI=5)

## Архитектура

```
UrnetworkEngine (implements Engine)
  └── UrnetworkDelegate (interface)
        └── [prod] UrnetworkSdkDelegate — оборачивает com.bringyour.ConnectViewController
        └── [test] MockUrnetworkDelegate — unit-тесты без AAR

UrnetworkCandidateSource (implements CandidateSource)
  └── подключается через DI в StrategyEngine.extraSources
  └── активен только если URNETWORK_ENABLED=true && jwtToken != null
```

## Конфигурация

`EngineConfig.Urnetwork` параметры:
- `jwtToken` — JWT токен авторизации от urnetwork.com (обязателен)
- `apiUrl` — URL API (default: `https://api.urnetwork.com`)  
- `region` — предпочитаемый регион (null = автовыбор)
- `mode` — `"consumer"` (использует P2P) или `"provider"` (шарит трафик)
- `socksPort` — логический порт (default: 10810)

## Пользовательские настройки

В `SettingsKeys`:
- `URNETWORK_ENABLED` — toggle включения (default: `false`)
- `URNETWORK_JWT` — JWT токен (null = не настроен)

## Blockers для gomobile bind

### 1. Go 1.25+ с GOEXPERIMENT=greenteagc

Upstream `urnetwork/sdk` требует Go 1.25.0 (`go 1.25.0` в go.mod) с
`GOEXPERIMENT=greenteagc`. На момент написания (2026-04) Go 1.25 ещё
в release candidate. GitHub Actions `setup-go@v5` с `go-version: '1.25.x'`
должен подхватить RC когда он появится.

**TODO**: обновить workflow когда Go 1.25 stable выйдет.

### 2. Нестандартные зависимости

`urnetwork/sdk` зависит от:
- `github.com/urnetwork/connect` — публичный репо, но требует `go replace`
- `github.com/urnetwork/glog` — собственная ветка glog

Скрипт `tools/build-urnetwork-aar.sh` клонирует их рядом и инжектирует
`go mod edit -replace` перед сборкой.

### 3. -checklinkname=0

Upstream использует `-ldflags "-checklinkname=0"` из-за
[pion/webrtc#2640](https://github.com/pion/webrtc/issues/2640) и
[wlynxg/anet#9](https://github.com/wlynxg/anet/pull/9). Это нормально
для Go 1.25 с новым линкером.

### 4. Статус

**AAR ещё не собран**. До успешного прогона `urnetwork-aar.yml` workflow
`engine-urnetwork/libs/URnetworkSdk.aar` отсутствует. `build.gradle.kts`
модуля подключает AAR только если файл существует (`if (aarFile.exists())`).

Без AAR:
- Unit тесты работают (через mock `UrnetworkDelegate`)
- `UrnetworkEngine` компилируется
- `UrnetworkCandidateSource` работает
- Runtime: если AAR отсутствует, DI не создаёт `UrnetworkEngine`

## E2E тест

`TODO`: `src/androidTest/UrnetworkE2ETest.kt` — instrumented тест:
- Bootstrap engine с тестовым JWT из `URNETWORK_TEST_JWT` env-var
- Connect → GET `https://cloudflare.com/cdn-cgi/trace`
- Assert response содержит `fl=`, `ip=`
- Skip если `URNETWORK_TEST_JWT` не задан

**Блокер E2E**: требует реального AAR + JWT токен. Реализовать после
успешного прогона `urnetwork-aar.yml`.
