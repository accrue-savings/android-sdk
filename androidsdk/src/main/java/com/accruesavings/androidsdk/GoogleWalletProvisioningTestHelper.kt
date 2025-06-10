package com.accruesavings.androidsdk

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.util.Random

/**
 * Helper class for testing Google Wallet Provisioning functionality
 * This class contains methods to simulate API responses without calling actual Google Pay APIs
 */
class GoogleWalletProvisioningTestHelper(
    private val webViewInterface: GoogleWalletProvisioningWebViewInterface
) {
    private val TAG = "GWProvisioningTest"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val random = Random()
    
    /**
     * Interface to communicate with the WebView
     */
    interface GoogleWalletProvisioningWebViewInterface {
        fun handleSuccessEvent(data: String)
        fun handleErrorEvent(errorJson: String)
    }
    
    /**
     * Simulate checking if Google Pay is available
     * @param callback Callback with the result
     */
    fun mockIsGooglePayAvailable(callback: (Boolean) -> Unit) {
        Log.d(TAG, "TEST MODE: Mocking Google Pay availability check")
        runWithMockDelay {
            val mockResult = TestConfig.GoogleWalletProvisioning.mockOperationsSucceed
            Log.d(TAG, "TEST MODE: Google Pay ${if (mockResult) "available" else "unavailable"}")
            callback(mockResult)
            
            if (!mockResult) {
                notifyError(
                    TestConfig.GoogleWalletProvisioning.mockErrorCode,
                    TestConfig.GoogleWalletProvisioning.mockErrorMessage
                )
            }
        }
    }
    
    /**
     * Simulate getting device information
     * @param callback Callback with the mocked device info
     */
    fun mockGetDeviceInfo(callback: (DeviceInfo) -> Unit) {
        Log.d(TAG, "TEST MODE: Mocking device info retrieval")
        runWithMockDelay {
            val mockDeviceInfo = DeviceInfo(
                deviceId = "mock-device-id-${random.nextInt(10000)}",
                deviceType = "MOBILE_PHONE",
                provisioningAppVersion = "1.0.0-mock",
                walletAccountId = "mock-wallet-account-id-${random.nextInt(10000)}"
            )
            Log.d(TAG, "TEST MODE: Retrieved mock device info: $mockDeviceInfo")
            callback(mockDeviceInfo)
            
            if (!TestConfig.GoogleWalletProvisioning.mockOperationsSucceed) {
                notifyError(
                    TestConfig.GoogleWalletProvisioning.mockErrorCode,
                    TestConfig.GoogleWalletProvisioning.mockErrorMessage
                )
            }
        }
    }
    
    /**
     * Simulate getting wallet account ID
     * @param callback Callback with the mocked wallet account ID
     */
    fun mockGetWalletAccountId(callback: (String?) -> Unit) {
        Log.d(TAG, "TEST MODE: Mocking wallet account ID retrieval")
        runWithMockDelay {
            if (TestConfig.GoogleWalletProvisioning.mockOperationsSucceed) {
                val mockAccountId = "mock-wallet-account-id-${random.nextInt(10000)}"
                Log.d(TAG, "TEST MODE: Retrieved mock wallet account ID: $mockAccountId")
                callback(mockAccountId)
            } else {
                Log.d(TAG, "TEST MODE: Failed to retrieve mock wallet account ID")
                callback(null)
                
                notifyError(
                    TestConfig.GoogleWalletProvisioning.mockErrorCode,
                    TestConfig.GoogleWalletProvisioning.mockErrorMessage
                )
            }
        }
    }
    
    /**
     * Simulate parsing a push provisioning response
     * @return Mocked push provisioning response or null if mock operations should fail
     */
    fun mockParsePushProvisioningResponse(): PushProvisioningResponse? {
        Log.d(TAG, "TEST MODE: Mocking response parsing")
        
        if (!TestConfig.GoogleWalletProvisioning.mockOperationsSucceed) {
            Log.d(TAG, "TEST MODE: Simulating parsing failure")
            notifyError(
                TestConfig.GoogleWalletProvisioning.mockErrorCode,
                TestConfig.GoogleWalletProvisioning.mockErrorMessage
            )
            return null
        }
        
        // Create a mock response
        return PushProvisioningResponse(
            success = true,
            pushTokenizeRequestData = PushTokenizeRequestData(
                opaquePaymentCard = "mock-opaque-payment-card",
                lastDigits = "1234",
                tspProvider = "TOKEN_PROVIDER_VISA",
                userAddress = com.accruesavings.androidsdk.UserAddress(
                    name = "John Doe",
                    address1 = "123 Main St",
                    address2 = "Apt 4B",
                    city = "New York",
                    state = "NY",
                    postalCode = "10001",
                    countryCode = "US",
                    phoneNumber = "212-555-1234"
                )
            )
        )
    }
    
    /**
     * Simulate starting push provisioning
     */
    fun mockStartPushProvisioning() {
        Log.d(TAG, "TEST MODE: Mocking push provisioning")
        runWithMockDelay {
            if (TestConfig.GoogleWalletProvisioning.mockOperationsSucceed) {
                Log.d(TAG, "TEST MODE: Push provisioning succeeded")
                webViewInterface.handleSuccessEvent("{}")
            } else {
                Log.d(TAG, "TEST MODE: Push provisioning failed")
                notifyError(
                    TestConfig.GoogleWalletProvisioning.mockErrorCode,
                    TestConfig.GoogleWalletProvisioning.mockErrorMessage
                )
            }
        }
    }
    
    /**
     * Manually simulate a successful operation
     */
    fun simulateSuccess() {
        if (!TestConfig.enableTestMode) {
            Log.w(TAG, "Cannot simulate success because test mode is disabled")
            return
        }
        
        Log.d(TAG, "TEST MODE: Simulating success response")
        runWithMockDelay {
            webViewInterface.handleSuccessEvent("{}")
        }
    }
    
    /**
     * Manually simulate an error
     * @param errorCode Optional custom error code
     * @param errorMessage Optional custom error message
     */
    fun simulateError(errorCode: String? = null, errorMessage: String? = null) {
        if (!TestConfig.enableTestMode) {
            Log.w(TAG, "Cannot simulate error because test mode is disabled")
            return
        }
        
        val code = errorCode ?: TestConfig.GoogleWalletProvisioning.mockErrorCode
        val message = errorMessage ?: TestConfig.GoogleWalletProvisioning.mockErrorMessage
        
        Log.d(TAG, "TEST MODE: Simulating error response with code: $code")
        runWithMockDelay {
            notifyError(code, message, "Simulated error for testing")
        }
    }
    
    /**
     * Toggle test mode
     * @param enabled Whether test mode should be enabled
     * @param mockSuccess Whether mock operations should succeed
     */
    fun setTestMode(enabled: Boolean, mockSuccess: Boolean = true) {
        TestConfig.enableTestMode = enabled
        TestConfig.GoogleWalletProvisioning.mockGooglePayApi = enabled
        TestConfig.GoogleWalletProvisioning.mockOperationsSucceed = mockSuccess
        
        Log.d(TAG, "Test mode ${if (enabled) "enabled" else "disabled"}, operations will ${if (mockSuccess) "succeed" else "fail"}")
    }
    
    /**
     * Run code with a mock delay to simulate network/processing time
     */
    private fun runWithMockDelay(block: () -> Unit) {
        mainHandler.postDelayed({
            block()
        }, TestConfig.GoogleWalletProvisioning.mockOperationDelay)
    }
    
    /**
     * Notify WebView about an error
     */
    private fun notifyError(code: String, message: String, details: String? = null) {
        val errorJson = JSONObject().apply {
            put("code", code)
            put("message", message)
            details?.let { put("details", it) }
        }.toString()
        
        webViewInterface.handleErrorEvent(errorJson)
    }
} 