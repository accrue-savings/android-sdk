package com.accruesavings.accruepaysdk

import android.os.Parcel
import android.os.Parcelable

data class ContextData(
    val referenceId: String,
    val phoneNumber: String,
    val email: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    ) {
        // Read values from Parcel
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(referenceId)
        parcel.writeString(phoneNumber)
        parcel.writeString(email)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ContextData> {
        override fun createFromParcel(parcel: Parcel): ContextData {
            return ContextData(parcel)
        }

        override fun newArray(size: Int): Array<ContextData?> {
            return arrayOfNulls(size)
        }
    }
}