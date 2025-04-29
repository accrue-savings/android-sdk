package com.accruesavings.androidsdk

import android.net.Uri
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
        set(value) {
            field = value
            if (::webView.isInitialized) {
                webView.updateContextData(value)
            }
        }
    private lateinit var webView: AccrueWebView
    private var googleWalletProvisioning: GoogleWalletProvisioning? = null

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
        
        // Initialize Google Wallet Provisioning
        googleWalletProvisioning = GoogleWalletProvisioning(requireContext())
        googleWalletProvisioning?.initialize(requireActivity(), webView)
        
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

    fun updateContextData(newContextData: AccrueContextData) {
        contextData = newContextData
    }

    fun handleEvent(eventName: String, data: String?) {
        webView.handleEvent(eventName, data ?: "{}")
    }
    
    /**
     * Check if Google Pay is available on this device
     * @param callback Callback with the result of the check
     */
    fun isGooglePayAvailable(callback: (Boolean) -> Unit) {
        googleWalletProvisioning?.isGooglePayAvailable(callback) ?: callback(false)
    }
    
    /**
     * Get the GoogleWalletProvisioning instance for testing purposes
     * @return The GoogleWalletProvisioning instance or null if not initialized
     */
    fun getGoogleWalletProvisioning(): GoogleWalletProvisioning? {
        return googleWalletProvisioning
    }
}
