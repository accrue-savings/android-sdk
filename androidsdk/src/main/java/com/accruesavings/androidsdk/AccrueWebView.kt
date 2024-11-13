package com.accruesavings.androidsdk

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


class AccrueWebView @JvmOverloads constructor(
    context: Context,
    private var url: String,
    private var contextData: AccrueContextData? = null,
    private var onAction: Map<AccrueAction, () -> Unit> = emptyMap()
) : WebView(context) {

    init {
        setupWebView()
    }

    private fun setupWebView() {
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
        addJavascriptInterface(WebAppInterface(this.onAction, contextData), AccrueWebEvents.eventHandlerName)

        // Load URL
        loadUrl(url)
    }

    private class WebAppInterface(private val onAction: Map<AccrueAction, () -> Unit> = emptyMap(), private val contextData: AccrueContextData?) {
        @JavascriptInterface
        fun getParentType(): String {
            return "android"
        }

        @JavascriptInterface
        fun getContextData(): String {
            if(contextData === null) {
                return "";
            }
            val userData = contextData.userData
            val settingsData = contextData.settingsData
            val deviceContextData = AccrueDeviceContextData()
            return JSONObject(mapOf(
                "contextData" to mapOf(
                    "userData" to mapOf(
                        "referenceId" to userData.referenceId,
                        "email" to userData.email,
                        "phoneNumber" to userData.phoneNumber
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
                        "deviceYearClass" to deviceContextData.deviceYearClass,
                        "isDevice" to deviceContextData.isDevice,
                        "manufacturer" to deviceContextData.manufacturer,
                        "modelName" to deviceContextData.modelName,
                        "osBuildId" to deviceContextData.osBuildId,
                        "osInternalBuildId" to deviceContextData.osInternalBuildId,
                        "osName" to deviceContextData.osName,
                        "osVersion" to deviceContextData.osVersion,
                        "modelId" to deviceContextData.androidId
                    )
                ))).toString()
        }

        @JavascriptInterface
        fun postMessage(message: String) {
            try {
                val jsonObject = JSONObject(message)
                val type = jsonObject.optString("action")

                when (type) {
                    "AccrueWallet::${AccrueAction.SignInButtonClicked}" -> onAction[AccrueAction.SignInButtonClicked]?.invoke()
                    "AccrueWallet::${AccrueAction.RegisterButtonClicked}" -> onAction[AccrueAction.RegisterButtonClicked]?.invoke()
                    else -> Log.w("AccrueWebView", "Unknown message type: $type")
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
            if (url.startsWith("http://") || url.startsWith("https://")) {
                Log.i(TAG, "Opening link: $url")
                view.context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                )
                return true
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

}