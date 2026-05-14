---
title: "URnetwork LocationToken — потеря поля bestAvailable"
aliases: [location-token-best-available, urnetwork-best-available-loss]
tags: [urnetwork, serialization, datastore, persistence, bug-pattern]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-13
---

# URnetwork LocationToken — потеря поля bestAvailable

## Key Points

- `LocationToken.fromConnectLocation` не сохранял поле `bestAvailable` → после DataStore round-trip флаг всегда `false`
- `isBestAvailable` после перехода на другой экран и возврата = `false` → UI показывал "выбрана конкретная локация" вместо "авто"
- Fix: добавить `bestAvailable: Boolean = false` (backward-compat default) + обновить `fromConnectLocation` + `toConnectLocation` + `LocationTokenSerializer`

## Details

### Архитектура

`LocationToken` — data class, хранимый в `DataStore` как JSON через `LocationTokenSerializer`. Используется для персистентности выбранной локации между перезапусками приложения.

`ConnectLocation` (SDK) содержит поле `bestAvailable: Boolean` — флаг того, что локация выбрана системой автоматически.

### Симптом

```kotlin
val isBestAvailable = selectedLocation == null || selectedLocation.connectLocationId?.bestAvailable == true
```

После перехода на другой экран и возврата: `selectedLocation != null` (восстановлен из DataStore), но `bestAvailable == false` → `isBestAvailable = false` → UI показывает "выбрана конкретная локация" вместо "авто".

### Root Cause

Однонаправленная конвертация без сохранения флага:

```
ConnectLocation(bestAvailable=true) → fromConnectLocation() → LocationToken(bestAvailable=false)
                                                                        ↑ поле отсутствовало
```

### Fix

Добавить поле в data class с default `= false` для backward-compat со старым JSON:

```kotlin
data class LocationToken(
    val connectLocationId: ConnectLocationId?,
    val name: String?,
    val bestAvailable: Boolean = false,
)
```

Обновить `fromConnectLocation`:

```kotlin
fun fromConnectLocation(location: ConnectLocation) = LocationToken(
    connectLocationId = location.connectLocationId,
    name = location.name,
    bestAvailable = location.bestAvailable,
)
```

Обновить `toConnectLocation`:

```kotlin
fun toConnectLocation() = ConnectLocation(
    connectLocationId = connectLocationId,
    name = name,
    bestAvailable = bestAvailable,
)
```

Обновить `LocationTokenSerializer` — добавить JSON поле `"bestAvailable"` с `orElse(false)` для старых записей.

### Backward Compatibility

`= false` default в data class + `orElse(false)` в Serializer: старый JSON без поля читается без ошибок. После upgrade `isBestAvailable` вернётся в `false` до первого явного авто-выбора пользователем — приемлемо.

## Anti-Pattern

При добавлении нового поля в DTO проверять всю цепочку: `from*` конструктор + `to*` обратная конвертация + Serializer. Упустить любое звено → silent data loss после round-trip.

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] — `selectedLocation`/`ConnectLocation` API
- [[concepts/ip-probe-route-architecture]] — другой контракт URnetwork SDK, StaticLocation для IP-detection

## Sources

- [[daily/2026-05-13.md]]
