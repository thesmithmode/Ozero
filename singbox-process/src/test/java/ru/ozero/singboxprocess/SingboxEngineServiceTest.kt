package ru.ozero.singboxprocess

import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxEngineServiceTest {

    private val source = File(
        locateRepoRoot(),
        "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt",
    ).readText()

    private val runtimeSource = File(
        locateRepoRoot(),
        "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
    ).readText()

    @Test
    fun `stop waits for runtime shutdown before returning`() {
        val stopBlock = source.substringAfter("override fun stop()")
            .substringBefore("override fun stopAndWait")
        assertTrue(stopBlock.contains("stopAndWait(DEFAULT_STOP_TIMEOUT_MS)"))

        val stopAndWaitBlock = source.substringAfter("private fun stopRuntimeAndWait")
            .substringBefore("override fun getStats()")
        assertTrue(stopAndWaitBlock.contains("withTimeoutOrNull"))
        assertTrue(stopAndWaitBlock.contains("SingboxRuntime.stop()"))
        assertTrue(stopAndWaitBlock.contains("getOrDefault(false)"))
        assertTrue(stopAndWaitBlock.contains("Process.killProcess"))
        assertTrue(stopAndWaitBlock.contains("AtomicBoolean"))
    }

    @Test
    fun `runtime exposes Android trust anchors to libbox`() {
        val first = FakeCertificate(byteArrayOf(1, 2, 3))
        val duplicate = FakeCertificate(byteArrayOf(1, 2, 3))
        val second = FakeCertificate(byteArrayOf(4, 5, 6))
        val trustManagers = arrayOf<TrustManager>(FakeTrustManager(first, duplicate, second))

        val certificates = TrustAnchorPemReader { bytes -> bytes.joinToString("") }.read(trustManagers)

        assertEquals(
            listOf(
                "-----BEGIN CERTIFICATE-----\n123\n-----END CERTIFICATE-----",
                "-----BEGIN CERTIFICATE-----\n456\n-----END CERTIFICATE-----",
            ),
            certificates,
        )
    }

    @Test
    fun `stats do not fake active connections from runtime flag`() {
        val statsBlock = source.substringAfter("override fun getStats()")
            .substringBefore("override fun registerStatusCallback")
        assertTrue(statsBlock.contains("SingboxStats()"))
        assertFalse(statsBlock.contains("activeConnections = if"))
        assertFalse(statsBlock.contains("SingboxRuntime.isRunning()) 1"))
    }

    @Test
    fun `destroy path uses acknowledged stop`() {
        val destroyBlock = source.substringAfter("override fun onDestroy()")
            .substringBefore("companion object")
        assertTrue(destroyBlock.contains("binder.stopAndWait(DEFAULT_STOP_TIMEOUT_MS)"))
        assertTrue(destroyBlock.contains("serviceScope.cancel()"))
    }

    @Test
    fun `Android network bridge uses SFA callback split and concrete libbox properties`() {
        val bridge = File(
            locateRepoRoot(),
            "singbox-process/src/main/java/ru/ozero/singboxprocess/DefaultInterfaceMonitor.kt",
        ).readText()

        assertTrue(bridge.contains("registerBestMatchingNetworkCallback"))
        assertTrue(bridge.contains("connectivity.requestNetwork(request, callback, Handler"))
        assertTrue(bridge.contains("registerDefaultNetworkCallback(callback, Handler"))
        assertTrue(bridge.contains("value.name ="))
        assertTrue(bridge.contains("value.index ="))
        assertTrue(bridge.contains("value.mtu ="))
        assertTrue(bridge.contains("value.dnsServer ="))
        assertTrue(bridge.contains("value.type ="))
        assertTrue(bridge.contains("value.flags ="))
        assertTrue(bridge.contains("NetworkCapabilities.TRANSPORT_VPN"))
        assertTrue(bridge.contains("isCurrent(listener, callback)"))
        assertFalse(bridge.contains("applySetter"))
    }

    @Test
    fun `runtime fails unprotected auto detect sockets`() {
        val runtime = File(
            locateRepoRoot(),
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        ).readText()

        assertTrue(runtime.contains("val protected = protector.protect(fd)"))
        assertTrue(runtime.contains("check(protected) { \"active VPN protect failed\" }"))
    }

    @Test
    fun `profile probe start checks idle state under runtime mutex`() {
        val guardedStart = runtimeSource.substringAfter("suspend fun startIfIdle")
            .substringBefore("private fun startLocked")

        assertTrue(guardedStart.contains("mutex.withLock"))
        assertTrue(guardedStart.contains("if (commandServer != null) return@withLock false"))
        assertTrue(guardedStart.contains("startLocked"))
    }

    @Test
    fun `runtime retains gomobile callbacks until native service stops`() {
        val startBlock = runtimeSource.substringAfter("private fun startLocked")
            .substringBefore("suspend fun stop()")
        val stopBlock = runtimeSource.substringAfter("suspend fun stop()")
            .substringBefore("fun isRunning()")

        assertTrue(startBlock.contains("platformInterface = platform"))
        assertTrue(startBlock.contains("commandServerHandler = handler"))
        assertTrue(stopBlock.contains("releaseServerCallbacks()"))
    }

    @Test
    fun `runtime releases gomobile callbacks on restart and failed start`() {
        val startBlock = runtimeSource.substringAfter("suspend fun start(")
            .substringBefore("suspend fun stop()")
        val releaseBlock = runtimeSource.substringAfter("private fun releaseServerCallbacks()")
            .substringBefore("private fun launchNativeLogSubscription")
        val createBlock = runtimeSource.substringAfter("private fun createCommandServer")
            .substringBefore("private fun launchNativeLogSubscription")

        assertTrue(startBlock.split("releaseServerCallbacks()").size >= 5)
        assertTrue(createBlock.contains("releaseServerCallbacks()"))
        assertTrue(releaseBlock.contains("platformInterface = null"))
        assertTrue(releaseBlock.contains("commandServerHandler = null"))
    }

    @Test
    fun `runtime health requires live gomobile callbacks`() {
        val runningBlock = runtimeSource.substringAfter("fun isRunning(): Boolean")
            .substringBefore("fun getLastStatus()")

        assertTrue(runningBlock.contains("commandServer != null"))
        assertTrue(runningBlock.contains("platformInterface != null"))
        assertTrue(runningBlock.contains("commandServerHandler != null"))
    }

    @Test
    fun `native diagnostics retains gomobile callback while client is connected`() {
        val connectBlock = runtimeSource.substringAfter("private suspend fun connectNativeLogSubscription")
            .substringBefore("private suspend fun stopNativeLogSubscription")
        val disconnectBlock = runtimeSource.substringAfter("private fun disconnectNativeLogClient")
            .substringBefore("private fun handleNativeLogDisconnected")

        assertTrue(runtimeSource.contains("private var logClientHandler: NativeLogHandler? = null"))
        assertTrue(connectBlock.contains("logClientHandler = handler"))
        assertTrue(disconnectBlock.contains("logClientHandler = null"))
    }

    private class FakeTrustManager(
        private vararg val certificates: X509Certificate,
    ) : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(*certificates)

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    }

    private class FakeCertificate(private val bytes: ByteArray) : X509Certificate() {
        override fun getEncoded(): ByteArray = bytes.copyOf()
        override fun verify(key: PublicKey) {}
        override fun verify(key: PublicKey, sigProvider: String) {}
        override fun toString(): String = bytes.contentToString()
        override fun getPublicKey(): PublicKey? = null
        override fun checkValidity() {}
        override fun checkValidity(date: Date) {}
        override fun getVersion(): Int = 3
        override fun getSerialNumber(): BigInteger = BigInteger.ONE
        override fun getIssuerDN(): Principal? = null
        override fun getSubjectDN(): Principal? = null
        override fun getNotBefore(): Date = Date(0)
        override fun getNotAfter(): Date = Date(0)
        override fun getTBSCertificate(): ByteArray = bytes.copyOf()
        override fun getSignature(): ByteArray = byteArrayOf()
        override fun getSigAlgName(): String = ""
        override fun getSigAlgOID(): String = ""
        override fun getSigAlgParams(): ByteArray? = null
        override fun getIssuerUniqueID(): BooleanArray? = null
        override fun getSubjectUniqueID(): BooleanArray? = null
        override fun getKeyUsage(): BooleanArray? = null
        override fun getBasicConstraints(): Int = -1
        override fun getCriticalExtensionOIDs(): Set<String>? = null
        override fun getNonCriticalExtensionOIDs(): Set<String>? = null
        override fun getExtensionValue(oid: String): ByteArray? = null
        override fun hasUnsupportedCriticalExtension(): Boolean = false
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: dir
        }
        return File(System.getProperty("user.dir") ?: ".").absoluteFile
    }
}
