package com.accruesavings.androidsdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient
import org.json.JSONObject
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.util.Random
import com.google.android.gms.pay.PayClient.RequestType
import com.google.android.gms.pay.PayClient.TokenServiceProvider
import com.google.android.gms.pay.PayClient.PushTokenizeRequest
import com.google.android.gms.pay.PayClient.PushTokenizeRequest.Builder

/**
 * Data class for device information required for provisioning
 */
data class DeviceInfo(
    val deviceId: String,
    val deviceType: String,
    val provisioningAppVersion: String,
    val walletAccountId: String
)

/**
 * Data class to represent the response from the web for push provisioning
 */
data class PushProvisioningResponse(
    val cardToken: String,
    val createdTime: String,
    val lastModifiedTime: String,
    val pushTokenizeRequestData: PushTokenizeRequestData
)

data class PushTokenizeRequestData(
    val displayName: String?,
    val opaquePaymentCard: String?,
    val lastDigits: String?,
    val network: String?,
    val tokenServiceProvider: String?,
    val userAddress: UserAddress?
)

data class UserAddress(
    val name: String?,
    val address1: String?,
    val address2: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String?,
    val phone: String?
)

/**
 * Error data class to send structured error information to the WebView
 */
data class ProvisioningError(
    val code: String,
    val message: String,
    val details: String? = null
)

/**
 * Handles Google Wallet Push Provisioning integration
 */
