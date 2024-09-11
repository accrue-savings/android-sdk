package com.accruesavings.embedsdk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.accruesavings.embedsdk.AccrueContextData
import android.net.Uri
import com.accruesavings.embedsdk.AppConstants

class AccrueWallet : Fragment() {

    private lateinit var merchantId: String
    private var redirectionToken: String? = null
    private var isSandbox: Boolean = false
    private var url: String? = null
    private var onAction: ((String) -> Unit)? = null
    private var contextData: AccrueContextData = AccrueContextData()
    private lateinit var webView: AccrueWebView

    companion object {
        fun newInstance(
            merchantId: String,
            redirectionToken: String? = null,
            isSandbox: Boolean,
            url: String? = null,
            contextData: AccrueContextData = AccrueContextData(),
            onAction: ((String) -> Unit)? = null
        ): AccrueWallet {
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
            isSandbox -> AppConstants.sandboxUrl
            url != null -> url
            else -> AppConstants.productionUrl
        }

        val uri = Uri.parse(apiBaseUrl).buildUpon()
            .appendQueryParameter("merchantId", merchantId)

        redirectionToken?.let {
            uri.appendQueryParameter("redirectionToken", it)
        }

        return uri.build().toString()
    }
}
