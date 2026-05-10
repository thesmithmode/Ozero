package ru.ozero.engineurnetwork

enum class UrnetworkWindowType(val rawValue: String) {
    AUTO("auto"),
    QUALITY("quality"),
    SPEED("speed");

    companion object {
        fun fromRaw(raw: String?): UrnetworkWindowType =
            entries.firstOrNull { it.rawValue == raw } ?: AUTO
    }
}
