package ru.ozero.app.ui.splittunnel

import androidx.compose.ui.graphics.ImageBitmap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val icon: ImageBitmap? = null,
)
