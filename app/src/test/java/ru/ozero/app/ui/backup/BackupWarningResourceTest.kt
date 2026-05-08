package ru.ozero.app.ui.backup

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class BackupWarningResourceTest {

    private fun stringsContent(rel: String): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, rel)
        assertTrue(f.exists(), "Файл не найден: $f")
        return f.readText()
    }

    @Test
    fun `backup_warning resource ru существует и упоминает ключи`() {
        val xml = stringsContent("src/main/res/values/strings.xml")
        val match = Regex(
            "<string\\s+name=\"backup_warning\"[^>]*>([^<]*)</string>",
        ).find(xml)
        val text = match?.groupValues?.get(1)?.trim().orEmpty()
        assertTrue(text.isNotBlank(), "backup_warning ru не должен быть пустым")
        assertTrue(
            text.contains("ключ", ignoreCase = true) || text.contains("WireGuard", ignoreCase = true),
            "backup_warning ru обязан явно упоминать чувствительные данные (ключи/WireGuard) — " +
                "иначе юзер не понимает что хранится в plaintext: '$text'",
        )
    }

    @Test
    fun `backup_warning resource en существует и упоминает sensitive`() {
        val xml = stringsContent("src/main/res/values-en/strings.xml")
        val match = Regex(
            "<string\\s+name=\"backup_warning\"[^>]*>([^<]*)</string>",
        ).find(xml)
        val text = match?.groupValues?.get(1)?.trim().orEmpty()
        assertTrue(text.isNotBlank(), "backup_warning en не должен быть пустым")
        assertTrue(
            text.contains("sensitive", ignoreCase = true) ||
                text.contains("key", ignoreCase = true) ||
                text.contains("WireGuard", ignoreCase = true),
            "backup_warning en обязан явно упоминать чувствительные данные: '$text'",
        )
    }

    @Test
    fun `BackupScreen показывает backup_warning над Export button`() {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val src = File(
            moduleRoot,
            "src/main/java/ru/ozero/app/ui/backup/BackupScreen.kt",
        ).readText()
        val warningIdx = src.indexOf("R.string.backup_warning")
        val exportButtonIdx = src.indexOf("R.string.backup_export_button")
        assertTrue(warningIdx > 0, "BackupScreen обязан ссылаться на R.string.backup_warning")
        assertTrue(exportButtonIdx > 0, "BackupScreen обязан ссылаться на R.string.backup_export_button")
        assertTrue(
            warningIdx < exportButtonIdx,
            "warning text обязан рендериться ВЫШЕ Export button (Composable order) — " +
                "иначе юзер кликнет Export не увидев предупреждения",
        )
    }
}
