package ru.ozero.coreapi

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ByeDpiDefaultArgsTest {

    @Test
    fun `EngineConfig ByeDpi default args = РФ ТСПУ multi-stage preset`() {
        val expected = "-s1 -q1 -a1 -Y -Ar -a1 -s5 -o2 -At -f-1 -r1+s -a1 -As -s1 -o1+s -s-1 -a1"
        assertEquals(
            expected,
            EngineConfig.ByeDpi().args,
            "Дефолт args для ByeDPI — рабочий многоэтапный preset для обхода ТСПУ в РФ. " +
                "Каскад -s/-q/-a/-Y/-Ar/-o/-At/-f/-r/-As комбинирует TCP fragmentation, " +
                "fake records, OOB bytes и timing-based bypass. Преобладание над одиночными " +
                "стратегиями (-Ku -a1 -An -o1 -At,r,s -d1) — выше success rate в полевых условиях.",
        )
    }

    @Test
    fun `EngineConfig ByeDpi default socks port = 1080`() {
        assertEquals(1080, EngineConfig.ByeDpi().socksPort)
    }
}
