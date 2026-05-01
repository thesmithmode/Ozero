package ru.ozero.app.ui.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * До W9.2 (machine translations + native review) UI обязан показывать только
 * языки, для которых есть `values-*/strings.xml`. Любой расширенный список
 * = регрессия — юзер выбирает например китайский, а получает английский
 * fallback.
 */
class LocaleApplierTest {

    @Test
    fun `SUPPORTED_TAGS содержит ровно system + RU + EN до W9_2`() {
        assertEquals(
            listOf("", "ru", "en"),
            LocaleApplier.SUPPORTED_TAGS,
            "Расширение списка требует добавления values-* перевода (W9.2). " +
                "Просто добавить tag без перевода = регрессия (юзер видит EN fallback).",
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
