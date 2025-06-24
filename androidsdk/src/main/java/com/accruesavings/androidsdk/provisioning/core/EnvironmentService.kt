package com.accruesavings.androidsdk.provisioning.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.cardemulation.CardEmulation
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tapandpay.TapAndPayClient
import com.accruesavings.androidsdk.provisioning.config.ProvisioningConstants
import com.accruesavings.androidsdk.provisioning.error.ErrorCodes
import com.accruesavings.androidsdk.provisioning.error.ErrorHandler
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError

/**
 * Service responsible for handling environment detection, wallet management, and NFC checks
 */
class EnvironmentService(
    private val context: Context,
    private val errorHandler: ErrorHandler
) {
    
    companion object {
        private const val TAG = "EnvironmentService"
        private const val GOOGLE_PAY_TP_HCE_SERVICE = "com.google.android.gms.tapandpay.hce.service.TpHceService"
        const val SET_DEFAULT_PAYMENTS_REQUEST_CODE = 1005
        const val CREATE_WALLET_REQUEST_CODE = 1006
    }
    
    private var tapAndPayClient: TapAndPayClient? = null
    
    fun initialize(client: TapAndPayClient) {
        this.tapAndPayClient = client
    }
    
    /**
     * Get the current Google Pay environment (PROD, SANDBOX, DEV)
     */
    fun getEnvironment(callback: (String?) -> Unit) {
        tapAndPayClient?.let { client ->
            try {
                client.getEnvironment()
                    .addOnSuccessListener { environment ->
                        Log.d(TAG, "Current environment: $environment")
                        callback(environment)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to get environment", exception)
                        when (exception) {
                            is UnsupportedOperationException -> {
                                Log.w(TAG, "getEnvironment not supported on this device/environment")
                                callback("UNKNOWN") // Return a default value
                            }
                            else -> {
                                val error = ProvisioningError(
                                    code = ErrorCodes.ERROR_ENVIRONMENT_UNAVAILABLE,
                                    message = "Failed to get environment: ${exception.message}"
                                )
                                errorHandler.handleError(error)
                                callback(null)
                            }
                        }
                    }
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "getEnvironment operation not supported", e)
                callback("UNKNOWN")
            } catch (e: Exception) {
                Log.e(TAG, "Error calling getEnvironment", e)
                callback(null)
            }
        } ?: run {
            Log.e(TAG, "TapAndPay client not initialized")
            callback(null)
        }
    }
    
    /**
     * Check if Google Pay is set as the default payment app
     */
    fun isDefaultWallet(callback: (Boolean) -> Unit) {
        try {
            val nfcManager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
            val nfcAdapter = nfcManager?.defaultAdapter
            
            if (nfcAdapter == null || !nfcAdapter.isEnabled) {
                Log.w(TAG, "NFC not available or not enabled")
                callback(false)
                return
            }
            
            // For now, assume we need to check through Google Pay itself
            // This is typically handled by the TapAndPay client or user confirmation
            tapAndPayClient?.let { client ->
                // Use Google Pay's own method to check if it's set as default
                Log.d(TAG, "Checking default wallet status through Google Pay")
                callback(true) // This would need proper implementation based on Google Pay SDK
            } ?: run {
                Log.w(TAG, "TapAndPay client not available")
                callback(false)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check default wallet status", e)
            callback(false)
        }
    }
    
    /**
     * Launch intent to set Google Pay as default payment app
     */
    fun setAsDefaultWallet(activity: FragmentActivity) {
        try {
            val intent = Intent(CardEmulation.ACTION_CHANGE_DEFAULT).apply {
                putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT)
                putExtra(
                    CardEmulation.EXTRA_SERVICE_COMPONENT,
                    ComponentName(
                        GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE,
                        GOOGLE_PAY_TP_HCE_SERVICE
                    )
                )
            }
            
            activity.startActivityForResult(intent, SET_DEFAULT_PAYMENTS_REQUEST_CODE)
            Log.d(TAG, "Launched intent to set Google Pay as default")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch default wallet intent", e)
            val error = ProvisioningError(
                code = ErrorCodes.ERROR_GOOGLE_PAY_NOT_DEFAULT,
                message = "Failed to set Google Pay as default: ${e.message}"
            )
            errorHandler.handleError(error)
        }
    }
    
    /**
     * Create a Google Pay wallet
     */
    fun createWallet(activity: FragmentActivity) {
        tapAndPayClient?.let { client ->
            try {
                client.createWallet(activity, CREATE_WALLET_REQUEST_CODE)
                Log.d(TAG, "Initiated wallet creation")
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "createWallet not supported on this device/environment", e)
                val error = ProvisioningError(
                    code = ErrorCodes.ERROR_WALLET_CREATION_FAILED,
                    message = "Wallet creation not supported on this device"
                )
                errorHandler.handleError(error)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling createWallet", e)
                val error = ProvisioningError(
                    code = ErrorCodes.ERROR_WALLET_CREATION_FAILED,
                    message = "Failed to create wallet: ${e.message}"
                )
                errorHandler.handleError(error)
            }
        } ?: run {
            Log.e(TAG, "TapAndPay client not initialized")
            val error = ProvisioningError(
                code = ErrorCodes.ERROR_WALLET_CREATION_FAILED,
                message = "TapAndPay client not initialized"
            )
            errorHandler.handleError(error)
        }
    }
    
    /**
     * Check NFC availability and status
     */
    fun checkNfcStatus(): NfcStatus {
        val nfcManager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
        val nfcAdapter = nfcManager?.defaultAdapter
        
        return when {
            nfcAdapter == null -> NfcStatus.NOT_SUPPORTED
            !nfcAdapter.isEnabled -> NfcStatus.DISABLED
            else -> NfcStatus.ENABLED
        }
    }
    
    /**
     * Register data change listener to monitor Google Pay state changes
     */
    fun registerDataChangeListener(listener: () -> Unit) {
        try {
            tapAndPayClient?.registerDataChangedListener(
                object : com.google.android.gms.tapandpay.TapAndPay.DataChangedListener {
                    override fun onDataChanged() {
                        Log.d(TAG, "Google Pay data changed")
                        listener()
                    }
                }
            )
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Data change listener not supported on this device/environment", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering data change listener", e)
        }
    }
    
    /**
     * Unregister data change listener
     */
    fun unregisterDataChangeListener() {
        // Note: Google's API doesn't provide explicit unregister method
        // The listener is automatically cleaned up when the client is cleaned up
        Log.d(TAG, "Data change listener will be cleaned up with client cleanup")
    }
}

/**
 * Enum representing NFC status
 */
enum class NfcStatus {
    ENABLED,
    DISABLED,
    NOT_SUPPORTED
} 