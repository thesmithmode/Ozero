package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class WarpPresetCoverageTest {

    @Test
    fun `preset display fields are populated`() {
        EndpointPresets.ALL.forEach { preset ->
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.endpoint.isNotBlank())
        }
        DnsPresets.ALL.forEach { preset ->
            assertTrue(preset.name.isNotBlank())
        }
        AwgPresets.ALL.forEach { preset ->
            assertTrue(preset.id.isNotBlank())
        }
    }
}
