package ru.ozero.app.ui.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleApplierTest {

    @Test
    fun `SUPPORTED_TAGS содержит system + RU + EN + 8 топ-10 локалей`() {
        assertEquals(
            listOf("", "ru", "en", "zh-rCN", "es", "ar", "fr", "hi", "pt", "de", "ja"),
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
        val top10 = setOf("ru", "en", "zh-rCN", "hi", "es", "ar", "pt", "fr", "ja", "de")
        val present = LocaleApplier.SUPPORTED_TAGS.toSet().intersect(top10)
        assertEquals(top10, present, "Все 10 языков должны быть в SUPPORTED_TAGS")
    }
}
