package com.ecartpay.sample

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ecartpay.sdk.EcartPaySDK
import com.ecartpay.sdk.EnrollmentResult
import com.ecartpay.sdk.NetworkListener
import com.ecartpay.sdk.OrderItem
import com.ecartpay.sdk.PaymentRequest
import com.ecartpay.sdk.PaymentResult
import com.ecartpay.sdk.RegistrationResult
import com.ecartpay.sdk.SetupResult
import com.ecartpay.sdk.ShippingAddress
import com.ecartpay.sdk.ShippingItem
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Sample app to exercise the SDK end-to-end with different payment scenarios:
 *
 * 1. Register the device (creates RSA key pair + calls the API, once).
 * 2. Enroll the device with the CyberSource mPOS provider (via Visa "Tap to Pay Ready").
 * 3. Select a payment scenario and enter the required fields.
 * 4. Observe the [PaymentResult] on the log view.
 *
 * **Payment Scenarios (incrementales):**
 * 1. Mínimo: Solo amount + currency
 * 2. + Customer: Agrega email, phone, first_name, last_name
 * 3. + Reference: Agrega reference, reference_id, notify_url
 * 4. Items: Usa items[] en lugar de amount
 * 5. Items + Customer: Items con datos del cliente
 * 6. Items + Shipping: Items con shipping_items[] y shipping_address{}
 * 7. Completo: Todos los campos del schema
 */
class MainActivity : AppCompatActivity() {

    private val sdk by lazy { EcartPaySDK.instance() }

    private lateinit var scenarioSpinner: Spinner
    private lateinit var amountInput: EditText
    private lateinit var currencyInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var referenceInput: EditText
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var setupButton: Button
    private lateinit var registerButton: Button
    private lateinit var enrollButton: Button
    private lateinit var payButton: Button
    private lateinit var resetButton: Button
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scenarioSpinner = findViewById(R.id.scenario_spinner)
        amountInput = findViewById(R.id.amount_input)
        currencyInput = findViewById(R.id.currency_input)
        emailInput = findViewById(R.id.email_input)
        firstNameInput = findViewById(R.id.first_name_input)
        lastNameInput = findViewById(R.id.last_name_input)
        phoneInput = findViewById(R.id.phone_input)
        referenceInput = findViewById(R.id.reference_input)
        logView = findViewById(R.id.log_view)
        logScroll = findViewById(R.id.log_scroll)
        setupButton = findViewById(R.id.setup_button)
        registerButton = findViewById(R.id.register_button)
        enrollButton = findViewById(R.id.enroll_button)
        payButton = findViewById(R.id.pay_button)
        resetButton = findViewById(R.id.reset_button)
        statusView = findViewById(R.id.status_view)

        setupScenarioSpinner()

        setupButton.setOnClickListener { onSetupClicked() }
        registerButton.setOnClickListener { onRegisterClicked() }
        enrollButton.setOnClickListener { onEnrollClicked() }
        payButton.setOnClickListener { onPayClicked() }
        resetButton.setOnClickListener { onResetClicked() }

