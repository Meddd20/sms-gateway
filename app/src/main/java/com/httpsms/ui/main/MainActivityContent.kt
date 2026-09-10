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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.httpsms.R
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
            contentDescription = stringResource(id = R.string.img_http_sms_logo),
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
            needsAutostartStep -> GatewayDialog(
                title = stringResource(id = R.string.main_dialog_autostart_title),
                description = stringResource(id = R.string.main_dialog_autostart_description),
                primaryText = stringResource(id = R.string.main_dialog_autostart_open),
                onPrimary = onOemAutostartClick,
                secondaryText = stringResource(id = R.string.main_dialog_autostart_done),
                onSecondary = { viewModel.acknowledgeOemAutostart(context) },
                onDismiss = null
            )
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

@Composable
private fun GatewayDialog(
    title: String,
    description: String,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    onDismiss: (() -> Unit)?
) {
    Dialog(onDismissRequest = { onDismiss?.invoke() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colorResource(id = R.color.gateway_navy),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = colorResource(id = R.color.gateway_navy),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.gateway_navy)),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = primaryText.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                if (secondaryText != null && onSecondary != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = secondaryText.uppercase(),
                            color = colorResource(id = R.color.gateway_navy),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}



