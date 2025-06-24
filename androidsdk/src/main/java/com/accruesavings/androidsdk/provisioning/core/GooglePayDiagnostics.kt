package com.accruesavings.androidsdk.provisioning.core

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Build
import android.util.Log
import com.google.android.gms.common.GoogleApiAvailability
import org.json.JSONObject

/**
 * Diagnostic utility for troubleshooting Google Pay and TapAndPay issues
 */
class GooglePayDiagnostics(private val context: Context) {
    
    companion object {
        private const val TAG = "GooglePayDiagnostics"
    }
    
    /**
     * Perform comprehensive diagnostic checks
     */
    fun performDiagnostics(): JSONObject {
        val diagnostics = JSONObject()
        
        try {
            // Device information
            diagnostics.put("device", getDeviceInfo())
            
            // Feature availability
            diagnostics.put("features", getFeatureSupport())
            
            // Google Play Services
            diagnostics.put("googlePlayServices", getGooglePlayServicesInfo())
            
            // NFC status
            diagnostics.put("nfc", getNfcInfo())
            
            // Common issues
            diagnostics.put("commonIssues", getCommonIssues())
            
            // Recommendations
            diagnostics.put("recommendations", getRecommendations())
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing diagnostics", e)
            diagnostics.put("error", "Failed to perform diagnostics: ${e.message}")
        }
        
        return diagnostics
    }
    
    private fun getDeviceInfo(): JSONObject {
        return JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("androidVersion", Build.VERSION.RELEASE)
            put("sdkVersion", Build.VERSION.SDK_INT)
            put("isEmulator", isEmulator())
            put("fingerprint", Build.FINGERPRINT)
        }
    }
    
    private fun getFeatureSupport(): JSONObject {
        return JSONObject().apply {
            put("nfc", context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC))
            put("nfcHce", context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION))
            put("secure", context.packageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN))
        }
    }
    
    private fun getGooglePlayServicesInfo(): JSONObject {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        
        return JSONObject().apply {
            put("available", resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS)
            put("resultCode", resultCode)
            put("resultDescription", getGooglePlayServicesStatusDescription(resultCode))
            put("canResolve", availability.isUserResolvableError(resultCode))
        }
    }
    
    private fun getNfcInfo(): JSONObject {
        val nfcManager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
        val nfcAdapter = nfcManager?.defaultAdapter
        
        return JSONObject().apply {
            put("supported", nfcAdapter != null)
            put("enabled", nfcAdapter?.isEnabled ?: false)
            put("available", nfcAdapter != null && nfcAdapter.isEnabled)
        }
    }
    
    private fun getCommonIssues(): JSONObject {
        val issues = JSONObject()
        
        // Check for emulator
        if (isEmulator()) {
            issues.put("emulator", "Running on emulator - Google Pay TapAndPay APIs may not work")
        }
        
        // Check for missing NFC
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            issues.put("noNfc", "Device does not support NFC - required for Google Pay")
        }
        
        // Check for missing HCE
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            issues.put("noHce", "Device does not support NFC Host Card Emulation - required for Google Pay")
        }
        
        // Check Google Play Services
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            issues.put("googlePlayServices", "Google Play Services issue: ${getGooglePlayServicesStatusDescription(resultCode)}")
        }
        
        // Check NFC enabled
        val nfcManager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
        val nfcAdapter = nfcManager?.defaultAdapter
        if (nfcAdapter != null && !nfcAdapter.isEnabled) {
            issues.put("nfcDisabled", "NFC is disabled - user needs to enable it in settings")
        }
        
        return issues
    }
    
    private fun getRecommendations(): JSONObject {
        val recommendations = JSONObject()
        
        if (isEmulator()) {
            recommendations.put("emulator", "Test on a real device with NFC support")
        }
        
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            recommendations.put("noNfc", "Use a device with NFC support for Google Pay testing")
        }
        
        val nfcManager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
        val nfcAdapter = nfcManager?.defaultAdapter
        if (nfcAdapter != null && !nfcAdapter.isEnabled) {
            recommendations.put("enableNfc", "Enable NFC in device settings")
        }
        
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            if (availability.isUserResolvableError(resultCode)) {
                recommendations.put("updateGooglePlay", "Update Google Play Services")
            } else {
                recommendations.put("googlePlayUnavailable", "Google Play Services not available on this device")
            }
        }
        
        recommendations.put("packageVerification", "Ensure your app is registered with Google Pay Console")
        recommendations.put("testEnvironment", "Use TEST environment for development")
        
        return recommendations
    }
    
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                "google_sdk" == Build.PRODUCT)
    }
    
    private fun getGooglePlayServicesStatusDescription(resultCode: Int): String {
        return when (resultCode) {
            com.google.android.gms.common.ConnectionResult.SUCCESS -> "Available"
            com.google.android.gms.common.ConnectionResult.SERVICE_MISSING -> "Missing"
            com.google.android.gms.common.ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "Update Required"
            com.google.android.gms.common.ConnectionResult.SERVICE_DISABLED -> "Disabled"
            com.google.android.gms.common.ConnectionResult.SERVICE_INVALID -> "Invalid"
            else -> "Unknown ($resultCode)"
        }
    }
    
    /**
     * Log diagnostic information
     */
    fun logDiagnostics() {
        val diagnostics = performDiagnostics()
        Log.i(TAG, "Google Pay Diagnostics:")
        Log.i(TAG, diagnostics.toString(2))
    }
    
    /**
     * Get a user-friendly summary of issues
     */
    fun getSummary(): String {
        val diagnostics = performDiagnostics()
        val issues = diagnostics.optJSONObject("commonIssues")
        val recommendations = diagnostics.optJSONObject("recommendations")
        
        val summary = StringBuilder()
        summary.append("Google Pay Diagnostic Summary:\n\n")
        
        if (issues != null && issues.length() > 0) {
            summary.append("Issues Found:\n")
            issues.keys().forEach { key ->
                summary.append("• ${issues.optString(key)}\n")
            }
            summary.append("\n")
        } else {
            summary.append("No major issues detected.\n\n")
        }
        
        if (recommendations != null && recommendations.length() > 0) {
            summary.append("Recommendations:\n")
            recommendations.keys().forEach { key ->
                summary.append("• ${recommendations.optString(key)}\n")
            }
        }
        
        return summary.toString()
    }
} 