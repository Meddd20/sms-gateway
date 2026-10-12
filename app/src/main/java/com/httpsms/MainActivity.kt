package com.httpsms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.provider.Settings as ProviderSettings
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.httpsms.background.DeviceRegistration
import com.httpsms.background.WorkQueue
import com.httpsms.core.GatewayLogging
import com.httpsms.core.OemSettings
import com.httpsms.core.PermissionGate
import com.httpsms.core.Settings
import com.httpsms.background.StickyNotificationService
import com.httpsms.ui.main.MainScreen
import com.httpsms.ui.main.MainViewModel
import com.httpsms.ui.theme.HttpSmsTheme
import com.sevanam.androidsmsgateway.BuildConfig
import timber.log.Timber
import com.sevanam.androidsmsgateway.R

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    // Registered here, before the activity reaches STARTED: the contract cannot be
    // registered from onStart, and this is the only handle the rest of the class needs.
    private val permissions = PermissionGate.attach(this) { result ->
        result.entries.forEach { Timber.d("${it.key} = ${it.value}") }
        viewModel.updateState(this, getString(R.string.app_version, BuildConfig.VERSION_NAME))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GatewayLogging.init(this)

        redirectToLogin()

        viewModel.initialize(this, getString(R.string.app_version, BuildConfig.VERSION_NAME))

        setContent {
            HttpSmsTheme {
                MainScreen(
                    viewModel = viewModel,
                    onSmsPermissionClick = {
                        val intent = Intent(
                            ProviderSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    },
                    onBatteryOptimizationClick = {
                        val intent = Intent()
                        intent.action = ProviderSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    },
                    onOemAutostartClick = {
                        OemSettings.openAutoStartSettings(this)
                    },
                    onHeartbeatClick = {
                        viewModel.sendHeartbeat(this) { error ->
                            // Failures are surfaced by the ErrorDialog from uiState.errorMessage,
                            // carrying the message the request itself returned.
                            if (error == null) {
                                Toast.makeText(this, getString(R.string.heartbeat_sent_success), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onLogoutClick = {
                        Timber.d("logging out user")
                        viewModel.logout(this) {
                            redirectToLogin()
                        }
                    }
                )
            }
        }

        createChannel()
        refreshToken(this)

        StickyNotificationService.start(this)
        WorkQueue.scheduleHeartbeat(this)
    }

    override fun onStart() {
        super.onStart()
        permissions.request()
    }

    override fun onResume() {
        super.onResume()
        Timber.d( "on activity resume")
        redirectToLogin()
        refreshToken(this)
        viewModel.updateState(this, getString(R.string.app_version, BuildConfig.VERSION_NAME))
    }

    private fun refreshToken(context: Context) {
        DeviceRegistration.refreshAsync(context)
    }

    private fun redirectToLogin():Boolean {
        if (Settings.isLoggedIn(this)) {
            return false
        }
        val switchActivityIntent = Intent(this, LoginActivity::class.java)
        startActivity(switchActivityIntent)
        return true
    }

    private fun createChannel() {
        // Create the NotificationChannel
        val name = getString(R.string.notification_channel_default)
        val descriptionText = getString(R.string.notification_channel_default)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(name, name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }
}
