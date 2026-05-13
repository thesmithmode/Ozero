package ru.ozero.app.ui.utils

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 МБ"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return "%.2f ГБ".format(gb)
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1.0) return "%.1f МБ".format(mb)
    val kb = bytes / 1024.0
    return "%.0f КБ".format(kb)
}
