package com.accruesavings.accruepaysdkdemo

import AccrueWallet
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.accruesavings.accruepaysdk.AccrueContextData
import com.accruesavings.accruepaysdk.AccrueUserData
import com.accruesavings.accruepaysdk.SampleData

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
                url = merchantId,
                contextData = contextData,
                redirectionToken = redirectionToken,
                isSandbox = true,
                merchantId = merchantIdInput.text.toString(),
            )

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }
}