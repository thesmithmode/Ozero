package ru.ozero.app.vpn

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import kotlin.test.assertTrue
import kotlin.test.fail

class EngineAutoCascadeContractTest {

    private val nonStubEngineClasses: Map<EngineId, String> = mapOf(
        EngineId.BYEDPI to "ru.ozero.enginebyedpi.ByeDpiEngine",
        EngineId.URNETWORK to "ru.ozero.engineurnetwork.EngineUrnetwork",
        EngineId.WARP to "ru.ozero.enginewarp.EngineWarp",
        EngineId.MASTERDNS to "ru.ozero.enginemasterdns.MasterDnsEngine",
        EngineId.FPTN to "ru.ozero.enginefptn.FptnEngine",
    )

    @Test
    fun `все non-stub EngineId перечислены в map контрактного теста`() {
        val mapped = nonStubEngineClasses.keys
        val nonStub = EngineId.entries.filter { !it.isStub }.toSet()
        val unmapped = nonStub - mapped
        assertTrue(
            unmapped.isEmpty(),
            "Добавь EngineId → ::class.java.name в nonStubEngineClasses: $unmapped",
        )
    }

    @Test
    fun `каждый non-stub EnginePlugin обязан override buildManualConfig для участия в auto cascade`() {
        val missing = mutableListOf<String>()
        EngineId.entries.filter { !it.isStub }.forEach { id ->
            val className = nonStubEngineClasses[id] ?: fail("Добавь $id в map выше")
            val clazz = runCatching { Class.forName(className) }.getOrElse {
                fail("Класс не найден: $className (id=$id)")
            }
            val hasOverride = clazz.declaredMethods.any { it.name == "buildManualConfig" }
            if (!hasOverride) {
                missing += "$id → $className"
            }
        }
        assertTrue(
            missing.isEmpty(),
            "Non-stub engines обязаны override fun buildManualConfig(settings): EngineConfig?, " +
                "иначе StartSequenceCoordinator.autoCandidates() их пропускает через mapNotNull, " +
                "и они не участвуют в auto cascade. Missing: $missing",
        )
    }
}
