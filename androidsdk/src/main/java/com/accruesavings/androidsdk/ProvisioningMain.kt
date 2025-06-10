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
import org.json.JSONObject
import org.json.JSONArray
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Import our modular provisioning components
import com.accruesavings.androidsdk.provisioning.core.TapAndPayClientManager
import com.accruesavings.androidsdk.provisioning.core.PushProvisioningService
import com.accruesavings.androidsdk.provisioning.device.DeviceInfoService
import com.accruesavings.androidsdk.provisioning.device.DeviceInfo as NewDeviceInfo
import com.accruesavings.androidsdk.provisioning.error.ErrorHandler
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError
import com.accruesavings.androidsdk.provisioning.error.ErrorCodes
import com.accruesavings.androidsdk.provisioning.config.ProvisioningConstants
import com.accruesavings.androidsdk.provisioning.testing.ProvisioningTestHelper

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
    private lateinit var deviceInfoService: DeviceInfoService
    private lateinit var errorHandler: ErrorHandler
    private lateinit var provisioningTestHelper: ProvisioningTestHelper
    
    /**
     * Initialize all services and components
     */
    fun initialize(activity: FragmentActivity, webView: AccrueWebView) {
        this.activity = activity
        this.webView = webView
        
        Log.d(TAG, "Initializing ProvisioningMain")
        
        // Initialize Google Pay client
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
            .build()
        paymentsClient = Wallet.getPaymentsClient(context, walletOptions)
        
        // Initialize specialized services
        errorHandler = ErrorHandler(webView)
        tapAndPayClientManager = TapAndPayClientManager(context)
        pushProvisioningService = PushProvisioningService(tapAndPayClientManager, errorHandler)
        provisioningTestHelper = ProvisioningTestHelper(errorHandler)
        deviceInfoService = DeviceInfoService(context, provisioningTestHelper)
        
        // Set up test helper callbacks to properly notify webview
        Log.d(TAG, "Setting up test helper callbacks")
        provisioningTestHelper.setNotificationCallbacks(
            onSuccess = { data -> 
                Log.d(TAG, "Test helper success callback invoked with: $data")
                notifySuccess(data) 
            },
            onError = { code, message, details -> 
                Log.d(TAG, "Test helper error callback invoked with: $code - $message")
                notifyError(code, message, details) 
            }
        )
        
        // Initialize TapAndPay client
        if (!tapAndPayClientManager.initialize(activity)) {
            Log.w(TAG, "Failed to initialize TapAndPay client")
        }
         
        
        Log.d(TAG, "ProvisioningMain initialized successfully")
    }
    
    /**
     * Check if TapAndPay is available on this device
     */
    fun isTapAndPayAvailable(callback: (Boolean) -> Unit) {
        Log.d(TAG, "Checking TapAndPay availability")
        
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            provisioningTestHelper.mockIsTapAndPayAvailable(callback)
            return
        }
        
        tapAndPayClientManager.isTapAndPayAvailable(callback)
    }
    
    /**
     * Check if Google Pay is available on this device
     */
    fun isGooglePayAvailable(callback: (Boolean) -> Unit) {
        Log.d(TAG, "Checking Google Pay availability")
        
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            provisioningTestHelper.mockIsGooglePayAvailable(callback)
            return
        }
        
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
     * Get device information for provisioning
     */
    fun getDeviceInfo(callback: (DeviceInfo) -> Unit) {
        Log.d(TAG, "Getting device information")
        
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            provisioningTestHelper.mockGetDeviceInfo { deviceInfo ->
                // Convert new DeviceInfo to legacy DeviceInfo
                val legacyInfo = DeviceInfo(
                    deviceId = deviceInfo.stableHardwareId,
                    deviceType = deviceInfo.deviceType,
                    provisioningAppVersion = deviceInfo.osVersion,
                    walletAccountId = deviceInfo.walletAccountId
                )
                callback(legacyInfo)
            }
            return
        }
        
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
        checkPrerequisitesAndProvision(jsonData)
    }
    
    /**
     * Parse provisioning response from WebView
     */
    fun parsePushProvisioningResponse(jsonData: String): PushProvisioningResponse? {
        if(TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockPushProvisioningPayload) {
            return provisioningTestHelper.mockParsePushProvisioningResponse()
        }
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
            }
            
            Activity.RESULT_CANCELED -> {
                notifyError("ERROR_USER_CANCELLED", "User cancelled provisioning")
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
                
                notifyError(errorCode, errorMessage, "Result code: $resultCode")
            }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up ProvisioningMain")
        tapAndPayClientManager.cleanup()
        deviceInfoService.clearCache()
    }
    
    
    /**
     * Query the status of a specific token
     */
    fun getTokenStatus(tokenServiceProvider: Int, tokenReferenceId: String, callback: (Boolean, String?) -> Unit) {
        Log.d(TAG, "Token status query requested for token: $tokenReferenceId")
        callback(true, "Token status check not fully supported")
    }
    
    /**
     * Check if a token needs activation and handle accordingly
     */
    fun checkAndActivateToken(tokenServiceProvider: Int, tokenReferenceId: String, callback: (Boolean, String?) -> Unit) {
        getTokenStatus(tokenServiceProvider, tokenReferenceId) { isActive, message ->
            callback(isActive, if (isActive) "Token is active" else (message ?: "Token may need activation"))
        }
    }
    
    /**
     * Request deletion of a token
     */
    fun requestDeleteToken(tokenServiceProvider: Int, tokenReferenceId: String) {
        Log.d(TAG, "Token deletion requested for: $tokenReferenceId")
        notifyError(ErrorCodes.ERROR_PUSH_PROVISIONING_FAILED, "Token deletion not fully supported", "Token ID: $tokenReferenceId")
    }
    
 
    private fun checkPrerequisitesAndProvision(jsonData: String) {
        // Launch coroutine to handle async operations with early returns
        CoroutineScope(Dispatchers.Main).launch {
            try {
                checkPrerequisitesAndProvisionAsync(jsonData)
            } catch (e: Exception) {
                Log.e(TAG, "Error in provisioning prerequisites", e)
                notifyError(ErrorCodes.ERROR_PUSH_PROVISIONING_FAILED, "Provisioning failed: ${e.message}")
            }
        }
    }
    
    private suspend fun checkPrerequisitesAndProvisionAsync(jsonData: String) {
        Log.d(TAG, "Checking prerequisites and provisioning")
        
        // Skip verification checks if test mode is enabled and APIs are mocked
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            Log.d(TAG, "Skipping Google Pay and TapAndPay verification checks (test mode with mocked APIs)")
        } else {
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
        
        // Delegate to service
        pushProvisioningService.startPushProvisioning(
            activity = currentActivity,
            provisioningData = response,
        ) { result ->
            result.fold(
                onSuccess = { notifySuccess("{\"message\": \"$it\"}") },
                onFailure = { notifyError("PROVISIONING_FAILED", "Provisioning failed: ${it.message}") }
            )
        }
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