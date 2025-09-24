package com.accruesavings.androidsdk

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONException
import org.json.JSONObject
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

fun contextToJson(contextData: AccrueContextData?): String {
    if(contextData === null) {
        return "";
    }
    val userData = contextData.userData
    val settingsData = contextData.settingsData
    val deviceContextData = AccrueDeviceContextData()
    val finalContextData = JSONObject(mapOf(
        "contextData" to mapOf(
            "userData" to mapOf(
                "referenceId" to userData.referenceId,
                "email" to userData.email,
                "phoneNumber" to userData.phoneNumber,
                "additionalData" to userData.additionalData
            ),
            "settingsData" to mapOf(
                "shouldInheritAuthentication" to settingsData.shouldInheritAuthentication
            ),
            "deviceData" to mapOf(
                "sdk" to deviceContextData.sdk,
                "sdkVersion" to deviceContextData.sdkVersion,
                "brand" to deviceContextData.brand,
                "deviceName" to deviceContextData.deviceName,
                "deviceType" to deviceContextData.deviceType,
                "isDevice" to deviceContextData.isDevice,
                "manufacturer" to deviceContextData.manufacturer,
                "modelName" to deviceContextData.modelName,
                "osBuildId" to deviceContextData.osBuildId,
                "osInternalBuildId" to deviceContextData.osInternalBuildId,
                "osName" to deviceContextData.osName,
                "osVersion" to deviceContextData.osVersion,
            )
        ))).toString()
    Log.i("AccrueWebView", "Constructed accrue context data $finalContextData")
    return finalContextData;
}

