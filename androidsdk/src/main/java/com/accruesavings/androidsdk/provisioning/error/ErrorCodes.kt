package com.accruesavings.androidsdk.provisioning.error

/**
 * Error codes used throughout the provisioning system
 */
object ErrorCodes {
    // Device and hardware errors
    const val ERROR_DEVICE_NOT_SUPPORTED = "ERROR_DEVICE_NOT_SUPPORTED"
    const val ERROR_HARDWARE_ID_NOT_AVAILABLE = "ERROR_HARDWARE_ID_NOT_AVAILABLE"
    
    // Wallet account errors
    const val ERROR_WALLET_ACCOUNT_NOT_FOUND = "ERROR_WALLET_ACCOUNT_NOT_FOUND"
    const val ERROR_WALLET_CREATION_FAILED = "ERROR_WALLET_CREATION_FAILED"
    
    // Provisioning errors
    const val ERROR_PUSH_PROVISIONING_FAILED = "ERROR_PUSH_PROVISIONING_FAILED"
    const val ERROR_INVALID_PROVISIONING_DATA = "ERROR_INVALID_PROVISIONING_DATA"
    const val ERROR_TOKEN_NOT_FOUND = "ERROR_TOKEN_NOT_FOUND"
    const val ERROR_ACTIVATION_REQUIRED = "ERROR_ACTIVATION_REQUIRED"
    
    // Google Pay specific errors
    const val ERROR_GOOGLE_PAY_UNAVAILABLE = "ERROR_GOOGLE_PAY_UNAVAILABLE"
    const val ERROR_PACKAGE_NOT_VERIFIED = "ERROR_PACKAGE_NOT_VERIFIED"
    
    // Launcher and activity errors
    const val ERROR_LAUNCHER_UNAVAILABLE = "ERROR_LAUNCHER_UNAVAILABLE"
    const val ERROR_CLIENT_NOT_AVAILABLE = "ERROR_CLIENT_NOT_AVAILABLE"
    
    // JavaScript bridge errors
    const val ERROR_WEBVIEW_NOT_AVAILABLE = "ERROR_WEBVIEW_NOT_AVAILABLE"
    const val ERROR_INVALID_JSON = "ERROR_INVALID_JSON"
    
    // Security errors
    const val ERROR_SECURITY_EXCEPTION = "ERROR_SECURITY_EXCEPTION"
    
    // Request constants
    const val PUSH_PROVISION_REQUEST_CODE = 1001
} 