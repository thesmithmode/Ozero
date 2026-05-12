package ru.ozero.engineurnetwork

enum class UrnetworkProvideControlMode(val rawValue: String) {
    AUTO("auto"),
    ALWAYS("always");

    companion object {
        fun fromRaw(raw: String?): UrnetworkProvideControlMode =
            entries.firstOrNull { it.rawValue == raw } ?: ALWAYS
    }
}