class AccrueWebView @JvmOverloads constructor(
    context: Context,
    private var url: String,
    private var contextData: AccrueContextData? = null,
    private var onAction: Map<AccrueAction, () -> Unit> = emptyMap()
) : WebView(context) {

    private var webAppInterface: WebAppInterface? = null
    private var provisioningMain: ProvisioningMain? = null
    internal var hasInitialLoadCompleted = false

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        overScrollMode = OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true

        if (WebViewFeature.isFeatureSupported(
                WebViewFeature.PAYMENT_REQUEST)) {
            WebSettingsCompat.setPaymentRequestEnabled(settings, true)
        }
        // local storage does not work without it
        settings.domStorageEnabled = true
//        settings.setSupportMultipleWindows(true)
//        settings.javaScriptCanOpenWindowsAutomatically = true
        webViewClient = AccrueWebViewClient(this)
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d("WebViewConsole", "${consoleMessage.message()} at line ${consoleMessage.lineNumber()} in ${consoleMessage.sourceId()}")
                return true
            }
        }
        
        // Note: Google Wallet Provisioning initialization is handled by AccrueWallet
        // to avoid duplicate initialization
        
        // Add JavaScript interface and context data
        webAppInterface = WebAppInterface(this.onAction, contextData, this)
        addJavascriptInterface(webAppInterface!!, AccrueWebEvents.eventHandlerName)

        // Load URL
        loadUrl(url)
    }

    private class WebAppInterface(
        private val onAction: Map<AccrueAction, () -> Unit> = emptyMap(),
        private var _contextData: AccrueContextData?,
        private val webView: AccrueWebView
    ) {
        var contextData: AccrueContextData?
            get() = _contextData
            set(value) {
                _contextData = value
            }

        @JavascriptInterface
        fun getParentType(): String {
            return "android"
        }

        @JavascriptInterface
        fun getContextData(): String {
            return contextToJson(contextData)
        }

        @JavascriptInterface
        fun postMessage(message: String) {
            try {
                Log.i("AccrueWebView", "FFW message received: $message")
                val jsonObject = JSONObject(message)
                val key = jsonObject.optString("key")

                when (key) {
                    AccrueWebEvents.accrueWalletSignInButtonClickedKey -> onAction[AccrueAction.SignInButtonClicked]?.invoke()
                    AccrueWebEvents.accrueWalletRegisterButtonClickedKey -> onAction[AccrueAction.RegisterButtonClicked]?.invoke()
                    AccrueWebEvents.accrueWalletGoogleWalletProvisioningIsSupportedRequestedKey -> {
                        Log.i("AccrueWebView", "Google Wallet Provisioning Is Supported Requested")
                        webView.sendGoogleWalletProvisioningIsSupportedStatus(isAutomatic = false)
                    }
                    AccrueWebEvents.accrueWalletGoogleProvisioningRequestedKey -> {
                        Log.i("AccrueWebView", "Google Wallet Provisioning Requested")
                        onAction[AccrueAction.GoogleWalletProvisioningRequested]?.invoke()
                        
                        Log.i("AccrueWebView", "provisioningMain is ${if (webView.provisioningMain == null) "NULL" else "AVAILABLE"}")
                        // Get device info and send it to WebView to generate token
                        webView.provisioningMain?.let { provisioning ->
                            provisioning.getDeviceInfo { deviceInfo ->
                                deviceInfo?.let {
                                    val deviceInfoJson = JSONObject().apply {
                                        put("deviceId", it.deviceId)
                                        put("deviceType", it.deviceType)
                                        put("provisioningAppVersion", it.provisioningAppVersion)
                                        put("walletAccountId", it.walletAccountId)
                                        
                                        // Add mocked data if it contains tokenId
                                        val originalData = jsonObject.optJSONObject("data")
                                        if (originalData != null && originalData.has("tokenId")) {
                                            put("mockedData", originalData)
                                            Log.i("AccrueWebView", "Added mocked data to device info with tokenId")
                                        }
                                    }.toString()
                                    
                                    Log.i("AccrueWebView", "Device Info being sent to web: $deviceInfoJson")
                                    Log.i("AccrueWebView", "Device ID: ${it.deviceId}")
                                    Log.i("AccrueWebView", "Wallet Account ID: ${it.walletAccountId}")
                                    // We need to get this to the main thread to evaluate JavaScript
                                    webView.post {
                                        webView.evaluateJavascript("""
                                            if (typeof window !== "undefined" && typeof window?.["${AccrueWebEvents.generateGoogleWalletProvisioningTokenFunction}"] === "function") {
                                                window?.["${AccrueWebEvents.generateGoogleWalletProvisioningTokenFunction}"]?.($deviceInfoJson);
                                            }
                                        """.trimIndent(), null)
                                    }
                                } ?: provisioning.notifyError(
                                    com.accruesavings.androidsdk.provisioning.error.ErrorCodes.ERROR_DEVICE_INFO_UNAVAILABLE,
                                    "Device info is null, cannot proceed with provisioning request."
                                )
                            }
                        }
                    }
                    AccrueWebEvents.accrueWalletGoogleProvisioningResponseKey -> {
                        Log.i("AccrueWebView", "Google Wallet Provisioning Response Received")
                        Log.i("AccrueWebView", "Message: $message")
                        webView.provisioningMain?.startPushProvisioning(message)
                    }
                    AccrueWebEvents.accrueWalletGoogleWalletProvisioningWalletInformationRequestedKey -> {
                        Log.i("AccrueWebView", "Google Wallet Provisioning Wallet Information Request Received")
                        Log.i("AccrueWebView", "Message: $message")
                        webView.provisioningMain?.getWalletInfo { response ->
                            Log.i("AccrueWebView", "Token status response received: $response")
                            webView.post {
                                webView.evaluateJavascript("""
                                    if (typeof window !== "undefined" && typeof window?.["${AccrueWebEvents.googleWalletProvisioningWalletInformationResponseFunction}"] === "function") {
                                        window?.["${AccrueWebEvents.googleWalletProvisioningWalletInformationResponseFunction}"]?.($response);
                                    }
                                """.trimIndent(), null)
                            }
                        }
                    }
                    else -> Log.w("AccrueWebView", "Unknown message type: $key")
                }
            } catch (e: JSONException) {
                Log.e("AccrueWebView", "Error parsing JSON message: ${e.message}")
            }
        }
    }

    // for links that open in new window to work
    private class AccrueWebViewClient(private val webView: AccrueWebView) : WebViewClient() {
        val TAG: String = "AccrueWebViewClient"

        fun isAppDeepLink(url: String): Boolean {
            if (url.isBlank()) return false

            return try {
                val scheme = url.toUri().scheme?.lowercase()
                scheme != null && scheme !in listOf("http", "https", "mailto", "tel", "ftp", "sms")
            } catch (e: Exception) {
                false
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()

            // Check if the URL is the same as the current one (e.g., for reload)
            if (url == view.url) {
                Log.i(TAG, "Reloading the same URL: $url")
                return false // Allow the WebView to handle reloading the same URL
            }

            // Allow localhost and local file URLs
            if (url.startsWith("http://localhost") || url.startsWith("file://")) {
                Log.i(TAG, "Handling local URL: $url")
                return false // Let the WebView handle local URLs
            }

            // Handle deeplinks
            if (isAppDeepLink(url)) {
                openDeepLink(view.context, url)
                return true // Prevent WebView from handling deeplinks
            }

            // Handle external links using Chrome Custom Tabs
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                Log.i(TAG, "Opening external link in in-app browser: $url")
                openInAppBrowser(view.context, url)
                return true // Prevent WebView from handling external links
            }

            return false
        }
        fun openDeepLink(context: Context, url: String) {
            try {
                val intent = if (url.startsWith("intent://")) {
                    // Parse intent:// URLs
                    parseIntentUrl(url)
                } else {
                    // Handle regular deep links
                    Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                }
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("Deeplink", "No activity found to handle deep link: $url", e)
                // Try to handle the fallback URL if it's an intent:// URL
                if (url.startsWith("intent://")) {
                    handleIntentFallback(context, url)
                }
            } catch (e: Exception) {
                Log.e("Deeplink", "Error handling deep link: ${e.message}", e)
            }
        }
        
        private fun parseIntentUrl(url: String): Intent {
            try {
                // Parse intent:// URLs using Intent.parseUri
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                
                // Add flags to ensure proper handling
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                
                return intent
            } catch (e: Exception) {
                Log.e("Deeplink", "Error parsing intent URL: ${e.message}", e)
                // Fallback to regular intent handling
                return Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }
        
        private fun handleIntentFallback(context: Context, url: String) {
            try {
                // Try to extract fallback URL from intent:// URL
                val fallbackUrl = extractFallbackUrl(url)
                if (fallbackUrl != null) {
                    Log.i("Deeplink", "Using fallback URL: $fallbackUrl")
                    openInAppBrowser(context, fallbackUrl)
                } else {
                    Log.w("Deeplink", "No fallback URL found for intent: $url")
                    // Try to open Google Play Store for the app
                    openGooglePlayStore(context, url)
                }
            } catch (e: Exception) {
                Log.e("Deeplink", "Error handling intent fallback: ${e.message}", e)
            }
        }
        
        private fun extractFallbackUrl(url: String): String? {
            return try {
                // Look for S.browser_fallback_url parameter in the intent URL
                val uri = Uri.parse(url)
                uri.getQueryParameter("S.browser_fallback_url")
            } catch (e: Exception) {
                Log.e("Deeplink", "Error extracting fallback URL: ${e.message}", e)
                null
            }
        }
        
        private fun openGooglePlayStore(context: Context, url: String) {
            try {
                // Extract package name from intent URL
                val packageName = extractPackageName(url)
                if (packageName != null) {
                    val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(playStoreIntent)
                } else {
                    Log.w("Deeplink", "Could not extract package name from intent URL")
                }
            } catch (e: ActivityNotFoundException) {
                // Fallback to web browser
                try {
                    val packageName = extractPackageName(url)
                    if (packageName != null) {
                        val webIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(webIntent)
                    }
                } catch (e2: Exception) {
                    Log.e("Deeplink", "Error opening Google Play Store: ${e2.message}", e2)
                }
            } catch (e: Exception) {
                Log.e("Deeplink", "Error opening Google Play Store: ${e.message}", e)
            }
        }
        
        private fun extractPackageName(url: String): String? {
            return try {
                // Extract package name from intent:// URL
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                intent.`package`
            } catch (e: Exception) {
                Log.e("Deeplink", "Error extracting package name: ${e.message}", e)
                null
            }
        }
        private fun openInAppBrowser(context: Context, url: String) {
            try {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(false)
                    .build()
                customTabsIntent.launchUrl(context, Uri.parse(url))
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "No browser available to handle the request. Opening with WebView fallback.", e)
    
                // Fallback: Open inside an internal WebView
                val intent = Intent(context, InAppBrowserActivity::class.java)
                intent.putExtra("url", url)
                context.startActivity(intent)
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            
            if (webView.hasInitialLoadCompleted) {
                return
            }
            // Send Google Wallet eligibility status only after the initial page load
            webView.hasInitialLoadCompleted = true
            Log.d(TAG, "Initial page load completed")
        }
    }

    class InAppBrowserActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
    
            val webView = WebView(this)
            setContentView(webView)
    
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.webViewClient = WebViewClient()
    
            val url = intent.getStringExtra("url") ?: return
            webView.loadUrl(url)
        }
    }
    

    private class AccrueWebChromeClient: WebChromeClient() {
        val TAG: String = "AccrueWebChromeClient"

        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            val href = view?.handler?.obtainMessage()
            Log.i(TAG, "Opening link in a new window: $href")
            view?.requestFocusNodeHref(href)
            val url = href?.data?.getString("url") ?: return false

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            view?.context?.startActivity(intent)
            return true
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(
                "WebView", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId()
            )
            return true
        }
    }

   fun updateContextData(newContextData: AccrueContextData) {
        // Update the stored contextData
        this.contextData = newContextData
        
        // Update the JavaScript interface
        this.webAppInterface?.contextData = newContextData

        // Send the update to the WebView
        evaluateJavascript("""
            var event = new CustomEvent("${AccrueWebEvents.accrueWalletContextChangedEventKey}", {});
            window.dispatchEvent(event);
        """.trimIndent(), null)
    }

    fun handleEvent(eventName: String, data: String) {
        val eventMap = mapOf(
            "AccrueTabPressed" to "__GO_TO_HOME_SCREEN",
            "googleWalletProvisioningResult" to AccrueWebEvents.googleWalletProvisioningResultFunction
        )
        val mappedEventName = eventMap[eventName] ?: eventName

        evaluateJavascript("""
            if (typeof window !== "undefined" && typeof window?.["$mappedEventName"] === "function") {
                window?.["$mappedEventName"]?.($data);
            }
        """.trimIndent(), null)
    }

    /**
     * Send Google Wallet provisioning is supported status to the webview
     * @param isAutomatic Whether this check was triggered automatically or manually
     */
    internal fun sendGoogleWalletProvisioningIsSupportedStatus(isAutomatic: Boolean) {
        val logPrefix = if (isAutomatic) "Automatically" else "Manually" 
        
        // Check if Google Wallet is available and device is eligible
        provisioningMain?.let { provisioning ->
            provisioning.checkGoogleWalletEligibility { isEligible, details ->
                val responseJson = JSONObject().apply {
                    put("isSupported", isEligible)
                    put("details", details ?: "")
                    put("timestamp", System.currentTimeMillis())
                    put("automatic", isAutomatic)
                }.toString()
                
                Log.i("AccrueWebView", "$logPrefix Google Wallet Eligibility Result: $responseJson")
                // Send the result to the WebView
                post {
                    evaluateJavascript("""
                        if (typeof window !== "undefined" && typeof window?.["${AccrueWebEvents.googleWalletProvisioningIsSupportedResponseFunction}"] === "function") {
                            window?.["${AccrueWebEvents.googleWalletProvisioningIsSupportedResponseFunction}"]?.($responseJson);
                        }
                    """.trimIndent(), null)
                }
            }
        } ?: run {
            // If provisioning is not available, return not eligible
            val responseJson = JSONObject().apply {
                put("isEligible", false)
                put("details", "Google Wallet provisioning not available")
                put("timestamp", System.currentTimeMillis())
                put("automatic", isAutomatic)
            }.toString()
            
            Log.i("AccrueWebView", "$logPrefix Google Wallet Eligibility Result: $responseJson")
            post {
                evaluateJavascript("""
                    if (typeof window !== "undefined" && typeof window?.["${AccrueWebEvents.googleWalletProvisioningIsSupportedResponseFunction}"] === "function") {
                        window?.["${AccrueWebEvents.googleWalletProvisioningIsSupportedResponseFunction}"]?.($responseJson);
                    }
                """.trimIndent(), null)
            }
        }
    }

    /**
     * Update the ProvisioningMain reference
     * This method should be called after ProvisioningMain is initialized
     */
    internal fun setProvisioningMain(provisioning: ProvisioningMain) {
        this.provisioningMain = provisioning 
    }
}