        sdk.setNetworkListener(networkLogger)
        refreshRegistrationStatus()
    }

    private fun setupScenarioSpinner() {
        val scenarios = resources.getStringArray(R.array.payment_scenarios)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, scenarios)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scenarioSpinner.adapter = adapter
    }

    override fun onDestroy() {
        sdk.setNetworkListener(null)
        super.onDestroy()
    }

    /**
     * Mirrors every request/response the SDK performs into [logView] so we can
     * inspect the checkout / API payloads without attaching a debugger. Callbacks
     * arrive on the SDK background thread — [appendLog] internally hops to the
     * main thread through the [TextView.append] machinery.
     */
    private val networkLogger = object : NetworkListener {
        override fun onRequest(method: String, url: String, body: String?) {
            runOnUiThread {
                appendLog("→ $method $url")
                body?.let { appendLog(prettyJson(it)) }
            }
        }

        override fun onResponse(method: String, url: String, statusCode: Int, body: String) {
            runOnUiThread {
                appendLog("← $method $url [$statusCode]")
                if (body.isNotBlank()) appendLog(prettyJson(body))
            }
        }

        override fun onError(method: String, url: String, error: Throwable) {
            runOnUiThread {
                appendLog("× $method $url — ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun prettyJson(raw: String): String = try {
        JSONObject(raw).toString(2)
    } catch (_: JSONException) {
        try {
            JSONArray(raw).toString(2)
        } catch (_: JSONException) {
            raw
        }
    }

    private fun onResetClicked() {
        sdk.reset()
        appendLog("Local state cleared. Register the device again.")
        refreshRegistrationStatus()
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case the user came back from installing "Tap to Pay Ready".
        refreshRegistrationStatus()
    }

    private fun onSetupClicked() {
        setBusy(true)
        appendLog("Setting up device (register + enroll)…")

        sdk.setupDevice(this) { result ->
            setBusy(false)
            when (result) {
                is SetupResult.Ready -> {
                    appendLog("SETUP COMPLETE — id=${result.deviceId} serial=${result.serialNumber ?: "?"}")
                    refreshRegistrationStatus()
                }
                is SetupResult.TapToPayReadyRequired -> {
                    appendLog("Tap to Pay Ready required (device registered: ${result.deviceId})")
                    showTapToPayReadyDialog()
                    refreshRegistrationStatus()
                }
                is SetupResult.Cancelled -> {
                    appendLog("Setup cancelled by user (device registered: ${result.deviceId})")
                    refreshRegistrationStatus()
                }
                is SetupResult.RegistrationFailed -> {
                    appendLog("Registration failed: ${result.code} — ${result.message}")
                    Log.e(TAG, "setupDevice registration failed", result.cause)
                }
                is SetupResult.EnrollmentFailed -> {
                    appendLog("Enrollment failed: ${result.code} — ${result.message} (device: ${result.deviceId})")
                    Log.e(TAG, "setupDevice enrollment failed", result.cause)
                    refreshRegistrationStatus()
                }
            }
        }
    }

    private fun onRegisterClicked() {
        setBusy(true)
        appendLog("Registering device…")

        sdk.registerDevice { result ->
            setBusy(false)
            when (result) {
                is RegistrationResult.Success -> {
                    appendLog("Device registered. id=${result.deviceId}")
                    refreshRegistrationStatus()
                }
                is RegistrationResult.Failure -> {
                    appendLog("Registration failed: ${result.code} — ${result.message}")
                    Log.e(TAG, "registerDevice failed", result.cause)
                }
            }
        }
    }

    private fun onEnrollClicked() {
        setBusy(true)
        appendLog("Starting enrollment…")

        sdk.enrollDevice(this) { result ->
            setBusy(false)
            when (result) {
                is EnrollmentResult.Enrolled -> {
                    appendLog("Device enrolled. serial=${result.serialNumber ?: "?"}")
                    refreshRegistrationStatus()
                }
                EnrollmentResult.TapToPayReadyRequired -> {
                    appendLog("Tap to Pay Ready is required — showing install prompt")
                    showTapToPayReadyDialog()
                }
                is EnrollmentResult.Cancelled -> {
                    appendLog("Enrollment cancelled by user")
                }
                is EnrollmentResult.Failure -> {
                    appendLog("Enrollment failed: ${result.code} — ${result.message}")
                    Log.e(TAG, "enrollDevice failed", result.cause)
                }
            }
        }
    }

    private fun onPayClicked() {
        val amount = amountInput.text.toString().toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            appendLog("Enter a valid amount")
            return
        }
        val currency = currencyInput.text.toString().ifBlank { "MXN" }

        val request = buildPaymentRequest(amount, currency)
        if (request == null) {
            appendLog("Failed to build payment request")
            return
        }

        val scenarioName = scenarioSpinner.selectedItem.toString()
        setBusy(true)
        appendLog("Starting payment ($scenarioName) $amount $currency…")

        sdk.startPayment(this, request) { result ->
            setBusy(false)
            when (result) {
                is PaymentResult.Approved -> appendLog(
                    "APPROVED — order=${result.orderId} tx=${result.transactionId} " +
                            "${result.amount} ${result.currency} card=${result.maskedCardNumber ?: "?"}"
                )
                is PaymentResult.Declined -> appendLog(
                    "DECLINED — order=${result.orderId} tx=${result.transactionId} " +
                            "reason=${result.reason ?: "?"}"
                )
                is PaymentResult.Cancelled -> appendLog("CANCELLED — order=${result.orderId}")
                is PaymentResult.Error -> {
                    appendLog("ERROR — ${result.code}: ${result.message}")
                    if (result.code == "TAP_TO_PAY_READY_REQUIRED") {
                        showTapToPayReadyDialog()
                    }
                    Log.e(TAG, "startPayment failed", result.cause)
                }
            }
        }
    }

    /**
     * Builds a [PaymentRequest] based on the selected scenario in the spinner.
     *
     * Scenarios demonstrate different field combinations:
     * 1. **Mínimo**: Solo amount + currency (campos requeridos mínimos)
     * 2. **+ Customer**: Agrega email, phone, first_name, last_name
     * 3. **+ Reference**: Agrega reference y reference_id
     * 4. **Items**: Usa items en lugar de amount
     * 5. **Items + Customer**: Items con datos del cliente
     * 6. **Items + Shipping**: Items con shipping_items y shipping_address
     * 7. **Completo**: Todos los campos del schema
     */
    private fun buildPaymentRequest(amount: Double, currency: String): PaymentRequest? {
        val email = emailInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val firstName = firstNameInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val lastName = lastNameInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val phone = phoneInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val reference = referenceInput.text.toString().trim().takeIf { it.isNotEmpty() }

        return when (scenarioSpinner.selectedItemPosition) {
            SCENARIO_MINIMUM -> buildMinimumRequest(amount, currency)
            SCENARIO_WITH_CUSTOMER -> buildWithCustomerRequest(amount, currency, email, phone, firstName, lastName)
            SCENARIO_WITH_REFERENCE -> buildWithReferenceRequest(amount, currency, reference)
            SCENARIO_ITEMS_ONLY -> buildItemsOnlyRequest(amount, currency)
            SCENARIO_ITEMS_CUSTOMER -> buildItemsWithCustomerRequest(amount, currency, email, phone, firstName, lastName)
            SCENARIO_ITEMS_SHIPPING -> buildItemsWithShippingRequest(amount, currency)
            SCENARIO_COMPLETE -> buildCompleteRequest(amount, currency, email, phone, firstName, lastName, reference)
            else -> null
        }
    }

    /**
     * Escenario 1: Campos mínimos requeridos.
     * Envía: amount, currency
     * Caso de uso: Cargo rápido sin datos adicionales.
     */
    private fun buildMinimumRequest(
        amount: Double,
        currency: String,
    ) = PaymentRequest(
        amount = amount,
        currency = currency,
    )

    /**
     * Escenario 2: Con datos del cliente.
     * Envía: amount, currency, email, phone, first_name, last_name
     * Caso de uso: Cargo con información del cliente para recibos.
     */
    private fun buildWithCustomerRequest(
        amount: Double,
        currency: String,
        email: String?,
        phone: String?,
        firstName: String?,
        lastName: String?,
    ) = PaymentRequest(
        amount = amount,
        currency = currency,
        email = email ?: "cliente@ejemplo.com",
        phone = phone ?: "8112345678",
        firstName = firstName ?: "Juan",
        lastName = lastName ?: "Pérez",
    )

    /**
     * Escenario 3: Con referencias.
     * Envía: amount, currency, reference, reference_id, notify_url
     * Caso de uso: Cargo con referencias para tracking interno.
     */
    private fun buildWithReferenceRequest(
        amount: Double,
        currency: String,
        reference: String?,
    ) = PaymentRequest(
        amount = amount,
        currency = currency,
        reference = reference ?: "INV-2026-${System.currentTimeMillis() % 10000}",
        referenceId = "ORDER-${System.currentTimeMillis()}",
        notifyUrl = "https://ejemplo.com/webhooks/ecartpay",
    )

    /**
     * Escenario 4: Solo items (sin amount).
     * Envía: currency, items[]
     * Caso de uso: Venta de productos con cálculo de total en backend.
     */
    private fun buildItemsOnlyRequest(
        amount: Double,
        currency: String,
    ): PaymentRequest {
        val itemPrice = amount / 2.0
        return PaymentRequest(
            currency = currency,
            items = listOf(
                OrderItem(name = "Brazalete religioso plateado BR3017", price = itemPrice, quantity = 1),
                OrderItem(name = "Collar de plata con cruz CR2045", price = itemPrice, quantity = 1),
            ),
        )
    }

    /**
     * Escenario 5: Items con datos del cliente.
     * Envía: currency, items[], email, phone, first_name, last_name
     * Caso de uso: Carrito de compras con datos del cliente.
     */
    private fun buildItemsWithCustomerRequest(
        amount: Double,
        currency: String,
        email: String?,
        phone: String?,
        firstName: String?,
        lastName: String?,
    ): PaymentRequest {
        val itemPrice = amount / 3.0
        return PaymentRequest(
            currency = currency,
            email = email ?: "cliente@ejemplo.com",
            phone = phone ?: "8112345678",
            firstName = firstName ?: "María",
            lastName = lastName ?: "González",
            items = listOf(
                OrderItem(name = "Brazalete religioso plateado BR3017", price = itemPrice, quantity = 1),
                OrderItem(name = "Collar de plata con cruz CR2045", price = itemPrice, quantity = 1),
                OrderItem(name = "Aretes de plata AR1089", price = itemPrice, quantity = 1),
            ),
        )
    }

    /**
     * Escenario 6: Items con shipping.
     * Envía: currency, items[], shipping_items[], shipping_address{}
     * Caso de uso: Compra de productos físicos con envío.
     */
    private fun buildItemsWithShippingRequest(
        amount: Double,
        currency: String,
    ): PaymentRequest {
        val productPrice = amount * 0.8
        val shippingCost = amount * 0.2
        return PaymentRequest(
            currency = currency,
            items = listOf(
                OrderItem(name = "Brazalete religioso plateado BR3017", price = productPrice, quantity = 1),
            ),
            shippingItems = listOf(
                ShippingItem(
                    name = "Envío Express",
                    amount = shippingCost,
                    carrier = "FEDEX",
                    trackingNumber = null,
                ),
            ),
            shippingAddress = ShippingAddress(
                firstName = "Juan",
                lastName = "Pérez",
                address1 = "Av. Revolución 123",
                address2 = "Col. Centro",
                city = "Monterrey",
                postalCode = "64000",
                countryCode = "MX",
                countryName = "Mexico",
                stateCode = "NL",
                stateName = "Nuevo León",
                phone = "8112345678",
            ),
        )
    }

    /**
     * Escenario 7: Todos los campos del schema.
     * Envía: amount (null), currency, email, phone, first_name, last_name,
     *        reference, reference_id, notify_url, items[], shipping_items[], shipping_address{}
     * Caso de uso: Checkout completo de e-commerce.
     */
    private fun buildCompleteRequest(
        amount: Double,
        currency: String,
        email: String?,
        phone: String?,
        firstName: String?,
        lastName: String?,
        reference: String?,
    ): PaymentRequest {
        val productTotal = amount * 0.7
        val shippingCost = amount * 0.3
        val item1Price = productTotal * 0.6
        val item2Price = productTotal * 0.4

        return PaymentRequest(
            amount = null,
            currency = currency,
            email = email ?: "cliente@ejemplo.com",
            phone = phone ?: "8112345678",
            firstName = firstName ?: "María",
            lastName = lastName ?: "González",
            reference = reference ?: "INV-2026-${System.currentTimeMillis() % 10000}",
            referenceId = "ORDER-${System.currentTimeMillis()}",
            notifyUrl = "https://ejemplo.com/webhooks/ecartpay",
            items = listOf(
                OrderItem(
                    name = "Brazalete religioso plateado BR3017",
                    price = item1Price,
                    quantity = 2,
                ),
                OrderItem(
                    name = "Collar de plata con cruz CR2045",
                    price = item2Price,
                    quantity = 1,
                ),
            ),
            shippingItems = listOf(
                ShippingItem(
                    name = "Envío Express (2-3 días)",
                    amount = shippingCost,
                    carrier = "DHL",
                    trackingNumber = "1234567890",
                ),
            ),
            shippingAddress = ShippingAddress(
                firstName = firstName ?: "María",
                lastName = lastName ?: "González",
                address1 = "Calle Hidalgo 456",
                address2 = "Depto. 5B",
                address3 = "Edificio Los Pinos",
                city = "Guadalajara",
                postalCode = "44100",
                countryCode = "MX",
                countryName = "Mexico",
                stateCode = "JAL",
                stateName = "Jalisco",
                phone = phone ?: "3312345678",
                reference = "Junto a la farmacia Guadalajara",
            ),
        )
    }

    private companion object {
        const val TAG = "EcartPaySample"

        const val SCENARIO_MINIMUM = 0
        const val SCENARIO_WITH_CUSTOMER = 1
        const val SCENARIO_WITH_REFERENCE = 2
        const val SCENARIO_ITEMS_ONLY = 3
        const val SCENARIO_ITEMS_CUSTOMER = 4
        const val SCENARIO_ITEMS_SHIPPING = 5
        const val SCENARIO_COMPLETE = 6
    }

    private fun showTapToPayReadyDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.tap_to_pay_ready_dialog_title)
            .setMessage(R.string.tap_to_pay_ready_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.tap_to_pay_ready_dialog_open_playstore) { dialog, _ ->
                sdk.openTapToPayReadyPlayStore()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.tap_to_pay_ready_dialog_retry) { dialog, _ ->
                dialog.dismiss()
                if (sdk.isTapToPayReadyInstalled()) {
                    onEnrollClicked()
                } else {
                    appendLog("Tap to Pay Ready still not installed")
                }
            }
            .show()
    }

    private fun refreshRegistrationStatus() {
        val id = sdk.deviceId
        val enrolled = sdk.isEnrollmentPersisted
        val tapReady = sdk.isTapToPayReadyInstalled()
        val hasMerchant = sdk.hasProviderCredentials

        statusView.text = buildString {
            append(if (id != null) "Registered — $id" else "Not registered")
            if (id != null && !hasMerchant) append(" (missing keys_sym_*)")
            append("\nTap to Pay Ready: ${if (tapReady) "installed" else "missing"}")
            append("\nEnrolled: ${if (enrolled) "yes" else "no"}")
        }

        // Setup and Register are always tappable
        setupButton.isEnabled = true
        registerButton.isEnabled = true
        enrollButton.isEnabled = id != null && hasMerchant
        payButton.isEnabled = id != null && hasMerchant && enrolled
    }

    private fun setBusy(busy: Boolean) {
        val hasId = sdk.deviceId != null
        val hasMerchant = sdk.hasProviderCredentials
        val enrolled = sdk.isEnrollmentPersisted
        setupButton.isEnabled = !busy
        registerButton.isEnabled = !busy
        enrollButton.isEnabled = !busy && hasId && hasMerchant
        payButton.isEnabled = !busy && hasId && hasMerchant && enrolled
        resetButton.isEnabled = !busy
        findViewById<View>(R.id.progress).visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun appendLog(line: String) {
        val stamped = "[${System.currentTimeMillis() / 1000}] $line"
        logView.append("$stamped\n")
        Log.d(TAG, stamped)
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }
}
