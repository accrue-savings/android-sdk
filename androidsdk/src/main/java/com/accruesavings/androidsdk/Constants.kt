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
//    const val merchantId = "d6040b84-601c-4661-8ff0-2fea45687fb4"
    const val merchantId = "d0069102-e4a1-410b-ad82-ebe278d7785d" //snipes in sandbox
    const val redirectionToken = ""
}
