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
        val refreshBlock = text.substringAfter("fun refreshGroupInternal(").substringBefore("\n    private ")
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

    @Test
    fun `onRefreshAll использует async и awaitAll для параллельного обновления`() {
        val src = locateFile(vmPath)
        val text = src.readText()
        val block = text.substringAfter("fun onRefreshAll(").substringBefore("\n    fun ")
        assertTrue(
            block.contains("async") && block.contains("awaitAll"),
            "onRefreshAll обязан использовать async+awaitAll — " +
                "последовательный forEach блокирует обновление групп: группы обновляются по очереди, " +
                "при 5 группах по 5с = 25с вместо 5с. Регрессия 2026-05-25.",
        )
        assertFalse(
            Regex("""forEach\s*\{[^}]*refreshGroupInternal""").containsMatchIn(block),
            "onRefreshAll НЕ должен использовать forEach для вызова refreshGroupInternal — только async+awaitAll",
        )
    }

    @Test
    fun `onPingAll существует и использует async и awaitAll`() {
        val src = locateFile(vmPath)
        val text = src.readText()
        assertTrue(
            text.contains("fun onPingAll()"),
            "ViewModel обязан содержать метод onPingAll() — " +
                "пользователь запросил кнопку ручного пинга всех групп в TopAppBar",
        )
        val block = text.substringAfter("fun onPingAll(").substringBefore("\n    fun ")
        assertTrue(
            block.contains("async") && block.contains("awaitAll"),
            "onPingAll обязан использовать async+awaitAll — параллельный пинг всех групп",
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
