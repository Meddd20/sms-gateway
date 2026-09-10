package com.httpsms.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.httpsms.R
import com.httpsms.ui.components.SimCardSelector
import com.httpsms.ui.theme.Blue500
import com.httpsms.ui.theme.Pink500

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onQrScanClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showApiKeyHelp by remember { mutableStateOf(false) }
    var showSimHelp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Image(
            painter = painterResource(id = R.drawable.sevanam),
            contentDescription = stringResource(id = R.string.img_http_sms_logo),
            modifier = Modifier.size(200.dp)
        )

        Text(
            text = stringResource(id = R.string.login_header_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(id = R.color.gateway_navy),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(id = R.string.login_header_subtitle),
            fontSize = 14.sp,
            color = colorResource(id = R.color.gateway_body_gray),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.text_area_api_key),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorResource(id = R.color.gateway_navy)
            )
            IconButton(onClick = { showApiKeyHelp = true }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(id = R.string.api_key_help_description),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = { viewModel.onApiKeyChange(it) },
            placeholder = { Text(stringResource(id = R.string.hint_api_key)) },
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.apiKeyError != null,
            supportingText = uiState.apiKeyError?.let { { Text(it) } },
            trailingIcon = {
                IconButton(onClick = onQrScanClick) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(id = R.string.scan_qr_content_description)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            enabled = !uiState.isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        SimCardSelector(
            sims = uiState.detectedSims,
            selectedSimIndex = uiState.selectedSimIndex,
            numberForIndex = { index ->
                when (index) {
                    0 -> uiState.phoneNumberSIM1
                    1 -> uiState.phoneNumberSIM2
                    else -> ""
                }
            },
            enabled = !uiState.isLoading,
            isError = uiState.phoneNumberSIM1Error != null,
            supportingText = uiState.phoneNumberSIM1Error,
            onSimSelected = viewModel::onSimSelected,
            onSimNumberConfirmed = viewModel::onSimNumberConfirmed,
            onHelpClick = { showSimHelp = true },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLoginClick,
            enabled = !uiState.isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(containerColor = Blue500),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_login),
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
                tint = Color.White
            )
            Text(
                text = stringResource(id = R.string.sign_in_button),
                color = Color.White,
                fontSize = 18.sp
            )
        }

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Pink500
            )
        }
    }

    if (showApiKeyHelp) {
        ApiKeyHelpSheet(onDismiss = { showApiKeyHelp = false })
    }

    if (showSimHelp) {
        SimHelpSheet(onDismiss = { showSimHelp = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            HelpSheetHeader(
                icon = Icons.Default.VpnKey,
                title = stringResource(id = R.string.help_api_key_title),
                description = stringResource(id = R.string.help_api_key_desc)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(id = R.string.help_api_key_instruction),
                fontSize = 13.sp,
                color = colorResource(id = R.color.gateway_body_gray),
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorResource(id = R.color.gateway_pill),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HeadsetMic,
                        contentDescription = null,
                        tint = colorResource(id = R.color.gateway_navy),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.help_api_key_contact),
                        fontSize = 13.sp,
                        color = colorResource(id = R.color.gateway_navy)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            HelpSheetHeader(
                icon = Icons.Default.Smartphone,
                title = stringResource(id = R.string.help_sim_title),
                description = stringResource(id = R.string.help_sim_desc)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HelpNote(
                icon = Icons.Default.Sync,
                text = stringResource(id = R.string.help_sim_note_consistency)
            )
            HelpNote(
                icon = Icons.Default.AttachMoney,
                text = stringResource(id = R.string.help_sim_note_balance)
            )
            HelpNote(
                icon = Icons.Default.SignalCellularAlt,
                text = stringResource(id = R.string.help_sim_note_signal)
            )
        }
    }
}

@Composable
private fun HelpSheetHeader(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colorResource(id = R.color.gateway_navy)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(id = R.color.gateway_navy)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = description,
        fontSize = 13.sp,
        color = colorResource(id = R.color.gateway_body_gray),
        lineHeight = 19.sp
    )
}

@Composable
private fun HelpNote(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorResource(id = R.color.gateway_navy),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text.removePrefix("• "),
            fontSize = 13.sp,
            color = colorResource(id = R.color.gateway_body_gray),
            lineHeight = 19.sp
        )
    }
}
