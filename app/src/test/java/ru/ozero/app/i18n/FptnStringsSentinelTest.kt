package ru.ozero.app.i18n

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class FptnStringsSentinelTest {

    private val repoRoot by lazy { locateRepoRoot() }

    private val locales = listOf("values", "values-en", "values-es", "values-pt")

    @Test
    fun `все 4 локали имеют strings_fptn xml`() {
        locales.forEach { dir ->
            val file = stringsFptnFile(dir)
            assertTrue(
                file.isFile,
                "$dir/strings_fptn.xml отсутствует (${file.absolutePath}). " +
                    "FPTN UI ремейк требует все 4 локали ru/en/es/pt.",
            )
        }
    }

    @Test
    fun `fptn_bot_hint использует t_me_ а не голый собака — кликабельная ссылка`() {
        locales.forEach { dir ->
            val raw = stringsFptnFile(dir).readText(Charsets.UTF_8)
            val value = extract(raw, "fptn_bot_hint")
                ?: error("$dir/strings_fptn.xml: ключ fptn_bot_hint отсутствует")
            assertTrue(
                value.contains("t.me/"),
                "$dir/strings_fptn.xml fptn_bot_hint должен содержать t.me/ для кликабельной " +
                    "ссылки. Текущее значение: \"$value\". User feedback 2026-05-23: " +
                    "@username не кликабелен, использовать t.me/username.",
            )
            assertTrue(
                !Regex("""(^|[^a-zA-Z0-9_./])@[a-zA-Z0-9_]+""").containsMatchIn(value),
                "$dir/strings_fptn.xml fptn_bot_hint содержит голый @username (не кликабельно). " +
                    "Заменить на t.me/username. Значение: \"$value\".",
            )
        }
    }

    @Test
    fun `все 4 локали имеют parity по ключам strings_fptn`() {
        val baseline = keysFor(stringsFptnFile("values"))
        val nonTrivial = baseline.size
        assertTrue(
            nonTrivial >= 25,
            "ru baseline strings_fptn имеет $nonTrivial ключей (<25). " +
                "Регрессия — FPTN UI ремейк добавлял минимум 25 ключей.",
        )
        listOf("values-en", "values-es", "values-pt").forEach { dir ->
            val keys = keysFor(stringsFptnFile(dir))
            val missing = (baseline - keys).sorted()
            val extra = (keys - baseline).sorted()
            assertTrue(
                missing.isEmpty() && extra.isEmpty(),
                "$dir/strings_fptn.xml parity broken vs ru baseline. " +
                    "Missing (${missing.size}): $missing. Extra (${extra.size}): $extra.",
            )
        }
    }

    @Test
    fun `обязательные ключи FPTN UI ремейка присутствуют в ru baseline`() {
        val required = setOf(
            "fptn_about_description",
            "fptn_link_website",
            "fptn_link_telegram",
            "fptn_link_play",
            "fptn_link_boosty",
            "fptn_section_token_description",
            "fptn_experimental_reconnect_header",
            "fptn_experimental_attempts_header",
            "fptn_experimental_others_header",
            "fptn_experimental_cancel",
            "fptn_experimental_save",
        )
        val baseline = keysFor(stringsFptnFile("values"))
        val missing = (required - baseline).sorted()
        assertTrue(
            missing.isEmpty(),
            "ru strings_fptn.xml: отсутствуют обязательные ключи FPTN UI ремейка: $missing.",
        )
    }

    private fun extract(xml: String, key: String): String? =
        Regex("""<string\s+name="$key"\s*>([\s\S]*?)</string>""").find(xml)?.groupValues?.get(1)

    private fun keysFor(file: File): Set<String> =
        Regex("""name="([^"]+)"""")
            .findAll(file.readText(Charsets.UTF_8))
            .map { it.groupValues[1] }
            .toSet()

    private fun stringsFptnFile(localeDir: String): File =
        File(repoRoot, "app/src/main/res/$localeDir/strings_fptn.xml")

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root (settings.gradle.kts) не найден")
    }
}