class GoogleWalletProvisioning(private val context: Context) : 
    GoogleWalletProvisioningTestHelper.GoogleWalletProvisioningWebViewInterface {
    
    private val TAG = "GoogleWalletProvisioning"
    
    private lateinit var payClient: PayClient
    private var webView: AccrueWebView? = null
    private var activity: FragmentActivity? = null
    private lateinit var provisioningResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var googleSignInClient: GoogleSignInClient
    
    // Test helper for simulating operations
    private val testHelper = GoogleWalletProvisioningTestHelper(this)
    
    // Error codes
    companion object {
        const val ERROR_GOOGLE_PAY_UNAVAILABLE = "ERROR_GOOGLE_PAY_UNAVAILABLE"
        const val ERROR_WALLET_ACCOUNT_NOT_FOUND = "ERROR_WALLET_ACCOUNT_NOT_FOUND"
        const val ERROR_PARSING_RESPONSE = "ERROR_PARSING_RESPONSE"
        const val ERROR_PROVISIONING_FAILED = "ERROR_PROVISIONING_FAILED"
        const val ERROR_USER_CANCELLED = "ERROR_USER_CANCELLED"
        const val ERROR_DEVICE_NOT_SUPPORTED = "ERROR_DEVICE_NOT_SUPPORTED"
    }
    
    // Wallet and hardware ID management
    private var walletAccountId: String? = null
    private var stableHardwareId: String? = null
    
    // Queue pending deviceInfo callbacks until wallet and hardware ID are ready
    private val pendingDeviceInfoRequests = mutableListOf<(DeviceInfo) -> Unit>()
    
    /**
     * Initialize Google Wallet Provisioning
     */
    fun initialize(activity: FragmentActivity, webView: AccrueWebView) {
        this.activity = activity
        this.webView = webView
        this.payClient = Pay.getClient(context)
        
        // Initialize Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        this.googleSignInClient = GoogleSignIn.getClient(activity, gso)
        
        // Register for provisioning result
        this.provisioningResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Card was successfully added to Google Pay
                Log.d(TAG, "Card was successfully added to Google Pay")
                handleSuccessEvent("{}")
            } else {
                // Handle error or cancellation
                Log.e(TAG, "Failed to add card to Google Pay, result code: ${result.resultCode}")
                
                val errorCode = if (result.resultCode == Activity.RESULT_CANCELED) {
                    ERROR_USER_CANCELLED
                } else {
                    ERROR_PROVISIONING_FAILED
                }
                
                val error = ProvisioningError(
                    code = errorCode,
                    message = "Google Pay provisioning failed with result code: ${result.resultCode}"
                )
                
                notifyError(error)
            }
        }

        // Initialize wallet and hardware ID
        initializeWalletAndHardwareId()
    }
    
    /**
     * Initialize wallet and hardware ID using modern Google Pay API
     */
    private fun initializeWalletAndHardwareId() {
        // Get wallet account ID
        getWalletAccountId { accountId ->
            if (accountId != null) {
                walletAccountId = accountId
                // Get stable hardware ID
                getStableHardwareId()
            } else {
                val error = ProvisioningError(
                    code = ERROR_WALLET_ACCOUNT_NOT_FOUND,
                    message = "Could not retrieve wallet account ID"
                )
                notifyError(error)
            }
        }
    }
    
    /**
     * Get stable hardware ID using modern Google Pay API
     */
    private fun getStableHardwareId() {
        payClient.getStableHardwareId()
            .addOnSuccessListener { hardwareId ->
                stableHardwareId = hardwareId
                Log.d(TAG, "Stable hardware ID: $hardwareId")
                // Now both walletAccountId and stableHardwareId are available, flush pending requests
                flushPendingDeviceInfoRequests()
            }
            .addOnFailureListener { exception ->
                val error = ProvisioningError(
                    code = ERROR_DEVICE_NOT_SUPPORTED,
                    message = "Stable Hardware ID not available",
                    details = exception.message
                )
                notifyError(error)
            }
    }
    
    /**
     * When both walletAccountId and stableHardwareId are set, deliver queued getDeviceInfo callbacks
     */
    private fun flushPendingDeviceInfoRequests() {
        val accountId = walletAccountId
        val hwId = stableHardwareId
        if (accountId != null && hwId != null) {
            val deviceType = when {
                isTablet(context) -> "TABLET"
                isWearable(context) -> "WATCH"
                else -> "MOBILE_PHONE"
            }
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                "unknown"
            }
            val info = DeviceInfo(hwId, deviceType, version, accountId)
            pendingDeviceInfoRequests.forEach { it(info) }
            pendingDeviceInfoRequests.clear()
        }
    }
    
    /**
     * Check if Google Pay is available on this device
     */
    fun isGooglePayAvailable(callback: (Boolean) -> Unit) {
        // Use test implementation if testing is enabled
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            testHelper.mockIsGooglePayAvailable(callback)
            return
        }
        
        // Real implementation
        payClient.getPayApiAvailabilityStatus(PayClient.RequestType.PUSH_TOKENIZE_CREDIT_CARD)
            .addOnSuccessListener { status ->
                val isAvailable = status == PayApiAvailabilityStatus.AVAILABLE
                callback(isAvailable)
                
                if (!isAvailable) {
                    val error = ProvisioningError(
                        code = ERROR_GOOGLE_PAY_UNAVAILABLE,
                        message = "Google Pay is not available on this device",
                        details = "Status: $status"
                    )
                    notifyError(error)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error checking Google Pay availability", exception)
                callback(false)
                
                val error = ProvisioningError(
                    code = ERROR_GOOGLE_PAY_UNAVAILABLE,
                    message = "Failed to check Google Pay availability",
                    details = exception.message
                )
                notifyError(error)
            }
    }
    
    /**
     * Get device information for provisioning asynchronously
     */
    fun getDeviceInfo(callback: (DeviceInfo) -> Unit) {
        // If test mode is mocking, delegate to test helper
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            testHelper.mockGetDeviceInfo(callback)
            return
        }
        
        // If both IDs ready, return immediately
        val accountId = walletAccountId
        val hwId = stableHardwareId
        if (accountId != null && hwId != null) {
            // Full info available
            val deviceType = when {
                isTablet(context) -> "TABLET"
                isWearable(context) -> "WATCH"
                else -> "MOBILE_PHONE"
            }
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                "unknown"
            }
            val info = DeviceInfo(hwId, deviceType, version, accountId)
            callback(info)
            return
        }
        
        // Otherwise queue until wallet & hardware IDs are ready
        pendingDeviceInfoRequests.add(callback)
        // Trigger wallet fetch if not already started
        if (walletAccountId == null) initializeWalletAndHardwareId()
    }
    
    /**
     * Helper method to determine if device is a tablet
     */
    private fun isTablet(context: Context): Boolean {
        return context.resources.configuration.screenLayout and 
               android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK >= 
               android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
    }
    
    /**
     * Helper method to determine if device is a wearable
     */
    private fun isWearable(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.type.watch")
    }
    
    /**
     * Get the wallet account ID from Google Pay
     */
    private fun getWalletAccountId(callback: (String?) -> Unit) {
        // Use test implementation if testing is enabled
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            testHelper.mockGetWalletAccountId(callback)
            return
        }
        
        // Real implementation
        try {
            // First try to get the account from Google Sign-In
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                val googleAccountId = account.id
                Log.d(TAG, "Got wallet account ID from Google Sign-In: $googleAccountId")
                callback(googleAccountId)
                return
            }
            
            // If not successful, try using PayClient
            payClient.getTokenStatus()
                .addOnSuccessListener { tokenStatusResult ->
                    // Try to extract the wallet account ID from the token status result
                    val walletAccountId = extractWalletAccountIdFromTokenStatus(tokenStatusResult)
                    Log.d(TAG, "Got wallet account ID from PayClient: $walletAccountId")
                    callback(walletAccountId)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get wallet account ID from PayClient", exception)
                    
                    // As a fallback, get the primary Google account on the device
                    val primaryAccount = getPrimaryGoogleAccount()
                    Log.d(TAG, "Using primary Google account as fallback: $primaryAccount")
                    callback(primaryAccount)
                    
                    if (primaryAccount == null) {
                        val error = ProvisioningError(
                            code = ERROR_WALLET_ACCOUNT_NOT_FOUND,
                            message = "Could not retrieve wallet account ID from any source",
                            details = exception.message
                        )
                        notifyError(error)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting wallet account ID", e)
            callback(null)
            
            val error = ProvisioningError(
                code = ERROR_WALLET_ACCOUNT_NOT_FOUND,
                message = "Exception while retrieving wallet account ID",
                details = e.message
            )
            notifyError(error)
        }
    }
    
    /**
     * Extract wallet account ID from token status
     */
    private fun extractWalletAccountIdFromTokenStatus(tokenStatusResult: Any): String? {
        // This is where you would parse the tokenStatusResult to extract the wallet account ID
        // The format of tokenStatusResult depends on the version of Google Pay API
        // For now, just use the string representation as a fallback
        return tokenStatusResult.toString()
    }
    
    /**
     * Get the primary Google account on the device
     */
    private fun getPrimaryGoogleAccount(): String? {
        try {
            val accountManager = android.accounts.AccountManager.get(context)
            val accounts = accountManager.getAccountsByType("com.google")
            
            if (accounts.isNotEmpty()) {
                // Use the first Google account
                return accounts[0].name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting primary Google account", e)
        }
        return null
    }
    
    /**
     * Parse the push provisioning response from WebView
     */
    fun parsePushProvisioningResponse(jsonData: String): PushProvisioningResponse? {
        // Use test implementation if testing is enabled
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            return testHelper.mockParsePushProvisioningResponse()
        }
        
        // Real implementation
        return try {
            val json = JSONObject(jsonData)
            val data = json.getJSONObject("data")
            
            val pushTokenizeRequestDataJson = data.getJSONObject("pushTokenizeRequestData")
            
            // Parse user address if it exists
            val userAddressJson = pushTokenizeRequestDataJson.optJSONObject("userAddress")
            val userAddress = userAddressJson?.let {
                UserAddress(
                    name = it.optString("name"),
                    address1 = it.optString("address1"),
                    address2 = it.optString("address2"),
                    city = it.optString("city"),
                    state = it.optString("state"),
                    postalCode = it.optString("postalCode"),
                    country = it.optString("country"),
                    phone = it.optString("phone")
                )
            }
            
            val pushTokenizeRequestData = PushTokenizeRequestData(
                displayName = pushTokenizeRequestDataJson.optString("displayName"),
                opaquePaymentCard = pushTokenizeRequestDataJson.optString("opaquePaymentCard"),
                lastDigits = pushTokenizeRequestDataJson.optString("lastDigits"),
                network = pushTokenizeRequestDataJson.optString("network"),
                tokenServiceProvider = pushTokenizeRequestDataJson.optString("tokenServiceProvider"),
                userAddress = userAddress
            )
            
            PushProvisioningResponse(
                cardToken = data.getString("cardToken"),
                createdTime = data.getString("createdTime"),
                lastModifiedTime = data.getString("lastModifiedTime"),
                pushTokenizeRequestData = pushTokenizeRequestData
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing push provisioning response", e)
            
            val error = ProvisioningError(
                code = ERROR_PARSING_RESPONSE,
                message = "Failed to parse push provisioning response",
                details = e.message
            )
            notifyError(error)
            
            null
        }
    }
    
    /**
     * Start push provisioning with data from WebView
     */
    fun startPushProvisioning(jsonData: String) {
        // Use test implementation if testing is enabled
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            testHelper.mockStartPushProvisioning()
            return
        }
        
        // Real implementation
        val response = parsePushProvisioningResponse(jsonData) ?: return
        
        // Create push tokenize request
        val pushTokenizeRequest = createPushTokenizeRequest(response)
        
        // Launch Google Pay push provisioning
        val task = payClient.pushTokenizeRequest(pushTokenizeRequest)
        
        task.addOnSuccessListener { intent ->
            // Launch the intent with our ActivityResultLauncher
            provisioningResultLauncher.launch(intent)
        }
        
        task.addOnFailureListener { exception ->
            Log.e(TAG, "Error launching push provisioning", exception)
            
            val error = ProvisioningError(
                code = ERROR_PROVISIONING_FAILED,
                message = "Failed to launch Google Pay push provisioning",
                details = exception.message
            )
            notifyError(error)
        }
    }
    
    /**
     * Create push tokenize request from response data
     */
    private fun createPushTokenizeRequest(response: PushProvisioningResponse): PayClient.PushTokenizeRequest {
        // Determine token service provider
        val tsp = when (response.pushTokenizeRequestData.tokenServiceProvider) {
            "TOKEN_PROVIDER_VISA" -> PayClient.TokenServiceProvider.VISA
            "TOKEN_PROVIDER_MASTERCARD" -> PayClient.TokenServiceProvider.MASTERCARD
            else -> PayClient.TokenServiceProvider.VISA // Default to VISA if not specified
        }
        
        // Build the push tokenize request
        return PayClient.PushTokenizeRequest.Builder()
            .setOpaquePaymentCard(response.pushTokenizeRequestData.opaquePaymentCard ?: "")
            .setTokenServiceProvider(tsp)
            .setDisplayName(response.pushTokenizeRequestData.displayName ?: "")
            .setLastDigits(response.pushTokenizeRequestData.lastDigits ?: "")
            .build()
    }
    
    /**
     * Handle successful event in the WebView (implementation of interface)
     */
    override fun handleSuccessEvent(data: String) {
        webView?.post {
            webView?.handleEvent(AccrueWebEvents.googleWalletProvisioningSuccessFunction, data)
        }
    }
    
    /**
     * Handle error event in the WebView (implementation of interface)
     */
    override fun handleErrorEvent(errorJson: String) {
        webView?.post {
            webView?.handleEvent(AccrueWebEvents.googleWalletProvisioningErrorFunction, errorJson)
        }
    }
    
    /**
     * Notify the WebView about an error
     */
    private fun notifyError(error: ProvisioningError) {
        val errorJson = JSONObject().apply {
            put("code", error.code)
            put("message", error.message)
            error.details?.let { put("details", it) }
        }.toString()
        
        handleErrorEvent(errorJson)
    }
    
    /**
     * Toggle test mode (exposed for testing)
     */
    fun setTestMode(enabled: Boolean, mockSuccess: Boolean = true) {
        testHelper.setTestMode(enabled, mockSuccess)
    }
    
    /**
     * Simulate a success response (exposed for testing)
     */
    fun simulateSuccess() {
        testHelper.simulateSuccess()
    }
    
    /**
     * Simulate an error response (exposed for testing)
     */
    fun simulateError(errorCode: String? = null, errorMessage: String? = null) {
        testHelper.simulateError(errorCode, errorMessage)
    }
} 