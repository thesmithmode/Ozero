package ru.ozero.app.regression

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class V108RegressionTest {

    @Test
    fun ozeroAppOnCreate_noEagerLoadLibrary() {
        val onCreate = funBody(read(OZERO_APP), "onCreate")
        assertFalse(
            onCreate.contains("loadLibrary"),
            "OzeroApp.onCreate eagerly грузит native — JNI_OnLoad SIGSEGV не ловится " +
                "runCatching и убивает процесс на старте (regression v1.0.5).",
        )
    }

    @Test
    fun ozeroAppAttachBaseContext_wiresCrashLogStore() {
        val attach = funBody(read(OZERO_APP), "attachBaseContext")
        assertTrue(
            attach.contains("CrashLogStore"),
            "attachBaseContext должен создавать CrashLogStore и передавать crashSink " +
                "в installUncaughtHandler — иначе uncaught JVM-крахи не пишутся в filesDir/crashes/.",
        )
    }

    @Test
    fun bootDiagnosticsInstallUncaughtHandler_acceptsCrashSink() {
        val src = read(BOOT_DIAGNOSTICS)
        assertTrue(
            src.contains("crashSink"),
            "installUncaughtHandler должен принимать crashSink: ((Thread, Throwable) -> Unit)?.",
        )
    }

    @Test
    fun vpnManifest_noSpecialUseFGS() {
        val mf = read(VPN_MANIFEST)
        assertFalse(
            mf.contains("specialUse") || mf.contains("FOREGROUND_SERVICE_SPECIAL_USE"),
            "VPN с BIND_VPN_SERVICE авто-получает SYSTEM_EXEMPTED. Явный specialUse " +
                "без Play allowlist → ForegroundServiceStartNotAllowedException на API 34+.",
        )
    }

    @Test
    fun vpnService_noSpecialUseFGSConstant() {
        val src = read(VPN_SERVICE)
        assertFalse(
            src.contains("FOREGROUND_SERVICE_TYPE_SPECIAL_USE"),
            "FGS-тип не задаётся явно для VpnService с BIND_VPN_SERVICE.",
        )
    }

    @Test
    fun vpnService_onStartCommand_hasOuterTryCatch() {
        val body = funBody(read(VPN_SERVICE), "onStartCommand")
        assertTrue(
            body.contains("try {") && body.contains("START_NOT_STICKY"),
            "onStartCommand должен ловить исключения и возвращать START_NOT_STICKY — " +
                "иначе SecurityException/IAE из startForeground/establish убьёт процесс.",
        )
    }

    @Test
    fun vpnService_startForeground_inTryCatch() {
        val body = funBody(read(VPN_SERVICE), "startVpn")
        val tryStart = body.indexOf("try {")
        val sfStart = body.indexOf("startForeground(")
        assertTrue(
            tryStart in 0 until sfStart,
            "startForeground должен быть в try/catch — на API 34+ SecurityException возможен.",
        )
    }

    @Test
    fun vpnService_establish_inTryCatch() {
        val body = funBody(read(VPN_SERVICE), "startVpn")
        val pattern = Regex("try\\s*\\{[^}]*\\.establish\\(\\)", RegexOption.DOT_MATCHES_ALL)
        assertTrue(
            pattern.containsMatchIn(body),
            "Builder.establish() должен быть в try/catch — IAE на устройствах без IPv6.",
        )
    }

    @Test
    fun vpnService_ipv6_isBestEffort() {
        val body = funBody(read(VPN_SERVICE), "buildTunBuilder")
        val pattern = Regex(
            "runCatching\\s*\\{[^}]*addAddress\\(\\s*TUN_ADDRESS_V6",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(body),
            "IPv6 addAddress должен быть в runCatching — fallback IPv4-only.",
        )
    }


    @Test
    fun vpnService_startBlockedWhileStopping() {
        val body = funBody(read(VPN_SERVICE), "startVpn")
        assertTrue(
            body.contains("if (stopping.get())"),
            "startVpn должен блокироваться пока stopVpn не завершил cleanup — иначе race закрывает новый TUN fd.",
        )
    }

    @Test
    fun vpnService_stopCapturesFdBeforeAsyncCleanup() {
        val body = funBody(read(VPN_SERVICE), "stopVpn")
        assertTrue(
            body.contains("val fdToClose = tunFdRef.getAndSet(null)"),
            "stopVpn должен захватывать текущий tunFd до async cleanup, чтобы не закрыть FD новой сессии.",
        )
        assertTrue(
            body.contains("closeTunFd(fdToClose)"),
            "stopVpn должен закрывать только захваченный fdToClose после pipeline.stop.",
        )
    }

    @Test
    fun subscriptionLogs_redactRawUrl() {
        val files = listOf(
            SUB_HTTP_SOURCE,
            SUB_MANAGER,
            DIAG_TESTER,
        )
        val rawUrlInLog = Regex("""Log\.\w\([^)]*\$url\b[^)]*\)""")
        for (path in files) {
            val src = read(path)
            assertFalse(
                rawUrlInLog.containsMatchIn(src),
                "$path логирует raw \$url — может содержать userinfo/token. " +
                    "Использовать LogSanitizer.redactUrl.",
            )
        }
    }

    private fun read(rel: String): String {
        val file = File(projectRoot(), rel)
        check(file.exists()) { "missing source: ${file.absolutePath}" }
        return file.readText()
    }

    private fun projectRoot(): File {
        var d: File? = File(".").canonicalFile
        while (d != null && !File(d, "settings.gradle.kts").exists()) {
            d = d.parentFile
        }
        return d ?: error("settings.gradle.kts not found upward from ${File(".").canonicalPath}")
    }

    private fun funBody(src: String, name: String): String {
        val idx = src.indexOf("fun $name")
        check(idx >= 0) { "fun $name not found" }
        val openIdx = src.indexOf('{', idx)
        check(openIdx >= 0) { "fun $name has no body" }
        var depth = 0
        var i = openIdx
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(openIdx, i + 1)
                }
            }
            i++
        }
        error("unclosed body for $name")
    }

    private companion object {
        const val OZERO_APP = "app/src/main/java/ru/ozero/app/OzeroApp.kt"
        const val BOOT_DIAGNOSTICS = "app/src/main/java/ru/ozero/app/logging/BootDiagnostics.kt"
        const val VPN_SERVICE = "common-vpn/src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt"
        const val VPN_MANIFEST = "common-vpn/src/main/AndroidManifest.xml"
        const val SUB_HTTP_SOURCE =
            "core-subscriptions/src/main/java/ru/ozero/coresubscriptions/OkHttpSubscriptionSource.kt"
        const val SUB_MANAGER =
            "core-subscriptions/src/main/java/ru/ozero/coresubscriptions/SubscriptionManager.kt"
        const val DIAG_TESTER = "app/src/main/java/ru/ozero/app/ui/diag/DiagnosticsTester.kt"
    }
}
