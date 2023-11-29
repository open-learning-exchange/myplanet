package org.ole.planet.myplanet.model

import android.os.Parcel
import android.os.Parcelable

class Download() : Parcelable {
    var fileName: String? = null
    var progress: Int = 0
    var currentFileSize: Int = 0
    var totalFileSize: Int = 0
    var completeAll: Boolean = false
    var failed: Boolean = false
    var message: String? = null
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
