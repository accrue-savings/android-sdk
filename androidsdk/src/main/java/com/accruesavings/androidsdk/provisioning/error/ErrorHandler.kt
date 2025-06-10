package com.accruesavings.androidsdk.provisioning.error

import android.util.Log
import android.webkit.WebView
import org.json.JSONObject

/**
 * Centralized error handling for provisioning operations
 */
class ErrorHandler(private val webView: WebView?) {
    
    companion object {
        private const val TAG = "ErrorHandler"
    }
    
    /**
     * Handle any provisioning error
     */
    fun handleError(error: ProvisioningError) {
        Log.e(TAG, "Provisioning error: $error")
        
        // Special handling for package verification errors
        if (error.code == ErrorCodes.ERROR_PACKAGE_NOT_VERIFIED) {
            handlePackageVerificationError(error)
            return
        }
        
        // General error notification
        notifyJavaScript(error.toJson())
    }
    
    /**
     * Handle package verification errors with detailed troubleshooting
     */
    private fun handlePackageVerificationError(error: ProvisioningError) {
        val troubleshootingMessage = """
            ERROR: Google Pay package verification failed (Error 15009)
            
            SOLUTION REQUIRED:
            1. Register your app with Google Pay Console at: https://console.developers.google.com/
            2. Complete your Business Profile 
            3. Navigate to "Google Pay API" → "Get Started"
            4. Add your Android app package name
            5. Upload screenshots of your TEST integration
            6. Submit for approval
            
            Note: This error occurs because Google Pay requires apps to be explicitly whitelisted 
            before accessing TapAndPay APIs. Your app package isn't registered with Google Pay yet.
            
            For testing, you can also:
            - Use TEST environment with WalletConstants.ENVIRONMENT_TEST
            - Use sample cards for testing: https://developers.google.com/pay/api/android/guides/test-and-deploy/test-with-sample-cards
        """.trimIndent()
        
        Log.e(TAG, troubleshootingMessage)
        
        // Notify JavaScript with troubleshooting info
        val errorWithTroubleshooting = JSONObject().apply {
            put("error", true)
            put("code", error.code)
            put("message", error.message)
            put("troubleshooting", troubleshootingMessage)
            put("consoleUrl", "https://console.developers.google.com/")
            put("timestamp", error.timestamp)
        }
        
        notifyJavaScript(errorWithTroubleshooting.toString())
    }
    
    /**
     * Handle simple error messages
     */
    fun handleErrorMessage(message: String) {
        val error = ProvisioningError(
            code = "ERROR_GENERAL",
            message = message
        )
        handleError(error)
    }
    
    /**
     * Notify JavaScript of errors
     */
    private fun notifyJavaScript(errorJson: String) {
        webView?.post {
            webView.evaluateJavascript(
                "if (window.AccrueWallet && window.AccrueWallet.onError) { " +
                "window.AccrueWallet.onError($errorJson); }", null
            )
        }
    }
    
    /**
     * Notify observers of errors (for internal SDK use)
     */
    fun notifyError(error: ProvisioningError) {
        // This could be expanded to notify multiple observers
        handleError(error)
    }
} 