package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class RemoteAwgRuntimeContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/enginewarp/RemoteAwgRuntime.kt")
        assertTrue(f.exists(), "RemoteAwgRuntime.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `serviceConnection tracked в Volatile поле`() {
        assertTrue(
            source.contains("@Volatile private var serviceConnection: ServiceConnection?"),
            "RemoteAwgRuntime обязан хранить ссылку на ServiceConnection в @Volatile поле — " +
                "иначе локальная переменная теряется после ensureConnected(), а bindService держит её " +
                "в ServiceManager → невозможно unbind при teardown.",
        )
    }

    @Test
    fun `ensureConnected unbind на timeout перед throw`() {
        val ensureBlock = source.substringAfter("private fun ensureConnected")
            .substringBefore("override fun turnOn")
        assertTrue(
            ensureBlock.contains("latch.await(CONNECT_TIMEOUT_S"),
            "ensureConnected обязан ждать onServiceConnected через CountDownLatch с timeout",
        )
        val timeoutSection = ensureBlock.substringAfter("latch.await(CONNECT_TIMEOUT_S")
            .substringBefore("return engine")
        assertTrue(
            timeoutSection.contains("context.unbindService(conn)"),
            "На timeout latch.await обязан unbindService(conn) перед throw — иначе ServiceConnection " +
                "остаётся зарегистрирован в ServiceManager, accumulating N orphan connections на N retries.",
        )
    }

    @Test
    fun `ensureConnected unbind на bindService false`() {
        val ensureBlock = source.substringAfter("private fun ensureConnected")
            .substringBefore("override fun turnOn")
        val boundCheck = ensureBlock.substringAfter("if (!bound)").substringBefore("serviceConnection = conn")
        assertTrue(
            boundCheck.contains("context.unbindService(conn)"),
            "На bindService==false обязан unbindService(conn) перед throw — иначе spurious leak в edge case " +
                "если bindService возвращает false но всё же зарегистрировал conn.",
        )
    }

    @Test
    fun `onBindingDied unbind ServiceConnection`() {
        assertTrue(
            source.contains("override fun onBindingDied"),
            "ServiceConnection обязан имплементировать onBindingDied — иначе на permanent binding death " +
                "(system kill процесса :engine_warp) connection остаётся 'bound' в системной таблице.",
        )
        val onBindingDied = source.substringAfter("override fun onBindingDied").substringBefore("}")
        assertTrue(
            source.substringAfter("override fun onBindingDied").contains("context.unbindService"),
            "onBindingDied обязан unbindService — иначе leak orphan connection. Body=$onBindingDied",
        )
    }

    @Test
    fun `ensureConnected чистит stale serviceConnection при reconnect`() {
        val ensureBlock = source.substringAfter("private fun ensureConnected")
            .substringBefore("override fun turnOn")
        assertTrue(
            ensureBlock.contains("serviceConnection?.let") &&
                ensureBlock.contains("context.unbindService"),
            "ensureConnected обязан unbind старую serviceConnection если есть — иначе при reconnect " +
                "(после onServiceDisconnected) висит stale binding параллельно с новой.",
        )
    }

    @Test
    fun `close метод unbind и обнуляет refs`() {
        assertTrue(
            source.contains("fun close()"),
            "RemoteAwgRuntime обязан иметь close() для explicit teardown — нужен в shutdown path.",
        )
        val closeBlock = source.substringAfter("fun close()").substringBefore("private companion object")
        assertTrue(
            closeBlock.contains("context.unbindService") &&
                closeBlock.contains("serviceConnection = null") &&
                closeBlock.contains("engine = null"),
            "close() обязан unbindService + обнулить serviceConnection + engine — иначе reuse runtime " +
                "после close ведёт к double-bind ошибке от системы.",
        )
    }

    @Test
    fun `onProcessDied callback присутствует в конструкторе`() {
        assertTrue(
            source.contains("private val onProcessDied: () -> Unit"),
            "RemoteAwgRuntime обязан принимать onProcessDied callback — иначе main process не узнаёт " +
                "о крашe Go runtime в :engine_warp до next AIDL timeout (P31 audit).",
        )
    }

    @Test
    fun `binder linkToDeath вызывается в onServiceConnected`() {
        val onConnected = source.substringAfter("override fun onServiceConnected")
            .substringBefore("override fun onServiceDisconnected")
        assertTrue(
            onConnected.contains("binder.linkToDeath"),
            "onServiceConnected обязан вешать DeathRecipient через binder.linkToDeath — " +
                "иначе SIGSEGV/SIGABRT в Go runtime внутри :engine_warp не обнаруживается до next AIDL call.",
        )
        assertTrue(
            onConnected.contains("DeathRecipient"),
            "onServiceConnected обязан создавать IBinder.DeathRecipient inline.",
        )
    }

    @Test
    fun `DeathRecipient вызывает onProcessDied и unbind`() {
        val onConnected = source.substringAfter("override fun onServiceConnected")
            .substringBefore("override fun onServiceDisconnected")
        val recipientBody = onConnected
            .substringAfter("DeathRecipient {")
            .substringBefore("deathRecipient = recipient")
        assertTrue(
            recipientBody.contains("onProcessDied()"),
            "DeathRecipient обязан вызвать onProcessDied() — иначе TunnelController не уведомляется " +
                "о death и killswitch не engaged. Body=$recipientBody",
        )
        assertTrue(
            recipientBody.contains("context.unbindService"),
            "DeathRecipient обязан unbindService — иначе orphan ServiceConnection leak " +
                "в ServiceManager. Body=$recipientBody",
        )
        assertTrue(
            recipientBody.contains("engine = null"),
            "DeathRecipient обязан обнулить engine — иначе stale IWarpEngineProcess вызывает " +
                "DeadObjectException на next AIDL call. Body=$recipientBody",
        )
    }

    @Test
    fun `close unlink DeathRecipient`() {
        val closeBlock = source.substringAfter("fun close()").substringBefore("private companion object")
        assertTrue(
            closeBlock.contains("unlinkDeathRecipient") || closeBlock.contains("unlinkToDeath"),
            "close() обязан отвязывать DeathRecipient — иначе при explicit teardown остаётся " +
                "ссылка на recipient, мешая GC binder reference.",
        )
    }
}
