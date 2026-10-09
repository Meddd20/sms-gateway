package com.httpsms.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sevanam.androidsmsgateway.R
import com.httpsms.ui.components.ErrorDialog
import com.httpsms.ui.components.GatewayDialog
import com.httpsms.ui.components.HelpNote
import com.httpsms.ui.components.HelpSheet
import com.httpsms.ui.components.HelpSheetHeader
import com.httpsms.ui.components.SimCardSelector
import com.httpsms.ui.theme.Blue500
import com.httpsms.ui.theme.Pink500

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSettingsClick: () -> Unit,
    onSmsPermissionClick: () -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onOemAutostartClick: () -> Unit,
    onHeartbeatClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSimHelp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        Image(
            painter = painterResource(id = R.drawable.sevanam),
            contentDescription = stringResource(id = R.string.img_logo),
            modifier = Modifier.size(200.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        SimCardSelector(
            sims = uiState.sims,
            selectedSimIndex = uiState.selectedSimIndex,
            numberForIndex = { index ->
                when (index) {
                    0 -> uiState.phoneNumberSIM1
                    1 -> uiState.phoneNumberSIM2
                    else -> ""
                }
            },
            // The line is fixed once logged in - changing it happens through the
            // login flow, which the help sheet explains.
            enabled = false,
            onHelpClick = { showSimHelp = true },
            onSimSelected = { index -> viewModel.selectSim(context, index) },
            onSimNumberConfirmed = { index, number -> viewModel.confirmSimNumber(context, index, number) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        val needsBatteryStep = !uiState.isBatteryOptimizationDisabled
        val needsSmsStep = uiState.isBatteryOptimizationDisabled && !uiState.isSmsPermissionGranted
        val needsAutostartStep = uiState.isBatteryOptimizationDisabled &&
            uiState.isSmsPermissionGranted &&
            !uiState.isOemAutostartAcknowledged
        when {
            needsBatteryStep -> RequiredSetupDialog(
                title = stringResource(id = R.string.main_dialog_battery_title),
                description = stringResource(id = R.string.main_card_battery_description),
                buttonText = stringResource(id = R.string.main_card_disable_battery),
                onAction = onBatteryOptimizationClick
            )
            needsSmsStep -> RequiredSetupDialog(
                title = stringResource(id = R.string.main_dialog_sms_title),
                description = stringResource(id = R.string.main_card_sms_description),
                buttonText = stringResource(id = R.string.main_card_enable_sms),
                onAction = onSmsPermissionClick
            )
            needsAutostartStep -> {
                // The confirm button only appears once the user has actually been sent
                // to the OEM autostart screen: the toggle itself cannot be read back,
                // so requiring the visit is the strongest precondition available.
                val confirmAutostart: (() -> Unit)? = if (uiState.hasOpenedAutostartSettings) {
                    { viewModel.acknowledgeOemAutostart(context) }
                } else {
                    null
                }
                GatewayDialog(
                    title = stringResource(id = R.string.main_dialog_autostart_title),
                    description = stringResource(id = R.string.main_dialog_autostart_description),
                    primaryText = stringResource(id = R.string.main_dialog_autostart_open),
                    onPrimary = {
                        viewModel.markAutostartSettingsOpened(context)
                        onOemAutostartClick()
                    },
                    secondaryText = if (confirmAutostart != null) stringResource(id = R.string.main_dialog_autostart_done) else null,
                    onSecondary = confirmAutostart,
                    onDismiss = null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onHeartbeatClick,
            enabled = !uiState.isHeartbeatLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Blue500),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = if (uiState.isHeartbeatLoading) LocalContentColor.current else Pink500
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(id = R.string.send_heartbeat),
                color = Color.White,
                fontSize = 18.sp
            )
        }

        if (uiState.isHeartbeatLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                color = Pink500
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showLogoutDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(id = R.string.main_log_out),
                color = Color.White,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = uiState.appVersion,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(16.dp))

        /*
        // App Settings page is disabled for now - everything lives on this screen.
        Button(
            onClick = onSettingsClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(id = R.string.main_app_settings),
                color = Color.White,
                fontSize = 18.sp
            )
        }
        */

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLogoutDialog) {
        GatewayDialog(
            title = stringResource(id = R.string.logout_dialog_title),
            description = stringResource(id = R.string.logout_dialog_message),
            primaryText = stringResource(id = R.string.dialog_logout),
            onPrimary = {
                showLogoutDialog = false
                onLogoutClick()
            },
            secondaryText = stringResource(id = R.string.dialog_cancel),
            onSecondary = { showLogoutDialog = false },
            onDismiss = { showLogoutDialog = false }
        )
    }

    // A user-initiated request (e.g. the heartbeat) came back with an error: show
    // the message the request returned in the app's standard dialog.
    val errorMessage = uiState.errorMessage
    if (errorMessage != null) {
        ErrorDialog(
            message = errorMessage,
            onDismiss = { viewModel.dismissError() }
        )
    }

    if (showSimHelp) {
        HelpSheet(onDismiss = { showSimHelp = false }) {
            HelpSheetHeader(
                icon = Icons.Default.Smartphone,
                title = stringResource(id = R.string.main_sim_help_title),
                description = stringResource(id = R.string.main_sim_help_desc)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HelpNote(
                icon = Icons.AutoMirrored.Filled.Logout,
                text = stringResource(id = R.string.main_sim_help_note_logout)
            )
        }
    }
}

@Composable
private fun RequiredSetupDialog(
    title: String,
    description: String,
    buttonText: String,
    onAction: () -> Unit
) {
    GatewayDialog(
        title = title,
        description = description,
        primaryText = buttonText,
        onPrimary = onAction,
        onDismiss = null
    )
}



