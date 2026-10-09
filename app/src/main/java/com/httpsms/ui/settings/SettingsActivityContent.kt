package com.httpsms.ui.settings

/*
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sevanam.androidsmsgateway.R
import com.httpsms.ui.theme.Blue500
import com.httpsms.ui.theme.LogoGreen
import com.httpsms.ui.theme.Pink500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onHeartbeatClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val primaryColor = if (isDarkTheme) Color.Black else LogoGreen

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = primaryColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back_content_description))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // SIM 1 Settings
//            OutlinedTextField(
//                value = uiState.phoneNumberSIM1,
//                onValueChange = { },
//                label = { Text(stringResource(id = R.string.settings_sim1)) },
//                modifier = Modifier.fillMaxWidth(),
//                enabled = false
//            )
//
//            SwitchSetting(
//                text = stringResource(id = R.string.settings_outgoing_messages_sim1),
//                checked = uiState.isActiveSIM1,
//                onCheckedChange = { viewModel.setActiveSIM1(context, it) }
//            )
//
//            SwitchSetting(
//                text = stringResource(id = R.string.settings_incoming_messages_sim1),
//                checked = uiState.isIncomingSIM1Enabled,
//                onCheckedChange = { viewModel.setIncomingSIM1Enabled(context, it) }
//            )

//            if (uiState.isDualSim) {
//                Spacer(modifier = Modifier.height(16.dp))
//                // SIM 2 Settings
//                OutlinedTextField(
//                    value = uiState.phoneNumberSIM2,
//                    onValueChange = { },
//                    label = { Text(stringResource(id = R.string.settings_sim_2)) },
//                    modifier = Modifier.fillMaxWidth(),
//                    enabled = false
//                )
//
//                SwitchSetting(
//                    text = stringResource(id = R.string.settings_outgoing_messages_sim2),
//                    checked = uiState.isActiveSIM2,
//                    onCheckedChange = { viewModel.setActiveSIM2(context, it) }
//                )
//
//                SwitchSetting(
//                    text = stringResource(id = R.string.settings_incoming_messages_sim2),
//                    checked = uiState.isIncomingSIM2Enabled,
//                    onCheckedChange = { viewModel.setIncomingSIM2Enabled(context, it) }
//                )
//            }

//            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onHeartbeatClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !uiState.isHeartbeatLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Blue500),
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

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onLogoutClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
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
        }
    }
}

@Composable
fun SwitchSetting(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
*/
