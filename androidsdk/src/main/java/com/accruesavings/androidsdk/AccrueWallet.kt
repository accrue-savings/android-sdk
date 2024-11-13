package com.accruesavings.androidsdk

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment

class AccrueWallet : Fragment() {
    val TAG: String = "AccrueWallet"
    private lateinit var merchantId: String
    private var redirectionToken: String? = null
    private var isSandbox: Boolean = false
    private var url: String? = null
    private var onAction: Map<AccrueAction, () -> Unit> = emptyMap()
    private var contextData: AccrueContextData = AccrueContextData()
    private lateinit var webView: AccrueWebView

    companion object {
        fun newInstance(
            merchantId: String,
            redirectionToken: String? = null,
            isSandbox: Boolean = false,
            url: String? = null,
            contextData: AccrueContextData = AccrueContextData(),
            onAction: Map<AccrueAction, () -> Unit> = emptyMap()
        ): AccrueWallet {
            WebView.setWebContentsDebuggingEnabled(true);
            return AccrueWallet().apply {
                this.merchantId = merchantId
                this.redirectionToken = redirectionToken
                this.isSandbox = isSandbox
                this.url = url
                this.contextData = contextData
                this.onAction = onAction
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val builtUrl = buildURL(isSandbox, url)
        Log.v(TAG, "builtUrl=$builtUrl");
        // Create AccrueWebView programmatically
        webView = AccrueWebView(requireContext(), url = builtUrl, contextData, onAction)
        
        // Set layout parameters
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.layoutParams = layoutParams

        // Return the webView as the root view
        return webView
    }

    private fun buildURL(isSandbox: Boolean, url: String?): String {
        val apiBaseUrl = when {
            url != null -> url
            isSandbox -> AppConstants.sandboxUrl
            else -> AppConstants.productionUrl
        }

        val uri = Uri.parse(apiBaseUrl).buildUpon()
            .appendQueryParameter("merchantId", merchantId)

        redirectionToken?.takeIf { it.isNotEmpty() }?.let {
            uri.appendQueryParameter("redirectionToken", it)
        }

        return uri.build().toString()
    }
}
