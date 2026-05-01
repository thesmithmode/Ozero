package ru.ozero.app.ui.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleApplierTest {

    @Test
    fun `SUPPORTED_TAGS содержит system + RU + EN + 8 топ-10 локалей`() {
        assertEquals(
            listOf("", "ru", "en", "zh-CN", "es", "ar", "fr", "hi", "pt", "de", "ja"),
            LocaleApplier.SUPPORTED_TAGS,
            "Расширение требует одновременного добавления values-* перевода — иначе юзер " +
                "выбирает язык, а получает values-en fallback.",
        )
    }

    @Test
    fun `SUPPORTED_TAGS содержит пустую строку для system default`() {
        assertTrue(
            "" in LocaleApplier.SUPPORTED_TAGS,
            "Пустой tag = system default (LocaleListCompat.getEmptyLocaleList) — " +
                "обязан быть в SUPPORTED_TAGS чтобы LanguageSection показывал 'System'.",
        )
    }

    @Test
    fun `SUPPORTED_TAGS не содержит дублей`() {
        assertEquals(
            LocaleApplier.SUPPORTED_TAGS.size,
            LocaleApplier.SUPPORTED_TAGS.toSet().size,
        )
    }

    @Test
    fun `SUPPORTED_TAGS содержит топ-10 языков заявленных юзером`() {
        val top10 = setOf("ru", "en", "zh-CN", "hi", "es", "ar", "pt", "fr", "ja", "de")
        val present = LocaleApplier.SUPPORTED_TAGS.toSet().intersect(top10)
        assertEquals(top10, present, "Все 10 языков должны быть в SUPPORTED_TAGS")
    }

    @Test
    fun `SUPPORTED_TAGS не содержит Android resource-qualifier prefix r`() {
        val withResourcePrefix = LocaleApplier.SUPPORTED_TAGS
            .filter { it.isNotEmpty() }
            .filter { tag -> tag.split("-").any { it.length == 3 && it.startsWith("r") } }
        assertTrue(
            withResourcePrefix.isEmpty(),
            "BCP-47 не использует префикс 'r' для региона — это Android values-zh-rCN формат, " +
                "AppCompatDelegate.setApplicationLocales парсит его как unrecognized → язык не " +
                "применяется. Используй 'zh-CN' (BCP-47), не 'zh-rCN'. " +
                "Найдены теги с resource-qualifier: $withResourcePrefix",
        )
    }

    @Test
    fun `SUPPORTED_TAGS все теги парсятся в Locale forLanguageTag без потери языка`() {
        LocaleApplier.SUPPORTED_TAGS.forEach { tag ->
            if (tag.isEmpty()) return@forEach
            val locale = java.util.Locale.forLanguageTag(tag)
            assertTrue(
                locale.language.isNotEmpty() && locale.language != "und",
                "tag '$tag' распарсился в Locale без language → невалидный BCP-47. " +
                    "AppCompatDelegate.setApplicationLocales его проигнорирует.",
            )
        }
    }
}
