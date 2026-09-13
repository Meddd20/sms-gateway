package com.httpsms.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.httpsms.core.Constants
import com.httpsms.core.DeviceStatus
import com.httpsms.core.Settings
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi
import com.httpsms.sms.SimInfo
import com.httpsms.sms.SmsManagerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
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

        val smsPermissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        val allGranted = smsPermissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOptimized = pm.isIgnoringBatteryOptimizations(context.packageName)

        // SIMs shown in the picker: live subscriptions when readable (with carrier
        // names), otherwise fall back to the numbers registered in settings.
        val liveSims = SmsManagerService.getActiveSims(context)
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
        if (!Settings.isLoggedIn(context)) {
            Timber.w("not logged in, skipping fcm token registration for the selected SIM")
            return
        }
        val fcmToken = Settings.getFcmToken(context) ?: return
        val sim = if (index == 1) Constants.SIM2 else Constants.SIM1
        val phoneNumber = if (index == 1) Settings.getSIM2PhoneNumber(context) else Settings.getSIM1PhoneNumber(context)
        if (phoneNumber.isBlank()) {
            Timber.w("no phone number stored for [$sim], skipping registration")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            when (val result = SmsGatewayApi.from(context).updateFcmToken(phoneNumber, sim, fcmToken)) {
                is ApiResult.Success -> {
                    Settings.setUserID(context, result.value.userID)
                    Timber.i("[$sim] fcm token registered for [$phoneNumber]")
                }
                is ApiResult.Failure -> Timber.e("[$sim] could not register fcm token: [${result.message}]")
            }
        }
    }

    private fun setActiveSim(context: Context, index: Int) {
        val selectedLabel = if (index == 1) Constants.SIM2 else Constants.SIM1
        val otherLabel = if (selectedLabel == Constants.SIM1) Constants.SIM2 else Constants.SIM1
        Settings.setActiveStatusAsync(context, true, selectedLabel)
        Settings.setActiveStatusAsync(context, false, otherLabel)
    }

    fun sendHeartbeat(context: Context, onComplete: (String?) -> Unit) {
        _uiState.value = _uiState.value.copy(isHeartbeatLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val phoneNumbers = mutableListOf<String>()
                phoneNumbers.add(Settings.getSIM1PhoneNumber(context))
                if (Settings.getActiveStatus(context, Constants.SIM2)) {
                    phoneNumbers.add(Settings.getSIM2PhoneNumber(context))
                }

                when (val heartbeat = SmsGatewayApi.from(context).storeHeartbeat(DeviceStatus.read(context), phoneNumbers)) {
                    // Success carries no message to show, so the screen stays clean.
                    is ApiResult.Success -> {
                        Settings.setHeartbeatTimestampAsync(context, System.currentTimeMillis())
                        null
                    }
                    is ApiResult.Failure -> heartbeat.message
                }
            }

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
        Settings.setEncryptionKey(context, null)
        Settings.setEncryptReceivedMessages(context, false)
        Settings.setFcmTokenLastUpdateTimestampAsync(context, 0)
        onLogoutComplete()
    }
}
