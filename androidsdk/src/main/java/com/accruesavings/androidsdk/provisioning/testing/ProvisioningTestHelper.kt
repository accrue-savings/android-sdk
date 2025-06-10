package com.accruesavings.androidsdk.provisioning.testing

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.accruesavings.androidsdk.TestConfig
import com.accruesavings.androidsdk.provisioning.device.DeviceInfo
import com.accruesavings.androidsdk.provisioning.error.ErrorHandler
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError
import com.accruesavings.androidsdk.provisioning.config.ProvisioningConstants
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
     * Mock start push provisioning (for compatibility)
     */
    fun mockStartPushProvisioning() {
        Log.d(TAG, "TEST MODE: Mocking start push provisioning")
        mockPushProvisioning { result ->
            result.fold(
                onSuccess = { Log.d(TAG, "TEST MODE: Start push provisioning succeeded") },
                onFailure = { Log.d(TAG, "TEST MODE: Start push provisioning failed") }
            )
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
        
        // Create a mock response
        return com.accruesavings.androidsdk.PushProvisioningResponse(
            success = true,
            pushTokenizeRequestData = com.accruesavings.androidsdk.PushTokenizeRequestData(
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
     * Manually simulate a successful operation
     */
    fun simulateSuccess(successMessage: String = "Mock operation succeeded") {
        if (!TestConfig.enableTestMode) {
            Log.w(TAG, "Cannot simulate success because test mode is disabled")
            return
        }
        
        Log.d(TAG, "TEST MODE: Simulating success response: $successMessage")
        // Success simulation would typically be handled by the calling code
    }
    
    /**
     * Manually simulate an error
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
            val error = ProvisioningError(
                code = code,
                message = message,
                details = "Simulated error for testing"
            )
            errorHandler.handleError(error)
        }
    }
    
    /**
     * Configure test mode settings
     */
    fun setTestMode(enabled: Boolean, mockSuccess: Boolean = true) {
        TestConfig.enableTestMode = enabled
        TestConfig.GoogleWalletProvisioning.mockGooglePayApi = enabled
        TestConfig.GoogleWalletProvisioning.mockOperationsSucceed = mockSuccess
        
        Log.d(TAG, "Test mode ${if (enabled) "enabled" else "disabled"}, " +
                  "operations will ${if (mockSuccess) "succeed" else "fail"}")
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