package com.accruesavings.androidsdk

object AppConstants {
    // Mocked merchantId
    const val merchantId: String = "08b13e48-06be-488f-9a93-91f59d94f30d"
    const val redirectionToken = "redirection-token"
}

object AccrueWebEvents {
    const val eventHandlerName: String = "AccrueWallet"
    const val accrueWalletSignInPerformedMessageKey: String = "AccrueWallet::SignInPerformed"
    const val accrueWalletSignInButtonClickedKey: String = "AccrueWallet::SignInButtonClicked"
    const val accrueWalletRegisterButtonClickedKey: String = "AccrueWallet::RegisterButtonClicked"
    const val accrueWalletUpdateNumberClickedKey: String = "AccrueWallet::UpdateNumberClicked"
    const val accrueWalletContextChangedEventKey: String = "AccrueWallet::ContextChanged"
    const val accrueWalletParentAppEventKey: String = "AccrueWallet::ParentAppEvent"
    
    // Google Wallet Provisioning Events
    const val accrueWalletGoogleProvisioningRequestedKey: String = "AccrueWallet::GoogleWalletProvisioningRequested"
    const val accrueWalletGoogleProvisioningResponseKey: String = "AccrueWallet::GoogleWalletProvisioningResponse"
    const val accrueWalletGoogleWalletProvisioningIsSupportedRequestedKey: String = "AccrueWallet::GoogleWalletProvisioningIsSupportedRequested"
    const val accrueWalletGoogleWalletProvisioningWalletInformationRequestedKey: String = "AccrueWallet::GoogleWalletProvisioningWalletInformationRequested"
    
    // Google Wallet Provisioning WebView Functions
    const val generateGoogleWalletProvisioningTokenFunction: String = "__GENERATE_GOOGLE_WALLET_PUSH_PROVISIONING_TOKEN"
    const val googleWalletProvisioningResultFunction: String = "__GOOGLE_WALLET_PROVISIONING_RESULT"
    const val googleWalletProvisioningIsSupportedResponseFunction: String = "__GOOGLE_WALLET_PROVISIONING_IS_SUPPORTED_RESPONSE"
    const val googleWalletProvisioningWalletInformationResponseFunction: String = "__GOOGLE_WALLET_PROVISIONING_WALLET_INFORMATION_RESPONSE"
}

object SampleData {
    const val url = "http://localhost:5173/webview" // requires: adb reverse tcp:5173 tcp:5173
    const val merchantId = "61afbcef-0fd7-4b2e-92b9-d6baf123dec4"
    const val redirectionToken = ""
    const val phoneNumber = "2125555813"
    const val referenceId = "bcaNNF9mtF2tc1SjxW7NuTm353"
    const val stableReferenceId = ""
}
