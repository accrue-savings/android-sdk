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

object SampleData {
//    const val merchantId = "e510593e-b975-4f94-baa6-2ae42a8fa6f5"
    const val merchantId = "d0069102-e4a1-410b-ad82-ebe278d7785d" //snipes in sandbox
    const val redirectionToken = ""
    const val phoneNumber = "2035559382"
    const val referenceId = "othecos-b123"
}
