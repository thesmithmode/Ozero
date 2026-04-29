package ru.ozero.enginetor.bridges

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorBridgesJsonParserTest {

    @Test
    fun `пустой JSON — пустой список без exception`() {
        assertTrue(TorBridgesJsonParser.parse("").isEmpty())
        assertTrue(TorBridgesJsonParser.parse("{}").isEmpty())
        assertTrue(TorBridgesJsonParser.parse("not-a-json").isEmpty())
    }

    @Test
    fun `parse валидного obfs4 bridge с args`() {
        val json = """
            {
              "bridges": [
                {
                  "transport": "obfs4",
                  "address": "192.95.36.142:443",
                  "fingerprint": "CDF2E852BF539B82BD10E27E9115A31734E378C2",
                  "args": {
                    "cert": "qUVQ0srL1JI/vO6V6m/24anYXiJD3QP2HgzUKQtQ7GRqqUvs7P+tG43RtAqdhLOALP7DJQ",
                    "iat-mode": "1"
                  },
                  "remark": "obfs4-public-1"
                }
              ]
            }
        """.trimIndent()

        val bridges = TorBridgesJsonParser.parse(json)

        assertEquals(1, bridges.size)
        val b = bridges[0]
        assertEquals("obfs4", b.transport)
        assertEquals("192.95.36.142:443", b.address)
        assertEquals("CDF2E852BF539B82BD10E27E9115A31734E378C2", b.fingerprint)
        assertEquals("1", b.args["iat-mode"])
        assertEquals("obfs4-public-1", b.remark)
    }

    @Test
    fun `bridges без transport address fingerprint пропускаются`() {
        val json = """
            {
              "bridges": [
                { "transport": "obfs4", "address": "1.2.3.4:443", "fingerprint": "ABCD" },
                { "transport": "", "address": "1.2.3.4:443", "fingerprint": "ABCD" },
                { "transport": "obfs4", "address": "", "fingerprint": "ABCD" },
                { "transport": "obfs4", "address": "1.2.3.4:443", "fingerprint": "" }
              ]
            }
        """.trimIndent()

        val bridges = TorBridgesJsonParser.parse(json)

        assertEquals(1, bridges.size, "только полностью валидные bridges остаются")
    }

    @Test
    fun `bridge с пробелом в args отклоняется без падения парсера`() {
        val json = """
            {
              "bridges": [
                { "transport": "obfs4", "address": "1.2.3.4:443", "fingerprint": "ABCD",
                  "args": { "cert": "with space" } }
              ]
            }
        """.trimIndent()

        val bridges = TorBridgesJsonParser.parse(json)

        assertEquals(0, bridges.size, "TorBridge.requireSafe запрещает пробелы → парсер должен skipать")
    }

    @Test
    fun `массив bridges из нескольких записей парсится`() {
        val json = """
            {
              "bridges": [
                { "transport": "obfs4", "address": "1.1.1.1:443", "fingerprint": "AAAA" },
                { "transport": "obfs4", "address": "2.2.2.2:443", "fingerprint": "BBBB" }
              ]
            }
        """.trimIndent()

        val bridges = TorBridgesJsonParser.parse(json)

        assertEquals(2, bridges.size)
        assertEquals("1.1.1.1:443", bridges[0].address)
        assertEquals("2.2.2.2:443", bridges[1].address)
    }
}
