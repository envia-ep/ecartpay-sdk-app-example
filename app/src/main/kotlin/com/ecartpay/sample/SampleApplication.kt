package com.ecartpay.sample

import android.app.Application
import com.ecartpay.sdk.EcartPayConfig
import com.ecartpay.sdk.EcartPaySDK
import com.ecartpay.sdk.Environment

/**
 * Wires up [EcartPaySDK] with sandbox credentials for the sample app.
 *
 * Replace the placeholders below with real values before running against real hardware.
 * You can also override them at build time via `sample-app/local.properties`.
 *
 * By omitting `providerMode` we rely on the SDK's default mapping
 * ([Environment.SANDBOX] -> `ProviderMode.TEST`, [Environment.PRODUCTION] ->
 * `ProviderMode.LIVE`). Add an explicit `providerMode = ProviderMode.LIVE` here
 * if your ecartpay sandbox merchant is provisioned against CyberSource production
 * (for example when you want to test with real cards on staging, as
 * `ecart-pay-app` does today).
 */
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        EcartPaySDK.initialize(
            context = this,
            config = EcartPayConfig(
                publicId = BuildCredentials.PUBLIC_ID,
                privateId = BuildCredentials.PRIVATE_ID,
                environment = Environment.SANDBOX,
            ),
        )
    }
}
