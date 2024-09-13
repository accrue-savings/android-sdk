package com.accruesavings.embedsdkdemo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.accruesavings.accruepaysdkdemo.R
import com.accruesavings.androidsdk.AccrueContextData
import com.accruesavings.androidsdk.AccrueUserData
import com.accruesavings.androidsdk.AccrueWallet
import com.accruesavings.androidsdk.SampleData

class MainActivity : AppCompatActivity() {
    private lateinit var merchantIdInput: EditText
    private lateinit var redirectTokenInput: EditText
    private lateinit var reloadButton: Button
    private lateinit var sampleDatabutton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        merchantIdInput = findViewById(R.id.merchant_id_input)
        redirectTokenInput = findViewById(R.id.redirect_token_input)
        reloadButton = findViewById(R.id.reload_button)
        sampleDatabutton = findViewById(R.id.use_sample_values_button)

        sampleDatabutton.setOnClickListener {
            merchantIdInput.setText(SampleData.merchantId)
        }

        reloadButton.setOnClickListener {
            val merchantId = merchantIdInput.text.toString()
            val redirectionToken = redirectTokenInput.text.toString()

            val userData = AccrueUserData(
                referenceId = "defaultReferenceId",
                phoneNumber = "defaultPhoneNumber",
                email = "defaultEmail"
            )

            val contextData = AccrueContextData(userData)

            val fragment = AccrueWallet.newInstance(
                url = "http://localhost:5173/webview",
                contextData = contextData,
                redirectionToken = redirectionToken,
                isSandbox = true,
                merchantId = merchantId,
            )

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }
}