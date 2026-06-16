package ru.ozero.enginewarp

import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable

data class WarpTurnOnResult(
    val handle: Int,
    val socketV4: ParcelFileDescriptor?,
    val socketV6: ParcelFileDescriptor?,
) : Parcelable {

    override fun describeContents(): Int =
        (socketV4?.describeContents() ?: 0) or (socketV6?.describeContents() ?: 0)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(handle)
        parcel.writeInt(if (socketV4 != null) 1 else 0)
        socketV4?.writeToParcel(parcel, flags)
        parcel.writeInt(if (socketV6 != null) 1 else 0)
        socketV6?.writeToParcel(parcel, flags)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<WarpTurnOnResult> = object : Parcelable.Creator<WarpTurnOnResult> {
            override fun createFromParcel(parcel: Parcel): WarpTurnOnResult {
                val handle = parcel.readInt()
                val v4 = if (parcel.readInt() != 0) {
                    ParcelFileDescriptor.CREATOR.createFromParcel(parcel)
                } else {
                    null
                }
                val v6 = if (parcel.readInt() != 0) {
                    ParcelFileDescriptor.CREATOR.createFromParcel(parcel)
                } else {
                    null
                }
                return WarpTurnOnResult(handle, v4, v6)
            }

            override fun newArray(size: Int): Array<WarpTurnOnResult?> = arrayOfNulls(size)
        }
    }
}
