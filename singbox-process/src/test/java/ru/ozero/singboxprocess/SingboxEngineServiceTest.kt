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

    private class FakeTrustManager(
        private vararg val certificates: X509Certificate,
    ) : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = certificates.copyOf()

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
