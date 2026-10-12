package com.httpsms.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.httpsms.background.DeviceRegistration
import com.httpsms.background.HeartbeatReporter
import com.httpsms.core.Constants
import com.httpsms.core.DeviceStatus
import com.httpsms.core.PermissionGate
import com.httpsms.core.Settings
import com.httpsms.core.SimLines
import com.httpsms.core.SimInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class MainUiState(
    val phoneNumberSIM1: String = "",
    val isActiveSIM1: Boolean = false,
    val phoneNumberSIM2: String = "",
    val isActiveSIM2: Boolean = false,
    val isDualSim: Boolean = false,
    val sims: List<SimInfo> = emptyList(),
    val selectedSimIndex: Int = -1,
    val lastHeartbeatTime: String = "--",
    val isSmsPermissionGranted: Boolean = true,
    val isBatteryOptimizationDisabled: Boolean = true,
    val isOemAutostartAcknowledged: Boolean = false,
    val hasOpenedAutostartSettings: Boolean = false,
    val isHeartbeatLoading: Boolean = false,
    val errorMessage: String? = null,
    val appVersion: String = ""
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    fun initialize(context: Context, appVersion: String) {
        updateState(context, appVersion)
    }

    fun updateState(context: Context, appVersion: String) {
        val isDualSim = Settings.isDualSIM(context)
        val phone1 = Settings.getSIM1PhoneNumber(context) ?: ""
        val active1 = Settings.getActiveStatus(context, Constants.SIM1)
        val phone2 = Settings.getSIM2PhoneNumber(context) ?: ""
        val active2 = Settings.getActiveStatus(context, Constants.SIM2)
        
        val timestamp = Settings.getHeartbeatTimestamp(context)
        val lastHeartbeat = if (timestamp == 0L) {
            "--"
        } else {
            val timestampZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC)
            val localTime = timestampZdt.withZoneSameInstant(ZoneId.systemDefault())
            localTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }

        val allGranted = PermissionGate.messagingGranted(context)

        val batteryOptimized = DeviceStatus.batteryOptimizationDisabled(context)

        // SIMs shown in the picker: live subscriptions when readable (with carrier
        // names), otherwise fall back to the numbers registered in settings.
        val liveSims = SimLines.active(context)
        val sims = if (liveSims.isNotEmpty()) {
            liveSims
        } else {
            buildList {
                if (phone1.isNotEmpty()) add(SimInfo(subscriptionId = -1, slotIndex = 0, displayName = "", number = phone1))
                if (isDualSim && phone2.isNotEmpty()) add(SimInfo(subscriptionId = -1, slotIndex = 1, displayName = "", number = phone2))
            }
        }

        val selectedSimIndex = when {
            sims.isEmpty() -> -1
            isDualSim && active2 && !active1 && sims.size > 1 -> 1
            else -> 0
        }

        _uiState.value = _uiState.value.copy(
            phoneNumberSIM1 = phone1,
            isActiveSIM1 = active1,
            phoneNumberSIM2 = phone2,
            isActiveSIM2 = active2,
            isDualSim = isDualSim,
            sims = sims,
            selectedSimIndex = selectedSimIndex,
            lastHeartbeatTime = lastHeartbeat,
            isSmsPermissionGranted = allGranted,
            isBatteryOptimizationDisabled = batteryOptimized,
            isOemAutostartAcknowledged = Settings.isOemAutostartAcknowledged(context),
            hasOpenedAutostartSettings = Settings.isOemAutostartSettingsOpened(context),
            appVersion = appVersion
        )
    }

    fun acknowledgeOemAutostart(context: Context) {
        Settings.setOemAutostartAcknowledged(context, true)
        updateState(context, _uiState.value.appVersion)
    }

    /**
     * Recorded when the user is sent to the OEM autostart screen. The state of that
     * toggle cannot be read back, so this is the only precondition we can enforce
     * before letting them confirm the step.
     */
    fun markAutostartSettingsOpened(context: Context) {
        Settings.setOemAutostartSettingsOpened(context)
        updateState(context, _uiState.value.appVersion)
    }

    fun selectSim(context: Context, index: Int) {
        setActiveSim(context, index)
        registerSelection(context, index)
        updateState(context, _uiState.value.appVersion)
    }

    fun confirmSimNumber(context: Context, index: Int, number: String) {
        if (index == 1) Settings.setSIM2PhoneNumber(context, number)
        else Settings.setSIM1PhoneNumber(context, number)
        setActiveSim(context, index)
        registerSelection(context, index)
        updateState(context, _uiState.value.appVersion)
    }

    // Re-register the freshly picked SIM with the server so routing follows the
    // chosen number immediately, without requiring a full re-login.
    private fun registerSelection(context: Context, index: Int) {
        val sim = if (index == 1) Constants.SIM2 else Constants.SIM1
        val phoneNumber = if (index == 1) Settings.getSIM2PhoneNumber(context) else Settings.getSIM1PhoneNumber(context)
        DeviceRegistration.registerLineAsync(context, phoneNumber, sim)
    }

    private fun setActiveSim(context: Context, index: Int) {
        val selectedLabel = if (index == 1) Constants.SIM2 else Constants.SIM1
        val otherLabel = SimLines.otherLabel(selectedLabel)
        Settings.setActiveStatusAsync(context, true, selectedLabel)
        Settings.setActiveStatusAsync(context, false, otherLabel)
    }

    fun sendHeartbeat(context: Context, onComplete: (String?) -> Unit) {
        _uiState.value = _uiState.value.copy(isHeartbeatLoading = true, errorMessage = null)

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                HeartbeatReporter.send(context, "manual")
            }
            // Only a real failure carries something worth showing; "nothing to report"
            // and "not logged in" leave the screen clean.
            val result = (outcome as? HeartbeatReporter.Outcome.Failed)?.message

            _uiState.value = _uiState.value.copy(isHeartbeatLoading = false, errorMessage = result)
            updateState(context, _uiState.value.appVersion)
            onComplete(result)
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun logout(context: Context, onLogoutComplete: () -> Unit) {
        Settings.setApiKeyAsync(context, null)
        Settings.setSIM1PhoneNumber(context, null)
        Settings.setSIM2PhoneNumber(context, null)
        Settings.setActiveStatusAsync(context, true, Constants.SIM1)
        Settings.setActiveStatusAsync(context, true, Constants.SIM2)
        Settings.setIncomingActiveSIM1(context, true)
        Settings.setIncomingActiveSIM2(context, true)
        Settings.setUserID(context, null)
        Settings.setFcmTokenLastUpdateTimestampAsync(context, 0)
        onLogoutComplete()
    }
}
