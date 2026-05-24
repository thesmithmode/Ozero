package ru.ozero.enginesingbox

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SingboxStats(
    val txRateProxy: Long = 0L,
    val rxRateProxy: Long = 0L,
    val txTotal: Long = 0L,
    val rxTotal: Long = 0L,
    val activeConnections: Int = 0,
) : Parcelable
