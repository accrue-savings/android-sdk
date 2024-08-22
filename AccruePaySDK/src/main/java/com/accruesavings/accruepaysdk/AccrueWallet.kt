package com.accruesavings.accruepaysdk

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import android.util.Log
import android.webkit.WebSettings

private const val TAG = "AccrueWallet"

class AccrueWallet : Fragment() {

    companion object {
        private const val ARG_MERCHANT_ID = "arg_merchant_id"
        private const val ARG_REDIRECT_TOKEN = "arg_redirect_token"
        private const val ARG_CONTEXT_DATA = "arg_context_data"

        @JvmStatic
        fun newInstance(
            merchantId: String,
            redirectToken: String,
            onSignIn: (() -> Unit)? = null,
            contextData: ContextData? = null
        ): AccrueWallet {
            val fragment = AccrueWallet()
            val args = Bundle()
            args.putString(ARG_MERCHANT_ID, merchantId)
            args.putString(ARG_REDIRECT_TOKEN, redirectToken)
            args.putParcelable(ARG_CONTEXT_DATA, contextData)
            fragment.arguments = args
            fragment.onSignIn = onSignIn
            return fragment
        }
    }

    private var merchantId: String? = null
    private var redirectToken: String? = null
    private var contextData: ContextData? = null
    private var onSignIn: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            merchantId = it.getString(ARG_MERCHANT_ID)
            redirectToken = it.getString(ARG_REDIRECT_TOKEN)
            contextData = it.getParcelable(ARG_CONTEXT_DATA)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_webview, container, false)
        val webView: WebView = view.findViewById(R.id.webview)

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.userAgentString = "AccruePay SDK 0.0.1"

        contextData?.let {
            webView.addJavascriptInterface(ContextDataInterface(requireContext(), it), "AccruePay")
        }

        // Use the parameters in your WebView configuration or loading logic
        val url = buildUrl(merchantId, redirectToken)

        webView.loadUrl(url)

        // Optionally, set up JavaScript interface or other WebView settings

        return view
    }

    private fun buildUrl(merchantId: String?, redirectToken: String?): String {
        // Build your URL based on the parameters
        // For example:
        return "${AppConstants.apiBaseUrl}?merchantId=$merchantId&redirectionToken=$redirectToken"
    }

    // Optionally call onSignIn when needed
    fun signIn() {
        Log.i(TAG, "onSignIn called")
        onSignIn?.invoke()
    }

}