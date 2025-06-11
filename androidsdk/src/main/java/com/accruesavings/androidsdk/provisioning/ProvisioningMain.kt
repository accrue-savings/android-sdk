package com.accruesavings.androidsdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log 
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.tapandpay.TapAndPay
import com.google.android.gms.tapandpay.TapAndPayStatusCodes
import com.google.android.gms.tapandpay.issuer.TokenInfo
import com.google.android.gms.tapandpay.issuer.TokenStatus
import org.json.JSONObject
import org.json.JSONArray
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.util.concurrent.CountDownLatch

// Import our modular provisioning components
import com.accruesavings.androidsdk.provisioning.core.TapAndPayClientManager
import com.accruesavings.androidsdk.provisioning.core.PushProvisioningService
import com.accruesavings.androidsdk.provisioning.core.EnvironmentService
import com.accruesavings.androidsdk.provisioning.core.TokenManagementService
import com.accruesavings.androidsdk.provisioning.core.NfcStatus
import com.accruesavings.androidsdk.provisioning.device.DeviceInfoService
import com.accruesavings.androidsdk.provisioning.device.DeviceInfo as NewDeviceInfo
import com.accruesavings.androidsdk.provisioning.error.ErrorHandler
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError
import com.accruesavings.androidsdk.provisioning.error.ErrorCodes
import com.accruesavings.androidsdk.provisioning.config.ProvisioningConstants
import com.accruesavings.androidsdk.provisioning.core.GooglePayDiagnostics

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
 * Matches PublicGoogleWalletProvisioningResponse interface
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
    val network: String?, // 'Visa' | 'Mastercard'
    val tokenServiceProvider: String?, // 'TOKEN_PROVIDER_VISA' | 'TOKEN_PROVIDER_MASTERCARD'
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
 * Data class for comprehensive device and environment information
 */
data class EnvironmentInfo(
    val environment: String,
    val isGooglePayDefault: Boolean,
    val nfcStatus: NfcStatus,
    val hardwareId: String?,
    val walletId: String?
)

/**
 * Main coordinator for Google Wallet Provisioning operations
 * Delegates to specialized services for clean separation of concerns
 */
class ProvisioningMain(private val context: Context) {
    
    private val TAG = ProvisioningConstants.LogTags.MAIN
    
    // Core components
    private var paymentsClient: PaymentsClient? = null
    private var webView: AccrueWebView? = null
    private var activity: FragmentActivity? = null
    
    // Specialized services
    private lateinit var tapAndPayClientManager: TapAndPayClientManager
    private lateinit var pushProvisioningService: PushProvisioningService
    private lateinit var environmentService: EnvironmentService
    private lateinit var tokenManagementService: TokenManagementService
    private lateinit var deviceInfoService: DeviceInfoService
    private lateinit var errorHandler: ErrorHandler
    private lateinit var diagnostics: GooglePayDiagnostics
    
    // Activity result handling
    private var pendingProvisioningCallback: ((Boolean, String?) -> Unit)? = null
    private var activityResultHandler: com.accruesavings.androidsdk.provisioning.core.ActivityResultHandler? = null
    
    /**
     * Initialize all services and components
     */
    fun initialize(activity: FragmentActivity, webView: AccrueWebView, activityResultHandler: com.accruesavings.androidsdk.provisioning.core.ActivityResultHandler? = null) {
        this.activity = activity
        this.webView = webView
        this.activityResultHandler = activityResultHandler
        
        Log.d(TAG, "Initializing ProvisioningMain")
        
        // Initialize Google Pay client
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
            .build()
        paymentsClient = Wallet.getPaymentsClient(context, walletOptions)
        
        // Initialize specialized services
        errorHandler = ErrorHandler(webView)
        tapAndPayClientManager = TapAndPayClientManager(context)
        environmentService = EnvironmentService(context, errorHandler)
        tokenManagementService = TokenManagementService(errorHandler)
        pushProvisioningService = PushProvisioningService(tapAndPayClientManager, errorHandler)
        deviceInfoService = DeviceInfoService(context)
        diagnostics = GooglePayDiagnostics(context)
        
        // Initialize TapAndPay client
        if (tapAndPayClientManager.initialize(activity)) {
            tapAndPayClientManager.tapAndPayClient?.let { client ->
                environmentService.initialize(client)
                tokenManagementService.initialize(client)
                
                // Register data change listener
                environmentService.registerDataChangeListener {
                    Log.d(TAG, "Google Pay data changed - refreshing state")
                    notifyDataChanged()
                }
            }
        } else {
            Log.w(TAG, "Failed to initialize TapAndPay client")
            
            // Run diagnostics to help identify the issue
            Log.w(TAG, "Running diagnostics to identify TapAndPay issues...")
            diagnostics.logDiagnostics()
            
            // Provide user-friendly error message
            val summary = diagnostics.getSummary()
            Log.w(TAG, summary)
            
            // Notify JavaScript with diagnostic information
            notifyError(ErrorCodes.ERROR_TAP_AND_PAY_UNAVAILABLE, 
                "TapAndPay initialization failed. Check device compatibility and Google Pay setup.")
        }
        
        Log.d(TAG, "ProvisioningMain initialized successfully")
    }
    
