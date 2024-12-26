package com.accruesavings.androidsdk

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONException
import org.json.JSONObject

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

    init {
        setupWebView()
    }

    private fun setupWebView() {
        overScrollMode = OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        // local storage does not work without it
        settings.domStorageEnabled = true
//        settings.setSupportMultipleWindows(true)
//        settings.javaScriptCanOpenWindowsAutomatically = true
        webViewClient = AccrueWebViewClient()
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d("WebViewConsole", "${consoleMessage.message()} at line ${consoleMessage.lineNumber()} in ${consoleMessage.sourceId()}")
                return true
            }
        }
        // Add JavaScript interface and context data
        webAppInterface = WebAppInterface(this.onAction, contextData)
        addJavascriptInterface(webAppInterface!!, AccrueWebEvents.eventHandlerName)

        // Load URL
        loadUrl(url)
    }

    private class WebAppInterface(
        private val onAction: Map<AccrueAction, () -> Unit> = emptyMap(),
        private var _contextData: AccrueContextData?
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
                    "AccrueWallet::${AccrueAction.SignInButtonClicked}" -> onAction[AccrueAction.SignInButtonClicked]?.invoke()
                    "AccrueWallet::${AccrueAction.RegisterButtonClicked}" -> onAction[AccrueAction.RegisterButtonClicked]?.invoke()
                    else -> Log.w("AccrueWebView", "Unknown message type: $key")
                }
            } catch (e: JSONException) {
                Log.e("AccrueWebView", "Error parsing JSON message: ${e.message}")
            }
        }
    }

    // for links that open in new window to work
    private class AccrueWebViewClient : WebViewClient() {
        val TAG: String = "AccrueWebViewClient"

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

            // Handle external links
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                Log.i(TAG, "Opening external link: $url")
                try {
                    view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No application can handle this request. URL: $url", e)
                }
                return true // Prevent the WebView from loading external links
            }

            return false
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
        evaluateJavascript("""
            var event = new CustomEvent("${AccrueWebEvents.accrueWalletParentAppEventKey}", {
                detail: {
                    name: $eventName,
                    data: $data
                }
            });
            window.dispatchEvent(event);
        """.trimIndent(), null)
    }

}