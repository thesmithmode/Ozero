package ru.ozero.security.antiemu

/**
 * Источник Build-полей для тестируемости.
 */
data class DeviceFingerprint(
    val brand: String,
    val device: String,
    val product: String,
    val model: String,
    val manufacturer: String,
    val hardware: String,
    val fingerprint: String,
)

/**
 * Эвристики обнаружения эмулятора по Build-полям. Не идеально (Genymotion с custom build
 * fingerprint обходит), но отсекает 95% setup'ов anti-cheat / DPI-bypass-fingerprinting.
 */
class AntiEmulatorCheck(
    private val provider: () -> DeviceFingerprint = { realFingerprint() },
) {

    fun isEmulator(): Boolean = matchedReasons().isNotEmpty()

    /** Список сработавших эвристик — для диагностики и логов. */
    @Suppress("ComplexCondition")
    // Эвристики содержат группы fingerprint-substring проверок (4+ OR на категорию):
    // generic/unknown/vbox/test-keys, google_sdk/Emulator/SDK, Genymotion/unknown и т.п.
    // Дробление потеряет атомарность правил детекции.
    fun matchedReasons(): List<String> {
        val f = provider()
        val reasons = mutableListOf<String>()
        if (f.fingerprint.startsWith("generic") ||
            f.fingerprint.startsWith("unknown") ||
            f.fingerprint.contains("vbox") ||
            f.fingerprint.contains("test-keys")
        ) {
            reasons += "fingerprint=${f.fingerprint}"
        }
        if (f.model.contains("google_sdk", ignoreCase = true) ||
            f.model.contains("Emulator", ignoreCase = true) ||
            f.model.contains("Android SDK built for", ignoreCase = true)
        ) {
            reasons += "model=${f.model}"
        }
        if (f.manufacturer.contains("Genymotion", ignoreCase = true) ||
            f.manufacturer.equals("unknown", ignoreCase = true)
        ) {
            reasons += "manufacturer=${f.manufacturer}"
        }
        if (f.brand.startsWith("generic") && f.device.startsWith("generic")) {
            reasons += "brand+device=generic"
        }
        if (f.product.contains("sdk_gphone") || f.product.equals("google_sdk")) {
            reasons += "product=${f.product}"
        }
        if (
            f.hardware.contains("goldfish") ||
            f.hardware.contains("ranchu") ||
            f.hardware.contains("vbox86")
        ) {
            reasons += "hardware=${f.hardware}"
        }
        return reasons
    }

    private companion object {
        fun realFingerprint() = DeviceFingerprint(
            brand = android.os.Build.BRAND ?: "",
            device = android.os.Build.DEVICE ?: "",
            product = android.os.Build.PRODUCT ?: "",
            model = android.os.Build.MODEL ?: "",
            manufacturer = android.os.Build.MANUFACTURER ?: "",
            hardware = android.os.Build.HARDWARE ?: "",
            fingerprint = android.os.Build.FINGERPRINT ?: "",
        )
    }
}
