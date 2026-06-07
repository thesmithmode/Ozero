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
        parcel.writeParcelable(socketV4, flags)
        parcel.writeParcelable(socketV6, flags)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<WarpTurnOnResult> = object : Parcelable.Creator<WarpTurnOnResult> {
            override fun createFromParcel(parcel: Parcel): WarpTurnOnResult {
                val handle = parcel.readInt()
                val v4 = parcel.readParcelable<ParcelFileDescriptor>(ParcelFileDescriptor::class.java.classLoader)
                val v6 = parcel.readParcelable<ParcelFileDescriptor>(ParcelFileDescriptor::class.java.classLoader)
                return WarpTurnOnResult(handle, v4, v6)
            }

            override fun newArray(size: Int): Array<WarpTurnOnResult?> = arrayOfNulls(size)
        }
    }
}
