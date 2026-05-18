package ru.ozero.app.ui.icons

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class MaterialIconsCoreSentinelTest {

    private val allowedIconImports = setOf(
        "androidx.compose.material.icons.Icons",
        "androidx.compose.material.icons.filled.Add",
        "androidx.compose.material.icons.filled.Check",
        "androidx.compose.material.icons.filled.Clear",
        "androidx.compose.material.icons.filled.Close",
        "androidx.compose.material.icons.filled.Delete",
        "androidx.compose.material.icons.filled.Edit",
        "androidx.compose.material.icons.filled.Home",
        "androidx.compose.material.icons.filled.KeyboardArrowDown",
        "androidx.compose.material.icons.filled.KeyboardArrowUp",
        "androidx.compose.material.icons.filled.LocationOn",
        "androidx.compose.material.icons.filled.Lock",
        "androidx.compose.material.icons.filled.Search",
        "androidx.compose.material.icons.filled.Settings",
        "androidx.compose.material.icons.filled.Share",
        "androidx.compose.material.icons.filled.Star",
        "androidx.compose.material.icons.filled.Warning",
        "androidx.compose.material.icons.automirrored.filled.ArrowBack",
        "androidx.compose.material.icons.automirrored.filled.List",
    )

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")
    private val mainSrc = File(moduleRoot, "src/main/java")

    @Test
    fun `все material-icons imports в app должны быть из material-icons-core whitelist`() {
        assertTrue(mainSrc.exists(), "src/main/java не найден: $mainSrc")

        val importRegex = Regex("""^import\s+(androidx\.compose\.material\.icons\.[A-Za-z0-9._]+)""")
        val violations = mutableListOf<String>()

        mainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { idx, line ->
                    val match = importRegex.find(line.trim()) ?: return@forEachIndexed
                    val fqcn = match.groupValues[1]
                    if (fqcn !in allowedIconImports) {
                        val relative = file.relativeTo(moduleRoot).path.replace('\\', '/')
                        violations += "$relative:${idx + 1}: запрещён `import $fqcn`"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            buildString {
                appendLine("Нарушения material-icons whitelist (см. feedback_material_icons_core):")
                appendLine("Проект подключает только `androidx.compose.material:material-icons-core`.")
                appendLine("Любая иконка вне core ломает компиляцию в CI (Icons.Filled.CallSplit прецедент v0.0.X).")
                appendLine("Варианты: 1) использовать icon из whitelist; 2) ImageVector в ui/icons/OzeroIcons.kt;")
                appendLine("3) расширить whitelist если icon реально в material-icons-core (verify в Android Studio).")
                appendLine()
                violations.forEach(::appendLine)
            },
        )
    }
}
