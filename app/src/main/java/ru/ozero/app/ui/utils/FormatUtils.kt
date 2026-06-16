package ru.ozero.app.ui.utils

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 МБ"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return String.format(Locale.US, "%.2f ГБ", gb)
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1.0) return String.format(Locale.US, "%.1f МБ", mb)
    val kb = bytes / 1024.0
    return String.format(Locale.US, "%.0f КБ", kb)
}
