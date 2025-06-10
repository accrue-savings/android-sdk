package com.accruesavings.androidsdk.provisioning.device

/**
 * Represents device information used for provisioning
 */
data class DeviceInfo(
    val stableHardwareId: String,
    val deviceType: String,
    val osVersion: String,
    val walletAccountId: String
) {
    /**
     * Convert to JSON representation for JavaScript bridge
     */
    fun toJson(): String {
        return """
        {
            "stableHardwareId": "$stableHardwareId",
            "deviceType": "$deviceType", 
            "osVersion": "$osVersion",
            "walletAccountId": "$walletAccountId"
        }
        """.trimIndent()
    }
} 