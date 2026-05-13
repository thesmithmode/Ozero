package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarpPresetsTest {

    @Test
    fun `DnsPresets ALL ids уникальны`() {
        val ids = DnsPresets.ALL.map { it.id }
        val dupes = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertEquals(ids.size, ids.toSet().size, "Дублирующиеся id: $dupes")
    }

    @Test
    fun `DnsPresets ALL servers непусты`() {
        DnsPresets.ALL.forEach { preset ->
            assertTrue(preset.servers.isNotEmpty(), "Preset '${preset.id}' имеет пустой список servers")
        }
    }

    @Test
    fun `DnsPresets содержит RU серверы`() {
        val ids = DnsPresets.ALL.map { it.id }.toSet()
        listOf("malw", "xbox_dns", "astracat", "geohide").forEach { id ->
            assertTrue(id in ids, "Отсутствует RU DNS preset '$id'")
        }
    }

    @Test
    fun `AwgPresets ALL ids уникальны`() {
        val ids = AwgPresets.ALL.map { it.id }
        val dupes = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertEquals(ids.size, ids.toSet().size, "Дублирующиеся id: $dupes")
    }

    @Test
    fun `AwgPresets содержит I1 пресеты`() {
        val ids = AwgPresets.ALL.map { it.id }.toSet()
        listOf("i1_v1", "i1_v2", "i1_v3", "i1_v4", "i1_off").forEach { id ->
            assertTrue(id in ids, "Отсутствует I1 preset '$id'")
        }
    }

    @Test
    fun `I1_OFF сбрасывает payloadPacketSizeCount в 0`() {
        val preset = AwgPresets.ALL.first { it.id == "i1_off" }
        assertEquals(0, preset.params.payloadPacketSizeCount1)
        assertEquals(0, preset.params.payloadPacketSizeCount2)
        assertEquals(0, preset.params.payloadPacketSizeCount3)
    }

    @Test
    fun `EndpointPresets ALL ids уникальны`() {
        val ids = EndpointPresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Дублирующиеся endpoint id")
    }
}
