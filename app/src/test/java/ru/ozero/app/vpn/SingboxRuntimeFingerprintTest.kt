package ru.ozero.app.vpn

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SingboxRuntimeFingerprintTest {

    @Test
    fun `selected auto fingerprints bounded prioritized runtime candidate payloads`() {
        val prefs = prefs(selected = SingboxEngine.SELECTED_AUTO)
        val profiles = (1L..80L).map { id ->
            profile(id, validBlob(id)).copy(
                userOrder = id.toInt(),
                latencyMs = if (id == 70L) 10 else -1,
            )
        }

        val fingerprint = singboxRuntimeFingerprint(prefs, profiles, emptyList(), ipv6Enabled = true)

        val expectedIds = listOf(70L) + (1L..49L)
        assertEquals(
            SingboxRuntimeFingerprint(
                selectedProfileId = SingboxEngine.SELECTED_AUTO,
                selectedProfile = null,
                autoSelectProfiles = expectedIds.map { id -> profile(id, validBlob(id)).toPayload() },
                chainProfiles = emptyList(),
                dnsServers = emptyList(),
                ipv6Enabled = true,
            ),
            fingerprint,
        )
    }

    @Test
    fun `selected profile fingerprints full payload and active chain`() {
        val prefs = prefs(selected = 10L)
        val profiles = listOf(
            profile(10, byteArrayOf(9, 9)),
            profile(20, byteArrayOf(7, 7)),
        )
        val chainSteps = listOf(chainStep(20, 0), chainStep(10, 1))

        assertEquals(
            SingboxRuntimeFingerprint(
                selectedProfileId = 10L,
                selectedProfile = profiles[0].toPayload(),
                autoSelectProfiles = emptyList(),
                chainProfiles = listOf(RuntimeChainPayload(20L, profiles[1].toPayload())),
                dnsServers = emptyList(),
                ipv6Enabled = false,
            ),
            singboxRuntimeFingerprint(prefs, profiles, chainSteps),
        )
    }

    @Test
    fun `selected profile fingerprint remains fail closed when selected row is absent`() {
        val prefs = prefs(selected = 10L, bean = byteArrayOf(5, 5))

        assertEquals(
            SingboxRuntimeFingerprint(10L, null, emptyList(), emptyList(), emptyList(), false),
            singboxRuntimeFingerprint(prefs, listOf(profile(20, byteArrayOf(7, 7))), emptyList()),
        )
    }

    @Test
    fun `selected profile fingerprint resolves selected and chain rows outside supplied window`() = runTest {
        val prefs = prefs(selected = 10L)
        val supplied = listOf(profile(20, byteArrayOf(7, 7)))
        val selected = profile(10, byteArrayOf(9, 9))
        val chained = profile(30, byteArrayOf(3, 3))

        assertEquals(
            SingboxRuntimeFingerprint(
                selectedProfileId = 10L,
                selectedProfile = selected.toPayload(),
                autoSelectProfiles = emptyList(),
                chainProfiles = listOf(RuntimeChainPayload(30L, chained.toPayload())),
                dnsServers = emptyList(),
                ipv6Enabled = false,
            ),
            singboxRuntimeFingerprint(
                prefs = prefs,
                profiles = supplied,
                chainSteps = listOf(chainStep(30, 0)),
                resolveProfileById = { id -> if (id == 10L) selected else chained },
            ),
        )
    }

    @Test
    fun `fingerprint includes sorted dns and ipv6 without UI cache fields`() {
        val prefs = prefs(selected = 10L, dnsServers = setOf("8.8.8.8", "1.1.1.1"))
        val initial = profile(10, byteArrayOf(9, 9), name = "Original", latency = 11)
        val cachedUiChange = initial.copy(name = "Renamed", latencyMs = 999, probeError = "timeout")

        assertEquals(
            singboxRuntimeFingerprint(prefs, listOf(initial), emptyList(), ipv6Enabled = true),
            singboxRuntimeFingerprint(prefs, listOf(cachedUiChange), emptyList(), ipv6Enabled = true),
        )
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            singboxRuntimeFingerprint(prefs, listOf(initial), emptyList(), ipv6Enabled = true).dnsServers,
        )
    }

    @Test
    fun `fingerprint distinguishes full payloads with equal byte array hash`() {
        val prefs = prefs(selected = 10L)
        val first = profile(10, byteArrayOf(1, 0))
        val second = profile(10, byteArrayOf(0, 31))

        assertEquals(first.beanBlob.contentHashCode(), second.beanBlob.contentHashCode())
        assertNotEquals(
            singboxRuntimeFingerprint(prefs, listOf(first), emptyList()),
            singboxRuntimeFingerprint(prefs, listOf(second), emptyList()),
        )
    }

    @Test
    fun `fingerprint ignores display name stored inside bean payload`() {
        val prefs = prefs(selected = 10L)

        assertEquals(
            singboxRuntimeFingerprint(prefs, listOf(profile(10, validBean("Original"))), emptyList()),
            singboxRuntimeFingerprint(prefs, listOf(profile(10, validBean("Renamed"))), emptyList()),
        )
    }

    @Test
    fun `fingerprint changes when outbound server changes`() {
        val prefs = prefs(selected = 10L)
        val first = validBean("Server")
        val second = KryoSerializer.serialize(
            KryoSerializer.deserialize(first).apply { serverAddress = "replacement.example.com" },
        )

        assertNotEquals(
            singboxRuntimeFingerprint(prefs, listOf(profile(10, first)), emptyList()),
            singboxRuntimeFingerprint(prefs, listOf(profile(10, second)), emptyList()),
        )
    }

    private fun prefs(
        selected: Long? = null,
        bean: ByteArray? = null,
        dnsServers: Set<String>? = null,
    ): Preferences {
        val preferences = mutablePreferencesOf()
        if (selected != null) preferences[SingboxProbeService.SELECTED_PROFILE_KEY] = selected
        if (bean != null) preferences[SingboxProbeService.BEAN_KEY] = bean
        if (dnsServers != null) preferences[stringSetPreferencesKey("singbox_dns_servers")] = dnsServers
        return preferences
    }

    private fun profile(
        id: Long,
        blob: ByteArray,
        name: String = "P$id",
        latency: Int = -1,
    ) = ProxyProfile(
        id = id,
        groupId = 1L,
        name = name,
        beanBlob = blob,
        protocolType = 0,
        userOrder = id.toInt(),
        latencyMs = latency,
    )

    private fun ProxyProfile.toPayload() = RuntimeProfilePayload(id, protocolType, runtimeBeanPayload())

    private fun validBlob(id: Long): ByteArray = KryoSerializer.serialize(
        VLESSBean().apply {
            uuid = "12345678-1234-1234-1234-${id.toString().padStart(12, '0')}"
            serverAddress = "s$id.example.com"
            serverPort = 443
            type = "tcp"
            security = "none"
        },
    )

    private fun validBean(name: String): ByteArray = KryoSerializer.serialize(
        VLESSBean().apply {
            this.name = name
            uuid = "12345678-1234-1234-1234-123456789012"
            serverAddress = "server.example.com"
            serverPort = 443
            type = "tcp"
            security = "none"
        },
    )

    private fun chainStep(profileId: Long, userOrder: Int) = ProxyChainStep(
        id = 0L,
        profileId = profileId,
        userOrder = userOrder,
    )
}
