package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxRefreshErrorSentinelTest {

    private val vmPath =
        "app/src/main/java/ru/ozero/app/ui/settings/engines/singbox/SingboxEngineSettingsViewModel.kt"

    @Test
    fun `SingboxSettingsUiState содержит groupRefreshErrors`() {
        val src = locateFile(vmPath)
        val text = src.readText()
        assertTrue(
            text.contains("groupRefreshErrors"),
            "SingboxSettingsUiState обязан содержать groupRefreshErrors: Map<Long, String> — " +
                "без него ошибка обновления подписки (сеть, парсинг) проглатывается молча, " +
                "пользователь видит пустой список без объяснений. Регрессия 2026-05-25.",
        )
    }

    @Test
    fun `refreshGroupInternal обрабатывает Result от rawUpdater`() {
        val src = locateFile(vmPath)
        val text = src.readText()
        val refreshBlock = text.substringAfter("refreshGroupInternal(").substringBefore("\n    fun ")
        assertFalse(
            Regex("""(?m)^\s*rawUpdater\.refresh\(group\)\s*$""").containsMatchIn(refreshBlock),
            "refreshGroupInternal НЕ должен игнорировать Result от rawUpdater.refresh() — " +
                "присваивать результат и проверять isFailure для установки groupRefreshErrors",
        )
        assertTrue(
            refreshBlock.contains("result") && refreshBlock.contains("isFailure"),
            "refreshGroupInternal обязан проверять result.isFailure и устанавливать groupRefreshErrors при провале",
        )
    }

    private fun locateFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) {
                return File(dir, relativePath).also {
                    assertTrue(it.isFile, "Файл не найден: ${it.absolutePath}")
                }
            }
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root не найден")
    }
}
