package com.accruesavings.androidsdk.provisioning.core

import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * Modern ActivityResult handler using ActivityResultContracts
 * This eliminates the need for the app to implement onActivityResult
 */
class ActivityResultHandler(
    private val fragment: Fragment,
    private val onResult: (requestCode: Int, resultCode: Int, data: Intent?) -> Unit
) {
    
    companion object {
        private const val TAG = "ActivityResultHandler"
    }
    
    private var currentRequestCode: Int = 0
    
    private val activityResultLauncher: ActivityResultLauncher<Intent> = 
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleActivityResult(result)
        }
    
    /**
     * Launch an intent and handle the result
     */
    fun launchIntent(intent: Intent, requestCode: Int) {
        currentRequestCode = requestCode
        Log.d(TAG, "Launching intent with request code: $requestCode")
        activityResultLauncher.launch(intent)
    }
    
    /**
     * Handle the activity result
     */
    private fun handleActivityResult(result: ActivityResult) {
        Log.d(TAG, "Activity result received: requestCode=$currentRequestCode, resultCode=${result.resultCode}")
        onResult(currentRequestCode, result.resultCode, result.data)
    }
} 