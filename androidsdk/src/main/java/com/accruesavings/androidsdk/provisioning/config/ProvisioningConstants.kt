package com.accruesavings.androidsdk.provisioning.config

/**
 * Constants used throughout the provisioning system
 */
object ProvisioningConstants {
    
    // Request codes for activity results
    const val PUSH_PROVISION_REQUEST_CODE = 1001
    const val CREATE_WALLET_REQUEST_CODE = 1002
    const val DELETE_TOKEN_REQUEST_CODE = 1003
    const val SET_DEFAULT_PAYMENTS_REQUEST_CODE = 1005
    const val VIEW_TOKEN_REQUEST_CODE = 1007
    
    
    // Logging tags
    object LogTags {
        const val MAIN = "GoogleWalletProvisioning"
        const val CLIENT_MANAGER = "TapAndPayClientManager"
        const val PROVISIONING_SERVICE = "PushProvisioningService"
        const val DEVICE_SERVICE = "DeviceInfoService"
        const val ERROR_HANDLER = "ErrorHandler"
    }
} 