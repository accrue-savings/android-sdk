package com.accruesavings.androidsdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
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

        webViewClient = AccrueWebViewClient()
        // Add JavaScript interface
        addJavascriptInterface(WebAppInterface(this.onAction), AccrueWebEvents.eventHandlerName)

        // Load URL
        loadUrl(url)

        // Inject context data
        contextData?.let { injectContextData(it) }
    }

    fun updateContent(newUrl: String? = null, newContextData: AccrueContextData? = null) {
        newUrl?.let { url ->
            if (url != this.url) {
                this.url = url
                loadUrl(url)
            }
        }

        newContextData?.let {
            contextData = it
            refreshContextData(it)
        }
    }

    private fun injectContextData(contextData: AccrueContextData) {
        val script = generateContextDataScript(contextData)
        evaluateJavascript(script, null)
    }

    private fun refreshContextData(contextData: AccrueContextData) {
        val script = generateContextDataScript(contextData)
        evaluateJavascript(script, null)
    }

    private class WebAppInterface(private val onAction: Map<AccrueAction, () -> Unit> = emptyMap()) {
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
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                view.context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                )
                return true
            }
            return false
        }
    }

    private fun generateContextDataScript(contextData: AccrueContextData): String {
        val userData = contextData.userData
        val settingsData = contextData.settingsData
        val deviceContextData = AccrueDeviceContextData()

        return """
            (function() {
                window["${AccrueWebEvents.eventHandlerName}"] = {
                    "contextData": {
                        "userData": {
                            "referenceId": ${userData.referenceId?.let { "\"$it\"" } ?: "null"},
                            "email": ${userData.email?.let { "\"$it\"" } ?: "null"},
                            "phoneNumber": ${userData.phoneNumber?.let { "\"$it\"" } ?: "null"}
                        },
                        "settingsData": {
                            "shouldInheritAuthentication": ${settingsData.shouldInheritAuthentication}
                        },
                        "deviceData": {
                            "sdk": "${deviceContextData.sdk}",
                            "sdkVersion": "${deviceContextData.sdkVersion ?: "null"}",
                            "brand": "${deviceContextData.brand ?: "null"}",
                            "deviceName": "${deviceContextData.deviceName ?: "null"}",
                            "deviceType": "${deviceContextData.deviceType ?: ""}",
                            "deviceYearClass": "${deviceContextData.deviceYearClass ?: 0}",
                            "isDevice": ${deviceContextData.isDevice},
                            "manufacturer": "${deviceContextData.manufacturer ?: "null"}",
                            "modelName": "${deviceContextData.modelName ?: "null"}",
                            "osBuildId": "${deviceContextData.osBuildId ?: "null"}",
                            "osInternalBuildId": "${deviceContextData.osInternalBuildId ?: "null"}",
                            "osName": "${deviceContextData.osName ?: "null"}",
                            "osVersion": "${deviceContextData.osVersion ?: "null"}",
                            "modelId": "${deviceContextData.androidId ?: "null"}"
                        }
                    }
                };
                // Notify the web page that contextData has been updated
                var event = new CustomEvent("${AccrueWebEvents.accrueWalletContextChangedEventKey}", {
                    detail: window["${AccrueWebEvents.eventHandlerName}"].contextData
                });
                window.dispatchEvent(event);
            })();
        """.trimIndent()
    }
}