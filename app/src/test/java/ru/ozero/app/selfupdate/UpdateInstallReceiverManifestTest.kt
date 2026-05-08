package ru.ozero.app.selfupdate

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UpdateInstallReceiverManifestTest {

    private val manifest by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/AndroidManifest.xml")
        assertTrue(f.exists(), "AndroidManifest.xml не найден: $f")
        f.readText()
    }

    @Test
    fun `UpdateInstallResultReceiver зарегистрирован в манифесте`() {
        assertTrue(
            manifest.contains(".selfupdate.UpdateInstallResultReceiver"),
            "Receiver обязан быть в манифесте — без регистрации PackageInstaller broadcast " +
                "ru.ozero.app.UPDATE_INSTALL_RESULT не доставляется и self-update silently fails",
        )
    }

    @Test
    fun `receiver слушает action UPDATE_INSTALL_RESULT`() {
        val receiverBlock = manifest.substringAfter(".selfupdate.UpdateInstallResultReceiver")
            .substringBefore("</receiver>")
        assertTrue(
            receiverBlock.contains("ru.ozero.app.UPDATE_INSTALL_RESULT"),
            "Receiver обязан фильтровать action ru.ozero.app.UPDATE_INSTALL_RESULT",
        )
    }

    @Test
    fun `receiver exported false (intent доставляется только из этого пакета через PendingIntent)`() {
        val receiverBlock = manifest.substringAfter(".selfupdate.UpdateInstallResultReceiver")
            .substringBefore("</receiver>")
        assertTrue(
            receiverBlock.contains("android:exported=\"false\""),
            "Receiver обязан быть exported=false — broadcast приходит через PendingIntent от PackageInstaller, " +
                "exported=true увеличивает attack surface",
        )
    }
}
