package ru.ozero.singboxsubscription

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.VLESSBean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RawUpdaterStableIdentityTest {

    @Test
    fun `stable identity keys cover standard unknown and corrupted beans`() {
        val standard = object : StandardV2RayBean() {}.apply {
            name = "Standard"
            serverAddress = " std.example.com "
            serverPort = 443
            uuid = " uuid "
            type = " ws "
            security = " tls "
            sni = " sni.example.com "
            host = " host.example.com "
            path = " /ws "
            grpcServiceName = " grpc "
            earlyDataHeaderName = " ed "
            splithttpMode = " auto "
            headerType = " none "
            mKcpSeed = " seed "
            quicSecurity = " aes "
            quicKey = " key "
            alpn = " h2 "
            utlsFingerprint = " chrome "
            realityPublicKey = " pub "
            realityShortId = " sid "
        }
        val unknown = object : ru.ozero.singboxfmt.AbstractBean() {
            init {
                name = "Unknown"
                serverAddress = "unknown.example.com"
                serverPort = 8443
            }
        }

        val standardKeys = stableBeanKeysForTest(standard)
        val unknownKeys = stableBeanKeysForTest(unknown)
        val vlessKeys = stableIdentityKeysForTest(
            VLESSBean().apply {
                serverAddress = "vless.example.com"
                serverPort = 443
                uuid = "vless-uuid"
            },
        )
        val corruptedKeys = corruptedStableIdentityKeysForTest(byteArrayOf(9, 8, 7), groupId = 3L)
        val defaultGroupCorruptedKeys = corruptedStableIdentityKeysForTest(byteArrayOf(1, 2, 3))

        assertEquals("uuid=uuid", standardKeys.first)
        assertTrue(standardKeys.second.contains("type=ws"))
        assertTrue(unknownKeys.first.contains("blob="))
        assertEquals("", unknownKeys.second)
        assertTrue(vlessKeys.first.contains("vless.example.com|443|uuid=vless-uuid"))
        assertTrue(corruptedKeys.first.startsWith("3|0|"))
        assertTrue(corruptedKeys.second.endsWith("|"))
        assertTrue(defaultGroupCorruptedKeys.first.startsWith("1|0|"))
    }

    @Test
    fun `stable base identity falls back to blob hash for invalid serialized profile`() {
        val corruptedKeys = corruptedStableIdentityKeysForTest(byteArrayOf(), groupId = 7L)

        assertTrue(corruptedKeys.first.startsWith("7|0|"))
        val hashPart = corruptedKeys.first.removePrefix("7|0|")
        assertTrue(hashPart.isNotEmpty())
        assertTrue(hashPart.all { it.isDigit() || it == '-' })
        assertTrue(corruptedKeys.second.endsWith("|"))
    }

    @Test
    fun `stable full identity includes runtime key when bean is deserializable`() {
        val flowBean = VLESSBean().apply {
            name = "VLESS"
            serverAddress = "runtime.example.com"
            serverPort = 443
            uuid = "runtime-uuid"
            flow = "xtls-rprx-vision"
            sni = "runtime.example.com"
            host = "host.example.com"
        }

        val keys = stableIdentityKeysForTest(flowBean)

        assertTrue(keys.second.contains("flow=xtls-rprx-vision"))
        assertTrue(keys.first.contains("runtime.example.com|443|uuid=runtime-uuid"))
    }
}
