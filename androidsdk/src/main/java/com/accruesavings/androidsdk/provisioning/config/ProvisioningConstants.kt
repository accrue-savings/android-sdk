package com.accruesavings.androidsdk.provisioning.config

/**
 * Constants used throughout the provisioning system
 */
object ProvisioningConstants {
    
    // Request codes for activity results
    const val PUSH_PROVISION_REQUEST_CODE = 1001
    const val CREATE_WALLET_REQUEST_CODE = 1002
    const val DELETE_TOKEN_REQUEST_CODE = 1003
    
    // Network type constants
    object Networks {
        const val VISA = "VISA"
        const val MASTERCARD = "MASTERCARD"
        const val MASTER = "MASTER"
        const val AMEX = "AMEX"
        const val AMERICAN_EXPRESS = "AMERICAN_EXPRESS"
        const val DISCOVER = "DISCOVER"
        const val GOOGLE = "GOOGLE"
        const val EFTPOS = "EFTPOS"
        const val INTERAC = "INTERAC"
        const val OBERTHUR = "OBERTHUR"
        const val PAYPAL = "PAYPAL"
    }
    
    // Device type constants
    object DeviceTypes {
        const val MOBILE_PHONE = "MOBILE_PHONE"
        const val TABLET = "TABLET"
        const val WATCH = "WATCH"
    }
    
    // Default values
    object Defaults {
        const val DEFAULT_CARD_DISPLAY_NAME = "Card"
        const val DEFAULT_LAST_FOUR_DIGITS = "0000"
        const val DEFAULT_COUNTRY_CODE = "US"
        const val UNKNOWN_VERSION = "unknown"
    }
    
    // Logging tags
    object LogTags {
        const val MAIN = "GoogleWalletProvisioning"
        const val CLIENT_MANAGER = "TapAndPayClientManager"
        const val PROVISIONING_SERVICE = "PushProvisioningService"
        const val DEVICE_SERVICE = "DeviceInfoService"
        const val ERROR_HANDLER = "ErrorHandler"
    }
} 