package com.accruesavings.accruepaysdk

import android.content.Context
import android.webkit.JavascriptInterface

class ContextDataInterface(private val context: Context, private val contextData: ContextData) {
    @JavascriptInterface
    fun getContextData(): String {
        // Return context data as a JSON string or any other format JavaScript can handle
        return """window["${AccrueWebEvents.EventHandlerName}"] = {
            "referenceId": "${contextData.referenceId}",
            "phoneNumber": "${contextData.phoneNumber}",
            "email": "${contextData.email}"
        }"""
    }
}