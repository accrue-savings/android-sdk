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
}

object SampleData {
//    const val merchantId = "7ac10172-c0bd-4009-a85a-972d33efbd04"
    const val merchantId = "49c7cce7-2df7-4213-9d31-887f295fee10"
    const val redirectionToken = ""
}
