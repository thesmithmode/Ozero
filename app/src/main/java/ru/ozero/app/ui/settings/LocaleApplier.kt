package ru.ozero.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ru.ozero.enginescore.PersistentLoggers

object LocaleApplier {

    private const val TAG = "LocaleApplier"

    val SUPPORTED_TAGS: List<String> = listOf(
        "",
        "ru",
        "en",
        "zh-CN",
        "es",
        "ar",
        "fr",
        "hi",
        "pt",
        "de",
        "ja",
    )

    fun apply(localeTag: String?) {
        val locales = if (localeTag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(localeTag)
        }
        runCatching { AppCompatDelegate.setApplicationLocales(locales) }
            .onFailure { PersistentLoggers.warn(TAG, "setApplicationLocales failed: ${it.message}") }
    }
}
