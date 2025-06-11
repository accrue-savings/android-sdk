package com.accruesavings.androidsdk.provisioning.core

import android.app.PendingIntent
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.tapandpay.TapAndPay
import com.google.android.gms.tapandpay.TapAndPayClient
import com.google.android.gms.tapandpay.issuer.IsTokenizedRequest
import com.google.android.gms.tapandpay.issuer.TokenInfo
import com.google.android.gms.tapandpay.issuer.TokenStatus
import com.google.android.gms.tapandpay.issuer.ViewTokenRequest
import com.accruesavings.androidsdk.provisioning.config.ProvisioningConstants
import com.accruesavings.androidsdk.provisioning.error.ErrorCodes
import com.accruesavings.androidsdk.provisioning.error.ErrorHandler
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError

/**
 * Service responsible for token management operations
 */
class TokenManagementService(
    private val errorHandler: ErrorHandler
) {
    
    companion object {
        private const val TAG = "TokenManagementService"
        const val VIEW_TOKEN_REQUEST_CODE = 1007
    }
    
    private var tapAndPayClient: TapAndPayClient? = null
    
    fun initialize(client: TapAndPayClient) {
        this.tapAndPayClient = client
    }
    
    /**
     * List all tokens in the Google Pay wallet
     */
    fun listTokens(callback: (List<TokenInfo>?) -> Unit) {
        tapAndPayClient?.let { client ->
            try {
                client.listTokens()
                    .addOnSuccessListener { tokenList ->
                        Log.d(TAG, "Retrieved ${tokenList.size} tokens")
                        callback(tokenList)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to list tokens", exception)
                        when (exception) {
                            is UnsupportedOperationException -> {
                                Log.w(TAG, "listTokens not supported on this device/environment")
                                callback(emptyList()) // Return empty list instead of null
                            }
                            else -> {
                                val error = ProvisioningError(
                                    code = ErrorCodes.ERROR_TOKEN_LIST_UNAVAILABLE,
                                    message = "Failed to list tokens: ${exception.message}"
                                )
                                errorHandler.handleError(error)
                                callback(null)
                            }
                        }
                    }
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "listTokens operation not supported", e)
                callback(emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "Error calling listTokens", e)
                callback(null)
            }
        } ?: run {
            Log.e(TAG, "TapAndPay client not initialized")
            callback(null)
        }
    }
    
    /**
     * Get the status of a specific token
     */
    fun getTokenStatus(
        tokenServiceProvider: Int,
        tokenReferenceId: String,
        callback: (TokenStatus?) -> Unit
    ) {
        tapAndPayClient?.let { client ->
            try {
                client.getTokenStatus(tokenServiceProvider, tokenReferenceId)
                    .addOnSuccessListener { tokenStatus ->
                        Log.d(TAG, "Token status for $tokenReferenceId: ${getTokenStateString(tokenStatus.tokenState)}")
                        callback(tokenStatus)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to get token status for $tokenReferenceId", exception)
                        when (exception) {
                            is UnsupportedOperationException -> {
                                Log.w(TAG, "getTokenStatus not supported on this device/environment")
                                callback(null)
                            }
                            else -> {
                                val error = ProvisioningError(
                                    code = ErrorCodes.ERROR_TOKEN_STATUS_UNAVAILABLE,
                                    message = "Failed to get token status: ${exception.message}"
                                )
                                errorHandler.handleError(error)
                                callback(null)
                            }
                        }
                    }
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "getTokenStatus operation not supported", e)
                callback(null)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling getTokenStatus", e)
                callback(null)
            }
        } ?: run {
            Log.e(TAG, "TapAndPay client not initialized")
            callback(null)
        }
    }
    
    /**
     * Check if a card is already tokenized (provisioned) in Google Pay
     */
    fun isTokenized(
        tokenServiceProvider: Int,
        network: Int,
        callback: (Boolean?) -> Unit
    ) {
        tapAndPayClient?.let { client ->
            try {
                val request = IsTokenizedRequest.Builder()
                    .setIdentifier(generateIdentifier(tokenServiceProvider, network))
                    .setNetwork(network)
                    .setTokenServiceProvider(tokenServiceProvider)
                    .build()
                
                client.isTokenized(request)
                    .addOnSuccessListener { isTokenized ->
                        Log.d(TAG, "Card tokenization status: $isTokenized")
                        callback(isTokenized)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to check tokenization status", exception)
                        when (exception) {
                            is UnsupportedOperationException -> {
                                Log.w(TAG, "isTokenized not supported on this device/environment")
                                callback(false) // Assume not tokenized if we can't check
                            }
                            else -> {
                                val error = ProvisioningError(
                                    code = ErrorCodes.ERROR_TOKEN_STATUS_UNAVAILABLE,
                                    message = "Failed to check tokenization status: ${exception.message}"
                                )
                                errorHandler.handleError(error)
                                callback(null)
                            }
                        }
                    }
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "isTokenized operation not supported", e)
                callback(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling isTokenized", e)
                callback(null)
            }
        } ?: run {
            Log.e(TAG, "TapAndPay client not initialized")
            callback(null)
        }
    }
    
    /**
     * View/manage a specific token (launches Google Pay token management UI)
     */
    fun viewToken(
        activity: FragmentActivity,
        tokenReferenceId: String,
        callback: (Boolean) -> Unit
    ) {
        tapAndPayClient?.let { client ->
            try {
                val request = ViewTokenRequest.Builder()
                    .setIssuerTokenId(tokenReferenceId)
                    .build()
                
                client.viewToken(request)
                    .addOnSuccessListener { pendingIntent ->
                        try {
                            activity.startIntentSenderForResult(
                                pendingIntent.intentSender,
                                VIEW_TOKEN_REQUEST_CODE,
                                null, 0, 0, 0
                            )
                            Log.d(TAG, "Launched token view for $tokenReferenceId")
                            callback(true)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to launch token view", e)
                            callback(false)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to create view token intent", exception)
                        when (exception) {
                            is UnsupportedOperationException -> {
                                Log.w(TAG, "viewToken not supported on this device/environment")
                                callback(false)
                            }
                            else -> {
                                val error = ProvisioningError(
                                    code = ErrorCodes.ERROR_TOKEN_VIEW_FAILED,
                                    message = "Failed to view token: ${exception.message}"
                                )
                                errorHandler.handleError(error)
                                callback(false)
                            }
                        }
                    }
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "viewToken operation not supported", e)
                callback(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling viewToken", e)
                callback(false)
            }
        } ?: run {
            Log.e(TAG, "TapAndPay client not initialized")
            callback(false)
        }
    }
    
    /**
     * Request deletion of a token
     * Note: This typically triggers user confirmation in Google Pay
     */
    fun requestDeleteToken(
        tokenServiceProvider: Int,
        tokenReferenceId: String,
        callback: (Boolean) -> Unit
    ) {
        tapAndPayClient?.let { client ->
            // Note: The actual token deletion is typically handled through the viewToken UI
            // or through backend calls. This is a placeholder for additional deletion logic
            Log.d(TAG, "Token deletion requested for: $tokenReferenceId")
            
            // In practice, you might need to:
            // 1. Show confirmation dialog
            // 2. Call backend to invalidate token
            // 3. Use viewToken to let user manage in Google Pay UI
            
            viewToken(object : FragmentActivity() {}, tokenReferenceId) { success ->
                if (success) {
                    Log.d(TAG, "Directed user to token management for deletion")
                    callback(true)
                } else {
                    val error = ProvisioningError(
                        code = ErrorCodes.ERROR_TOKEN_DELETE_FAILED,
                        message = "Failed to initiate token deletion for $tokenReferenceId"
                    )
                    errorHandler.handleError(error)
                    callback(false)
                }
            }
        } ?: run {
            Log.e(TAG, "TapAndPay client not initialized")
            callback(false)
        }
    }
    
    /**
     * Generate an identifier for token checking
     * This should match your backend's card identification logic
     */
    private fun generateIdentifier(tokenServiceProvider: Int, network: Int): String {
        // This is a placeholder - you should implement based on your card identification strategy
        return "${tokenServiceProvider}_${network}_${System.currentTimeMillis()}"
    }
    
    /**
     * Convert token state integer to human-readable string
     */
    private fun getTokenStateString(tokenState: Int): String {
        return when (tokenState) {
            0 -> "UNTOKENIZED"
            1 -> "PENDING"
            2 -> "NEEDS_IDENTITY_VERIFICATION"
            3 -> "PENDING_PROVISIONING"
            4 -> "ACTIVE"
            5 -> "FELICA_PENDING_PROVISIONING"
            6 -> "SUSPENDED"
            7 -> "DEACTIVATED"
            else -> "UNKNOWN($tokenState)"
        }
    }
    
    /**
     * Get token state description for user display
     */
    fun getTokenStateDescription(tokenState: Int): String {
        return when (tokenState) {
            0 -> "Card not added to Google Pay"
            1 -> "Card addition in progress"
            2 -> "Verification required"
            3 -> "Provisioning in progress"
            4 -> "Card ready for payments"
            5 -> "FeliCa provisioning in progress"
            6 -> "Card temporarily suspended"
            7 -> "Card deactivated"
            else -> "Unknown status"
        }
    }
} 