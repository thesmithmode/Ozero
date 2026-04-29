package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroAppBootstrapTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/OzeroApp.kt")
        assertTrue(f.exists(), "OzeroApp.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `OzeroApp инжектит FirstRunBootstrap`() {
        assertTrue(
            source.contains("FirstRunBootstrap"),
            "OzeroApp обязан @Inject FirstRunBootstrap — без него assets/bootstrap-servers.json " +
                "загружается ТОЛЬКО при OnboardingViewModel.onFinish, и при skip онбординга/" +
                "preconnect ServerDao остаётся пустой → candidates=[BYEDPI] only.",
        )
        assertTrue(
            source.contains("@Inject lateinit var firstRunBootstrap: FirstRunBootstrap") ||
                source.contains("@Inject lateinit var firstRunBootstrap : FirstRunBootstrap") ||
                source.contains("@Inject\n    lateinit var firstRunBootstrap: FirstRunBootstrap"),
            "FirstRunBootstrap должен быть инжектирован как lateinit var firstRunBootstrap.",
        )
    }

    @Test
    fun `OzeroApp_onCreate триггерит runIfFirstStart на appScope IO`() {
        val onCreateBody = source.substringAfter("override fun onCreate()").substringBefore("\n    private fun")
        assertTrue(
            onCreateBody.contains("firstRunBootstrap.runIfFirstStart()"),
            "OzeroApp.onCreate должен звать firstRunBootstrap.runIfFirstStart() на cold start, " +
                "не дожидаясь онбординга. Иначе ServerDao пуст при skip онбординга.",
        )
        assertTrue(
            onCreateBody.contains("appScope.launch") || onCreateBody.contains("appScope.launch("),
            "runIfFirstStart должен запускаться через appScope.launch — suspend функция, не main thread.",
        )
    }

    @Test
    fun `OzeroApp_onCreate триггерит HarvestWorker enqueueOneShotExpedited`() {
        val onCreateBody = source.substringAfter("override fun onCreate()").substringBefore("\n    private fun")
        assertTrue(
            onCreateBody.contains("HarvestWorker.enqueueOneShotExpedited") ||
                onCreateBody.contains("enqueueOneShotExpedited"),
            "OzeroApp.onCreate должен enqueueOneShotExpedited HarvestWorker — periodic 6h недостаточно " +
                "для свежей установки. Expedited one-shot подтянет публичные прокси сразу при первом " +
                "появлении сети.",
        )
    }
}
