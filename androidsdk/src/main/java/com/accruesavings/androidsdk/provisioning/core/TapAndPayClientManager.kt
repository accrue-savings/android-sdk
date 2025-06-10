package com.accruesavings.androidsdk.provisioning.core

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.tapandpay.TapAndPay
import com.google.android.gms.tapandpay.TapAndPayClient
import com.accruesavings.androidsdk.provisioning.error.ErrorCodes
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError
import com.accruesavings.androidsdk.provisioning.config.ProvisioningConstants

/**
 * Manages the TapAndPay client lifecycle and availability
 */
class TapAndPayClientManager(private val context: Context) {
    
    companion object {
        private const val TAG = ProvisioningConstants.LogTags.CLIENT_MANAGER
    }
    
    private var _tapAndPayClient: TapAndPayClient? = null
    
    val tapAndPayClient: TapAndPayClient?
        get() = _tapAndPayClient
    
    /**
     * Initialize the TapAndPay client
     */
    fun initialize(activity: FragmentActivity): Boolean {
        return try {
            _tapAndPayClient = TapAndPay.getClient(activity)
            Log.d(TAG, "TapAndPay client initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TapAndPay client", e)
            _tapAndPayClient = null
            false
        }
    }
    
    /**
     * Check if TapAndPay is available on this device
     */
    fun isTapAndPayAvailable(callback: (Boolean) -> Unit) {
        _tapAndPayClient?.let { client ->
            client.getActiveWalletId()
                .addOnSuccessListener { walletId ->
                    Log.d(TAG, "TapAndPay is available, wallet ID: $walletId")
                    callback(true)
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "TapAndPay is not available: ${exception.message}")
                    
                    // Check for package verification errors
                    if (isPackageVerificationError(exception)) {
                        Log.e(TAG, "Package verification error - app not registered with Google Pay Console")
                    }
                    
                    callback(false)
                }
        } ?: run {
            Log.w(TAG, "TapAndPay client is null")
            callback(false)
        }
    }
    
    /**
     * Get the active wallet ID
     */
    fun getActiveWalletId(
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        _tapAndPayClient?.let { client ->
            client.getActiveWalletId()
                .addOnSuccessListener { walletId ->
                    if (walletId.isNotEmpty()) {
                        Log.d(TAG, "Got active wallet ID: $walletId")
                        onSuccess(walletId)
                    } else {
                        Log.w(TAG, "Empty wallet ID returned")
                        onFailure(Exception("Empty wallet ID"))
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get active wallet ID", exception)
                    onFailure(exception)
                }
        } ?: run {
            Log.e(TAG, "TapAndPay client is null")
            onFailure(Exception("TapAndPay client not initialized"))
        }
    }
    
    /**
     * Check if the exception is a package verification error
     */
    private fun isPackageVerificationError(exception: Exception): Boolean {
        return exception.message?.contains("15009") == true || 
               exception.message?.contains("Calling package not verified") == true
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        _tapAndPayClient = null
        Log.d(TAG, "TapAndPay client cleaned up")
    }
} 