    /**
     * Check if TapAndPay is available on this device
     */
    fun isTapAndPayAvailable(callback: (Boolean) -> Unit) {
        Log.d(TAG, "Checking TapAndPay availability")
        tapAndPayClientManager.isTapAndPayAvailable(callback)
    }
    
    /**
     * Check if Google Pay is available on this device
     */
    fun isGooglePayAvailable(callback: (Boolean) -> Unit) {
        Log.d(TAG, "Checking Google Pay availability")
        
        paymentsClient?.let { client ->
            val request = IsReadyToPayRequest.fromJson(createIsReadyToPayRequest().toString())
            client.isReadyToPay(request)
                .addOnSuccessListener { callback(it) }
                .addOnFailureListener { 
                    Log.e(TAG, "Failed to check Google Pay availability", it)
                    callback(false)
                    notifyError(ErrorCodes.ERROR_GOOGLE_PAY_UNAVAILABLE, "Failed to check Google Pay availability: ${it.message}")
                }
        } ?: run {
            callback(false)
            notifyError(ErrorCodes.ERROR_GOOGLE_PAY_UNAVAILABLE, "Payments client not initialized")
        }
    }
    
    /**
     * Get comprehensive environment information
     */
    fun getEnvironmentInfo(callback: (EnvironmentInfo?) -> Unit) {
        Log.d(TAG, "Getting environment information")
        
        var environment: String? = null
        var isDefault: Boolean? = null
        var hardwareId: String? = null
        var walletId: String? = null
        val nfcStatus = environmentService.checkNfcStatus()
        
        val countdown = CountDownLatch(4)
        
        // Get environment
        environmentService.getEnvironment { env ->
            environment = env
            countdown.countDown()
        }
        
        // Check if default wallet
        environmentService.isDefaultWallet { isDefaultWallet ->
            isDefault = isDefaultWallet
            countdown.countDown()
        }
        
        // Get hardware ID
        tapAndPayClientManager.getStableHardwareId { hwId ->
            hardwareId = hwId
            countdown.countDown()
        }
        
        // Get wallet ID
        tapAndPayClientManager.getActiveWalletId(
            onSuccess = { wId ->
                walletId = wId
                countdown.countDown()
            },
            onFailure = {
                countdown.countDown()
            }
        )
        
        // Wait for all async operations or timeout after 5 seconds
        CoroutineScope(Dispatchers.IO).launch {
            try {
                countdown.await(5, java.util.concurrent.TimeUnit.SECONDS)
                
                val envInfo = EnvironmentInfo(
                    environment = environment ?: "UNKNOWN",
                    isGooglePayDefault = isDefault ?: false,
                    nfcStatus = nfcStatus,
                    hardwareId = hardwareId,
                    walletId = walletId
                )
                
                withContext(Dispatchers.Main) {
                    callback(envInfo)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting environment info", e)
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }
    
    /**
     * Get device information for provisioning
     */
    fun getDeviceInfo(callback: (DeviceInfo) -> Unit) {
        Log.d(TAG, "Getting device information")
        
        tapAndPayClientManager.getActiveWalletId(
            onSuccess = { walletId ->
                deviceInfoService.getDeviceInfo(walletId) { newInfo ->
                    val legacyInfo = newInfo?.let { convertToLegacyDeviceInfo(it) }
                        ?: createFallbackDeviceInfo(walletId)
                    callback(legacyInfo)
                }
            },
            onFailure = { 
                Log.w(TAG, "Failed to get wallet ID, using fallback device info")
                callback(createFallbackDeviceInfo("unknown"))
            }
        )
    }
    
    /**
     * Start push provisioning with data from WebView
     */
    fun startPushProvisioning(jsonData: String) {
        Log.d(TAG, "ProvisioningMain: Starting push provisioning")

        // Check prerequisites then delegate to service
        CoroutineScope(Dispatchers.Main).launch {
            try {
                checkPrerequisitesAndProvision(jsonData)
            } catch (e: Exception) {
                Log.e(TAG, "Error in provisioning prerequisites", e)
                notifyError(ErrorCodes.ERROR_PUSH_PROVISIONING_FAILED, "Provisioning failed: ${e.message}")
            }
        }
    }

    private suspend fun checkPrerequisitesAndProvision(jsonData: String) {
        Log.d(TAG, "Checking prerequisites and provisioning")
        
        // Early return if Google Pay not available
        if (!isGooglePayAvailableAsync()) {
            notifyError(ErrorCodes.ERROR_GOOGLE_PAY_UNAVAILABLE, "Google Pay not available")
            return
        }
        
        // Early return if TapAndPay not available
        if (!isTapAndPayAvailableAsync()) {
            notifyError(ErrorCodes.ERROR_DEVICE_NOT_SUPPORTED, "TapAndPay not available")
            return
        }
        
        // Early return if parsing fails
        val response = parsePushProvisioningResponse(jsonData)
        if (response == null) {
            notifyError("ERROR_PARSING_RESPONSE", "Invalid provisioning data")
            return
        }
        
        // Early return if activity not available
        val currentActivity = activity
        if (currentActivity == null) {
            notifyError(ErrorCodes.ERROR_PUSH_PROVISIONING_FAILED, "Activity not available")
            return
        }
        
        // Check if card is already provisioned
        val network = mapNetworkToTapAndPayConstant(response.pushTokenizeRequestData.network ?: "Visa")
        val tsp = mapTokenServiceProviderToConstant(response.pushTokenizeRequestData.tokenServiceProvider ?: "")
        
        tokenManagementService.isTokenized(tsp, network) { isAlreadyTokenized ->
            if (isAlreadyTokenized == true) {
                Log.d(TAG, "Card is already provisioned")
                notifyError(ErrorCodes.ERROR_PUSH_PROVISIONING_FAILED, "Card is already added to Google Pay")
                return@isTokenized
            }
            
            // Delegate to service
            pushProvisioningService.startPushProvisioning(
                activity = currentActivity,
                provisioningData = response,
            ) { result ->
                result.fold(
                    onSuccess = { 
                        Log.d(TAG, "Push provisioning initiated successfully")
                        // Result will be handled in onActivityResult
                    },
                    onFailure = { 
                        notifyError("PROVISIONING_FAILED", "Provisioning failed: ${it.message}")
                    }
                )
            }
        }
    }
    
    /**
     * Handle activity results from various Google Pay operations
     * This method should be called from the activity's onActivityResult
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "Handling activity result: requestCode=$requestCode, resultCode=$resultCode")
        
        when (requestCode) {
            ProvisioningConstants.PUSH_PROVISION_REQUEST_CODE -> {
                handlePushProvisioningResult(resultCode, data)
            }
            EnvironmentService.SET_DEFAULT_PAYMENTS_REQUEST_CODE -> {
                handleSetDefaultWalletResult(resultCode)
            }
            EnvironmentService.CREATE_WALLET_REQUEST_CODE -> {
                handleCreateWalletResult(resultCode, data)
            }
            TokenManagementService.VIEW_TOKEN_REQUEST_CODE -> {
                handleViewTokenResult(resultCode, data)
            }
            else -> {
                Log.w(TAG, "Unhandled activity result: $requestCode")
            }
        }
    }
    
    /**
     * List all tokens in Google Pay wallet
     */
    fun listTokens(callback: (List<TokenInfo>?) -> Unit) {
        Log.d(TAG, "Listing tokens")
        tokenManagementService.listTokens { tokens ->
            tokens?.let {
                Log.d(TAG, "Found ${it.size} tokens")
                val tokenJson = JSONArray()
                it.forEach { token ->
                    val tokenObj = JSONObject().apply {
                        put("tokenId", token.issuerTokenId ?: "")
                        put("issuerTokenId", token.issuerTokenId ?: "")
                        put("network", token.network ?: 0)
                        put("tokenServiceProvider", token.tokenServiceProvider ?: 0)
                        put("displayName", "Card ending in ${token.issuerTokenId?.takeLast(4) ?: "****"}")
                    }
                    tokenJson.put(tokenObj)
                }
                notifySuccess("{\"tokens\": $tokenJson}")
            }
            callback(tokens)
        }
    }
    
    /**
     * Get token status with comprehensive information
     */
    fun getTokenStatus(tokenServiceProvider: Int, tokenReferenceId: String, callback: (Boolean, String?) -> Unit) {
        Log.d(TAG, "Token status query requested for token: $tokenReferenceId")
        
        tokenManagementService.getTokenStatus(tokenServiceProvider, tokenReferenceId) { tokenStatus ->
            if (tokenStatus != null) {
                val isActive = tokenStatus.tokenState == TapAndPay.TOKEN_STATE_ACTIVE
                val statusDescription = tokenManagementService.getTokenStateDescription(tokenStatus.tokenState)
                
                val statusJson = JSONObject().apply {
                    put("tokenId", tokenReferenceId)
                    put("tokenState", tokenStatus.tokenState)
                    put("tokenStateDescription", statusDescription)
                    put("isActive", isActive)
                    put("canMakePayments", isActive)
                }
                
                notifySuccess("{\"tokenStatus\": $statusJson}")
                callback(isActive, statusDescription)
            } else {
                callback(false, "Token status unavailable")
            }
        }
    }
    
    /**
     * Check if a token needs activation and handle accordingly
     */
    fun checkAndActivateToken(tokenServiceProvider: Int, tokenReferenceId: String, callback: (Boolean, String?) -> Unit) {
        getTokenStatus(tokenServiceProvider, tokenReferenceId) { isActive, message ->
            if (!isActive && message?.contains("verification", ignoreCase = true) == true) {
                // Token needs verification - launch token view for user action
                activity?.let { act ->
                    tokenManagementService.viewToken(act, tokenReferenceId) { success ->
                        callback(success, if (success) "Verification flow launched" else "Failed to launch verification")
                    }
                } ?: callback(false, "Activity not available")
            } else {
                callback(isActive, message)
            }
        }
    }
    
    /**
     * Request deletion of a token
     */
    fun requestDeleteToken(tokenServiceProvider: Int, tokenReferenceId: String) {
        Log.d(TAG, "Token deletion requested for: $tokenReferenceId")
        
        tokenManagementService.requestDeleteToken(tokenServiceProvider, tokenReferenceId) { success ->
            if (success) {
                notifySuccess("{\"message\": \"Token deletion initiated\", \"tokenId\": \"$tokenReferenceId\"}")
            } else {
                notifyError(ErrorCodes.ERROR_TOKEN_DELETE_FAILED, "Failed to initiate token deletion", "Token ID: $tokenReferenceId")
            }
        }
    }
    
    /**
     * Set Google Pay as the default payment app
     */
    fun setGooglePayAsDefault() {
        Log.d(TAG, "Setting Google Pay as default wallet")
        
        activity?.let { act ->
            environmentService.setAsDefaultWallet(act)
        } ?: run {
            notifyError(ErrorCodes.ERROR_ACTIVITY_NOT_AVAILABLE, "Activity not available for setting default wallet")
        }
    }
    
    /**
     * Create a Google Pay wallet
     */
    fun createGooglePayWallet() {
        Log.d(TAG, "Creating Google Pay wallet")
        
        activity?.let { act ->
            environmentService.createWallet(act)
        } ?: run {
            notifyError(ErrorCodes.ERROR_ACTIVITY_NOT_AVAILABLE, "Activity not available for wallet creation")
        }
    }
    
    /**
     * Parse provisioning response from WebView
     */
    fun parsePushProvisioningResponse(jsonData: String): PushProvisioningResponse? {
        return try {
            val json = JSONObject(jsonData)
            val dataProperty = json.getJSONObject("data")
            val pushData = dataProperty.getJSONObject("pushTokenizeRequestData")

            
            PushProvisioningResponse(
                cardToken = dataProperty.getString("cardToken"),
                createdTime = dataProperty.getString("createdTime"),
                lastModifiedTime = dataProperty.getString("lastModifiedTime"),
                pushTokenizeRequestData = PushTokenizeRequestData(
                    displayName = pushData.optString("displayName"),
                    opaquePaymentCard = pushData.optString("opaquePaymentCard"),
                    lastDigits = pushData.optString("lastDigits"),
                    network = pushData.optString("network"),
                    tokenServiceProvider = pushData.optString("tokenServiceProvider"),
                    userAddress = parseUserAddress(pushData.optJSONObject("userAddress"))
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse provisioning response", e)
            notifyError("ERROR_PARSING_RESPONSE", "Failed to parse provisioning response")
            null
        }
    }
    
    /**
     * Handle the result from push provisioning activity
     */
    private fun handlePushProvisioningResult(resultCode: Int, data: Intent?) {
        Log.d(TAG, "Handling push provisioning result: $resultCode")
        
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "Push provisioning completed successfully")
                val tokenId = data?.getStringExtra(TapAndPay.EXTRA_ISSUER_TOKEN_ID)
                val result = JSONObject().apply {
                    put("success", true)
                    put("message", "Card added successfully")
                    tokenId?.let { put("tokenReferenceId", it) }
                }
                notifySuccess(result.toString())
                pendingProvisioningCallback?.invoke(true, tokenId)
            }
            
            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "Push provisioning cancelled by user")
                notifyError(ErrorCodes.ERROR_USER_CANCELLED, "User cancelled provisioning")
                pendingProvisioningCallback?.invoke(false, "User cancelled")
            }
            
            TapAndPayStatusCodes.TAP_AND_PAY_ATTESTATION_ERROR -> {
                Log.e(TAG, "Attestation error during provisioning")
                notifyError(ErrorCodes.ERROR_TAP_AND_PAY_ATTESTATION_ERROR, "Device attestation failed")
                pendingProvisioningCallback?.invoke(false, "Attestation error")
            }
            
            else -> {
                var errorMessage = "Provisioning failed"
                var errorCode = ErrorCodes.ERROR_PUSH_PROVISIONING_FAILED
                
                data?.let { intent ->
                    intent.getStringExtra("error_message")?.let { errorMessage = it }
                    intent.getStringExtra("error_code")?.let { errorCode = it }
                    
                    if (intent.hasExtra("status")) {
                        try {
                            val status = intent.getParcelableExtra<Status>("status")
                            status?.statusMessage?.let { errorMessage = it }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not extract status from result", e)
                        }
                    }
                }
                
                Log.e(TAG, "Push provisioning failed: $errorMessage (code: $resultCode)")
                notifyError(errorCode, errorMessage, "Result code: $resultCode")
                pendingProvisioningCallback?.invoke(false, errorMessage)
            }
        }
        
        pendingProvisioningCallback = null
    }
    
