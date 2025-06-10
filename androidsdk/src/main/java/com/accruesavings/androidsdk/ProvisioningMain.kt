package com.accruesavings.androidsdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
 * Main coordinator for Google Wallet Provisioning operations
 * Delegates to specialized services for clean separation of concerns
 */
class ProvisioningMain(private val context: Context) {
    
    private val TAG = ProvisioningConstants.LogTags.MAIN
    
    // Core components
    private var paymentsClient: PaymentsClient? = null
    private var webView: AccrueWebView? = null
    private var activity: FragmentActivity? = null
    private var provisioningResultLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    
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
        
        // Initialize TapAndPay client
        if (!tapAndPayClientManager.initialize(activity)) {
            Log.w(TAG, "Failed to initialize TapAndPay client")
        }
        
        // Register activity result launcher
        registerActivityResultLauncher(activity)
        
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
        Log.d(TAG, "Starting push provisioning")
        
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            provisioningTestHelper.mockStartPushProvisioning()
            return
        }
        
        if (provisioningResultLauncher == null) {
            notifyError(ErrorCodes.ERROR_LAUNCHER_UNAVAILABLE, "ActivityResultLauncher not available")
            return
        }
        
        // Check prerequisites then delegate to service
        checkPrerequisitesAndProvision(jsonData)
    }
    
    /**
     * Parse provisioning response from WebView
     */
    fun parsePushProvisioningResponse(jsonData: String): PushProvisioningResponse? {
        if (TestConfig.enableTestMode && TestConfig.GoogleWalletProvisioning.mockGooglePayApi) {
            return provisioningTestHelper.mockParsePushProvisioningResponse()
        }
        
        return try {
            val json = JSONObject(jsonData)
            val data = json.getJSONObject("data")
            val pushData = data.getJSONObject("pushTokenizeRequestData")
            
            PushProvisioningResponse(
                success = data.optBoolean("success", false),
                pushTokenizeRequestData = PushTokenizeRequestData(
                    opaquePaymentCard = pushData.optString("opaquePaymentCard"),
                    userAddress = parseUserAddress(pushData.optJSONObject("userAddress")),
                    lastDigits = pushData.optString("lastDigits"),
                    tspProvider = pushData.optString("tspProvider")
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
     * Set test mode (for testing purposes)
     */
    fun setTestMode(enabled: Boolean, mockSuccess: Boolean = true) {
        provisioningTestHelper.setTestMode(enabled, mockSuccess)
    }
    
    /**
     * Simulate a success response (for testing)
     */
    fun simulateSuccess() = provisioningTestHelper.simulateSuccess()
    
    /**
     * Simulate an error response (for testing)
     */
    fun simulateError(errorCode: String? = null, errorMessage: String? = null) = 
        provisioningTestHelper.simulateError(errorCode, errorMessage)
    
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
    
    // MARK: - Private Helper Methods
    
    private fun registerActivityResultLauncher(activity: FragmentActivity) {
        try {
            if (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                Log.w(TAG, "Activity already started, ActivityResultLauncher will be limited")
                this.provisioningResultLauncher = null
            } else {
                this.provisioningResultLauncher = activity.registerForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result -> handlePushProvisioningResult(result.resultCode, result.data) }
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to register ActivityResultLauncher: ${e.message}")
            this.provisioningResultLauncher = null
        }
    }
    
    private fun checkPrerequisitesAndProvision(jsonData: String) {
        isGooglePayAvailable { googlePayAvailable ->
            if (!googlePayAvailable) {
                notifyError(ErrorCodes.ERROR_GOOGLE_PAY_UNAVAILABLE, "Google Pay not available")
                return@isGooglePayAvailable
            }
            
            isTapAndPayAvailable { tapAndPayAvailable ->
                if (!tapAndPayAvailable) {
                    notifyError(ErrorCodes.ERROR_DEVICE_NOT_SUPPORTED, "TapAndPay not available")
                    return@isTapAndPayAvailable
                }
                
                val response = parsePushProvisioningResponse(jsonData)
                if (response?.success != true) {
                    notifyError("ERROR_PARSING_RESPONSE", "Invalid provisioning data")
                    return@isTapAndPayAvailable
                }
                
                // Delegate to service
                activity?.let { 
                    pushProvisioningService.startPushProvisioning(
                        activity = it,
                        provisioningData = jsonData,
                        launcher = provisioningResultLauncher
                    ) { result ->
                        result.fold(
                            onSuccess = { notifySuccess("{\"message\": \"$it\"}") },
                            onFailure = { notifyError("PROVISIONING_FAILED", "Provisioning failed: ${it.message}") }
                        )
                    }
                } ?: notifyError(ErrorCodes.ERROR_PUSH_PROVISIONING_FAILED, "Activity not available")
            }
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
            countryCode = it.optString("countryCode"),
            postalCode = it.optString("postalCode"),
            phoneNumber = it.optString("phoneNumber")
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
        webView?.post {
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
        webView?.post {
            webView?.handleEvent(AccrueWebEvents.googleWalletProvisioningErrorFunction, errorJson)
        }
    }
} 