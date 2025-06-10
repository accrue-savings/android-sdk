# Google Wallet Provisioning - Lifecycle Management Guide

## Overview

The Google Wallet Provisioning feature requires proper handling of Android's activity lifecycle to avoid `IllegalStateException` related to `ActivityResultLauncher` registration. This SDK now includes full push provisioning functionality using Google's TapAndPay API.

## Features

- **Google Pay Availability Check**: Verify if Google Pay is available on the device
- **TapAndPay Availability Check**: Verify if TapAndPay is available for push provisioning
- **Push Provisioning**: Full implementation using TapAndPay API to add cards to Google Pay
- **Token Status Querying**: Check individual token states and activation status
- **Token Activation Handling**: Detect and handle cards needing identity verification
- **Token Removal**: Remove cards from Google Pay with user confirmation
- **Wallet State Synchronization**: Monitor changes to the active wallet
- **Lifecycle-Safe Initialization**: Proper handling of ActivityResultLauncher registration
- **Base64 OPC Handling**: Proper decoding of Opaque Payment Cards per Marqeta specs

## The Problem

When `AccrueWallet` is created and initialized after the host activity has reached the `STARTED` state, the Android framework throws:

```
java.lang.IllegalStateException: LifecycleOwner is attempting to register while current state is RESUMED. LifecycleOwners must call register before they are STARTED.
```

This happens because `ActivityResultLauncher` instances must be registered before the activity reaches the `STARTED` state.

## Solutions

### Option 1: Use Early Initialization (Recommended)

Create the `AccrueWallet` instance in your activity's `onCreate` method using the new `newInstanceWithEarlyInit` method:

```kotlin
class MainActivity : AppCompatActivity() {
    private var accrueWallet: AccrueWallet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create AccrueWallet with early initialization - this prevents lifecycle issues
        accrueWallet = AccrueWallet.newInstanceWithEarlyInit(
            activity = this,
            merchantId = "your-merchant-id",
            contextData = getContextData(),
            onAction = mapOf(
                AccrueAction.SignInButtonClicked to {
                    Log.i("AccrueWebView", "SIGN IN BUTTON HANDLER ACTIVATED")
                },
                AccrueAction.GoogleWalletProvisioningRequested to {
                    // Check both Google Pay and TapAndPay availability
                    accrueWallet?.isGooglePayAvailable { googlePayAvailable ->
                        if (googlePayAvailable) {
                            accrueWallet?.isTapAndPayAvailable { tapAndPayAvailable ->
                                if (tapAndPayAvailable) {
                                    Log.i("AccrueWebView", "Ready for push provisioning")
                                } else {
                                    Log.w("AccrueWebView", "TapAndPay not available")
                                }
                            }
                        } else {
                            Log.w("AccrueWebView", "Google Pay not available")
                        }
                    }
                }
            )
        )

        // Add to fragment container when needed (e.g., button click)
        findViewById<Button>(R.id.load_wallet_button).setOnClickListener {
            loadWallet()
        }
    }

    private fun loadWallet() {
        accrueWallet?.let { wallet ->
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, wallet)
                .commit()
        }
    }
}
```

### Option 2: Manual Pre-initialization

If you prefer to use the regular `newInstance` method, you can manually pre-initialize:

```kotlin
class MainActivity : AppCompatActivity() {
    private var accrueWallet: AccrueWallet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create wallet instance
        accrueWallet = AccrueWallet.newInstance(
            merchantId = "your-merchant-id",
            contextData = getContextData(),
            onAction = mapOf(/* your actions */)
        )

        // Pre-initialize Google Wallet Provisioning
        accrueWallet?.preInitializeGoogleWalletProvisioning()

        // Set up button to load wallet
        findViewById<Button>(R.id.load_wallet_button).setOnClickListener {
            loadWallet()
        }
    }

    private fun loadWallet() {
        accrueWallet?.let { wallet ->
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, wallet)
                .commit()
        }
    }
}
```

### Option 3: Handle Runtime Initialization (Fallback)

The SDK now gracefully handles late initialization by detecting when the activity is already started and disabling the problematic `ActivityResultLauncher` registration:

```kotlin
// This will work but with limited push provisioning functionality
accrueWallet = AccrueWallet.newInstance(/* parameters */)
// Wallet will initialize but push provisioning may not work properly
```

## Push Provisioning

The SDK now includes full push provisioning functionality using Google's TapAndPay API. To use push provisioning:

1. **Ensure proper lifecycle initialization** (use Option 1 or 2 above)
2. **Check device compatibility**:
   ```kotlin
   accrueWallet?.isGooglePayAvailable { googlePayReady ->
       if (googlePayReady) {
           accrueWallet?.isTapAndPayAvailable { tapAndPayReady ->
               if (tapAndPayReady) {
                   // Device is ready for push provisioning
                   // The webview will handle the actual provisioning flow
               }
           }
       }
   }
   ```
3. **Handle provisioning results** in your action handlers

## Token Management

The SDK now includes comprehensive token management capabilities following Marqeta best practices:

### Querying Token Status

