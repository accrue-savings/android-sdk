package com.accruesavings.androidsdk

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class AccrueContextData(
    userData: AccrueUserData = AccrueUserData(),
    settingsData: AccrueSettingsData = AccrueSettingsData()
) : Parcelable {
    var userData by mutableStateOf(userData)
        private set
    var settingsData by mutableStateOf(settingsData)
        private set

    fun updateUserData(referenceId: String?, email: String?, phoneNumber: String?) {
        userData = AccrueUserData(referenceId, email, phoneNumber)
    }

    fun updateSettingsData(shouldInheritAuthentication: Boolean) {
        settingsData = AccrueSettingsData(shouldInheritAuthentication)
    }

    // Parcelable implementation
    constructor(parcel: Parcel) : this(
        userData = parcel.readParcelable(AccrueUserData::class.java.classLoader) ?: AccrueUserData(),
        settingsData = parcel.readParcelable(AccrueSettingsData::class.java.classLoader) ?: AccrueSettingsData()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(userData, flags)
        parcel.writeParcelable(settingsData, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AccrueContextData> {
        override fun createFromParcel(parcel: Parcel): AccrueContextData {
            return AccrueContextData(parcel)
        }

        override fun newArray(size: Int): Array<AccrueContextData?> {
            return arrayOfNulls(size)
        }
    }
}

data class AccrueUserData(
    val referenceId: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        referenceId = parcel.readString(),
        email = parcel.readString(),
        phoneNumber = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(referenceId)
        parcel.writeString(email)
        parcel.writeString(phoneNumber)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AccrueUserData> {
        override fun createFromParcel(parcel: Parcel): AccrueUserData {
            return AccrueUserData(parcel)
        }

        override fun newArray(size: Int): Array<AccrueUserData?> {
            return arrayOfNulls(size)
        }
    }
}

data class AccrueSettingsData(
    val shouldInheritAuthentication: Boolean = true
) : Parcelable {
    constructor(parcel: Parcel) : this(
        shouldInheritAuthentication = parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (shouldInheritAuthentication) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AccrueSettingsData> {
        override fun createFromParcel(parcel: Parcel): AccrueSettingsData {
            return AccrueSettingsData(parcel)
        }

        override fun newArray(size: Int): Array<AccrueSettingsData?> {
            return arrayOfNulls(size)
        }
    }
}

data class AccrueDeviceContextData(
    val sdk: String = "Android",
    val sdkVersion: String? = null,
    val brand: String? = null,
    val deviceName: String? = null,
    val deviceType: String? = null,
    val deviceYearClass: Double? = 0.0,
    val isDevice: Boolean = true,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val osBuildId: String? = null,
    val osInternalBuildId: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val androidId: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        sdk = parcel.readString() ?: "Android",
        sdkVersion = parcel.readString(),
        brand = parcel.readString(),
        deviceName = parcel.readString(),
        deviceType = parcel.readString(),
        deviceYearClass = parcel.readValue(Double::class.java.classLoader) as? Double,
        isDevice = parcel.readByte() != 0.toByte(),
        manufacturer = parcel.readString(),
        modelName = parcel.readString(),
        osBuildId = parcel.readString(),
        osInternalBuildId = parcel.readString(),
        osName = parcel.readString(),
        osVersion = parcel.readString(),
        androidId = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(sdk)
        parcel.writeString(sdkVersion)
        parcel.writeString(brand)
        parcel.writeString(deviceName)
        parcel.writeString(deviceType)
        parcel.writeValue(deviceYearClass)
        parcel.writeByte(if (isDevice) 1 else 0)
        parcel.writeString(manufacturer)
        parcel.writeString(modelName)
        parcel.writeString(osBuildId)
        parcel.writeString(osInternalBuildId)
        parcel.writeString(osName)
        parcel.writeString(osVersion)
        parcel.writeString(androidId)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AccrueDeviceContextData> {
        override fun createFromParcel(parcel: Parcel): AccrueDeviceContextData {
            return AccrueDeviceContextData(parcel)
        }

        override fun newArray(size: Int): Array<AccrueDeviceContextData?> {
            return arrayOfNulls(size)
        }
    }
}