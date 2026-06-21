package ru.ozero.app.vpn

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.jupiter.api.Test
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals

class SingboxRuntimeFingerprintTest {

    @Test
    fun `selected auto fingerprints bounded prioritized runtime candidate blobs`() {
        val prefs = prefs(selected = SingboxEngine.SELECTED_AUTO)
        val profiles = (1L..80L).map { id ->
            profile(id, validBlob(id)).copy(
                userOrder = id.toInt(),
                latencyMs = if (id == 70L) 10 else -1,
            )
        }

        val fingerprint = singboxRuntimeFingerprint(prefs, profiles, emptyList())

        val expectedIds = listOf(70L) + (1L..49L)
        val expected = expectedIds.map { id -> id to validBlob(id).contentHashCode() }
        assertEquals(
            listOf(
                SingboxEngine.SELECTED_AUTO,
                expected,
                emptyList<String>(),
            ),
            fingerprint,
        )
    }

    @Test
    fun `selected profile fingerprint falls back to bean blob and active chain hashes`() {
        val prefs = prefs(selected = 10L)
        val profiles = listOf(
            profile(10, byteArrayOf(9, 9)),
            profile(20, byteArrayOf(7, 7)),
        )
        val chainSteps = listOf(
            chainStep(20, 0),
            chainStep(10, 1),
        )

        val fingerprint = singboxRuntimeFingerprint(prefs, profiles, chainSteps)

        assertEquals(
            listOf(
                10L,
                byteArrayOf(9, 9).contentHashCode(),
                listOf(20L to byteArrayOf(7, 7).contentHashCode()),
                emptyList<String>(),
            ),
            fingerprint,
        )
    }

    @Test
    fun `selected profile fingerprint falls back to prefs bean when selected id missing`() {
        val prefs = prefs(selected = 10L, bean = byteArrayOf(5, 5))
        val profiles = listOf(profile(20, byteArrayOf(7, 7)))

        val fingerprint = singboxRuntimeFingerprint(prefs, profiles, emptyList())

        assertEquals(
            listOf(10L, 0, emptyList<Pair<Long, Int>>(), emptyList<String>()),
            fingerprint,
        )
    }

    @Test
    fun `selected profile fingerprint resolves row synchronously before missing-row fallback`() =
        kotlinx.coroutines.test.runTest {
            val prefs = prefs(selected = 10L, bean = byteArrayOf(5, 5))
            val profiles = listOf(profile(20, byteArrayOf(7, 7)))

            val fingerprint = singboxRuntimeFingerprint(
                prefs = prefs,
                profiles = profiles,
                chainSteps = emptyList(),
                resolveProfileById = { id -> profile(id, byteArrayOf(9, 9)) },
            )

            assertEquals(
                listOf(
                    10L,
                    byteArrayOf(9, 9).contentHashCode(),
                    emptyList<Pair<Long, Int>>(),
                    emptyList<String>(),
                ),
                fingerprint,
            )
        }

    @Test
    fun `selected profile fingerprint resolves active chain rows outside supplied profile window`() =
        kotlinx.coroutines.test.runTest {
            val prefs = prefs(selected = 10L)
            val profiles = listOf(profile(10, byteArrayOf(1, 1)))
            val chainSteps = listOf(
                chainStep(30, 0),
                chainStep(10, 1),
            )

            val fingerprint = singboxRuntimeFingerprint(
                prefs = prefs,
                profiles = profiles,
                chainSteps = chainSteps,
                resolveProfileById = { id -> profile(id, byteArrayOf(3, 3)) },
            )

            assertEquals(
                listOf(
                    10L,
                    byteArrayOf(1, 1).contentHashCode(),
                    listOf(30L to byteArrayOf(3, 3).contentHashCode()),
                    emptyList<String>(),
                ),
                fingerprint,
            )
        }

    @Test
    fun `selected profile fingerprint keeps fail closed fallback when synchronous row is absent`() =
        kotlinx.coroutines.test.runTest {
            val prefs = prefs(selected = 10L, bean = byteArrayOf(5, 5))
            val profiles = listOf(profile(20, byteArrayOf(7, 7)))

            val fingerprint = singboxRuntimeFingerprint(
                prefs = prefs,
                profiles = profiles,
                chainSteps = emptyList(),
                resolveProfileById = { null },
            )

            assertEquals(
                listOf(10L, 0, emptyList<Pair<Long, Int>>(), emptyList<String>()),
                fingerprint,
            )
        }

    @Test
    fun `fingerprint includes singbox dns servers`() {
        val prefs = prefs(selected = 10L, dnsServers = setOf("8.8.8.8", "1.1.1.1"))
        val profiles = listOf(profile(10, byteArrayOf(9, 9)))

        val fingerprint = singboxRuntimeFingerprint(prefs, profiles, emptyList())

        assertEquals(
            listOf(
                10L,
                byteArrayOf(9, 9).contentHashCode(),
                emptyList<Pair<Long, Int>>(),
                listOf("1.1.1.1", "8.8.8.8"),
            ),
            fingerprint,
        )
    }

    private fun prefs(
        selected: Long? = null,
        bean: ByteArray? = null,
        dnsServers: Set<String>? = null,
    ): Preferences {
        val preferences = mutablePreferencesOf()
        if (selected != null) {
            preferences[SingboxProbeService.SELECTED_PROFILE_KEY] = selected
        }
        if (bean != null) {
            preferences[SingboxProbeService.BEAN_KEY] = bean
        }
        if (dnsServers != null) {
            preferences[stringSetPreferencesKey("singbox_dns_servers")] = dnsServers
        }
        return preferences
    }

    private fun profile(id: Long, blob: ByteArray) = ProxyProfile(
        id = id,
        groupId = 1L,
        name = "P$id",
        beanBlob = blob,
        protocolType = 0,
        userOrder = id.toInt(),
    )

    private fun validBlob(id: Long): ByteArray = KryoSerializer.serialize(
        VLESSBean().apply {
            uuid = "12345678-1234-1234-1234-${id.toString().padStart(12, '0')}"
            serverAddress = "s$id.example.com"
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
