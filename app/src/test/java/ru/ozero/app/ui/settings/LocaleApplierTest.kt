package ru.ozero.app.ui.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleApplierTest {

    @Test
    fun `SUPPORTED_TAGS содержит ровно system + RU + EN`() {
        assertEquals(
            listOf("", "ru", "en"),
            LocaleApplier.SUPPORTED_TAGS,
            "Расширение списка требует одновременного добавления values-* перевода — иначе юзер " +
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
}