Check the status of provisioned tokens to detect activation needs:

```kotlin
// Check token status (you'll get the tokenReferenceId from push provisioning results)
accrueWallet?.getTokenStatus(
    PushTokenizeRequest.TOKEN_PROVIDER_VISA, // TSP constant
    tokenReferenceId
) { tokenStatus, error ->
    if (error != null) {
        Log.e("TokenStatus", "Error: $error")
        return@getTokenStatus
    }

    tokenStatus?.let { status ->
        when (status.tokenState) {
            TokenStatus.TOKEN_STATE_ACTIVE -> {
                Log.i("TokenStatus", "Token is active and ready to use")
            }
            TokenStatus.TOKEN_STATE_NEEDS_IDENTITY_VERIFICATION -> {
                Log.w("TokenStatus", "Token needs identity verification")
                // Handle activation requirement
            }
            TokenStatus.TOKEN_STATE_PENDING -> {
                Log.i("TokenStatus", "Token is pending activation")
            }
            TokenStatus.TOKEN_STATE_SUSPENDED -> {
                Log.w("TokenStatus", "Token is suspended")
            }
        }

        if (status.isSelected) {
            Log.i("TokenStatus", "This token is the default payment method")
        }
    }
}
```

### Token Activation

Handle tokens that need identity verification:

```kotlin
accrueWallet?.checkAndActivateToken(
    PushTokenizeRequest.TOKEN_PROVIDER_VISA,
    tokenReferenceId
) { isActive, message ->
    if (isActive) {
        Log.i("Activation", "Token is active: $message")
    } else {
        Log.w("Activation", "Token needs attention: $message")
        // You may need to direct users to complete verification
        // or trigger server-side activation via your backend
    }
}
```

### Token Removal

Remove tokens with user confirmation:

```kotlin
// This shows a system dialog asking the user to confirm deletion
accrueWallet?.requestDeleteToken(
    PushTokenizeRequest.TOKEN_PROVIDER_VISA,
    tokenReferenceId
)
// Result will be handled through the existing result handler
```

### Wallet State Synchronization

The SDK automatically monitors wallet changes (when the app is in foreground):

- Active wallet changes
- Token additions/removals
- Token status changes
- Default payment method changes

No additional code needed - the SDK handles this automatically via `DataChangedListener`.

## Error Handling

The SDK now includes comprehensive error handling for lifecycle and provisioning issues:

- `ERROR_LAUNCHER_UNAVAILABLE`: ActivityResultLauncher couldn't be registered due to lifecycle state
- `ERROR_DEVICE_NOT_SUPPORTED`: TapAndPay or Google Pay not available on device
- `ERROR_PROVISIONING_FAILED`: Push provisioning process failed
- `ERROR_USER_CANCELLED`: User cancelled the push provisioning flow
- `ERROR_GOOGLE_PAY_UNAVAILABLE`: Google Pay service not available
- `ERROR_PARSING_RESPONSE`: Failed to parse provisioning data from server
- `ERROR_TOKEN_NOT_FOUND`: Token not found (may have been removed by user)
- `ERROR_WALLET_CREATION_FAILED`: Unable to create or access wallet
- `ERROR_ACTIVATION_REQUIRED`: Token requires identity verification

## Best Practices

1. **Always use early initialization** when Google Wallet functionality is required
2. **Create AccrueWallet instances in onCreate()** rather than in button click handlers
3. **Reuse AccrueWallet instances** rather than creating new ones repeatedly
4. **Check logs for lifecycle warnings** during development
5. **Test on various devices** to ensure TapAndPay compatibility
6. **Handle all error cases** gracefully in your UI

## Requirements

For push provisioning to work, your app needs:

1. **TapAndPay API dependency** in `build.gradle`:

   ```kotlin
   implementation("com.google.android.gms:play-services-tapandpay:18.3.3")
   ```

2. **Proper permissions** in your `AndroidManifest.xml`:

   ```xml
   <uses-permission android:name="com.google.android.gms.permission.ACTIVITY_RECOGNITION" />
   <uses-permission android:name="android.permission.NFC" />
   ```

3. **Google Play Services** installed on the device
4. **Google Pay app** installed and set up
5. **NFC-enabled device** for tap payments

## Migration from Previous Versions

If you're experiencing the lifecycle error with existing code:

1. Move your `AccrueWallet.newInstance()` call from button handlers to `onCreate()`
2. Consider switching to `AccrueWallet.newInstanceWithEarlyInit()`
3. Store the wallet instance as a class property and reuse it
4. Update your error handling to account for new push provisioning errors

## Example Migration

**Before (problematic):**

```kotlin
button.setOnClickListener {
    val wallet = AccrueWallet.newInstance(/* params */)
    // This causes the lifecycle error
    supportFragmentManager.beginTransaction()
        .replace(R.id.container, wallet)
        .commit()
}
```

**After (fixed):**

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var wallet: AccrueWallet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wallet = AccrueWallet.newInstanceWithEarlyInit(this, /* other params */)

        button.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, wallet)
                .commit()
        }
    }
}
```
