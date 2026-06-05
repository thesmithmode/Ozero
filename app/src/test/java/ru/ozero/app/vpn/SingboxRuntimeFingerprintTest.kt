package ru.ozero.app.vpn

import androidx.datastore.preferences.core.Preferences
import org.junit.jupiter.api.Test
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals

class SingboxRuntimeFingerprintTest {

    @Test
    fun `selected auto fingerprints all profile blobs in order`() {
        val prefs = prefs(selected = SingboxEngine.SELECTED_AUTO)
        val profiles = listOf(profile(1, byteArrayOf(1, 2)), profile(2, byteArrayOf(3, 4)))

        val fingerprint = singboxRuntimeFingerprint(prefs, profiles, emptyList())

        assertEquals(
            listOf(
                SingboxEngine.SELECTED_AUTO,
                listOf(1L to byteArrayOf(1, 2).contentHashCode(), 2L to byteArrayOf(3, 4).contentHashCode()),
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
            listOf(10L, byteArrayOf(5, 5).contentHashCode(), emptyList<Pair<Long, Int>>()),
            fingerprint,
        )
    }

    private fun prefs(
        selected: Long? = null,
        bean: ByteArray? = null,
    ): Preferences {
        val preferences = androidx.datastore.preferences.core.mutablePreferencesOf()
        if (selected != null) {
            preferences[SingboxProbeService.SELECTED_PROFILE_KEY] = selected
        }
        if (bean != null) {
            preferences[SingboxProbeService.BEAN_KEY] = bean
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

    private fun chainStep(profileId: Long, userOrder: Int) = ProxyChainStep(
        id = 0L,
        profileId = profileId,
        userOrder = userOrder,
    )
}
