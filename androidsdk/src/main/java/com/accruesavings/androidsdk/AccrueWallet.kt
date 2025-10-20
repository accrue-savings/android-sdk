package com.accruesavings.androidsdk

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.accruesavings.androidsdk.provisioning.core.ActivityResultHandler

class AccrueWallet : Fragment() {
    private var merchantId: String? = null
    private var redirectionToken: String? = null
    private var isSandbox: Boolean = false
    private var url: String? = null
    private var onAction: Map<AccrueAction, () -> Unit> = emptyMap()
    private var contextData: AccrueContextData = AccrueContextData()
        set(value) {
            field = value
            webView?.updateContextData(value)
        }
    private var webView: AccrueWebView? = null
    private var provisioningMain: ProvisioningMain? = null
    private var activityResultHandler: ActivityResultHandler? = null

    companion object {
        private const val TAG = "AccrueWallet"
        private const val ARG_MERCHANT_ID = "com.accruesavings.androidsdk.AccrueWallet.ARG_MERCHANT_ID"
        private const val ARG_REDIRECTION_TOKEN = "com.accruesavings.androidsdk.AccrueWallet.ARG_REDIRECTION_TOKEN"
        private const val ARG_IS_SANDBOX = "com.accruesavings.androidsdk.AccrueWallet.ARG_IS_SANDBOX"
        private const val ARG_URL = "com.accruesavings.androidsdk.AccrueWallet.ARG_URL"

        fun newInstance(
            merchantId: String,
            redirectionToken: String? = null,
            isSandbox: Boolean = false,
            url: String? = null,
            contextData: AccrueContextData = AccrueContextData(),
            onAction: Map<AccrueAction, () -> Unit> = emptyMap()
        ): AccrueWallet {
            WebView.setWebContentsDebuggingEnabled(true)
            return AccrueWallet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MERCHANT_ID, merchantId)
                    putString(ARG_REDIRECTION_TOKEN, redirectionToken)
                    putBoolean(ARG_IS_SANDBOX, isSandbox)
                    putString(ARG_URL, url)
                }
                this.merchantId = merchantId
                this.redirectionToken = redirectionToken
                this.isSandbox = isSandbox
                this.url = url
                this.contextData = contextData
                this.onAction = onAction
            }
        }
        
        /**
         * Create a new AccrueWallet instance with early Google Wallet Provisioning initialization.
         * This method should be called from the host activity's onCreate method to ensure proper
         * ActivityResultLauncher registration before the activity reaches STARTED state.
         * 
         * @param activity The FragmentActivity that will host this fragment
         * @param merchantId The merchant ID for Accrue
         * @param redirectionToken Optional redirection token
         * @param isSandbox Whether to use sandbox environment
         * @param url Optional custom URL
         * @param contextData Context data for the wallet
         * @param onAction Action handlers map
         * @return AccrueWallet instance with pre-initialized Google Wallet Provisioning
         */
        @JvmStatic
        fun newInstanceWithEarlyInit(
            activity: androidx.fragment.app.FragmentActivity,
            merchantId: String,
            redirectionToken: String? = null,
            isSandbox: Boolean = false,
            url: String? = null,
            contextData: AccrueContextData = AccrueContextData(),
            onAction: Map<AccrueAction, () -> Unit> = emptyMap()
        ): AccrueWallet {
            WebView.setWebContentsDebuggingEnabled(true)
            return AccrueWallet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MERCHANT_ID, merchantId)
                    putString(ARG_REDIRECTION_TOKEN, redirectionToken)
                    putBoolean(ARG_IS_SANDBOX, isSandbox)
                    putString(ARG_URL, url)
                }
                this.merchantId = merchantId
                this.redirectionToken = redirectionToken
                this.isSandbox = isSandbox
                this.url = url
                this.contextData = contextData
                this.onAction = onAction
                
                // Pre-initialize Google Wallet Provisioning
                try {
                    this.provisioningMain = ProvisioningMain(activity)
                    Log.d(TAG, "Google Wallet Provisioning pre-initialized in newInstanceWithEarlyInit")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to pre-initialize Google Wallet Provisioning in newInstanceWithEarlyInit: ${e.message}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        restoreConfiguration(arguments)
        restoreConfiguration(savedInstanceState)

        if (merchantId.isNullOrBlank()) {
            Log.w(TAG, "AccrueWallet merchantId is missing. Ensure newInstance was used with a valid merchantId.")
        }
        
        // Initialize ActivityResultHandler for Google Pay operations
        activityResultHandler = ActivityResultHandler(this) { requestCode, resultCode, data ->
            provisioningMain?.handleActivityResult(requestCode, resultCode, data)
        }
        
        // Pre-initialize Provisioning if not already done
        if (provisioningMain == null) {
            preInitializeProvisioning()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val merchantIdValue = merchantId?.takeIf { it.isNotBlank() }
            ?: run {
                val errorMsg = "AccrueWallet must be created using newInstance() or newInstanceWithEarlyInit() factory methods"
                Log.e(TAG, errorMsg)
                throw IllegalStateException(errorMsg)
            }

        val builtUrl = buildURL(isSandbox, url, merchantIdValue)
        Log.v(TAG, "builtUrl=$builtUrl")
        // Create AccrueWebView programmatically
        val accrueWebView = AccrueWebView(requireContext(), url = builtUrl, contextData, onAction)
        webView = accrueWebView
        
        // Initialize Provisioning if not already pre-initialized
        if (provisioningMain == null) {
            provisioningMain = ProvisioningMain(requireContext())
        }
        provisioningMain?.initialize(requireActivity(), accrueWebView, activityResultHandler)
        
        // Set the ProvisioningMain reference in the WebView
        provisioningMain?.let { provisioning ->
            accrueWebView.setProvisioningMain(provisioning)
        }
        
        // Set layout parameters
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        accrueWebView.layoutParams = layoutParams

        // Return the webView as the root view
        return accrueWebView
    }

    private fun buildURL(isSandbox: Boolean, url: String?, merchantIdParam: String): String {
        val apiBaseUrl = when {
            url != null -> url
            isSandbox -> AppConstants.sandboxUrl
            else -> AppConstants.productionUrl
        }

        val uri = Uri.parse(apiBaseUrl).buildUpon()
            .appendQueryParameter("merchantId", merchantIdParam)

        redirectionToken?.takeIf { it.isNotEmpty() }?.let {
            uri.appendQueryParameter("redirectionToken", it)
        }

        return uri.build().toString()
    }

    fun updateContextData(newContextData: AccrueContextData) {
        contextData = newContextData
    }

    fun handleEvent(eventName: String, data: String?) {
        val targetWebView = webView
        if (targetWebView == null) {
            Log.w(TAG, "handleEvent called before WebView initialization; ignoring event $eventName")
            return
        }

        targetWebView.handleEvent(eventName, data ?: "{}")
    }

    
    /**
     * Pre-initialize Provisioning to handle ActivityResultLauncher registration
     * before the activity reaches STARTED state. This method can be called from the host 
     * activity's onCreate method to avoid lifecycle issues.
     */
    fun preInitializeProvisioning() {
        if (provisioningMain == null && activity != null) {
            try {
                provisioningMain = ProvisioningMain(requireContext())
                Log.d(TAG, "Provisioning pre-initialized successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-initialize Provisioning: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up Provisioning resources
        provisioningMain?.cleanup()
        activityResultHandler = null
    }

    override fun onDestroyView() {
        webView = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        merchantId?.let { outState.putString(ARG_MERCHANT_ID, it) }
        redirectionToken?.let { outState.putString(ARG_REDIRECTION_TOKEN, it) }
        outState.putBoolean(ARG_IS_SANDBOX, isSandbox)
        url?.let { outState.putString(ARG_URL, it) }
    }

    private fun restoreConfiguration(bundle: Bundle?) {
        bundle ?: return

        bundle.getString(ARG_MERCHANT_ID)?.let { merchantId = it }
        if (bundle.containsKey(ARG_REDIRECTION_TOKEN)) {
            redirectionToken = bundle.getString(ARG_REDIRECTION_TOKEN)
        }
        if (bundle.containsKey(ARG_IS_SANDBOX)) {
            isSandbox = bundle.getBoolean(ARG_IS_SANDBOX, false)
        }
        if (bundle.containsKey(ARG_URL)) {
            url = bundle.getString(ARG_URL)
        }
    }
}
