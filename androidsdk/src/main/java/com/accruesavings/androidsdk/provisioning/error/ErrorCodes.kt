package com.accruesavings.androidsdk.provisioning.error

/**
 * Error codes used throughout the provisioning system
 */
object ErrorCodes {
    // Device and hardware errors
    const val ERROR_DEVICE_NOT_SUPPORTED = "ERROR_DEVICE_NOT_SUPPORTED"
    const val ERROR_HARDWARE_ID_NOT_AVAILABLE = "ERROR_HARDWARE_ID_NOT_AVAILABLE"
    const val ERROR_DEVICE_INFO_UNAVAILABLE = "ERROR_DEVICE_INFO_UNAVAILABLE"
    
    // Wallet account errors
    const val ERROR_WALLET_ACCOUNT_NOT_FOUND = "ERROR_WALLET_ACCOUNT_NOT_FOUND"
    const val ERROR_WALLET_CREATION_FAILED = "ERROR_WALLET_CREATION_FAILED"
    const val ERROR_NO_ACTIVE_WALLET = "ERROR_NO_ACTIVE_WALLET"
    
    // Provisioning errors
    const val ERROR_PUSH_PROVISIONING_FAILED = "ERROR_PUSH_PROVISIONING_FAILED"
    const val ERROR_INVALID_PROVISIONING_DATA = "ERROR_INVALID_PROVISIONING_DATA"
    const val ERROR_TOKEN_NOT_FOUND = "ERROR_TOKEN_NOT_FOUND"
    const val ERROR_ACTIVATION_REQUIRED = "ERROR_ACTIVATION_REQUIRED"
    const val ERROR_USER_CANCELLED = "ERROR_USER_CANCELLED"
    
    // Google Pay specific errors
    const val ERROR_GOOGLE_PAY_UNAVAILABLE = "ERROR_GOOGLE_PAY_UNAVAILABLE"
    const val ERROR_PACKAGE_NOT_VERIFIED = "ERROR_PACKAGE_NOT_VERIFIED"
    const val ERROR_GOOGLE_PAY_NOT_DEFAULT = "ERROR_GOOGLE_PAY_NOT_DEFAULT"
    
    // TapAndPay specific error codes (matching Google's status codes)
    const val ERROR_TAP_AND_PAY_UNAVAILABLE = "ERROR_TAP_AND_PAY_UNAVAILABLE"
    const val ERROR_TAP_AND_PAY_ATTESTATION_ERROR = "ERROR_TAP_AND_PAY_ATTESTATION_ERROR"
    const val ERROR_TAP_AND_PAY_INVALID_TOKEN_STATE = "ERROR_TAP_AND_PAY_INVALID_TOKEN_STATE"
    const val ERROR_TAP_AND_PAY_TOKEN_NOT_FOUND = "ERROR_TAP_AND_PAY_TOKEN_NOT_FOUND"
    const val ERROR_TAP_AND_PAY_NO_ACTIVE_WALLET = "ERROR_TAP_AND_PAY_NO_ACTIVE_WALLET"
    
    // Launcher and activity errors
    const val ERROR_LAUNCHER_UNAVAILABLE = "ERROR_LAUNCHER_UNAVAILABLE"
    const val ERROR_CLIENT_NOT_AVAILABLE = "ERROR_CLIENT_NOT_AVAILABLE"
    const val ERROR_ACTIVITY_NOT_AVAILABLE = "ERROR_ACTIVITY_NOT_AVAILABLE"
    
    // JavaScript bridge errors
    const val ERROR_WEBVIEW_NOT_AVAILABLE = "ERROR_WEBVIEW_NOT_AVAILABLE"
    const val ERROR_INVALID_JSON = "ERROR_INVALID_JSON"
    
    // Security errors
    const val ERROR_SECURITY_EXCEPTION = "ERROR_SECURITY_EXCEPTION"
    
    // Environment and configuration errors
    const val ERROR_ENVIRONMENT_UNAVAILABLE = "ERROR_ENVIRONMENT_UNAVAILABLE"
    const val ERROR_INVALID_ENVIRONMENT = "ERROR_INVALID_ENVIRONMENT"
    
    // Token management errors
    const val ERROR_TOKEN_STATUS_UNAVAILABLE = "ERROR_TOKEN_STATUS_UNAVAILABLE"
    const val ERROR_TOKEN_LIST_UNAVAILABLE = "ERROR_TOKEN_LIST_UNAVAILABLE"
    const val ERROR_TOKEN_VIEW_FAILED = "ERROR_TOKEN_VIEW_FAILED"
    const val ERROR_TOKEN_DELETE_FAILED = "ERROR_TOKEN_DELETE_FAILED"
} 