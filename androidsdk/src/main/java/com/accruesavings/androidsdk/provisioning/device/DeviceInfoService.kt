package com.accruesavings.androidsdk.provisioning.device

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.accruesavings.androidsdk.provisioning.error.ErrorCodes
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError
import com.accruesavings.androidsdk.provisioning.core.TapAndPayClientManager

/**
 * Service responsible for gathering device information needed for provisioning
 */
class DeviceInfoService(
    private val context: Context,
    private val tapAndPayClientManager: TapAndPayClientManager
) {
    companion object {
        private const val TAG = "DeviceInfoService"
    }
    
    // Cache for device info components
    private var cachedStableHardwareId: String? = null
    private var cachedWalletAccountId: String? = null
    
    // Queue for pending requests
    private val pendingRequests = mutableListOf<(DeviceInfo) -> Unit>()
    
    /**
     * Get complete device information asynchronously
     */
    fun getDeviceInfo(
        walletAccountId: String?,
        callback: (DeviceInfo?) -> Unit
    ) {
        Log.d(TAG, "Getting device info with wallet account ID: $walletAccountId")
        
        if (walletAccountId == null) {
            Log.w(TAG, "Wallet account ID not available")
            callback(null)
            return
        }
        
        // Get stable hardware ID asynchronously
        getStableHardwareId { hardwareId ->
            if (hardwareId != null) {
                val deviceInfo = createDeviceInfo(hardwareId, walletAccountId)
                Log.d(TAG, "Created device info - Hardware ID: $hardwareId, Wallet ID: $walletAccountId")
                callback(deviceInfo)
            } else {
                Log.w(TAG, "Stable hardware ID not available")
                callback(null)
            }
        }
    }
    
    /**
     * Get the stable hardware ID for this device using Google's TapAndPay API
     */
    fun getStableHardwareId(callback: (String?) -> Unit) {
        if (cachedStableHardwareId != null) {
            Log.d(TAG, "Using cached stable hardware ID: $cachedStableHardwareId")
            callback(cachedStableHardwareId)
            return
        }
        
        tapAndPayClientManager.getStableHardwareId(
            onSuccess = { hardwareId ->
                Log.d(TAG, "Retrieved stable hardware ID from TapAndPay: $hardwareId")
                cachedStableHardwareId = hardwareId
                callback(hardwareId)
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to get stable hardware ID from TapAndPay", exception)
                // Fallback to ANDROID_ID if TapAndPay fails
                try {
                    val fallbackId = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    )
                    Log.w(TAG, "Using fallback ANDROID_ID: $fallbackId")
                    cachedStableHardwareId = fallbackId
                    callback(fallbackId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get fallback ANDROID_ID", e)
                    callback(null)
                }
            }
        )
    }
    
    /**
     * Create DeviceInfo from components
     */
    private fun createDeviceInfo(hardwareId: String, walletAccountId: String): DeviceInfo {
        val deviceType = when {
            isTablet() -> "TABLET"
            isWearable() -> "WATCH"
            else -> "MOBILE_PHONE"
        }
        
        val osVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }
        
        val deviceInfo = DeviceInfo(
            stableHardwareId = hardwareId,
            deviceType = deviceType,
            osVersion = osVersion,
            walletAccountId = walletAccountId
        )
        
        Log.d(TAG, "Created DeviceInfo: ${deviceInfo.toJson()}")
        return deviceInfo
    }
    
    /**
     * Check if device is a tablet
     */
    private fun isTablet(): Boolean {
        return context.resources.configuration.screenLayout and 
               android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK >= 
               android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
    }
    
    /**
     * Check if device is a wearable
     */
    private fun isWearable(): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.type.watch")
    }
    
    /**
     * Clear cached values
     */
    fun clearCache() {
        cachedStableHardwareId = null
        cachedWalletAccountId = null
    }
} 