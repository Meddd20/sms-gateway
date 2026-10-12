package com.httpsms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.httpsms.core.GatewayLogging
import com.httpsms.core.PermissionGate
import com.httpsms.core.Settings
import com.httpsms.ui.login.LoginScreen
import com.httpsms.ui.login.LoginViewModel
import com.httpsms.ui.theme.HttpSmsTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import timber.log.Timber
import com.sevanam.androidsmsgateway.R

class LoginActivity : AppCompatActivity() {
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GatewayLogging.init(this)
        redirectToMain()

        viewModel.initialize(this)

        setContent {
            HttpSmsTheme {
                val uiState by viewModel.uiState.collectAsState()
                
                LaunchedEffect(uiState.loginSuccess) {
                    if (uiState.loginSuccess) {
                        redirectToMain()
                    }
                }

                LoginScreen(
                    viewModel = viewModel,
                    onQrScanClick = { startQrCodeScan() },
                    onLoginClick = {
                        val error = isGooglePlayServicesAvailable()
                        if (error != null) {
                            Toast.makeText(this@LoginActivity, error, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.login(
                                context = this@LoginActivity,
                                countryCode = getCountryCode(),
                                onGooglePlayServicesError = {
                                    Toast.makeText(this@LoginActivity, it, Toast.LENGTH_SHORT).show()
                                },
                                onFcmTokenMissing = {
                                    Toast.makeText(
                                        this@LoginActivity,
                                        getString(R.string.login_fcm_token_missing),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.onPairingCodeChange(result.contents)
            Toast.makeText(this, getString(R.string.login_scanned_qr, result.contents), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, getString(R.string.login_scan_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startQrCodeScan() {
        val options = ScanOptions()
        options.setPrompt(getString(R.string.login_scan_qr_prompt))
        options.setBeepEnabled(true)
        options.setOrientationLocked(false)
        options.setCameraId(0)
        barcodeLauncher.launch(options)
    }

    override fun onStart() {
        super.onStart()
        Timber.i("on start")
        permissions.request()
    }

    private val permissions = PermissionGate.attach(this) { result ->
        result.entries.forEach {
            Timber.d("${it.key} = ${it.value}")
        }
        // Reload SIM list now that READ_PHONE_STATE is granted
        viewModel.loadSims(this)
    }

    private fun isGooglePlayServicesAvailable(): String? {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val status = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (status != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(status)) {
                googleApiAvailability.getErrorDialog(this, status, 2404)?.show()
            }
            return googleApiAvailability.getErrorString(status)
        }
        return null
    }

    private fun redirectToMain() {
        if (!Settings.isLoggedIn(this)) {
            return
        }
        finish()

        val switchActivityIntent = Intent(this, MainActivity::class.java)
        startActivity(switchActivityIntent)
    }

    private fun getCountryCode() : String {
        val tm = this.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val code = tm.networkCountryIso.uppercase()
        if (code.isEmpty()) {
            return this.resources.configuration.locales.get(0).country.uppercase()
        }
        return code
    }
}
