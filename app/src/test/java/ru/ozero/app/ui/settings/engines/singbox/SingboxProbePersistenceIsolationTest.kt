package ru.ozero.app.ui.settings.engines.singbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingboxProbePersistenceIsolationTest {
    @Test
    fun `rejected profile persistence failure does not abort valid sibling probe`() = runTest {
        val corrupted = ProxyProfile(
            id = 1L,
            groupId = 1L,
            name = "corrupted",
            beanBlob = byteArrayOf(1, 2, 3),
            protocolType = SingboxEngine.PROTOCOL_VLESS,
        )
        val valid = validProfile(2L)
        val dao = mockk<ProxyProfileDao>()
        coEvery {
            dao.updateProbeResultIfCurrent(corrupted.id, any(), any(), any(), any(), any())
        } throws IllegalStateException("database write failed")
        coEvery {
            dao.updateProbeResultIfCurrent(valid.id, any(), any(), any(), any(), any())
        } returns 1
        coEvery { dao.getById(valid.id) } returns valid

        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val probeCalls = AtomicInteger()
        val probe = SingboxProfileProbe { _, _ ->
            probeCalls.incrementAndGet()
            17
        }

        SingboxProbeService(dao, flowDataStore(prefsFlow), probe)
            .probeAndAutoSelect(listOf(corrupted, valid))

        assertEquals(1, probeCalls.get())
        assertEquals(valid.id, prefsFlow.value[SingboxProbeService.SELECTED_PROFILE_KEY])
        assertTrue(valid.beanBlob.contentEquals(prefsFlow.value[SingboxProbeService.BEAN_KEY]))
    }

    private fun validProfile(id: Long): ProxyProfile = ProxyProfile(
        id = id,
        groupId = 1L,
        name = "valid",
        beanBlob = KryoSerializer.serialize(
            VLESSBean().apply {
                uuid = "12345678-1234-1234-1234-123456789abc"
                serverAddress = "valid.example"
                serverPort = 443
                type = "tcp"
            },
        ),
        protocolType = SingboxEngine.PROTOCOL_VLESS,
    )

    private fun flowDataStore(prefsFlow: MutableStateFlow<Preferences>): DataStore<Preferences> =
        object : DataStore<Preferences> {
            override val data: Flow<Preferences> = prefsFlow

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                return updated
            }
        }
}
