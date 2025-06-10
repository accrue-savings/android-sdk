package com.accruesavings.androidsdk.provisioning.core

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.tapandpay.TapAndPay
import com.google.android.gms.tapandpay.issuer.PushTokenizeRequest
import com.google.android.gms.tapandpay.issuer.UserAddress as TapAndPayUserAddress
import com.accruesavings.androidsdk.provisioning.error.ErrorCodes
import com.accruesavings.androidsdk.provisioning.error.ErrorHandler
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError
import org.json.JSONObject
import android.util.Base64

/**
 * Service responsible for handling push provisioning operations
 */
class PushProvisioningService(
    private val clientManager: TapAndPayClientManager,
    private val errorHandler: ErrorHandler
) {
    
    companion object {
        private const val TAG = "PushProvisioningService"
    }
    
    /**
     * Start push provisioning with the given data
     */
    fun startPushProvisioning(
        activity: FragmentActivity,
        provisioningData: String,
        launcher: ActivityResultLauncher<IntentSenderRequest>?,
        callback: (Result<String>) -> Unit
    ) {
        Log.d(TAG, "Starting push provisioning")
        
        if (launcher == null) {
            val error = ProvisioningError(
                code = ErrorCodes.ERROR_LAUNCHER_UNAVAILABLE,
                message = "ActivityResultLauncher not available"
            )
            errorHandler.handleError(error)
            callback(Result.failure(Exception(error.message)))
            return
        }
        
        try {
            val response = JSONObject(provisioningData)
            val pushTokenizeRequest = createPushTokenizeRequest(response)
            
            // Perform the actual push provisioning
            performPushProvisioning(activity, pushTokenizeRequest, launcher, callback)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start push provisioning", e)
            val error = ProvisioningError(
                code = ErrorCodes.ERROR_PUSH_PROVISIONING_FAILED,
                message = "Failed to parse provisioning data: ${e.message}"
            )
            errorHandler.handleError(error)
            callback(Result.failure(e))
        }
    }
    
    /**
     * Create a PushTokenizeRequest from the provisioning data
     */
    private fun createPushTokenizeRequest(response: JSONObject): PushTokenizeRequest {
        val pushData = response.getJSONObject("pushTokenizeRequestData")
        
        return PushTokenizeRequest.Builder()
            .setOpaquePaymentCard(decodeOpaquePaymentCard(pushData.getString("opaquePaymentCard")))
            .setNetwork(TapAndPay.CARD_NETWORK_VISA) // Use constant network for now
            .setDisplayName(pushData.optString("displayName", "Card"))
            .apply {
                createUserAddress(pushData.optJSONObject("userAddress"))?.let {
                    setUserAddress(it)
                }
            }
            .build()
    }
    
    /**
     * Decode the Opaque Payment Card data
     */
    private fun decodeOpaquePaymentCard(opcData: String): ByteArray {
        return try {
            // Try Base64 decoding first (most common format)
            Base64.decode(opcData, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode OPC as Base64, trying direct conversion")
            try {
                // Fallback to direct byte array conversion
                opcData.toByteArray(Charsets.UTF_8)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to decode OPC data", e2)
                ByteArray(0)
            }
        }
    }
    
    /**
     * Map network string to TapAndPay constant
     */
    private fun mapNetworkToTapAndPayConstant(network: String): Int {
        return when (network.uppercase()) {
            "VISA" -> TapAndPay.CARD_NETWORK_VISA
            "MASTERCARD", "MASTER" -> TapAndPay.CARD_NETWORK_MASTERCARD
            "AMEX", "AMERICAN_EXPRESS" -> TapAndPay.CARD_NETWORK_AMEX
            "DISCOVER" -> TapAndPay.CARD_NETWORK_DISCOVER
            else -> TapAndPay.CARD_NETWORK_VISA // Default to Visa
        }
    }
    
    /**
     * Create UserAddress from JSON data
     */
    private fun createUserAddress(addressJson: JSONObject?): TapAndPayUserAddress? {
        if (addressJson == null) return null
        
        return try {
            TapAndPayUserAddress.newBuilder()
                .setName(addressJson.optString("name", ""))
                .setAddress1(addressJson.optString("address1", ""))
                .setAddress2(addressJson.optString("address2", ""))
                .setLocality(addressJson.optString("locality", ""))
                .setAdministrativeArea(addressJson.optString("administrativeArea", ""))
                .setCountryCode(addressJson.optString("countryCode", "US"))
                .setPostalCode(addressJson.optString("postalCode", ""))
                .setPhoneNumber(addressJson.optString("phoneNumber", ""))
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create user address", e)
            null
        }
    }
    
    /**
     * Perform the actual push provisioning call
     */
    private fun performPushProvisioning(
        activity: FragmentActivity,
        request: PushTokenizeRequest,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        callback: (Result<String>) -> Unit
    ) {
        clientManager.tapAndPayClient?.let { client ->
            try {
                Log.d(TAG, "Calling TapAndPay pushTokenize")
                
                // Call the TapAndPay API
                client.pushTokenize(
                    activity,
                    request,
                    ErrorCodes.PUSH_PROVISION_REQUEST_CODE
                )
                
                Log.d(TAG, "Push provisioning request sent successfully")
                callback(Result.success("Push provisioning initiated"))
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during push provisioning", e)
                val error = ProvisioningError(
                    code = ErrorCodes.ERROR_SECURITY_EXCEPTION,
                    message = "Security exception: ${e.message}"
                )
                errorHandler.handleError(error)
                callback(Result.failure(e))
                
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid argument for push provisioning", e)
                val error = ProvisioningError(
                    code = ErrorCodes.ERROR_INVALID_PROVISIONING_DATA,
                    message = "Invalid provisioning data: ${e.message}"
                )
                errorHandler.handleError(error)
                callback(Result.failure(e))
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during push provisioning", e)
                val error = ProvisioningError(
                    code = ErrorCodes.ERROR_PUSH_PROVISIONING_FAILED,
                    message = "Push provisioning failed: ${e.message}"
                )
                errorHandler.handleError(error)
                callback(Result.failure(e))
            }
        } ?: run {
            Log.e(TAG, "TapAndPay client not available")
            val error = ProvisioningError(
                code = ErrorCodes.ERROR_CLIENT_NOT_AVAILABLE,
                message = "TapAndPay client not available"
            )
            errorHandler.handleError(error)
            callback(Result.failure(Exception(error.message)))
        }
    }
} 