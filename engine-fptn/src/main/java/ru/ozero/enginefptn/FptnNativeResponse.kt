package ru.ozero.enginefptn

import androidx.annotation.Keep

@Keep
data class FptnNativeResponse(
    val code: Int,
    val body: String,
    val error: String,
)
