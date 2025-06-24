package com.accruesavings.androidsdk.provisioning.device

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.accruesavings.androidsdk.provisioning.error.ErrorCodes
import com.accruesavings.androidsdk.provisioning.error.ProvisioningError

/**
 * Service responsible for gathering device information needed for provisioning
 */
class DeviceInfoService(
    private val context: Context
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
        // Check if we have all required info
        val hwId = getStableHardwareId()
        if (hwId != null && walletAccountId != null) {
            val deviceInfo = createDeviceInfo(hwId, walletAccountId)
            callback(deviceInfo)
            return
        }
        
        // Queue the request if missing required data
        if (hwId == null) {
            Log.w(TAG, "Stable hardware ID not available")
            callback(null)
            return
        }
        
        if (walletAccountId == null) {
            Log.w(TAG, "Wallet account ID not available")
            callback(null)
            return
        }
        
        // This shouldn't happen, but handle gracefully
        callback(null)
    }
    
    /**
     * Get the stable hardware ID for this device
     */
    fun getStableHardwareId(): String? {
        if (cachedStableHardwareId != null) {
            return cachedStableHardwareId
        }
        
        try {
            cachedStableHardwareId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            Log.d(TAG, "Retrieved stable hardware ID")
            return cachedStableHardwareId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stable hardware ID", e)
            return null
        }
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
        
        return DeviceInfo(
            stableHardwareId = hardwareId,
            deviceType = deviceType,
            osVersion = osVersion,
            walletAccountId = walletAccountId
        )
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