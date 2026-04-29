package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServiceLifecycleTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `onCreate переопределён и вызывает super`() {
        assertTrue(
            source.contains("override fun onCreate()"),
            "OzeroVpnService должен переопределять onCreate() для диагностики Hilt инжекта.",
        )
        val onCreateBody = source.substringAfter("override fun onCreate()").substringBefore("\n    private val")
        assertTrue(
            onCreateBody.contains("super.onCreate()"),
            "onCreate обязан вызывать super.onCreate() — иначе Hilt не запустит инжект.",
        )
    }

    @Test
    fun `onCreate логирует before и after super через PersistentLoggers`() {
        val onCreateBody = source.substringAfter("override fun onCreate()").substringBefore("\n    private val")
        assertTrue(
            onCreateBody.contains("onCreate before super"),
            "onCreate должен логировать момент ДО super.onCreate() для диагностики падений Hilt.",
        )
        assertTrue(
            onCreateBody.contains("onCreate after super"),
            "onCreate должен логировать момент ПОСЛЕ super.onCreate() (Hilt инжект завершён).",
        )
        val beforeIdx = onCreateBody.indexOf("before super")
        val superIdx = onCreateBody.indexOf("super.onCreate()")
        val afterIdx = onCreateBody.indexOf("after super")
        assertTrue(
            beforeIdx in 0 until superIdx && superIdx < afterIdx,
            "Порядок логов в onCreate должен быть: before super → super.onCreate() → after super.",
        )
    }

    @Test
    fun `onStartCommand имеет entry-log первой строкой`() {
        val onStartBody = source.substringAfter("override fun onStartCommand")
            .substringBefore("private fun startVpn()")
        val entryIdx = onStartBody.indexOf("onStartCommand entry action=")
        val legacyIdx = onStartBody.indexOf("Log.i(TAG, \"onStartCommand action=")
        assertTrue(
            entryIdx >= 0,
            "onStartCommand должен начинаться с entry-лога через PersistentLoggers " +
                "для диагностики проблем доставки intent.",
        )
        assertTrue(
            legacyIdx > entryIdx,
            "Entry-лог обязан стоять ПЕРЕД остальной логикой и старым Log.i(TAG, ...).",
        )
    }

    @Test
    fun `onStartCommand guard на неинжекченный pipeline возвращает START_NOT_STICKY`() {
        val onStartBody = source.substringAfter("override fun onStartCommand")
            .substringBefore("private fun startVpn()")
        assertTrue(
            onStartBody.contains("::pipeline.isInitialized"),
            "onStartCommand должен проверять `::pipeline.isInitialized` — иначе при сбое " +
                "Hilt graph crash будет неинформативным UninitializedPropertyAccessException.",
        )
        val guardBlock = onStartBody.substringAfter("::pipeline.isInitialized").substringBefore("when (intent?.action)")
        assertTrue(
            guardBlock.contains("stopSelf(startId)"),
            "Guard на pipeline обязан вызвать stopSelf(startId) — освободить service slot.",
        )
        assertTrue(
            guardBlock.contains("START_NOT_STICKY"),
            "Guard на pipeline обязан вернуть START_NOT_STICKY — нет смысла рестартовать сломанный graph.",
        )
    }

    @Test
    fun `startVpn preload-ит hev TProxyService на main thread до serviceScope launch`() {
        val startVpnBody = source.substringAfter("private fun startVpn()")
            .substringBefore("private fun stopVpn()")
        val preloadIdx = startVpnBody.indexOf("hev.TProxyService.loadOnce()")
        val launchIdx = startVpnBody.indexOf("serviceScope.launch")
        assertTrue(
            preloadIdx in 0 until launchIdx,
            "startVpn обязан вызвать hev.TProxyService.loadOnce() ДО serviceScope.launch — " +
                "loadLibrary на coroutine worker thread триггерит SIGSEGV в vendor libglnubia.so " +
                "(nubia::Messager::timerLoop) на Nubia/RedMagic. Preload на main thread обходит race " +
                "vendor performance monitor с loader callback. См. v1.0.3 fix.",
        )
    }

    @Test
    fun `startVpn preload TProxyService не обёрнут в withContext или Dispatchers`() {
        val startVpnBody = source.substringAfter("private fun startVpn()")
            .substringBefore("private fun stopVpn()")
        val preloadIdx = startVpnBody.indexOf("hev.TProxyService.loadOnce()")
        check(preloadIdx >= 0) { "preload missing — запусти соседний тест preload-ит-hev сначала" }
        val launchIdx = startVpnBody.indexOf("serviceScope.launch")
        check(launchIdx > preloadIdx) { "preload должен быть до launch" }
        val beforeLaunch = startVpnBody.substring(0, launchIdx)
        val withContextIdx = beforeLaunch.indexOf("withContext(", preloadIdx - 200)
        if (withContextIdx in 0 until preloadIdx) {
            val tail = beforeLaunch.substring(withContextIdx)
            val closes = tail.substring(0, preloadIdx - withContextIdx).count { it == '}' }
            val opens = tail.substring(0, preloadIdx - withContextIdx).count { it == '{' }
            assertTrue(
                closes >= opens,
                "preload TProxyService обязан выполняться на main thread (контекст onStartCommand→startVpn). " +
                    "Не оборачивать в withContext(Dispatchers.IO/Default/...) — иначе vendor SIGSEGV вернётся.",
            )
        }
    }

    @Test
    fun `startVpn preload логирует thread name main looper для диагностики`() {
        val startVpnBody = source.substringAfter("private fun startVpn()")
            .substringBefore("private fun stopVpn()")
        val preloadIdx = startVpnBody.indexOf("hev.TProxyService.loadOnce()")
        check(preloadIdx >= 0) { "preload missing" }
        val window = startVpnBody.substring(maxOf(0, preloadIdx - 500), preloadIdx)
        assertTrue(window.contains("preload begin"), "preload должен иметь begin лог")
        assertTrue(window.contains("Thread.currentThread().name"), "preload должен логировать имя треда")
        assertTrue(
            window.contains("Looper.myLooper()") && window.contains("Looper.getMainLooper()"),
            "preload должен логировать isMain — диагностика Nubia race",
        )
    }

    @Test
    fun `startVpn preload логирует timing и libraryLoaded после loadOnce`() {
        val startVpnBody = source.substringAfter("private fun startVpn()")
            .substringBefore("private fun stopVpn()")
        val preloadIdx = startVpnBody.indexOf("hev.TProxyService.loadOnce()")
        check(preloadIdx >= 0) { "preload missing" }
        val tail = startVpnBody.substring(preloadIdx, minOf(startVpnBody.length, preloadIdx + 800))
        assertTrue(tail.contains("preload done"), "должен логировать done после loadOnce")
        assertTrue(tail.contains("dt="), "должен логировать timing — отличить мгновенный краш от deadlock")
        assertTrue(tail.contains("libraryLoaded="), "должен логировать результат — успех/нет")
        assertTrue(tail.contains("loadError="), "должен логировать loadError — текст UnsatisfiedLinkError")
    }

    @Test
    fun `stopVpn закрывает TUN fd ДО запуска корутины с pipeline_stop`() {
        val stopVpnBody = source.substringAfter("private fun stopVpn()")
            .substringBefore("\n    internal fun buildTunBuilder")
        val closeFdIdx = stopVpnBody.indexOf("closeTunFd(")
        val launchIdx = stopVpnBody.indexOf("serviceScope.launch")
        assertTrue(
            closeFdIdx in 0 until launchIdx,
            "stopVpn обязан вызвать closeTunFd(...) ДО serviceScope.launch — иначе upstream hev " +
                "worker thread читает оригинальный TUN fd, EOF не приходит, pthread_join в nativeStop " +
                "зависает на минуты (полевой тест Nubia: 59 сек до FORCE STOP).",
        )
    }

    @Test
    fun `stopVpn передаёт fdToClose в closeTunFd через локальную переменную`() {
        val stopVpnBody = source.substringAfter("private fun stopVpn()")
            .substringBefore("\n    internal fun buildTunBuilder")
        assertTrue(
            stopVpnBody.contains("tunFdRef.getAndSet(null)"),
            "stopVpn должен извлечь TUN fd из tunFdRef через getAndSet(null) — атомарно, до launch.",
        )
    }

    @Test
    fun `stopVpn использует Thread+join для блокирующего pipeline_stop`() {
        val stopVpnBody = source.substringAfter("private fun stopVpn()")
            .substringBefore("\n    internal fun buildTunBuilder")
        val threadIdx = stopVpnBody.indexOf("Thread({")
        val runBlockingIdx = stopVpnBody.indexOf("runBlocking {", threadIdx)
        val pipelineStopIdx = stopVpnBody.indexOf("pipeline.stop()", runBlockingIdx)
        val joinIdx = stopVpnBody.indexOf(".join(SHUTDOWN_JOIN_TIMEOUT_MS)", pipelineStopIdx)
        val ordered = threadIdx > 0 &&
            runBlockingIdx > threadIdx &&
            pipelineStopIdx > runBlockingIdx &&
            joinIdx > pipelineStopIdx
        assertTrue(
            ordered,
            "stopVpn обязан запускать pipeline.stop() в Thread с runBlocking + join(SHUTDOWN_JOIN_TIMEOUT_MS) — " +
                "withTimeoutOrNull не отменяет blocking JNI без suspension point, поток висит, stopSelf не вызывается.",
        )
    }

    @Test
    fun `stopVpn force-kill процесс при зависании pipeline_stop`() {
        val stopVpnBody = source.substringAfter("private fun stopVpn()")
            .substringBefore("\n    internal fun buildTunBuilder")
        val isAliveIdx = stopVpnBody.indexOf("shutdown.isAlive")
        val killIdx = stopVpnBody.indexOf("processKiller.kill(Process.myPid())", isAliveIdx)
        assertTrue(
            isAliveIdx in 0 until killIdx,
            "stopVpn обязан вызывать processKiller.kill(Process.myPid()) если shutdown.isAlive после join — " +
                "это единственный способ освободить foreground service slot когда blocking JNI завис, " +
                "иначе процесс висит до OOM/FORCE_STOP (полевой тест Nubia: 37 сек).",
        )
    }

    @Test
    fun `stopVpn использует Handler getMainLooper для stopForeground+stopSelf`() {
        val stopVpnBody = source.substringAfter("private fun stopVpn()")
            .substringBefore("\n    internal fun buildTunBuilder")
        val handlerIdx = stopVpnBody.indexOf("Handler(Looper.getMainLooper()).post")
        val stopForegroundIdx = stopVpnBody.indexOf("stopForeground(", handlerIdx)
        val stopSelfIdx = stopVpnBody.indexOf("stopSelf()", stopForegroundIdx)
        assertTrue(
            handlerIdx > 0 && stopForegroundIdx > handlerIdx && stopSelfIdx > stopForegroundIdx,
            "stopForeground+stopSelf должны выполняться через Handler(Looper.getMainLooper()).post — " +
                "withContext(Dispatchers.Main) убран чтобы не зависеть от coroutine cancellation семантики.",
        )
    }

    @Test
    fun `onDestroy эскалирует force-kill при зависании shutdown`() {
        val onDestroyBody = source.substringAfter("override fun onDestroy()")
            .substringBefore("\n    private fun closeTunFd")
        val isAliveIdx = onDestroyBody.indexOf("shutdown.isAlive")
        val killIdx = onDestroyBody.indexOf("processKiller.kill(Process.myPid())", isAliveIdx)
        assertTrue(
            isAliveIdx in 0 until killIdx,
            "onDestroy обязан вызывать processKiller.kill(Process.myPid()) если shutdown thread жив после join — " +
                "иначе процесс остаётся в зомби-состоянии после Service.onDestroy и блокирует следующий старт.",
        )
    }

    @Test
    fun `ProcessKiller интерфейс присутствует и инжектируется`() {
        assertTrue(
            source.contains("fun interface ProcessKiller"),
            "ProcessKiller должен быть fun interface — для тестируемости через перегрузку поля.",
        )
        assertTrue(
            source.contains("internal var processKiller: ProcessKiller"),
            "OzeroVpnService должен иметь internal var processKiller — overridable из теста.",
        )
        assertTrue(
            source.contains("Process.killProcess(pid)"),
            "default ProcessKiller обязан звать android.os.Process.killProcess(pid) — реальная kill.",
        )
    }
}
