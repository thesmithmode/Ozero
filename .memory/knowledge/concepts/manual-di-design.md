# Manual DI design для :common-vpn (W2.A research)

**Status:** research-only, не implementation. Implementation требует device verify (Hilt graph + VPN start). Юзер автономный — не имеем access к устройству.

## Проблема (P2 invisibility — AUDIT.md)

Текущая архитектура: `OzeroVpnService` — `@AndroidEntryPoint`. Hilt инжектирует `chainOrchestrator`/`tunnelGateway`/`tunnelController` через generated `Hilt_OzeroVpnService.onCreate(super)`.

Если любой Hilt provider бросит exception (например, `System.loadLibrary` в provider chain) → исключение происходит **внутри `super.onCreate()`** → service умирает до того, как первая собственная строчка кода в `onCreate` исполнится. **boot.log пуст**, `BootFileLogger.error` не вызвана. Для пользователя — silent crash без диагностики (документировано в `compose-launchedeffect-crash-invisibility` + `hilt-di-native-library-failure`).

## Цель

VpnService и Engine plugins инстанцируются через **explicit factory** (ServiceLocator pattern) до первого user-visible action. Любая ошибка инициализации движка пишется в `boot.log` ДО throw. Hilt остаётся для UI слоя (`MainActivity`, ViewModels) — там risk инициализации много меньше.

## Constraints (НЕЛЬЗЯ нарушать)

`OzeroVpnServiceLifecycleTest` фиксирует обязательные инварианты:

1. `onCreate` переопределён, вызывает `super.onCreate()`, логи "before/after super".
2. `onStartCommand` entry log с action.
3. `onStartCommand` guard на `chainOrchestrator.isInitialized` → `stopSelf` + `START_NOT_STICKY` если не injected.
4. `startVpn` preload `hev.TProxyService.loadOnce()` ДО `serviceScope.launch` — main thread (CLAUDE.md правило, Nubia v1.0.3 SIGSEGV).
5. `performShutdown` stop order: `chainOrchestrator.stop()` ПЕРЕД `tunnelGateway.stop()` — Phase A2 fix.
6. TUN fd закрывается ПОСЛЕ `tunnelGateway.stop()` в `finally` — Phase A4 fix.
7. `stopVpn`/`onDestroy` НЕ форсируют `processKiller.kill(myPid)` — после Nubia field bug.
8. `onRevoke` переопределён, вызывает `stopVpn()` + `super.onRevoke()`.

**Phase 0 P5 reject (AUDIT.md строка 771):** `Thread+runBlocking+isDaemon=true+Handler(getMainLooper()).post` шаблон зафиксирован тестом. `withTimeoutOrNull` не отменяет blocking JNI без suspension point → leak daemon + stopSelf — единственный безопасный путь после Nubia field bug. Manual DI рефакторинг не должен переписывать shutdown логику.

## Design предложение

### Step 1: VpnServiceLocator (object)

```kotlin
// :common-vpn/src/main/java/ru/ozero/commonvpn/locator/VpnServiceLocator.kt
object VpnServiceLocator {
    @Volatile private var initialized = false
    private val lock = Any()

    lateinit var chainOrchestrator: ChainOrchestrator
        private set
    lateinit var tunnelGateway: HevTunnelGateway
        private set
    lateinit var tunnelController: TunnelController
        private set

    fun init(
        orchestrator: ChainOrchestrator,
        gateway: HevTunnelGateway,
        controller: TunnelController,
    ) {
        synchronized(lock) {
            if (initialized) return
            chainOrchestrator = orchestrator
            tunnelGateway = gateway
            tunnelController = controller
            initialized = true
        }
    }

    fun isInitialized(): Boolean = initialized
}
```

### Step 2: OzeroApp.onCreate инициализирует

```kotlin
// :app OzeroApp.onCreate
override fun onCreate() {
    super.onCreate()
    runCatching {
        VpnServiceLocator.init(
            orchestrator = chainOrchestratorProvider.get(),
            gateway = NativeHevTunnelGateway(this),
            controller = tunnelController,  // singleton
        )
    }.onFailure { t ->
        BootFileLogger.error(TAG, "VpnServiceLocator.init failed", t)
        // не throw — UI всё равно может работать
    }
}
```

Каждый provider обёрнут в `runCatching` — exception → `BootFileLogger.error` пишется на диск **сразу**. Boot.log заполнится прежде чем service запустится.

