package ru.ozero.singboxsubscription

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxsubscription.parser.ClashYamlParser
import ru.ozero.singboxsubscription.parser.RawShareLinksParser
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParserCanonicalizationTest {

    @Test
    fun `share link raw transport canonicalizes to tcp`() {
        val links = RawShareLinksParser.parse(
            "vless://11111111-1111-1111-1111-111111111111@example.com:443?type=raw#raw",
        )

        val bean = assertIs<VLESSBean>(links.single())
        assertEquals("tcp", bean.type)
    }

    @Test
    fun `clash yaml raw network canonicalizes to tcp`() {
        val yaml = """
            proxies:
              - name: raw
                type: vless
                server: example.com
                port: 443
                uuid: 11111111-1111-1111-1111-111111111111
                network: raw
        """.trimIndent()

        val bean = assertIs<VLESSBean>(ClashYamlParser.parse(yaml).single())
        assertEquals("tcp", bean.type)
    }
}
