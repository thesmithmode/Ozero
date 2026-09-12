package ru.ozero.enginefptn

enum class FptnBypassMethod(val strategyName: String, val displayName: String) {
    SNI("SNI", "SNI · Подмена домена"),
    OBFUSCATION("OBFUSCATION", "Обфускация TLS"),
    SNI_REALITY_YANDEX_26_4("SNI-REALITY-YANDEX-26-4", "SNI Reality · Yandex 26.4"),
    SNI_REALITY_YANDEX_26_3("SNI-REALITY-YANDEX-26-3", "SNI Reality · Yandex 26.3"),
    SNI_REALITY_YANDEX_25("SNI-REALITY-YANDEX-25", "SNI Reality · Yandex 25"),
    SNI_REALITY_YANDEX_24("SNI-REALITY-YANDEX-24", "SNI Reality · Yandex 24"),
    SNI_REALITY_CHROME_149("SNI-REALITY-CHROME-149", "SNI Reality · Chrome 149"),
    SNI_REALITY_CHROME_148("SNI-REALITY-CHROME-148", "SNI Reality · Chrome 148"),
    SNI_REALITY_CHROME_147("SNI-REALITY-CHROME-147", "SNI Reality · Chrome 147"),
    SNI_REALITY_CHROME_146("SNI-REALITY-CHROME-146", "SNI Reality · Chrome 146"),
    SNI_REALITY_CHROME_145("SNI-REALITY-CHROME-145", "SNI Reality · Chrome 145"),
    SNI_REALITY_FIREFOX_151("SNI-REALITY-FIREFOX-151", "SNI Reality · Firefox 151"),
    SNI_REALITY_FIREFOX_150("SNI-REALITY-FIREFOX-150", "SNI Reality · Firefox 150"),
    SNI_REALITY_FIREFOX_149("SNI-REALITY-FIREFOX-149", "SNI Reality · Firefox 149"),
    SNI_REALITY_SAFARI_26_5("SNI-REALITY-SAFARI-26-5", "SNI Reality · Safari 26.5"),
    SNI_REALITY_SAFARI_26_4("SNI-REALITY-SAFARI-26-4", "SNI Reality · Safari 26.4"),
    ;

    val isReality: Boolean get() = this != SNI && this != OBFUSCATION
    val usesSni: Boolean get() = this != OBFUSCATION

    companion object {
        val DEFAULT = SNI
        val DEFAULT_REALITY = SNI_REALITY_YANDEX_25
        val REALITY_METHODS = entries.filter { it.isReality }

        fun fromStrategyName(name: String): FptnBypassMethod =
            when (name) {
                "SNI-REALITY-YANDEX-26" -> SNI_REALITY_YANDEX_26_4
                "SNI-REALITY-SAFARI-26" -> SNI_REALITY_SAFARI_26_5
                else -> entries.firstOrNull { it.strategyName == name } ?: DEFAULT
            }
    }
}
