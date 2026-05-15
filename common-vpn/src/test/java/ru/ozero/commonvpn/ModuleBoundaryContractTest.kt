package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

class ModuleBoundaryContractTest {

    @Test
    fun `common-vpn не должен switch по конкретным EngineId`() {
        val mainDir = File(System.getProperty("user.dir") ?: ".", "src/main/java/ru/ozero/commonvpn")
        assertTrue(mainDir.isDirectory, "main dir не найден: $mainDir")
        val forbidden = listOf("EngineId.BYEDPI", "EngineId.WARP", "EngineId.URNETWORK")
        val violations = mutableListOf<String>()
        mainDir.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            forbidden.forEach { id ->
                if (text.contains(id)) {
                    violations += "${f.relativeTo(mainDir)} → $id"
                }
            }
        }
        if (violations.isNotEmpty()) {
            fail(
                "common-vpn — общий модуль, не должен знать про конкретные движки. " +
                    "Используй EnginePlugin.buildManualConfig() / capabilities / id вместо when(EngineId). " +
                    "Нарушения: $violations",
            )
        }
    }

    @Test
    fun `common-vpn не должен импортировать конкретные engine модули`() {
        val mainDir = File(System.getProperty("user.dir") ?: ".", "src/main/java/ru/ozero/commonvpn")
        val forbidden = listOf(
            "import ru.ozero.enginebyedpi",
            "import ru.ozero.enginewarp",
            "import ru.ozero.engineurnetwork",
        )
        val violations = mutableListOf<String>()
        mainDir.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            forbidden.forEach { imp ->
                if (text.contains(imp)) violations += "${f.relativeTo(mainDir)} → $imp"
            }
        }
        if (violations.isNotEmpty()) {
            fail("common-vpn не должен зависеть от engine-* модулей: $violations")
        }
    }
}
