package ru.ozero.engineurnetwork

enum class UrnetworkProvideNetworkMode(val rawValue: String) {
    WIFI("wifi"),
    ALL("all");

    companion object {
        fun fromRaw(raw: String?): UrnetworkProvideNetworkMode =
            entries.firstOrNull { it.rawValue == raw } ?: WIFI
    }
}
