package com.accruesavings.androidsdk.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.accruesavings.androidsdk.AccrueAction
import com.accruesavings.androidsdk.AccrueContextData
import com.accruesavings.androidsdk.AccrueWallet
import com.accruesavings.androidsdk.GoogleWalletProvisioning
import com.accruesavings.androidsdk.SampleData
import com.accruesavings.androidsdk.TestConfig

/**
 * Sample activity to demonstrate Google Wallet Provisioning integration
 * This is for testing purposes only and should not be included in production builds
 */
class SampleGoogleWalletProvisioningActivity : AppCompatActivity() {
    private val TAG = "SampleGoogleWalletProv"
    private lateinit var accrueWallet: AccrueWallet
    private val PERMISSION_REQUEST_GET_ACCOUNTS = 100
    private var googleWalletProvisioning: GoogleWalletProvisioning? = null
    
    // Test controls
    private lateinit var testControlsLayout: LinearLayout
    private lateinit var testModeSwitch: Switch
    private lateinit var mockSuccessSwitch: Switch
    private lateinit var simulateSuccessButton: Button
    private lateinit var simulateErrorButton: Button
    private lateinit var hideTestControlsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up a LinearLayout as the main content view
        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        setContentView(mainLayout)
        
        // Create a LinearLayout for test controls
        testControlsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            visibility = View.GONE  // Initially hidden
        }
        
        // Test mode switch
        testModeSwitch = Switch(this).apply {
            text = "Enable Test Mode"
            isChecked = TestConfig.enableTestMode
            setOnCheckedChangeListener { _, isChecked ->
                googleWalletProvisioning?.setTestMode(isChecked, mockSuccessSwitch.isChecked)
                Toast.makeText(this@SampleGoogleWalletProvisioningActivity, 
                    "Test mode ${if (isChecked) "enabled" else "disabled"}", 
                    Toast.LENGTH_SHORT).show()
            }
        }
        testControlsLayout.addView(testModeSwitch)
        
        // Mock success switch
        mockSuccessSwitch = Switch(this).apply {
            text = "Mock Operations Succeed"
            isChecked = TestConfig.GoogleWalletProvisioning.mockOperationsSucceed
            setOnCheckedChangeListener { _, isChecked ->
                TestConfig.GoogleWalletProvisioning.mockOperationsSucceed = isChecked
                Toast.makeText(this@SampleGoogleWalletProvisioningActivity, 
                    "Mock operations will ${if (isChecked) "succeed" else "fail"}", 
                    Toast.LENGTH_SHORT).show()
            }
        }
        testControlsLayout.addView(mockSuccessSwitch)
        
        // Simulate success button
        simulateSuccessButton = Button(this).apply {
            text = "Simulate Success"
            setOnClickListener {
                googleWalletProvisioning?.simulateSuccess()
                Toast.makeText(this@SampleGoogleWalletProvisioningActivity, 
                    "Simulating success response", 
                    Toast.LENGTH_SHORT).show()
            }
        }
        testControlsLayout.addView(simulateSuccessButton)
        
        // Simulate error button
        simulateErrorButton = Button(this).apply {
            text = "Simulate Error"
            setOnClickListener {
                googleWalletProvisioning?.simulateError()
                Toast.makeText(this@SampleGoogleWalletProvisioningActivity, 
                    "Simulating error response", 
                    Toast.LENGTH_SHORT).show()
            }
        }
        testControlsLayout.addView(simulateErrorButton)
        
        // Hide test controls button
        hideTestControlsButton = Button(this).apply {
            text = "Hide Test Controls"
            setOnClickListener {
                testControlsLayout.visibility = View.GONE
            }
        }
        testControlsLayout.addView(hideTestControlsButton)
        
        // Add test controls to main layout
        mainLayout.addView(testControlsLayout)
        
        // Show test controls button
        val showTestControlsButton = Button(this).apply {
            text = "Show Test Controls"
            setOnClickListener {
                testControlsLayout.visibility = View.VISIBLE
            }
        }
        mainLayout.addView(showTestControlsButton)
        
        // Fragment container for AccrueWallet
        val fragmentContainer = LinearLayout(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        mainLayout.addView(fragmentContainer)
        
        // Check if we have GET_ACCOUNTS permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            // Request the permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.GET_ACCOUNTS),
                PERMISSION_REQUEST_GET_ACCOUNTS
            )
        } else {
            // We already have permission, so proceed with setup
            setupAccrueWallet(fragmentContainer.id)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_GET_ACCOUNTS -> {
                // If request is cancelled, the result arrays are empty
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    Log.d(TAG, "GET_ACCOUNTS permission granted")
                    setupAccrueWallet(android.R.id.content)
                } else {
                    // Permission denied
                    Log.e(TAG, "GET_ACCOUNTS permission denied")
                    Toast.makeText(this, "Account permission is required for Google Wallet provisioning", Toast.LENGTH_LONG).show()
                    // Still set up but with limited functionality
                    setupAccrueWallet(android.R.id.content)
                }
                return
            }
        }
    }
    
    private fun setupAccrueWallet(containerViewId: Int) {
        // Create context data
        val contextData = AccrueContextData()
        
        // Create onAction map
        val onAction: Map<AccrueAction, () -> Unit> = mapOf(
            AccrueAction.SignInButtonClicked to {
                Log.d(TAG, "SignIn button clicked")
            },
            AccrueAction.RegisterButtonClicked to {
                Log.d(TAG, "Register button clicked")
            },
            AccrueAction.GoogleWalletProvisioningRequested to {
                Log.d(TAG, "Google Wallet Provisioning requested")
                
                // Check if Google Pay is available
                accrueWallet.isGooglePayAvailable { isAvailable ->
                    if (isAvailable) {
                        Log.d(TAG, "Google Pay is available")
                        Toast.makeText(this@SampleGoogleWalletProvisioningActivity, "Google Pay is available", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d(TAG, "Google Pay is not available")
                        Toast.makeText(this@SampleGoogleWalletProvisioningActivity, "Google Pay is not available on this device", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        
        // Create AccrueWallet instance
        accrueWallet = AccrueWallet.newInstance(
            merchantId = SampleData.merchantId,
            isSandbox = true,
            contextData = contextData,
            onAction = onAction
        )
        
        // Get a reference to the GoogleWalletProvisioning instance
        accrueWallet.getGoogleWalletProvisioning()?.let { provisioning ->
            googleWalletProvisioning = provisioning
            
            // Set initial test mode state
            provisioning.setTestMode(testModeSwitch.isChecked, mockSuccessSwitch.isChecked)
        }
        
        // Set the AccrueWallet fragment as the content
        supportFragmentManager.beginTransaction()
            .replace(containerViewId, accrueWallet)
            .commit()
    }
} 