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
                    this.googleWalletProvisioning = GoogleWalletProvisioning(activity)
                    Log.d(TAG, "Google Wallet Provisioning pre-initialized in newInstanceWithEarlyInit")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to pre-initialize Google Wallet Provisioning in newInstanceWithEarlyInit: ${e.message}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Pre-initialize Google Wallet Provisioning if not already done
        if (googleWalletProvisioning == null) {
            preInitializeGoogleWalletProvisioning()
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
        
        // Initialize Google Wallet Provisioning if not already pre-initialized
        if (googleWalletProvisioning == null) {
            googleWalletProvisioning = GoogleWalletProvisioning(requireContext())
        }
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
     * Check if TapAndPay is available on this device for push provisioning
     * @param callback Callback with the result of the check
     */
    fun isTapAndPayAvailable(callback: (Boolean) -> Unit) {
        googleWalletProvisioning?.isTapAndPayAvailable(callback) ?: callback(false)
    }
    
    /**
     * Query the status of a specific token (simplified version)
     * @param tokenServiceProvider The TSP constant
     * @param tokenReferenceId The token reference ID to query
     * @param callback Callback with status information (isActive, message)
     */
    fun getTokenStatus(tokenServiceProvider: Int, tokenReferenceId: String, callback: (Boolean, String?) -> Unit) {
        googleWalletProvisioning?.getTokenStatus(tokenServiceProvider, tokenReferenceId, callback) 
            ?: callback(false, "Google Wallet Provisioning not initialized")
    }
    
    /**
     * Check if a token needs activation and handle accordingly
     * @param tokenServiceProvider The TSP constant
     * @param tokenReferenceId The token reference ID
     * @param callback Callback with activation result (isActive, message)
     */
    fun checkAndActivateToken(tokenServiceProvider: Int, tokenReferenceId: String, callback: (Boolean, String?) -> Unit) {
        googleWalletProvisioning?.checkAndActivateToken(tokenServiceProvider, tokenReferenceId, callback) 
            ?: callback(false, "Google Wallet Provisioning not initialized")
    }
    
    /**
     * Request deletion of a token (shows user confirmation dialog)
     * @param tokenServiceProvider The TSP constant
     * @param tokenReferenceId The token reference ID to delete
     */
    fun requestDeleteToken(tokenServiceProvider: Int, tokenReferenceId: String) {
        googleWalletProvisioning?.requestDeleteToken(tokenServiceProvider, tokenReferenceId) 
            ?: Log.w(TAG, "Cannot delete token - Google Wallet Provisioning not initialized")
    }
    
    /**
     * Get the GoogleWalletProvisioning instance for testing purposes
     * @return The GoogleWalletProvisioning instance or null if not initialized
     */
    fun getGoogleWalletProvisioning(): GoogleWalletProvisioning? {
        return googleWalletProvisioning
    }
    
    /**
     * Pre-initialize Google Wallet Provisioning to handle ActivityResultLauncher registration
     * before the activity reaches STARTED state. This method can be called from the host 
     * activity's onCreate method to avoid lifecycle issues.
     */
    fun preInitializeGoogleWalletProvisioning() {
        if (googleWalletProvisioning == null && activity != null) {
            try {
                googleWalletProvisioning = GoogleWalletProvisioning(requireContext())
                Log.d(TAG, "Google Wallet Provisioning pre-initialized successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-initialize Google Wallet Provisioning: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up Google Wallet Provisioning resources
        googleWalletProvisioning?.cleanup()
    }
}
