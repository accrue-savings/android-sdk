package com.accruesavings.androidsdk.provisioning.core

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tapandpay.TapAndPay
import com.google.android.gms.tapandpay.TapAndPayClient
import com.accruesavings.androidsdk.provisioning.error.ErrorCodes
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError
import com.accruesavings.androidsdk.provisioning.config.ProvisioningConstants

/**
 * Manages the TapAndPay client lifecycle and availability with enhanced error handling
 */
class TapAndPayClientManager(private val context: Context) {
    
    companion object {
        private const val TAG = ProvisioningConstants.LogTags.CLIENT_MANAGER
        private const val GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms"
    }
    
    private var _tapAndPayClient: TapAndPayClient? = null
    
    val tapAndPayClient: TapAndPayClient?
        get() = _tapAndPayClient
    
    /**
     * Initialize the TapAndPay client with comprehensive checks
     */
    fun initialize(activity: FragmentActivity): Boolean {
        return try {
            // Pre-flight checks
            if (!isDeviceCapable()) {
                Log.w(TAG, "Device not capable for TapAndPay")
                return false
            }
            
            if (!isGooglePlayServicesAvailable()) {
                Log.w(TAG, "Google Play Services not available")
                return false
            }
            
            _tapAndPayClient = TapAndPay.getClient(activity)
            Log.d(TAG, "TapAndPay client initialized successfully")
            true
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "TapAndPay not supported on this device/environment", e)
            _tapAndPayClient = null
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TapAndPay client", e)
            _tapAndPayClient = null
            false
        }
    }
    
    /**
     * Check if device is capable of supporting TapAndPay
     */
    private fun isDeviceCapable(): Boolean {
        // Check if NFC is supported
        val hasNfc = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        if (!hasNfc) {
            Log.w(TAG, "Device does not support NFC")
            return false
        }
        
        // Check if NFC HCE is supported
        val hasHce = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        if (!hasHce) {
            Log.w(TAG, "Device does not support NFC Host Card Emulation")
            return false
        }
        
        return true
    }
    
    /**
     * Check if Google Play Services is available and up to date
     */
    fun isGooglePlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        return resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
    }
    
    /**
     * Check if TapAndPay is available on this device with safer error handling
     */
    fun isTapAndPayAvailable(callback: (Boolean) -> Unit) {
        if (_tapAndPayClient == null) {
            Log.w(TAG, "TapAndPay client is null")
            callback(false)
            return
        }
        
        try {
            // Use a safer method for availability check
            _tapAndPayClient?.let { client ->
                // Instead of getActiveWalletId which can throw UnsupportedOperationException,
                // let's use getEnvironment which is more universally supported
                client.getEnvironment()
                    .addOnSuccessListener { environment ->
                        Log.d(TAG, "TapAndPay is available, environment: $environment")
                        callback(true)
                    }
                    .addOnFailureListener { exception ->
                        Log.w(TAG, "TapAndPay availability check failed: ${exception.message}")
                        handleTapAndPayException(exception)
                        callback(false)
                    }
            } ?: callback(false)
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "TapAndPay operation not supported", e)
            callback(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking TapAndPay availability", e)
            callback(false)
        }
    }
    
    /**
     * Get the active wallet ID with enhanced error handling
     */
    fun getActiveWalletId(
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (_tapAndPayClient == null) {
            Log.e(TAG, "TapAndPay client is null")
            onFailure(Exception("TapAndPay client not initialized"))
            return
        }
        
        try {
            _tapAndPayClient?.let { client ->
                client.getActiveWalletId()
                    .addOnSuccessListener { walletId ->
                        if (!walletId.isNullOrEmpty()) {
                            Log.d(TAG, "Got active wallet ID: $walletId")
                            onSuccess(walletId)
                        } else {
                            Log.w(TAG, "Empty wallet ID returned")
                            onFailure(Exception("Empty wallet ID"))
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to get active wallet ID", exception)
                        handleTapAndPayException(exception)
                        onFailure(exception)
                    }
            } ?: onFailure(Exception("TapAndPay client is null"))
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "getActiveWalletId not supported", e)
            onFailure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active wallet ID", e)
            onFailure(e)
        }
    }
    
    /**
     * Get stable hardware ID with error handling
     */
    fun getStableHardwareId(
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (_tapAndPayClient == null) {
            Log.e(TAG, "TapAndPay client is null")
            onFailure(Exception("TapAndPay client not initialized"))
            return
        }
        
        try {
            _tapAndPayClient?.let { client ->
                client.getStableHardwareId()
                    .addOnSuccessListener { hardwareId ->
                        if (!hardwareId.isNullOrEmpty()) {
                            Log.d(TAG, "Got stable hardware ID")
                            onSuccess(hardwareId)
                        } else {
                            Log.w(TAG, "Empty hardware ID returned")
                            onFailure(Exception("Empty hardware ID"))
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to get stable hardware ID", exception)
                        handleTapAndPayException(exception)
                        onFailure(exception)
                    }
            } ?: onFailure(Exception("TapAndPay client is null"))
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "getStableHardwareId not supported", e)
            onFailure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stable hardware ID", e)
            onFailure(e)
        }
    }
    
    /**
     * Handle TapAndPay specific exceptions with proper error codes
     */
    private fun handleTapAndPayException(exception: Exception) {
        when {
            exception.message?.contains("15009") == true ||
            exception.message?.contains("Calling package not verified") == true -> {
                Log.e(TAG, "Package verification error - app not registered with Google Pay Console")
            }
            exception.message?.contains("15010") == true ||
            exception.message?.contains("TAP_AND_PAY_NO_ACTIVE_WALLET") == true -> {
                Log.e(TAG, "No active wallet found")
            }
            exception.message?.contains("15013") == true ||
            exception.message?.contains("TAP_AND_PAY_ATTESTATION_ERROR") == true -> {
                Log.e(TAG, "Attestation error")
            }
            exception is UnsupportedOperationException -> {
                Log.e(TAG, "Operation not supported on this device/environment")
            }
        }
    }
    
    /**
     * Check if the device is likely an emulator
     */
    fun isEmulator(): Boolean {
        return (android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK built for x86") ||
                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic") ||
                "google_sdk" == android.os.Build.PRODUCT)
    }
    
    /**
     * Check if the exception is a package verification error
     */
    private fun isPackageVerificationError(exception: Exception): Boolean {
        return exception.message?.contains("15009") == true || 
               exception.message?.contains("Calling package not verified") == true
    }
    
    /**
     * Get the stable hardware ID for this device
     */
    fun getStableHardwareId(callback: (String?) -> Unit) {
        _tapAndPayClient?.let { client ->
            client.getStableHardwareId()
                .addOnSuccessListener { hardwareId ->
                    Log.d(TAG, "Got stable hardware ID: $hardwareId")
                    callback(hardwareId)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get stable hardware ID", exception)
                    callback(null)
                }
        } ?: run {
            Log.e(TAG, "TapAndPay client is null")
            callback(null)
        }
    }
    
    /**
     * Check if the exception indicates a specific TapAndPay error
     */
    fun getTapAndPayErrorCode(exception: Exception): String? {
        val message = exception.message ?: return null
        
        return when {
            message.contains("15000") || message.contains("TAP_AND_PAY_UNAVAILABLE") -> 
                ErrorCodes.ERROR_TAP_AND_PAY_UNAVAILABLE
            message.contains("15009") || message.contains("Calling package not verified") -> 
                ErrorCodes.ERROR_PACKAGE_NOT_VERIFIED
            message.contains("15010") || message.contains("TAP_AND_PAY_NO_ACTIVE_WALLET") -> 
                ErrorCodes.ERROR_TAP_AND_PAY_NO_ACTIVE_WALLET
            message.contains("15011") || message.contains("TAP_AND_PAY_TOKEN_NOT_FOUND") -> 
                ErrorCodes.ERROR_TAP_AND_PAY_TOKEN_NOT_FOUND
            message.contains("15012") || message.contains("TAP_AND_PAY_INVALID_TOKEN_STATE") -> 
                ErrorCodes.ERROR_TAP_AND_PAY_INVALID_TOKEN_STATE
            message.contains("15013") || message.contains("TAP_AND_PAY_ATTESTATION_ERROR") -> 
                ErrorCodes.ERROR_TAP_AND_PAY_ATTESTATION_ERROR
            else -> null
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        _tapAndPayClient = null
        Log.d(TAG, "TapAndPay client cleaned up")
    }
} 