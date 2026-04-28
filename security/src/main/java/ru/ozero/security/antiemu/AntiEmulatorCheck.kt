package ru.ozero.security.antiemu

data class DeviceFingerprint(
    val brand: String,
    val device: String,
    val product: String,
    val model: String,
    val manufacturer: String,
    val hardware: String,
    val fingerprint: String,
)

class AntiEmulatorCheck(
    private val provider: () -> DeviceFingerprint = { realFingerprint() },
) {

    fun isEmulator(): Boolean = matchedReasons().isNotEmpty()

    @Suppress("ComplexCondition")
    fun matchedReasons(): List<String> {
        val f = provider()
        val reasons = mutableListOf<String>()
        if (f.fingerprint.startsWith("generic") ||
            f.fingerprint.startsWith("unknown") ||
            f.fingerprint.contains("vbox")
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
