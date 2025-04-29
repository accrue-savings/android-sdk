# Google Wallet Push Provisioning Sample

This sample demonstrates how to integrate Google Wallet Push Provisioning with the AccrueWallet SDK.

## Integration Flow

1. The user clicks a trigger in the WebView
2. The WebView sends an event with key `AccrueWallet::GoogleWalletProvisioningRequested`
3. The SDK gets device information and sends it to the WebView by calling `__GENERATE_GOOGLE_WALLET_PUSH_PROVISIONING_TOKEN`
4. The WebView calls the backend to generate the provisioning payload
5. The WebView sends an event with key `AccrueWallet::GoogleWalletProvisioningResponse` containing the payload
6. The SDK parses the payload and initiates the Google Wallet Push Provisioning
7. Once the card is added, the SDK calls `__GOOGLE_WALLET_PROVISIONING_SUCCESS` in the WebView

## Testing Instructions

### Prerequisites

1. A device with Google Pay installed
2. A Google account set up on the device
3. Android Studio for running the sample app

### Testing with the Sample App

1. Open the sample app in Android Studio
2. Run the `SampleGoogleWalletProvisioningActivity` on a physical device (emulators may not have Google Pay)
3. The WebView will load and you can trigger the provisioning process by clicking on the appropriate button in the WebView

### Expected Behavior

1. When the user triggers the provisioning from the WebView:
   - The SDK will check if Google Pay is available on the device
   - It will gather device information and send it to the WebView
   - The WebView will generate the provisioning token and send it back to the SDK
   - The SDK will launch Google Pay to add the card
   - Upon success, the SDK will notify the WebView of the success

### Troubleshooting

If you encounter issues:

1. Check the logs for any error messages
2. Verify that Google Pay is installed and properly set up on the device
3. Ensure the device has internet connectivity
4. Make sure the WebView and backend are properly configured to handle the provisioning flow

## Notes for Implementers

1. The SDK handles all the communication with Google Pay
2. No implementation is necessary on the partner side to implement Push Provisioning
3. The WebView must handle the following events:
   - `__GENERATE_GOOGLE_WALLET_PUSH_PROVISIONING_TOKEN` to generate the token
   - `__GOOGLE_WALLET_PROVISIONING_SUCCESS` to handle successful provisioning

## Testing with Real Data

To test with real card data, you will need:

1. A test merchant ID that supports Push Provisioning
2. Test card data from the payment network (Visa, Mastercard)
3. A backend integration that can generate the proper payload for Push Provisioning

Follow these steps to test with real data:

1. Update the `merchantId` in the sample app to your test merchant ID
2. Configure the backend to generate valid Push Provisioning payloads
3. Run the sample app and complete the flow
