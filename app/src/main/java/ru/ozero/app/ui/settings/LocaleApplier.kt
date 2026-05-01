package ru.ozero.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleApplier {

    /**
     * BCP 47 теги, для которых уже существует values-* перевод. Любой другой
     * выбор приведёт к фолбэку на values-en/. Расширение списка — задача W9.2
     * (машинные переводы + ручной review).
     *
     * Пустая строка = "system default" (без явного override).
     */
    val SUPPORTED_TAGS: List<String> = listOf("", "ru", "en")

    fun apply(localeTag: String?) {
        val locales = if (localeTag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(localeTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
