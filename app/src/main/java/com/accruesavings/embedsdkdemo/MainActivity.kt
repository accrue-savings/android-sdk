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
    private lateinit var reloadButton: Button
    private lateinit var sampleDatabutton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        merchantIdInput = findViewById(R.id.merchant_id_input)
        redirectTokenInput = findViewById(R.id.redirect_token_input)
        phoneNumberInput = findViewById(R.id.phone_number_input)
        reloadButton = findViewById(R.id.reload_button)
        sampleDatabutton = findViewById(R.id.use_sample_values_button)

        sampleDatabutton.setOnClickListener {
            merchantIdInput.setText(SampleData.merchantId)
        }

        reloadButton.setOnClickListener {
            val merchantId = merchantIdInput.text.toString()
            val redirectionToken = redirectTokenInput.text.toString()
            val phoneNumber = phoneNumberInput.text.toString()

            val userData = AccrueUserData(
                phoneNumber,
                null,
                null
            )

            val settingsData = AccrueSettingsData(
                shouldInheritAuthentication = true
            )

            val contextData = AccrueContextData(userData, settingsData)

            val fragment = AccrueWallet.newInstance(
//                url = "http://localhost:5173/webview",
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
                    }
                )
            )

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }
}