    /**
     * Handle result from setting default wallet
     */
    private fun handleSetDefaultWalletResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "Google Pay set as default wallet successfully")
                notifySuccess("{\"message\": \"Google Pay set as default wallet\"}")
            }
            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "User cancelled setting default wallet")
                notifyError(ErrorCodes.ERROR_USER_CANCELLED, "User cancelled setting default wallet")
            }
            else -> {
                Log.w(TAG, "Unexpected result code for default wallet: $resultCode")
                notifyError(ErrorCodes.ERROR_GOOGLE_PAY_NOT_DEFAULT, "Failed to set Google Pay as default")
            }
        }
    }
    
    /**
     * Handle result from wallet creation
     */
    private fun handleCreateWalletResult(resultCode: Int, data: Intent?) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "Google Pay wallet created successfully")
                notifySuccess("{\"message\": \"Google Pay wallet created successfully\"}")
            }
            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "User cancelled wallet creation")
                notifyError(ErrorCodes.ERROR_USER_CANCELLED, "User cancelled wallet creation")
            }
            else -> {
                Log.e(TAG, "Wallet creation failed with result code: $resultCode")
                notifyError(ErrorCodes.ERROR_WALLET_CREATION_FAILED, "Failed to create Google Pay wallet")
            }
        }
    }
    
    /**
     * Handle result from token view
     */
    private fun handleViewTokenResult(resultCode: Int, data: Intent?) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "Token view completed successfully")
                notifySuccess("{\"message\": \"Token management completed\"}")
            }
            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "User cancelled token view")
                // This is normal behavior, don't treat as error
            }
            else -> {
                Log.w(TAG, "Token view returned unexpected result: $resultCode")
            }
        }
    }
    
    /**
     * Notify WebView of data changes
     */
    private fun notifyDataChanged() {
        webView?.post {
            webView?.handleEvent("googlePayDataChanged", "{\"timestamp\": ${System.currentTimeMillis()}}")
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up ProvisioningMain")
        
        environmentService.unregisterDataChangeListener()
        tapAndPayClientManager.cleanup()
        deviceInfoService.clearCache()
        
        pendingProvisioningCallback = null
        activity = null
        webView = null
    }
    
    // Convert callback-based methods to suspend functions
    private suspend fun isGooglePayAvailableAsync(): Boolean = suspendCoroutine { continuation ->
        isGooglePayAvailable { isAvailable ->
            continuation.resume(isAvailable)
        }
    }
    
    private suspend fun isTapAndPayAvailableAsync(): Boolean = suspendCoroutine { continuation ->
        isTapAndPayAvailable { isAvailable ->
            continuation.resume(isAvailable)
        }
    }
    
    private fun convertToLegacyDeviceInfo(newInfo: NewDeviceInfo) = DeviceInfo(
        deviceId = newInfo.stableHardwareId,
        deviceType = newInfo.deviceType,
        provisioningAppVersion = newInfo.osVersion,
        walletAccountId = newInfo.walletAccountId
    )
    
    private fun createFallbackDeviceInfo(walletId: String) = DeviceInfo(
        deviceId = "unknown",
        deviceType = ProvisioningConstants.DeviceTypes.MOBILE_PHONE,
        provisioningAppVersion = ProvisioningConstants.Defaults.UNKNOWN_VERSION,
        walletAccountId = walletId
    )
    
    private fun parseUserAddress(json: JSONObject?) = json?.let {
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
    
    private fun mapNetworkToTapAndPayConstant(network: String): Int {
        return when (network.uppercase()) {
            "VISA" -> TapAndPay.CARD_NETWORK_VISA
            "MASTERCARD", "MASTER" -> TapAndPay.CARD_NETWORK_MASTERCARD
            "AMEX", "AMERICAN_EXPRESS" -> TapAndPay.CARD_NETWORK_AMEX
            "DISCOVER" -> TapAndPay.CARD_NETWORK_DISCOVER
            else -> TapAndPay.CARD_NETWORK_VISA // Default to Visa
        }
    }
    
    private fun mapTokenServiceProviderToConstant(tsp: String): Int {
        return when (tsp.uppercase()) {
            "TOKEN_PROVIDER_VISA", "VISA" -> TapAndPay.TOKEN_PROVIDER_VISA
            "TOKEN_PROVIDER_MASTERCARD", "MASTERCARD" -> TapAndPay.TOKEN_PROVIDER_MASTERCARD
            else -> TapAndPay.TOKEN_PROVIDER_VISA // Default to Visa
        }
    }
    
    private fun createIsReadyToPayRequest() = JSONObject().apply {
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
    
    private fun notifySuccess(data: String) {
        Log.d(TAG, "notifySuccess called with data: $data")
        Log.d(TAG, "webView is ${if (webView == null) "null" else "available"}")
        webView?.post {
            Log.d(TAG, "Posting success event to webview: ${AccrueWebEvents.googleWalletProvisioningSuccessFunction}")
            webView?.handleEvent(AccrueWebEvents.googleWalletProvisioningSuccessFunction, data)
        }
    }
    
    private fun notifyError(code: String, message: String, details: String? = null) {
        val error = JSONObject().apply {
            put("code", code)
            put("message", message)
            details?.let { put("details", it) }
            put("timestamp", System.currentTimeMillis())
        }
        notifyError(error.toString())
    }
    
    private fun notifyError(errorJson: String) {
        Log.d(TAG, "notifyError called with errorJson: $errorJson")
        Log.d(TAG, "webView is ${if (webView == null) "null" else "available"}")
        webView?.post {
            Log.d(TAG, "Posting error event to webview: ${AccrueWebEvents.googleWalletProvisioningErrorFunction}")
            webView?.handleEvent(AccrueWebEvents.googleWalletProvisioningErrorFunction, errorJson)
        }
    }
} 