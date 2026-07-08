package com.accruesavings.androidsdk

import org.junit.Assert.assertEquals
import org.junit.Test

class AccrueSdkUrlsTest {
    @Test
    fun widgetUrl_appendsWebviewPathForProduction() {
        val config = AccrueSdkUrlConfig(
            production = "https://production.example.com",
            sandbox = "https://sandbox.example.com"
        )

        assertEquals(
            "https://production.example.com/webview",
            AccrueSdkUrls.widgetUrl(config, isSandbox = false)
        )
    }

    @Test
    fun widgetUrl_appendsWebviewPathForSandbox() {
        val config = AccrueSdkUrlConfig(
            production = "https://production.example.com",
            sandbox = "https://sandbox.example.com"
        )

        assertEquals(
            "https://sandbox.example.com/webview",
            AccrueSdkUrls.widgetUrl(config, isSandbox = true)
        )
    }

    @Test
    fun appendWidgetPath_doesNotDuplicateExistingWebviewPath() {
        assertEquals(
            "https://production.example.com/webview",
            AccrueSdkUrls.appendWidgetPath("https://production.example.com/webview")
        )
    }

    @Test
    fun fallbackWidgetUrl_returnsPreviousProductionUrl() {
        assertEquals(
            "https://embed.accruesavings.com/webview",
            AccrueSdkUrls.fallbackWidgetUrl(isSandbox = false)
        )
    }

    @Test
    fun fallbackWidgetUrl_returnsPreviousSandboxUrl() {
        assertEquals(
            "https://embed-sandbox.accruesavings.com/webview",
            AccrueSdkUrls.fallbackWidgetUrl(isSandbox = true)
        )
    }
}
