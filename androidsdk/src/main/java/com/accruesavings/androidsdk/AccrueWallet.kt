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
    private var provisioningMain: ProvisioningMain? = null
    private var activityResultHandler: ActivityResultHandler? = null

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
            WebView.setWebContentsDebuggingEnabled(true);
            return AccrueWallet().apply {
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
        val builtUrl = buildURL(isSandbox, url)
        Log.v(TAG, "builtUrl=$builtUrl");
        // Create AccrueWebView programmatically
        webView = AccrueWebView(requireContext(), url = builtUrl, contextData, onAction)
        
        // Initialize Provisioning if not already pre-initialized
        if (provisioningMain == null) {
            provisioningMain = ProvisioningMain(requireContext())
        }
        provisioningMain?.initialize(requireActivity(), webView, activityResultHandler)
        
        // Set the ProvisioningMain reference in the WebView
        provisioningMain?.let { provisioning ->
            webView.setProvisioningMain(provisioning)
        }
        
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
}
