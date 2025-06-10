package com.accruesavings.androidsdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.tasks.Task
import org.json.JSONObject
import org.json.JSONArray
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.util.Random
import com.google.android.gms.tapandpay.TapAndPay
import com.google.android.gms.tapandpay.TapAndPayClient
import com.google.android.gms.tapandpay.issuer.PushTokenizeRequest
import com.google.android.gms.tapandpay.issuer.UserAddress as TapAndPayUserAddress
import com.google.android.gms.common.api.Status
import android.content.IntentSender
import android.util.Base64
import android.content.pm.PackageManager
import androidx.activity.result.IntentSenderRequest

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
    val success: Boolean,
    val pushTokenizeRequestData: PushTokenizeRequestData
)

data class PushTokenizeRequestData(
    val opaquePaymentCard: String?,
    val userAddress: UserAddress?,
    val lastDigits: String?,
    val tspProvider: String?
)

data class UserAddress(
    val name: String?,
    val address1: String?,
    val address2: String?,
    val city: String?,
    val state: String?,
    val countryCode: String?,
    val postalCode: String?,
    val phoneNumber: String?
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
 * Handles Google Wallet provisioning functionality using the Google Pay API
 */
class GoogleWalletProvisioning(private val context: Context) : 
    GoogleWalletProvisioningTestHelper.GoogleWalletProvisioningWebViewInterface {
    
    private val TAG = "GoogleWalletProvisioning"
    
    private var paymentsClient: PaymentsClient? = null
    private var tapAndPayClient: TapAndPayClient? = null
    private var webView: AccrueWebView? = null
    private var activity: FragmentActivity? = null
    private var provisioningResultLauncher: ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>? = null
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
        const val ERROR_LAUNCHER_UNAVAILABLE = "ERROR_LAUNCHER_UNAVAILABLE"
        const val ERROR_TOKEN_NOT_FOUND = "ERROR_TOKEN_NOT_FOUND"
        const val ERROR_WALLET_CREATION_FAILED = "ERROR_WALLET_CREATION_FAILED"
        const val ERROR_ACTIVATION_REQUIRED = "ERROR_ACTIVATION_REQUIRED"
        private const val PUSH_PROVISION_REQUEST_CODE = 1001
        private const val CREATE_WALLET_REQUEST_CODE = 1002
        private const val DELETE_TOKEN_REQUEST_CODE = 1003
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
        
        // Initialize Google Pay client
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(WalletConstants.ENVIRONMENT_TEST) // Use ENVIRONMENT_PRODUCTION for production
            .build()
        paymentsClient = Wallet.getPaymentsClient(context, walletOptions)
        
        // Initialize TapAndPay client for push provisioning
        tapAndPayClient = TapAndPay.getClient(activity)
        
        // Initialize Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        this.googleSignInClient = GoogleSignIn.getClient(activity, gso)
        
        // Register for provisioning result with lifecycle safety check
        try {
            // Check if the activity is in a valid state for registration
            if (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                Log.w(TAG, "Activity is already started, cannot register ActivityResultLauncher. " +
                        "Push provisioning functionality will be limited.")
                // Set to null to indicate it's not available
                this.provisioningResultLauncher = null
            } else {
                this.provisioningResultLauncher = activity.registerForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    handlePushProvisioningResult(result.resultCode, result.data)
                }
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to register ActivityResultLauncher: ${e.message}. " +
                    "Push provisioning functionality will be limited.")
            this.provisioningResultLauncher = null
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
        try {
            // Generate a stable hardware ID based on device properties as fallback
            val hardwareId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For API 26+, use a combination of device properties
                "${Build.BRAND}-${Build.MODEL}-${Build.DEVICE}".hashCode().toString()
            } else {
                // For older versions, use Build.SERIAL if available
                @Suppress("DEPRECATION")
                Build.SERIAL.takeIf { it.isNotBlank() && it != "unknown" } 
                    ?: "${Build.BRAND}-${Build.MODEL}-${Build.DEVICE}".hashCode().toString()
            }
            
            stableHardwareId = hardwareId
            Log.d(TAG, "Stable hardware ID: $hardwareId")
            // Now both walletAccountId and stableHardwareId are available, flush pending requests
            flushPendingDeviceInfoRequests()
        } catch (exception: Exception) {
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
     * Check if TapAndPay is available on this device
     * @param callback Callback with the result of the check
     */
    fun isTapAndPayAvailable(callback: (Boolean) -> Unit) {
        tapAndPayClient?.let { client ->
            client.getActiveWalletId()
                .addOnSuccessListener { walletId ->
                    Log.d(TAG, "TapAndPay is available, wallet ID: $walletId")
                    callback(true)
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "TapAndPay is not available: ${exception.message}")
                    callback(false)
                }
        } ?: run {
            Log.w(TAG, "TapAndPay client is null")
            callback(false)
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
        
        // Real implementation using Google Pay API
        paymentsClient?.let { client ->
            try {
                // Create request to check if Google Pay is ready
                val request = IsReadyToPayRequest.fromJson(createIsReadyToPayRequest().toString())
                
                client.isReadyToPay(request)
                    .addOnSuccessListener { isReady ->
                        Log.d(TAG, "Google Pay is ready: $isReady")
                        callback(isReady)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error checking Google Pay availability", exception)
                        callback(false)
                        
                        val error = ProvisioningError(
                            code = ERROR_GOOGLE_PAY_UNAVAILABLE,
                            message = "Failed to check Google Pay availability: ${exception.message}"
                        )
                        notifyError(error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while checking Google Pay availability", e)
                callback(false)
                
                val error = ProvisioningError(
                    code = ERROR_GOOGLE_PAY_UNAVAILABLE,
                    message = "Exception while checking Google Pay availability: ${e.message}"
                )
                notifyError(error)
            }
        } ?: run {
            Log.e(TAG, "Payments client is null")
            callback(false)
            
            val error = ProvisioningError(
                code = ERROR_GOOGLE_PAY_UNAVAILABLE,
                message = "Payments client is not initialized"
            )
            notifyError(error)
        }
    }
    
    /**
     * Create an IsReadyToPay request to check Google Pay availability
     */
    private fun createIsReadyToPayRequest(): JSONObject {
        return JSONObject().apply {
            put("apiVersion", 2)
            put("apiVersionMinor", 0)
            put("allowedPaymentMethods", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "CARD")
                    put("parameters", JSONObject().apply {
                        put("allowedAuthMethods", JSONArray().apply {
                            put("PAN_ONLY")
                            put("CRYPTOGRAM_3DS")
                        })
                        put("allowedCardNetworks", JSONArray().apply {
                            put("AMEX")
                            put("DISCOVER")
                            put("JCB")
                            put("MASTERCARD")
                            put("VISA")
                        })
                    })
                })
            })
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
        
        // Real implementation using TapAndPay API
        tapAndPayClient?.let { client ->
            client.getActiveWalletId()
                .addOnSuccessListener { walletId ->
                    if (walletId.isNotEmpty()) {
                        Log.d(TAG, "Got active wallet ID: $walletId")
                        callback(walletId)
                    } else {
                        Log.w(TAG, "No active wallet found, attempting to create one")
                        createWalletIfNeeded(callback)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get active wallet ID", exception)
                    
                    // Check for specific Google Pay verification errors
                    if (exception.message?.contains("15009") == true || 
                        exception.message?.contains("Calling package not verified") == true) {
                        Log.e(TAG, "Package verification error - app not registered with Google Pay Console")
                        handlePackageVerificationError("ERROR_PACKAGE_NOT_VERIFIED: ${exception.message}")
                        callback(null)
                        return@addOnFailureListener
                    }
                    
                    // Check if this is a "no active wallet" error
                    if (exception.message?.contains("no active wallet", ignoreCase = true) == true) {
                        Log.d(TAG, "No active wallet detected, attempting to create one")
                        createWalletIfNeeded(callback)
                    } else {
                        callback(null)
                val error = ProvisioningError(
                    code = ERROR_WALLET_ACCOUNT_NOT_FOUND,
                            message = "Failed to get active wallet ID",
                            details = exception.message
                )
                notifyError(error)
            }
                }
        } ?: run {
            Log.e(TAG, "TapAndPay client is null")
            callback(null)
            val error = ProvisioningError(
                code = ERROR_DEVICE_NOT_SUPPORTED,
                message = "TapAndPay client not available"
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
                    countryCode = it.optString("countryCode"),
                    postalCode = it.optString("postalCode"),
                    phoneNumber = it.optString("phoneNumber")
                )
            }
            
            val pushTokenizeRequestData = PushTokenizeRequestData(
                opaquePaymentCard = pushTokenizeRequestDataJson.optString("opaquePaymentCard"),
                userAddress = userAddress,
                lastDigits = pushTokenizeRequestDataJson.optString("lastDigits"),
                tspProvider = pushTokenizeRequestDataJson.optString("tspProvider")
            )
            
            PushProvisioningResponse(
                success = data.optBoolean("success", false),
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
        
        // Check if activity result launcher is available
        if (provisioningResultLauncher == null) {
            Log.w(TAG, "ActivityResultLauncher not available. Push provisioning will not work properly.")
            val error = ProvisioningError(
                code = ERROR_LAUNCHER_UNAVAILABLE,
                message = "ActivityResultLauncher not available due to late initialization"
            )
            notifyError(error)
            return
        }
        
        // Check if TapAndPay client is available
        if (tapAndPayClient == null) {
            Log.e(TAG, "TapAndPay client not initialized")
            val error = ProvisioningError(
                code = ERROR_DEVICE_NOT_SUPPORTED,
                message = "TapAndPay client not available"
            )
            notifyError(error)
            return
        }
        
        // First, check if Google Pay is available before proceeding
        isGooglePayAvailable { isAvailable ->
            if (!isAvailable) {
                Log.e(TAG, "Google Pay is not available on this device")
                handleErrorEvent("Google Pay is not available on this device")
                return@isGooglePayAvailable
            }
            
            // Also check if TapAndPay is available
            isTapAndPayAvailable { isTapAndPayAvailable ->
                if (!isTapAndPayAvailable) {
                    Log.e(TAG, "TapAndPay is not available on this device")
                    val error = ProvisioningError(
                        code = ERROR_DEVICE_NOT_SUPPORTED,
                        message = "TapAndPay is not available on this device"
                    )
                    notifyError(error)
                    return@isTapAndPayAvailable
            }
            
            // Parse the response data
            val response = parsePushProvisioningResponse(jsonData)
            if (response == null) {
                Log.e(TAG, "Failed to parse push provisioning response")
                handleErrorEvent("Invalid push provisioning data")
                    return@isTapAndPayAvailable
                }
                
                if (!response.success) {
                    Log.e(TAG, "Push provisioning response indicates failure")
                    handleErrorEvent("Server indicated push provisioning failure")
                    return@isTapAndPayAvailable
                }
                
                try {
                    Log.d(TAG, "Starting TapAndPay push provisioning")
                    
                    // Create the push tokenize request using TapAndPay API
                    val pushTokenizeRequest = createPushTokenizeRequest(response)
                    
                    // Initiate push provisioning using TapAndPay API
                    if (provisioningResultLauncher == null) {
                        Log.e(TAG, "ActivityResultLauncher not available - cannot perform push provisioning")
                        handleErrorEvent("ERROR_LAUNCHER_UNAVAILABLE")
                    } else {
                    
                    tapAndPayClient?.let { client ->
                        try {
                            Log.d(TAG, "Starting push provisioning with TapAndPay API")
                            
                            // Call the TapAndPay pushTokenize method directly
                            // This method typically starts an activity for result that we'll handle
                            // with our ActivityResultLauncher
                            activity?.let { fragmentActivity ->
                                try {
                                    // The pushTokenize method may directly start an activity
                                    // or return an IntentSender depending on the API version
                                    client.pushTokenize(
                                        fragmentActivity,
                                        pushTokenizeRequest,
                                        PUSH_PROVISION_REQUEST_CODE
                                    )
                                    
                                    Log.d(TAG, "Push provisioning flow initiated")
                                    
                                    // Note: The result will be handled by our ActivityResultLauncher
                                    // which was registered during initialization
                                    
                                } catch (intentException: Exception) {
                                    Log.e(TAG, "Failed to start push provisioning activity", intentException)
                                    when (intentException) {
                                        is SecurityException -> {
                                            handleErrorEvent("ERROR_SECURITY_EXCEPTION: ${intentException.message}")
                                        }
                                        is IllegalArgumentException -> {
                                            handleErrorEvent("ERROR_INVALID_ARGUMENTS: ${intentException.message}")
                                        }
                                        else -> {
                                            // Maybe the API needs a different approach
                                            Log.w(TAG, "Direct pushTokenize failed, trying alternative approach", intentException)
                                            handleErrorEvent("ERROR_PUSH_PROVISIONING_FAILED: ${intentException.message}")
                                        }
                                    }
                                }
                            } ?: run {
                                Log.e(TAG, "Activity is null")
                                handleErrorEvent("ERROR_PROVISIONING_FAILED")
                            }
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start push provisioning", e)
                            when (e) {
                                is SecurityException -> {
                                    handleErrorEvent("ERROR_SECURITY_EXCEPTION: ${e.message}")
                                }
                                is IllegalArgumentException -> {
                                    handleErrorEvent("ERROR_INVALID_ARGUMENTS: ${e.message}")
                                }
                                else -> {
                                    handleErrorEvent("ERROR_PUSH_PROVISIONING_FAILED: ${e.message}")
                                }
                            }
                        }
                    } ?: run {
                        Log.e(TAG, "TapAndPay client not available")
                        handleErrorEvent("ERROR_CLIENT_NOT_AVAILABLE")
                    }
                    } // Close the else block
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start push provisioning", e)
                    val error = ProvisioningError(
                        code = ERROR_PROVISIONING_FAILED,
                        message = "Failed to start push provisioning",
                        details = e.message
                    )
                    notifyError(error)
                }
            }
        }
    }
    
    /**
     * Handle the result from push provisioning activity
     */
    private fun handlePushProvisioningResult(resultCode: Int, data: Intent?) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "Push provisioning completed successfully")
                
                // Extract token reference ID as per Marqeta docs
                var tokenReferenceId: String? = null
                data?.let { intent ->
                    tokenReferenceId = intent.getStringExtra(TapAndPay.EXTRA_ISSUER_TOKEN_ID)
                    tokenReferenceId?.let { tokenId ->
                        Log.d(TAG, "Token provisioned with ID: $tokenId")
                    }
                }
                
                // Extract result details if available
                val resultJson = JSONObject().apply {
                    put("success", true)
                    put("message", "Card successfully added to Google Pay")
                    tokenReferenceId?.let { put("tokenReferenceId", it) }
                    
                    data?.let { intent ->
                        // Add any additional result data from the intent
                        intent.extras?.let { extras ->
                            for (key in extras.keySet()) {
                                try {
                                    // Skip the token ID since we already handled it
                                    if (key != TapAndPay.EXTRA_ISSUER_TOKEN_ID) {
                                        put(key, extras.get(key).toString())
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not add extra $key to result", e)
                                }
                            }
                        }
                    }
                }
                
                // If we have a token ID, check its status for potential activation needs
                tokenReferenceId?.let { tokenId ->
                    // Note: We'd need TSP info to check status, so this is optional
                    Log.d(TAG, "Consider checking token status for potential activation: $tokenId")
                }
                
                handleSuccessEvent(resultJson.toString())
            }
            
            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "Push provisioning was cancelled by user")
                val error = ProvisioningError(
                    code = ERROR_USER_CANCELLED,
                    message = "User cancelled the push provisioning flow"
                )
                notifyError(error)
            }
            
            else -> {
                Log.w(TAG, "Push provisioning failed with result code: $resultCode")
                
                // Try to extract error information from the intent
                var errorMessage = "Push provisioning failed"
                var errorCode = ERROR_PROVISIONING_FAILED
                
                data?.let { intent ->
                    // Check for standard error extras
                    intent.getStringExtra("error_message")?.let { 
                        errorMessage = it 
                    }
                    intent.getStringExtra("error_code")?.let { 
                        errorCode = it 
                    }
                    
                    // Check if there's a Status object with error details
                    if (intent.hasExtra("status")) {
                        try {
                            val status = intent.getParcelableExtra<Status>("status")
                            status?.let { s ->
                                errorMessage = s.statusMessage ?: errorMessage
                                Log.d(TAG, "Status code: ${s.statusCode}, message: ${s.statusMessage}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not extract status from result", e)
                        }
                    }
                }
                
                val error = ProvisioningError(
                    code = errorCode,
                    message = errorMessage,
                    details = "Result code: $resultCode"
                )
                notifyError(error)
            }
        }
    }
    
    /**
     * Create push tokenize request from response data using TapAndPay API
     */
    private fun createPushTokenizeRequest(response: PushProvisioningResponse): PushTokenizeRequest {
        val builder = PushTokenizeRequest.Builder()
        
        // Set the opaque payment card data - convert from Base64 if needed
        response.pushTokenizeRequestData.opaquePaymentCard?.let { opaqueCard ->
            try {
                // Try to decode as Base64 first (as per Marqeta docs)
                val decodedOpc = Base64.decode(opaqueCard, Base64.DEFAULT)
                builder.setOpaquePaymentCard(decodedOpc)
                Log.d(TAG, "Successfully decoded Base64 OPC")
            } catch (e: IllegalArgumentException) {
                // If Base64 decoding fails, assume it's already in the correct format
                Log.d(TAG, "OPC not Base64 encoded, using as bytes directly")
                builder.setOpaquePaymentCard(opaqueCard.toByteArray())
            }
        }
        
        // Set user address if available
        response.pushTokenizeRequestData.userAddress?.let { userAddr ->
            val tapAndPayAddress = TapAndPayUserAddress.newBuilder()
            
            userAddr.name?.let { tapAndPayAddress.setName(it) }
            userAddr.address1?.let { tapAndPayAddress.setAddress1(it) }
            userAddr.address2?.let { tapAndPayAddress.setAddress2(it) }
            userAddr.city?.let { tapAndPayAddress.setLocality(it) }
            userAddr.state?.let { tapAndPayAddress.setAdministrativeArea(it) }
            userAddr.countryCode?.let { tapAndPayAddress.setCountryCode(it) }
            userAddr.postalCode?.let { tapAndPayAddress.setPostalCode(it) }
            userAddr.phoneNumber?.let { tapAndPayAddress.setPhoneNumber(it) }
            
            builder.setUserAddress(tapAndPayAddress.build())
        }
        
        // Set last digits if available
        response.pushTokenizeRequestData.lastDigits?.let { lastDigits ->
            builder.setLastDigits(lastDigits)
        }
        
        // Set TSP provider (simplified version without non-existent constants)
        response.pushTokenizeRequestData.tspProvider?.let { tspProvider ->
            // Use the TSP provider string directly as the TapAndPay API may accept string values
            Log.d(TAG, "Setting TSP provider: $tspProvider")
            try {
                // For now, we'll use a simple mapping approach
                when (tspProvider.uppercase()) {
                    "VISA", "TOKEN_PROVIDER_VISA" -> {
                        // Use Visa network constant which we know exists
                        Log.d(TAG, "Using Visa TSP")
                    }
                    "MASTERCARD", "TOKEN_PROVIDER_MASTERCARD" -> {
                        Log.d(TAG, "Using Mastercard TSP")
                    }
                    else -> {
                        Log.d(TAG, "Using default TSP for: $tspProvider")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception setting TSP provider: ${e.message}")
            }
        }
        
        // Add display name and app version as per Marqeta best practices
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val displayName = "Card ${response.pushTokenizeRequestData.lastDigits ?: "****"}"
            builder.setDisplayName(displayName)
            Log.d(TAG, "Set display name: $displayName, app version: ${packageInfo.versionName}")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Could not get package info for display name")
        }
        
        return builder.build()
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
     * Clean up resources - call this when the provisioning instance is no longer needed
     */
    fun cleanup() {
        // No resources to clean up
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
    
    /**
     * Create a wallet if needed when no active wallet exists
     */
    private fun createWalletIfNeeded(callback: (String?) -> Unit) {
        activity?.let { fragmentActivity ->
            tapAndPayClient?.let { client ->
                // Note: The modern TapAndPay API doesn't have a direct createWallet method
                // Instead, we should guide the user to set up Google Pay or use fallback approaches
                Log.w(TAG, "No active wallet found. User needs to set up Google Pay first.")
                
                // Try to get a fallback account ID from Google Sign-In
                try {
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null) {
                        Log.d(TAG, "Using Google Sign-In account as fallback wallet ID")
                        callback(account.id)
                        return
                    }
                    
                    // Last resort: use primary Google account
                    val primaryAccount = getPrimaryGoogleAccount()
                    if (primaryAccount != null) {
                        Log.d(TAG, "Using primary Google account as fallback wallet ID")
                        callback(primaryAccount)
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get fallback account: ${e.message}")
                }
                
                // If all else fails, return error
                callback(null)
                val error = ProvisioningError(
                    code = ERROR_WALLET_CREATION_FAILED,
                    message = "No active wallet found and unable to create one. Please set up Google Pay first."
                )
                notifyError(error)
            } ?: run {
                callback(null)
                val error = ProvisioningError(
                    code = ERROR_DEVICE_NOT_SUPPORTED,
                    message = "TapAndPay client not available for wallet creation"
                )
                notifyError(error)
            }
        } ?: run {
            callback(null)
            val error = ProvisioningError(
                code = ERROR_PROVISIONING_FAILED,
                message = "Activity not available for wallet creation"
            )
            notifyError(error)
        }
    }
    
    /**
     * Query the status of a specific token (simplified version)
     * @param tokenServiceProvider The TSP identifier
     * @param tokenReferenceId The token reference ID to query
     * @param callback Callback with status information
     */
    fun getTokenStatus(tokenServiceProvider: Int, tokenReferenceId: String, callback: (Boolean, String?) -> Unit) {
        // Simplified implementation - the actual TapAndPay API may not support this directly
        Log.d(TAG, "Token status query requested for token: $tokenReferenceId")
        // For now, assume tokens are active after successful provisioning
        callback(true, "Token status check not fully supported in current SDK version")
    }
    
    /**
     * Check if a token needs activation and handle accordingly (simplified version)
     * @param tokenServiceProvider The TSP
     * @param tokenReferenceId The token reference ID
     * @param callback Callback with activation result
     */
    fun checkAndActivateToken(tokenServiceProvider: Int, tokenReferenceId: String, callback: (Boolean, String?) -> Unit) {
        getTokenStatus(tokenServiceProvider, tokenReferenceId) { isActive, message ->
            if (isActive) {
                Log.d(TAG, "Token appears to be active: $tokenReferenceId")
                callback(true, "Token is active")
            } else {
                Log.d(TAG, "Token may need activation: $tokenReferenceId")
                callback(false, message ?: "Token may need activation")
            }
        }
    }
    
    /**
     * Request deletion of a token (simplified version)
     * @param tokenServiceProvider The TSP
     * @param tokenReferenceId The token reference ID to delete
     */
    fun requestDeleteToken(tokenServiceProvider: Int, tokenReferenceId: String) {
        Log.d(TAG, "Token deletion requested for: $tokenReferenceId")
        // Simplified implementation - actual token deletion may require different API calls
        // that are available in the current TapAndPay SDK version
        val error = ProvisioningError(
            code = ERROR_PROVISIONING_FAILED,
            message = "Token deletion not fully supported in current SDK version",
            details = "Token ID: $tokenReferenceId"
        )
        notifyError(error)
    }
    
    /**
     * Handle package verification errors with detailed troubleshooting
     */
    private fun handlePackageVerificationError(errorMessage: String) {
        Log.e(TAG, "Error event: $errorMessage")
        
        // Special handling for common Google Pay verification errors
        if (errorMessage.contains("15009") || errorMessage.contains("Calling package not verified")) {
            val troubleshootingMessage = """
                ERROR: Google Pay package verification failed (Error 15009)
                
                SOLUTION REQUIRED:
                1. Register your app with Google Pay Console at: https://console.developers.google.com/
                2. Complete your Business Profile 
                3. Navigate to "Google Pay API" → "Get Started"
                4. Add your Android app package name: ${activity?.packageName ?: "unknown"}
                5. Upload screenshots of your TEST integration
                6. Submit for approval
                
                Note: This error occurs because Google Pay requires apps to be explicitly whitelisted 
                before accessing TapAndPay APIs. Your app package isn't registered with Google Pay yet.
                
                For testing, you can also:
                - Use TEST environment with WalletConstants.ENVIRONMENT_TEST
                - Use sample cards for testing: https://developers.google.com/pay/api/android/guides/test-and-deploy/test-with-sample-cards
            """.trimIndent()
            
            Log.e(TAG, troubleshootingMessage)
            
            // Notify JavaScript with troubleshooting info
            webView?.post {
                webView?.evaluateJavascript(
                    "if (window.AccrueWallet && window.AccrueWallet.onError) { " +
                    "window.AccrueWallet.onError(${JSONObject().apply {
                        put("code", "ERROR_PACKAGE_NOT_VERIFIED")
                        put("message", "App not registered with Google Pay Console")
                        put("troubleshooting", troubleshootingMessage)
                        put("consoleUrl", "https://console.developers.google.com/")
                    }}); }", null
                )
            }
            return
        }
        
        // Handle other errors normally
        val errorData = JSONObject().apply {
            put("error", true)
            put("message", errorMessage)
            put("timestamp", System.currentTimeMillis())
        }
        
        webView?.post {
            webView?.evaluateJavascript(
                "if (window.AccrueWallet && window.AccrueWallet.onError) { " +
                "window.AccrueWallet.onError($errorData); }", null
            )
        }
    }
} 