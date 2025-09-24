package com.accruesavings.androidsdk

import android.os.Build

data class AccrueContextData(
    val userData: AccrueUserData = AccrueUserData(),
    val settingsData: AccrueSettingsData = AccrueSettingsData()
)

data class AccrueUserData(
    val referenceId: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val additionalData: Map<String, String> = emptyMap()
)

data class AccrueSettingsData(
    val shouldInheritAuthentication: Boolean = true
)

data class AccrueDeviceContextData(
    val sdk: String = "Android",
    val sdkVersion: String? = "v1.2.5",
    val brand: String? = Build.BRAND,
    val deviceName: String? = Build.DEVICE,
    val deviceType: String? = Build.PRODUCT,
    val isDevice: Boolean = true,
    val manufacturer: String? = Build.MANUFACTURER,
    val modelName: String? = Build.MODEL,
    val osBuildId: String? = Build.ID,
    val osInternalBuildId: String? = Build.DISPLAY,
    val osName: String? = "Android",
    val osVersion: String? = Build.VERSION.RELEASE,
)