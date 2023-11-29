package org.ole.planet.myplanet.model

import android.os.Parcel
import android.os.Parcelable

class Download() : Parcelable {
    @JvmField
    var fileName: String? = null
    @JvmField
    var progress: Int = 0
    @JvmField
    var currentFileSize: Int = 0
    @JvmField
    var totalFileSize: Int = 0
    @JvmField
    var completeAll: Boolean = false
    @JvmField
    var failed: Boolean = false
    @JvmField
    var message: String? = null
    @JvmField
    var fileUrl: String? = null

    constructor(parcel: Parcel) : this() {
        fileName = parcel.readString()
        progress = parcel.readInt()
        currentFileSize = parcel.readInt()
        totalFileSize = parcel.readInt()
        completeAll = parcel.readByte() != 0.toByte()
        failed = parcel.readByte() != 0.toByte()
        message = parcel.readString()
        fileUrl = parcel.readString()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(fileName)
        parcel.writeInt(progress)
        parcel.writeInt(currentFileSize)
        parcel.writeInt(totalFileSize)
        parcel.writeByte(if (completeAll) 1 else 0)
        parcel.writeByte(if (failed) 1 else 0)
        parcel.writeString(message)
        parcel.writeString(fileUrl)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Download> {
        override fun createFromParcel(parcel: Parcel): Download {
            return Download(parcel)
        }

        override fun newArray(size: Int): Array<Download?> {
            return arrayOfNulls(size)
        }
    }
}
