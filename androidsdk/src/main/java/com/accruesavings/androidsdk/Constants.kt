package com.accruesavings.androidsdk

object AppConstants {
    // Mocked testing environment
    const val sandboxUrl: String = "https://embed-sandbox.accruesavings.com/webview"
    const val productionUrl: String = "https://embed.accruesavings.com/webview"
    // Mocked merchantId
    const val merchantId: String = "08b13e48-06be-488f-9a93-91f59d94f30d"
    const val redirectionToken = "redirection-token"
}

object AccrueWebEvents {
    const val eventHandlerName: String = "AccrueWallet"
    const val accrueWalletSignInPerformedMessageKey: String = "AccrueWallet::SignInPerformed"
    const val accrueWalletContextChangedEventKey: String = "AccrueWallet::ContextChanged"
    const val accrueWalletParentAppEventKey: String = "AccrueWallet::ParentAppEvent"
    
    // Google Wallet Provisioning Events
    const val accrueWalletGoogleProvisioningRequestedKey: String = "AccrueWallet::GoogleWalletProvisioningRequested"
    const val accrueWalletGoogleProvisioningResponseKey: String = "AccrueWallet::GoogleWalletProvisioningResponse"
    
    // Google Wallet Provisioning WebView Functions
    const val generateGoogleWalletProvisioningTokenFunction: String = "__GENERATE_GOOGLE_WALLET_PUSH_PROVISIONING_TOKEN"
    const val googleWalletProvisioningSuccessFunction: String = "__GOOGLE_WALLET_PROVISIONING_SUCCESS"
    const val googleWalletProvisioningErrorFunction: String = "__GOOGLE_WALLET_PROVISIONING_ERROR"
}

object TestConfig {
    // Master switch to enable/disable all testing features
    @Volatile
    var enableTestMode: Boolean = false
    
    // Google Wallet Provisioning specific test configurations
    object GoogleWalletProvisioning {
        // If true and test mode is enabled, will bypass actual Google Pay API calls
        @Volatile
        var mockGooglePayApi: Boolean = true
        
        // Controls whether mock operations succeed or fail
        @Volatile
        var mockOperationsSucceed: Boolean = true
        
        // Simulated delays in milliseconds (to mimic real-world timing)
        @Volatile
        var mockOperationDelay: Long = 1500
        
        // Custom error code for mock failures
        @Volatile
        var mockErrorCode: String = "MOCK_ERROR"
        
        // Custom error message for mock failures
        @Volatile
        var mockErrorMessage: String = "This is a mocked error for testing purposes"
    }
}

object SampleData {
//    const val merchantId = "e510593e-b975-4f94-baa6-2ae42a8fa6f5"
    const val merchantId = "d0069102-e4a1-410b-ad82-ebe278d7785d" //snipes in sandbox
    const val redirectionToken = ""
}
