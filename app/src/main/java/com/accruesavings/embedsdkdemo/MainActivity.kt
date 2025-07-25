package com.accruesavings.embedsdkdemo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.accruesavings.accruepaysdkdemo.R
import com.accruesavings.androidsdk.AccrueAction
import com.accruesavings.androidsdk.AccrueContextData
import com.accruesavings.androidsdk.AccrueSettingsData
import com.accruesavings.androidsdk.AccrueUserData
import com.accruesavings.androidsdk.AccrueWallet
import com.accruesavings.androidsdk.SampleData

class MainActivity : AppCompatActivity() {
    private lateinit var merchantIdInput: EditText
    private lateinit var redirectTokenInput: EditText
    private lateinit var phoneNumberInput: EditText
    private lateinit var referenceIdInput: EditText
    private lateinit var reloadButton: Button
    private lateinit var sampleDatabutton: Button
    private lateinit var updateContextButton: Button
    private lateinit var goToHomeButton: Button
    private var accrueWallet: AccrueWallet? = null

    private fun getContext(): AccrueContextData {
        val phoneNumber = phoneNumberInput.text.toString()
        val referenceId = referenceIdInput.text.toString()
        Log.i("AccrueWebView", "Phone number $phoneNumber")
        val userData = AccrueUserData(
            referenceId.ifEmpty { null },
            null,
            phoneNumber.ifEmpty { null },
            mapOf("firstName" to "Sasa", "lastName" to "Sijak")
        )

        val settingsData = AccrueSettingsData(
            shouldInheritAuthentication = true
        )

        val contextData = AccrueContextData(userData, settingsData)
        return contextData
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        merchantIdInput = findViewById(R.id.merchant_id_input)
        redirectTokenInput = findViewById(R.id.redirect_token_input)
        phoneNumberInput = findViewById(R.id.phone_number_input)
        referenceIdInput = findViewById(R.id.reference_id_input)
        reloadButton = findViewById(R.id.reload_button)
        sampleDatabutton = findViewById(R.id.use_sample_values_button)
        updateContextButton = findViewById(R.id.update_context_button)
        goToHomeButton = findViewById(R.id.go_to_home_button)

        sampleDatabutton.setOnClickListener {
            merchantIdInput.setText(SampleData.merchantId)
            phoneNumberInput.setText(SampleData.phoneNumber)
            referenceIdInput.setText(SampleData.referenceId)
        }

        updateContextButton.setOnClickListener {
            accrueWallet?.updateContextData(getContext())
        }

        goToHomeButton.setOnClickListener {
            accrueWallet?.handleEvent("AccrueTabPressed", "")
        }

        reloadButton.setOnClickListener {
            loadAccrueWallet()
        }
    }
    
    private fun loadAccrueWallet() {
        val merchantId = merchantIdInput.text.toString()
        val redirectionToken = redirectTokenInput.text.toString()
        val contextData = getContext()

        // Remove existing fragment if it exists
        accrueWallet?.let { existingWallet ->
            supportFragmentManager.beginTransaction()
                .remove(existingWallet)
                .commit()
        }


        // Create new AccrueWallet instance with early initialization to prevent lifecycle issues
        accrueWallet = AccrueWallet.newInstanceWithEarlyInit(
            activity = this,
            contextData = contextData,
            redirectionToken = redirectionToken,
            isSandbox = true,
            merchantId = merchantId,
            onAction = mapOf(
                AccrueAction.SignInButtonClicked to {
                    Log.i("AccrueWebView", "SIGN IN BUTTON HANDLER ACTIVATED")
                },
                AccrueAction.RegisterButtonClicked to {
                    Log.i("AccrueWebView", "REGISTER BUTTON HANDLER ACTIVATED")
                },
                AccrueAction.GoogleWalletProvisioningRequested to {
                    Log.i("AccrueWebView", "GOOGLE WALLET PROVISIONING REQUESTED HANDLER ACTIVATED")
                }
            )
        )
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, accrueWallet!!)
            .commit()
    }
}