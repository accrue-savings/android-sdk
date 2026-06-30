package com.accruesavings.embedsdkdemo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.accruesavings.accruepaysdkdemo.R
import com.accruesavings.androidsdk.AccrueAction
import com.accruesavings.androidsdk.AccrueContextData
import com.accruesavings.androidsdk.AccrueSettingsData
import com.accruesavings.androidsdk.AccrueUserData
import com.accruesavings.androidsdk.AccrueWallet
import com.accruesavings.androidsdk.SampleData
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var merchantIdInput: EditText
    private lateinit var redirectTokenInput: EditText
    private lateinit var phoneNumberInput: EditText
    private lateinit var referenceIdInput: EditText
    private lateinit var stableReferenceIdInput: EditText
    private lateinit var reloadButton: Button
    private lateinit var sampleDataButton: Button
    private lateinit var updateContextButton: Button
    private lateinit var goToHomeButton: Button
    private lateinit var bottomNav: BottomNavigationView
    private var accrueWallet: AccrueWallet? = null

    private fun getContext(): AccrueContextData {
        val phoneNumber = phoneNumberInput.text.toString()
        val referenceId = referenceIdInput.text.toString()
        val stableReferenceId = stableReferenceIdInput.text.toString()
        Log.i("AccrueWebView", "Phone number $phoneNumber")
        val userData = AccrueUserData(
            referenceId = referenceId.ifEmpty { null },
            email = null,
            phoneNumber = phoneNumber.ifEmpty { null },
            additionalData = mapOf("firstName" to "Sasa", "lastName" to "Sijak"),
            stableReferenceId = stableReferenceId.ifEmpty { null }
        )

        val settingsData = AccrueSettingsData(
            shouldInheritAuthentication = true
        )

        return AccrueContextData(userData, settingsData)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.url_input)
        merchantIdInput = findViewById(R.id.merchant_id_input)
        redirectTokenInput = findViewById(R.id.redirect_token_input)
        phoneNumberInput = findViewById(R.id.phone_number_input)
        referenceIdInput = findViewById(R.id.reference_id_input)
        stableReferenceIdInput = findViewById(R.id.stable_reference_id_input)
        reloadButton = findViewById(R.id.reload_button)
        sampleDataButton = findViewById(R.id.use_sample_values_button)
        updateContextButton = findViewById(R.id.update_context_button)
        goToHomeButton = findViewById(R.id.go_to_home_button)
        bottomNav = findViewById(R.id.bottom_nav)

        // Leave empty to load from production. For local dev: http://localhost:5173/webview
        // (requires `adb reverse tcp:5173 tcp:5173` on the host first)

        sampleDataButton.setOnClickListener {
            urlInput.setText(SampleData.url)
            merchantIdInput.setText(SampleData.merchantId)
            phoneNumberInput.setText(SampleData.phoneNumber)
            referenceIdInput.setText(SampleData.referenceId)
            stableReferenceIdInput.setText(SampleData.stableReferenceId)
            loadAccrueWallet()
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

        bottomNav.setOnItemSelectedListener { true }
    }

    private fun loadAccrueWallet() {
        val url = urlInput.text.toString().trim()
        val merchantId = merchantIdInput.text.toString()
        val redirectionToken = redirectTokenInput.text.toString()
        val contextData = getContext()

        // Remove existing fragment if present
        accrueWallet?.let { existing ->
            supportFragmentManager.beginTransaction()
                .remove(existing)
                .commit()
        }

        accrueWallet = AccrueWallet.newInstanceWithEarlyInit(
            activity = this,
            contextData = contextData,
            redirectionToken = redirectionToken,
            isSandbox = false,
            merchantId = merchantId,
            url = url.ifEmpty { null },
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
            ),
            onSignInPerformed = { payload ->
                Log.i("AccrueWebView", "SIGN IN PERFORMED: userId=${payload.id}, isNewUser=${payload.isNewUser}")
            }
        )

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, accrueWallet!!)
            .commit()
    }
}
