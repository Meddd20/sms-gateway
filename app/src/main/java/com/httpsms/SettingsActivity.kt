package com.httpsms

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
// import com.httpsms.ui.settings.SettingsScreen
import com.httpsms.ui.settings.SettingsViewModel
import com.httpsms.ui.theme.HttpSmsTheme
import timber.log.Timber

class SettingsActivity : AppCompatActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Settings page is disabled for now - everything moved to the main screen.
        // viewModel.initialize(this)
        //
        // setContent {
        //     HttpSmsTheme {
        //         SettingsScreen(
        //             viewModel = viewModel,
        //             onBackClick = { onBackClicked() },
        //             onHeartbeatClick = {
        //                 viewModel.sendHeartbeat(this) { error ->
        //                     if (error != null) {
        //                         Timber.w("heartbeat sending failed with [$error]")
        //                         Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        //                     } else {
        //                         Toast.makeText(this, getString(R.string.heartbeat_sent_success), Toast.LENGTH_SHORT).show()
        //                     }
        //                 }
        //             },
        //             onLogoutClick = { onLogoutClick() }
        //         )
        //     }
        // }
    }

    private fun onBackClicked() {
        Timber.d("back button clicked")
        redirectToMain()
    }

    private fun redirectToMain() {
        finish()
        val switchActivityIntent = Intent(this, MainActivity::class.java)
        startActivity(switchActivityIntent)
    }

    private fun onLogoutClick() {
        Timber.d("logout button clicked")
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.logout_dialog_title))
            .setMessage(getString(R.string.logout_dialog_message))
            .setNeutralButton(getString(R.string.dialog_cancel)){ _, _ -> Timber.d("logout dialog canceled") }
            .setPositiveButton(getString(R.string.dialog_logout)){_, _ ->
                Timber.d("logging out user")
                viewModel.logout(this) {
                    redirectToLogin()
                }
            }
            .show()
    }

    private fun redirectToLogin():Boolean {
        if (Settings.isLoggedIn(this)) {
            return false
        }
        val switchActivityIntent = Intent(this, LoginActivity::class.java)
        startActivity(switchActivityIntent)
        return true
    }
}
