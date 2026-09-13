package com.httpsms.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.httpsms.R
import com.httpsms.sms.SimInfo

/**
 * A single-select SIM picker: a read-only text field summarizing the currently
 * chosen SIM, which opens a bottom sheet of tappable SIM cards when tapped.
 *
 * Fully data-driven so it can be reused on the login screen and the main screen:
 * - [sims] is the list of detected/registered SIMs to show
 * - [selectedSimIndex] is the currently chosen SIM (index into [sims])
 * - [numberForIndex] returns the phone number known for a SIM index
 *   (e.g. SIM1/SIM2 storage), which may be empty when the SIM hides its number
 * - [onSimSelected] fires when the user taps a SIM that already has a number
 * - [onSimNumberConfirmed] fires when the user typed a number for a SIM that
 *   had none (index + entered number)
 */
@Composable
fun SimCardSelector(
    sims: List<SimInfo>,
    selectedSimIndex: Int,
    numberForIndex: (Int) -> String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    onSimSelected: (Int) -> Unit,
    onSimNumberConfirmed: (Int, String) -> Unit,
    onHelpClick: (() -> Unit)? = null,
    onOpenClick: (() -> Unit)? = null
) {
    var showSimSheet by remember { mutableStateOf(false) }

    val summary = if (sims.isEmpty()) {
        stringResource(id = R.string.sim_card_no_sim)
    } else {
        val index = selectedSimIndex.coerceIn(0, sims.lastIndex)
        val number = numberForIndex(index)
        val label = simDisplayLabel(stringResource(id = R.string.sim_card_sim_label, index + 1), sims[index])
        if (number.isBlank()) "$label · ${stringResource(id = R.string.sim_card_number_unreadable)}"
        else "$label · $number"
    }

    val selectedNumberBlank = sims.isNotEmpty() &&
        selectedSimIndex in sims.indices &&
        numberForIndex(selectedSimIndex).isBlank()
    val effectiveSupportingText = supportingText
        ?: if (enabled && selectedNumberBlank) stringResource(id = R.string.sim_card_tap_to_enter) else null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(id = R.string.sim_card_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onHelpClick != null) {
                IconButton(onClick = onHelpClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(id = R.string.sim_card_help_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = summary,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                isError = isError,
                supportingText = effectiveSupportingText?.let { { Text(it) } }
            )
            // Make the whole field open the sheet.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = sims.isNotEmpty() && enabled) {
                        onOpenClick?.invoke()
                        showSimSheet = true
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.SimCard,
                    contentDescription = stringResource(id = R.string.sim_card_select_sim_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    }

    if (showSimSheet) {
        SimSelectionSheet(
            sims = sims,
            selectedSimIndex = selectedSimIndex,
            onSimSelected = { index ->
                onSimSelected(index)
                showSimSheet = false
            },
            onSimNumberConfirmed = { index, number ->
                onSimNumberConfirmed(index, number)
                showSimSheet = false
            },
            onDismiss = { showSimSheet = false }
        )
    }
}

private fun simDisplayLabel(simPrefix: String, sim: SimInfo): String {
    return if (sim.displayName.isBlank()) simPrefix else "$simPrefix · ${sim.displayName}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimSelectionSheet(
    sims: List<SimInfo>,
    selectedSimIndex: Int,
    onSimSelected: (Int) -> Unit,
    onSimNumberConfirmed: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var editingIndex by remember { mutableStateOf(-1) }
    var draftNumber by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorResource(id = R.color.white)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
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
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(id = R.string.sim_sheet_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(id = R.color.gateway_navy)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (sims.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.sim_card_no_sim),
                    fontSize = 13.sp,
                    color = colorResource(id = R.color.gateway_body_gray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            } else {
                sims.forEachIndexed { index, sim ->
                    val selected = index == selectedSimIndex

                    Card(
                        onClick = {
                            if (sim.number.isNotBlank()) {
                                onSimSelected(index)
                            } else {
                                editingIndex = index
                                draftNumber = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.white)),
                        border = BorderStroke(1.dp, if (selected) colorResource(id = R.color.gateway_navy) else colorResource(id = R.color.gateway_border))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = simDisplayLabel(stringResource(id = R.string.sim_card_sim_label, index + 1), sim),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorResource(id = R.color.gateway_navy)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (sim.number.isBlank()) {
                                        Text(
                                            text = stringResource(id = R.string.sim_card_number_unreadable),
                                            fontSize = 12.sp,
                                            color = colorResource(id = R.color.gateway_body_gray)
                                        )
                                    } else {
                                        Text(
                                            text = sim.number,
                                            fontSize = 13.sp,
                                            color = colorResource(id = R.color.gateway_body_gray)
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = stringResource(id = R.string.sim_card_selected_description),
                                        tint = colorResource(id = R.color.gateway_navy)
                                    )
                                }
                            }

                            if (editingIndex == index) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = draftNumber,
                                    onValueChange = { draftNumber = it },
                                    label = { Text(stringResource(id = R.string.sim_card_number_label)) },
                                    placeholder = { Text(stringResource(id = R.string.sim_card_number_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { if (draftNumber.isNotBlank()) onSimNumberConfirmed(index, draftNumber.trim()) },
                                    enabled = draftNumber.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.gateway_navy))
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.sim_card_number_use).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
