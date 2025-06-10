package com.accruesavings.androidsdk.provisioning.testing

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.accruesavings.androidsdk.TestConfig
import com.accruesavings.androidsdk.provisioning.device.DeviceInfo
import com.accruesavings.androidsdk.provisioning.error.ErrorHandler
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError
import com.accruesavings.androidsdk.provisioning.config.ProvisioningConstants
import org.json.JSONObject
import java.util.Random

/**
 * Helper class for testing provisioning functionality
 * Provides mock implementations for all provisioning operations
 */
class ProvisioningTestHelper(
    private val errorHandler: ErrorHandler
) {
    companion object {
        private const val TAG = "ProvisioningTestHelper"
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val random = Random()
    
    // Callback references for notifying webview
    private var notifySuccessCallback: ((String) -> Unit)? = null
    private var notifyErrorCallback: ((String, String, String?) -> Unit)? = null
    
    /**
     * Set the callback functions for notifying the webview
     */
    fun setNotificationCallbacks(
        onSuccess: (String) -> Unit,
        onError: (String, String, String?) -> Unit
    ) {
        notifySuccessCallback = onSuccess
        notifyErrorCallback = onError
    }
    
    /**
     * Mock Google Pay availability check
     */
    fun mockIsGooglePayAvailable(callback: (Boolean) -> Unit) {
        Log.d(TAG, "TEST MODE: Mocking Google Pay availability check")
        runWithMockDelay {
            val mockResult = TestConfig.GoogleWalletProvisioning.mockOperationsSucceed
            Log.d(TAG, "TEST MODE: Google Pay ${if (mockResult) "available" else "unavailable"}")
            callback(mockResult)
            
            if (!mockResult) {
                notifyMockError()
            }
        }
    }
    
    /**
     * Mock device information retrieval
     */
    fun mockGetDeviceInfo(callback: (DeviceInfo) -> Unit) {
        Log.d(TAG, "TEST MODE: Mocking device info retrieval")
        runWithMockDelay {
            val mockDeviceInfo = DeviceInfo(
                stableHardwareId = "mock-hardware-id-${random.nextInt(10000)}",
                deviceType = ProvisioningConstants.DeviceTypes.MOBILE_PHONE,
                osVersion = "1.0.0-mock",
                walletAccountId = "mock-wallet-account-id-${random.nextInt(10000)}"
            )
            Log.d(TAG, "TEST MODE: Retrieved mock device info: $mockDeviceInfo")
            callback(mockDeviceInfo)
            
            if (!TestConfig.GoogleWalletProvisioning.mockOperationsSucceed) {
                notifyMockError()
            }
        }
    }
    
    /**
     * Mock wallet account ID retrieval
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
                notifyMockError()
            }
        }
    }
    
    /**
     * Mock TapAndPay availability check
     */
    fun mockIsTapAndPayAvailable(callback: (Boolean) -> Unit) {
        Log.d(TAG, "TEST MODE: Mocking TapAndPay availability check")
        runWithMockDelay {
            val mockResult = TestConfig.GoogleWalletProvisioning.mockOperationsSucceed
            Log.d(TAG, "TEST MODE: TapAndPay ${if (mockResult) "available" else "unavailable"}")
            callback(mockResult)
            
            if (!mockResult) {
                notifyMockError()
            }
        }
    }
    
    /**
     * Mock push provisioning operation
     */
    fun mockPushProvisioning(callback: (Result<String>) -> Unit) {
        Log.d(TAG, "TEST MODE: Mocking push provisioning")
        runWithMockDelay {
            if (TestConfig.GoogleWalletProvisioning.mockOperationsSucceed) {
                Log.d(TAG, "TEST MODE: Push provisioning succeeded")
                callback(Result.success("Mock provisioning completed successfully"))
            } else {
                Log.d(TAG, "TEST MODE: Push provisioning failed")
                val error = Exception("Mock push provisioning failed")
                callback(Result.failure(error))
                notifyMockError()
            }
        }
    }
    
    
    /**
     * Mock parsing push provisioning response
     */
    fun mockParsePushProvisioningResponse(): com.accruesavings.androidsdk.PushProvisioningResponse? {
        Log.d(TAG, "TEST MODE: Mocking response parsing")
        
        if (!TestConfig.GoogleWalletProvisioning.mockOperationsSucceed) {
            Log.d(TAG, "TEST MODE: Simulating parsing failure")
            notifyMockError()
            return null
        }
        
        // Create a mock response with new structure
        return com.accruesavings.androidsdk.PushProvisioningResponse(
            cardToken = "mock-card-token-${random.nextInt(10000)}",
            createdTime = "2024-01-01T00:00:00Z",
            lastModifiedTime = "2024-01-01T00:00:00Z",
            pushTokenizeRequestData = com.accruesavings.androidsdk.PushTokenizeRequestData(
                displayName = "Mock Card",
                opaquePaymentCard = "mock-opaque-payment-card",
                lastDigits = "1234",
                network = "Visa",
                tokenServiceProvider = "TOKEN_PROVIDER_VISA",
                userAddress = com.accruesavings.androidsdk.UserAddress(
                    name = "John Doe",
                    address1 = "123 Main St",
                    address2 = "Apt 4B",
                    city = "New York",
                    state = "NY",
                    postalCode = "10001",
                    country = "US",
                    phone = "212-555-1234"
                )
            )
        )
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
     * Notify about mock errors using the error handler
     */
    private fun notifyMockError() {
        val error = ProvisioningError(
            code = TestConfig.GoogleWalletProvisioning.mockErrorCode,
            message = TestConfig.GoogleWalletProvisioning.mockErrorMessage,
            details = "Mock error for testing"
        )
        errorHandler.handleError(error)
    }
} 