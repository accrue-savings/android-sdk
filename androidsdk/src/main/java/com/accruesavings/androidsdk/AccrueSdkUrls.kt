package com.accruesavings.androidsdk

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal data class AccrueSdkUrlConfig(
    val production: String,
    val sandbox: String
)

internal object AccrueSdkUrls {
    private const val TAG = "AccrueSdkUrls"
    private const val SDK_URLS_ENDPOINT = "https://www.byaccrue.com/sdk-urls"
    private const val WIDGET_PATH = "webview"
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000
    private const val FALLBACK_PRODUCTION_URL = "https://embed.accruesavings.com/webview"
    private const val FALLBACK_SANDBOX_URL = "https://embed-sandbox.accruesavings.com/webview"

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun fetchWidgetUrl(
        isSandbox: Boolean,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val config = fetchConfig()
                widgetUrl(config, isSandbox)
            }

            mainHandler.post {
                callback(result)
            }
        }
    }

    fun parseConfig(responseBody: String): AccrueSdkUrlConfig {
        val json = JSONObject(responseBody)
        return AccrueSdkUrlConfig(
            production = json.getString("production"),
            sandbox = json.getString("sandbox")
        )
    }

    fun widgetUrl(config: AccrueSdkUrlConfig, isSandbox: Boolean): String {
        val baseUrl = if (isSandbox) config.sandbox else config.production
        return appendWidgetPath(baseUrl)
    }

    fun fallbackWidgetUrl(isSandbox: Boolean): String {
        return if (isSandbox) FALLBACK_SANDBOX_URL else FALLBACK_PRODUCTION_URL
    }

    fun appendWidgetPath(baseUrl: String): String {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        require(normalizedBaseUrl.isNotEmpty()) { "SDK widget base URL is empty" }

        return if (normalizedBaseUrl.endsWith("/$WIDGET_PATH")) {
            normalizedBaseUrl
        } else {
            "$normalizedBaseUrl/$WIDGET_PATH"
        }
    }

    private fun fetchConfig(): AccrueSdkUrlConfig {
        val connection = URL(SDK_URLS_ENDPOINT).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("SDK URL config request failed with HTTP $responseCode")
            }

            connection.inputStream.bufferedReader().use { reader ->
                parseConfig(reader.readText())
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to fetch SDK URL config", error)
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