### Step 3: OzeroVpnService без @AndroidEntryPoint

```kotlin
class OzeroVpnService : android.net.VpnService() {

    private val chainOrchestrator: ChainOrchestrator
        get() = VpnServiceLocator.chainOrchestrator
    private val tunnelGateway: HevTunnelGateway
        get() = VpnServiceLocator.tunnelGateway
    private val tunnelController: TunnelController
        get() = VpnServiceLocator.tunnelController

    override fun onCreate() {
        BootFileLogger.info(TAG, "onCreate before super")
        super.onCreate()
        BootFileLogger.info(TAG, "onCreate after super")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BootFileLogger.info(TAG, "onStartCommand action=${intent?.action}")
        if (!VpnServiceLocator.isInitialized()) {
            BootFileLogger.error(TAG, "VpnServiceLocator not initialized — OzeroApp.onCreate failed")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // существующая логика без изменений
    }
}
```

LifecycleTest всё ещё пройдёт — guard на `chainOrchestrator.isInitialized` заменяется на `VpnServiceLocator.isInitialized()`. Семантика та же, только source of truth.

### Step 4: Тест invariant

```kotlin
class VpnServiceManualDiTest {
    @Test fun `boot_log содержит причину когда engine init fails`() {
        // mock System.loadLibrary throw → VpnServiceLocator.init catch
        // assert BootFileLogger содержит "loadLibrary failed: ..."
    }

    @Test fun `OzeroVpnService не использует AndroidEntryPoint`() {
        val src = File("src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt").readText()
        assertFalse(src.contains("@AndroidEntryPoint"))
        assertFalse(src.contains("@Inject"))
    }
}
```

## Trade-offs

**Pro:**
- Boot.log заполняется ДО любого user-visible action.
- Явный init pipeline — легче дебажить.
- Hilt не нужен в multi-process (W2.5 process isolation отдельный win).

**Contra:**
- Singleton object — не идеально для тестов, нужен reset между ними.
- ChainOrchestrator/TunnelGateway — теперь два конструктор пути (Hilt + manual factory). Дубликат.
- Любая ошибка в `OzeroApp.onCreate` теперь fатальная для VPN — Activity всё ещё работает, но кнопка connect → silent fail (хотя bootlog заполнен).

## Decomposition (когда юзер вернётся)

Cледует распарсить W2.2 на:
- W2.2.1 VpnServiceLocator class + tests
- W2.2.2 OzeroApp.onCreate init pipeline + boot.log catch
- W2.2.3 OzeroVpnService deannotation + getter pattern
- W2.2.4 LifecycleTest update (тот же body, иной guard expression)
- W2.2.5 NoHiltAnnotationsTest sentinel

Каждый шаг atomic, isolated commit. Device verify нужен после W2.2.3 (manual smoke test connect).

## Соотношение с robolectric-hilt-eager-init-trap

Кажется противоречием: эта статья предписывает **eager** init в `OzeroApp.onCreate` через `VpnServiceLocator.init` (для production crash visibility), а [[concepts/robolectric-hilt-eager-init-trap]] предписывает **lazy** init в `@Inject` service fields (для Robolectric NPE robustness). Это разные слои:

- **manual-di-design** — VPN service init pipeline (production crash visibility). Eager init в `OzeroApp.onCreate` обеспечивает что `BootFileLogger.error` пишется на диск ДО любого user-visible action.
- **robolectric-hilt-eager-init-trap** — `@Inject` field init внутри `@HiltAndroidApp` graph. `by lazy` нужен на `@Inject` полях service-классов, потому что Robolectric не заполняет `applicationInfo.nativeLibraryDir` → NPE при field init.

`VpnServiceLocator.init` в `OzeroApp.onCreate` НЕ касается `@Inject` полей — он создаёт factory-instances вручную. Lazy field initialization внутри `@Inject` services продолжает применяться (Robolectric isolation).

## References

- AUDIT.md P2: `chainOrchestrator not injected` was fixed как guard в onStartCommand.
- AUDIT.md P5 reject: shutdown шаблон фиксирован тестом, не трогать.
- [[concepts/hilt-di-native-library-failure]] — описание исходной проблемы.
- [[concepts/compose-launchedeffect-crash-invisibility]] — параллельная invisibility category.
- [[concepts/robolectric-hilt-eager-init-trap]] — комплементарная стратегия для testing layer.
