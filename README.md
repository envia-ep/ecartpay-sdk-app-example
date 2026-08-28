# Ecart Pay SDK - Sample Application

Sample Android application demonstrating how to integrate the [Ecart Pay Tap to Phone SDK](https://docs.ecartpay.com).

Accept contactless card payments directly on your Android device — no additional hardware required.

## Requirements

| Requirement | Version |
|-------------|---------|
| Android SDK | minSdk 26 (Android 8.0) |
| Android Studio | Hedgehog or later |
| Device | Physical device with NFC support |

> ⚠️ **Note**: Tap to Phone functionality requires a physical device. Emulators are not supported.

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/ecartpay/android-sdk-sample.git
cd android-sdk-sample
```

### 2. Configure Credentials

Copy the example credentials file:

```bash
cp app/src/main/kotlin/com/ecartpay/sample/BuildCredentials.kt.example \
   app/src/main/kotlin/com/ecartpay/sample/BuildCredentials.kt
```

Edit `BuildCredentials.kt` with your Ecart Pay credentials:

```kotlin
object BuildCredentials {
    const val PUBLIC_ID = "pk_sandbox_your_public_id"
    const val PRIVATE_ID = "sk_sandbox_your_private_id"
}
```

#### How to Get Credentials

1. Log in to [Ecart Pay](https://ecartpay.com)
2. Navigate to **Integrations** > **Dev Tools** > **Credentials**
3. Create new API Keys selecting type **SDK Tap To Phone**
4. Copy your `Public ID` and `Private ID`

### 3. Build and Run

Open the project in Android Studio and run on a physical device with NFC support.

## Features Demonstrated

This sample app shows how to:

- ✅ Initialize the SDK
- ✅ Register and enroll a device (combined `setupDevice()`)
- ✅ Process payments with different configurations:
  - Direct amount charge
  - Itemized products
  - Customer information
  - Shipping details
- ✅ Handle payment results (approved, declined, cancelled, error)
- ✅ Check device registration status
- ✅ Install Tap to Pay Ready companion app

## SDK Integration

### Installation

Add to your `build.gradle`:

```groovy
dependencies {
    implementation 'com.ecartpay:tap-to-phone-sdk:1.0.0'
}
```

### Initialize

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        EcartPaySDK.initialize(
            context = this,
            config = EcartPayConfig(
                publicId = "your_public_id",
                privateId = "your_private_id",
                environment = Environment.SANDBOX
            )
        )
    }
}
```

### Setup Device

```kotlin
EcartPaySDK.instance().setupDevice(activity) { result ->
    when (result) {
        is SetupResult.Ready -> { /* Ready to accept payments */ }
        is SetupResult.TapToPayReadyRequired -> { /* Install Visa app */ }
        is SetupResult.Cancelled -> { /* User cancelled */ }
        is SetupResult.RegistrationFailed -> { /* Handle error */ }
        is SetupResult.EnrollmentFailed -> { /* Handle error */ }
    }
}
```

### Process Payment

```kotlin
val request = PaymentRequest(
    amount = 100.00,
    currency = "MXN",
    email = "customer@email.com"
)

EcartPaySDK.instance().startPayment(activity, request) { result ->
    when (result) {
        is PaymentResult.Approved -> { /* Success! */ }
        is PaymentResult.Declined -> { /* Payment declined */ }
        is PaymentResult.Cancelled -> { /* User cancelled */ }
        is PaymentResult.Error -> { /* Handle error */ }
    }
}
```

## Documentation

For complete documentation, visit [docs.ecartpay.com](https://docs.ecartpay.com).

## Support

- 📧 Email: support@ecartpay.com
- 📖 Documentation: [docs.ecartpay.com](https://docs.ecartpay.com)
- 💬 Live Chat: Available in the Ecart Pay dashboard

## License

This sample application is provided under the MIT License. See [LICENSE](LICENSE) for details.
