# URnetwork: режимы Auto / Web / Streaming (WindowType)

## Что это

URnetwork SDK поддерживает 3 режима через `PerformanceProfile.windowType`:

| Mode | rawValue | Sdk константа | Назначение |
|------|----------|---------------|------------|
| **Авто** | `auto` | (нет — не назначать profile) | Дефолт, SDK сам решает |
| **Web** | `quality` | `Sdk.WindowTypeQuality` | Низкая latency для браузинга/чата |
| **Streaming** | `speed` | `Sdk.WindowTypeSpeed` | Большая bandwidth для видео/загрузок |

## Референс — bringyour/network upstream

`.claude/Контекст/android/app/app/src/main/java/com/bringyour/network/ui/connect/ConnectViewModel.kt`:

```kotlin
enum class WindowType(val rawValue: String) {
    AUTO("auto"), QUALITY("quality"), SPEED("speed");

    val displayName: String get() = when (this) {
        AUTO -> "Auto"; QUALITY -> "Web"; SPEED -> "Streaming"
    }
}

// Применение через PerformanceProfile + WindowSizeSettings:
val profile = PerformanceProfile()
profile.windowType = if (selected == QUALITY) Sdk.WindowTypeQuality else Sdk.WindowTypeSpeed
profile.allowDirect = allowDirect

val sizes = WindowSizeSettings()
sizes.windowSizeMin = if (fixedIpSize) 1 else 2
sizes.windowSizeMax = if (fixedIpSize) 1 else 4
profile.windowSize = sizes

deviceManager.performanceProfile = profile  // setter на DeviceLocal SDK
```

UI: SegmentedButton с 3 опциями + дополнительный toggle `fixedIpSize` (доступен только если mode != AUTO).

## План интеграции в Ozero

### Файлы

1. `engine-urnetwork/src/main/java/.../UrnetworkConfigStore.kt`:
   - Добавить `windowType: Flow<UrnetworkWindowType>` + `setWindowType(UrnetworkWindowType)`
   - Добавить `fixedIpSize: Flow<Boolean>` + сеттер
2. `engine-urnetwork/src/main/java/.../UrnetworkWindowType.kt` (новый enum)
3. `engine-urnetwork/src/main/java/.../DataStoreUrnetworkConfigStore.kt`:
   - Persist в DataStore preferences (key=`urnetwork_window_type`, `urnetwork_fixed_ip_size`)
4. `engine-urnetwork/src/main/java/.../RealUrnetworkSdkBridge.kt`:
   - В `start()` после `Sdk.newDeviceLocalWithDefaults` применить performanceProfile если mode != AUTO
   - Добавить публичный метод `setPerformanceProfile(WindowType, Boolean fixedIp)` — вызывается из UI при изменении
5. `app/src/main/java/.../ui/settings/engines/UrnetworkEngineSettingsScreen.kt`:
   - SegmentedButton(Auto/Web/Streaming), toggle "Fixed IP size" (disabled если AUTO)
6. `UrnetworkEngineSettingsViewModel`:
   - StateFlow<UrnetworkWindowType>, экшены onSelectMode/onToggleFixedIp
   - При изменении — пишет в ConfigStore + если bridge connected — вызывает setPerformanceProfile

### SDK API

- `com.bringyour.sdk.Sdk.WindowTypeQuality: String` — JNI константа из gomobile bind
- `com.bringyour.sdk.Sdk.WindowTypeSpeed: String`
- `com.bringyour.sdk.PerformanceProfile()` — конструктор
- `com.bringyour.sdk.WindowSizeSettings()` — конструктор
- `DeviceLocal.setPerformanceProfile(profile)` — применение

### Strings (ru + en)

```xml
<string name="urnetwork_mode_auto">Авто</string>
<string name="urnetwork_mode_quality">Веб</string>
<string name="urnetwork_mode_speed">Стриминг</string>
<string name="urnetwork_fixed_ip_size">Фиксированный IP</string>
<string name="urnetwork_fixed_ip_size_hint">Один peer вместо пула</string>
```

### Тесты

- `UrnetworkWindowTypeTest`: enum mapping
- `DataStoreUrnetworkConfigStoreTest`: read/write windowType + fixedIpSize
- `RealUrnetworkSdkBridgeContractTest`: setPerformanceProfile передаёт правильные SDK константы
- `UrnetworkEngineSettingsViewModelTest`: state transitions, AUTO disables fixedIp

### Ограничения

- `WindowType.AUTO` не вызывает `setPerformanceProfile` — SDK работает с дефолтным profile
- Изменение mode на лету (без VPN restart) требует `device.performanceProfile = profile` — Go SDK поддерживает hot reload
- `windowSizeMin/Max=1` при `fixedIpSize=true` — гарантирует один peer на сессию (стабильный exit IP)

### Estimated effort

~150-200 LOC + 80-100 LOC tests. 1 итерация, отдельный feat-бранч.
