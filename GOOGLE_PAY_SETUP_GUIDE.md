# Google Pay Console Setup Guide

## Error: `15009: Calling package not verified`

This error occurs because your app isn't registered with Google Pay Console. Google Pay requires explicit whitelisting of apps before they can access TapAndPay APIs.

## Step-by-Step Solution:

### 1. Access Google Pay Console

- Go to: https://console.developers.google.com/
- Sign in with your Google account
- Or use: https://pay.google.com/business/console

### 2. Create Business Profile

1. Select **"Merchant"** as your business type
2. Fill out required business information:
   - Business name
   - Business address
   - Contact information
   - Tax ID (if applicable)
3. Complete your Business Profile
4. Wait for initial approval

### 3. Register Your Android App

1. Navigate to **"Google Pay API"** → **"Get Started"**
2. Accept the Google Pay API Terms of Service
3. Under **"Integrate your Android app"** section:
   - Find your app package name: `com.accruesavings.accruepaysdkdemo`
   - Click **"Manage"**

### 4. If Your App Isn't Listed:

1. Go to **"Users"** in the left navigation
2. Click **"Invite a user"**
3. Add the email address of your Google Play Developer Console account owner
4. Choose appropriate access level
5. Sign out and sign back in with that account

### 5. Configure Integration

1. Select **"Integration type: Gateway"** (if using payment processors)
2. Upload screenshots of your TEST Google Pay integration
3. Include screenshots showing:
   - Google Pay button in your app
   - Payment flow working
   - Test transaction completion
4. Click **"Submit for approval"**

### 6. For Testing (Before Approval):

While waiting for approval, you can test using:

```kotlin
// Use TEST environment
val walletOptions = Wallet.WalletOptions.Builder()
    .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
    .build()

// Use test card numbers
// Visa: 4111111111111111
// Mastercard: 5555555555554444
```

### 7. Production Configuration:

After approval:

```kotlin
// Switch to production environment
val walletOptions = Wallet.WalletOptions.Builder()
    .setEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)
    .build()
```

## Important Notes:

### APK Signing:

- **Debug builds**: Use for testing only
- **Release builds**: Must be signed with your release key
- **Google Play**: Must match the key used for Play Store

### Package Name Matching:

- Console registration must match your app's `applicationId`
- Current package: `com.accruesavings.accruepaysdkdemo`
- Verify in your `build.gradle`: `applicationId "com.accruesavings.accruepaysdkdemo"`

### Approval Timeline:

- Initial review: 1-2 business days
- Additional info requests: May extend timeline
- Production access: Granted after approval

## Testing Without Registration:

For immediate testing, you can:

1. **Use Google Pay API for Payments** (different from TapAndPay):

   ```kotlin
   // This doesn't require TapAndPay registration
   val paymentsClient = Wallet.getPaymentsClient(activity, walletOptions)
   ```

2. **Use Emulator/Test Environment**:

   - Test with sample payment methods
   - Verify UI flows work correctly
   - Test error handling

3. **Mock Implementation**:
   - Create mock responses for development
   - Test JavaScript bridge functionality
   - Validate business logic

## Troubleshooting Common Issues:

### "App not found in console"

- Ensure you're signed in with the Play Store developer account
- App must be published or in internal testing on Play Store
- Package name must match exactly

### "Integration type not available"

- You need a supported Payment Service Provider
- Or be PCI DSS compliant for direct integration
- Check the supported gateways list

### "Screenshots not accepted"

- Show actual Google Pay button and flow
- Must demonstrate working integration
- Include success/error states

## Payment Service Providers:

If you need a PSP, some popular options include:

- Stripe
- Braintree
- Adyen
- Square
- PayPal
- Worldpay

## Resources:

- [Google Pay Console](https://console.developers.google.com/)
- [Android Integration Guide](https://developers.google.com/pay/api/android/guides/tutorial)
- [Test Cards](https://developers.google.com/pay/api/android/guides/test-and-deploy/test-with-sample-cards)
- [Supported PSPs](https://developers.google.com/pay/api#participating-processors)

## Support:

If you need help:

1. Check the [Google Pay API documentation](https://developers.google.com/pay/api)
2. Visit [Stack Overflow](https://stackoverflow.com/questions/tagged/google-pay)
3. Contact Google Pay support through the Console
