package com.accruesavings.embedsdk

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface

class AccrueWebView @JvmOverloads constructor(
    context: Context,
    private var url: String,
    private var contextData: AccrueContextData? = null,
    private var onAction: ((String) -> Unit)? = null
) : WebView(context) {

    init {
        setupWebView()
    }

    private fun setupWebView() {
        settings.javaScriptEnabled = true
        webViewClient = WebViewClient()

        // Add JavaScript interface
        addJavascriptInterface(WebAppInterface(onAction), AccrueWebEvents.eventHandlerName)

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

    private class WebAppInterface(private val onAction: ((String) -> Unit)?) {
        @JavascriptInterface
        fun postMessage(message: String) {
            onAction?.invoke(message)
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