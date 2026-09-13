package com.httpsms.ui.login

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.httpsms.BuildConfig
import com.httpsms.core.Constants
import com.httpsms.R
import com.httpsms.core.Settings
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi
import com.httpsms.data.model.GatewayPairRequest
import com.httpsms.sms.SimInfo
import com.httpsms.sms.SmsManagerService
import com.httpsms.util.PhoneNumberValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class LoginUiState(
    val pairingCode: String = "",
    val phoneNumberSIM1: String = "",
    val phoneNumberSIM2: String = "",
    val isLoading: Boolean = false,
    val pairingCodeError: String? = null,
    val phoneNumberSIM1Error: String? = null,
    val requestError: String? = null,
    val isDualSim: Boolean = false,
    val detectedSims: List<SimInfo> = emptyList(),
    val selectedSimIndex: Int = -1,
    val loginSuccess: Boolean = false
)

class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun initialize(context: Context) {
        loadSims(context)
    }

    fun loadSims(context: Context) {
        val sims = SmsManagerService.getActiveSims(context)

        Timber.d("Detected SIMs: ${sims.map { "${it.displayName} - ${it.number}" }}")

        _uiState.value = _uiState.value.copy(
            detectedSims = sims,
            isDualSim = sims.size > 1,
            phoneNumberSIM1 = sims.getOrNull(0)?.number.orEmpty(),
            phoneNumberSIM2 = sims.getOrNull(1)?.number.orEmpty(),
            // Default the sending line to the first SIM so it is never empty at init.
            selectedSimIndex = if (sims.isEmpty()) -1 else 0,
        )
    }

    fun onPairingCodeChange(value: String) {
        _uiState.value = _uiState.value.copy(pairingCode = value, pairingCodeError = null)
    }

    fun onSimSelected(index: Int) {
        _uiState.value = _uiState.value.copy(selectedSimIndex = index, phoneNumberSIM1Error = null)
    }

    fun dismissRequestError() {
        _uiState.value = _uiState.value.copy(requestError = null)
    }

    fun onSimNumberConfirmed(index: Int, number: String) {
        val updated = _uiState.value.detectedSims.mapIndexed { i, sim ->
            if (i == index) sim.copy(number = number) else sim
        }
        _uiState.value = _uiState.value.copy(
            detectedSims = updated,
            selectedSimIndex = index,
            phoneNumberSIM1 = updated.getOrNull(0)?.number.orEmpty(),
            phoneNumberSIM2 = updated.getOrNull(1)?.number.orEmpty(),
            phoneNumberSIM1Error = null,
        )
    }

    fun login(context: Context, countryCode: String, onGooglePlayServicesError: (String) -> Unit, onFcmTokenMissing: () -> Unit) {
        val currentState = _uiState.value

        if (Settings.getFcmToken(context) == null) {
            onFcmTokenMissing()
            return
        }

        _uiState.value = currentState.copy(isLoading = true)

        viewModelScope.launch {
            val pairingCode = currentState.pairingCode.trim()
            val selectedIndex = currentState.selectedSimIndex.coerceAtLeast(0)
            val selectedSim = currentState.detectedSims.getOrNull(selectedIndex)

            val numberSIM1 = currentState.phoneNumberSIM1.trim()
            val numberSIM2 = currentState.phoneNumberSIM2.trim()
            val selectedNumber = if (selectedIndex == 1) numberSIM2 else numberSIM1

            // Numbers come from the device, never typed. The selected SIM must be readable.
            if (!PhoneNumberValidator.isValidPhoneNumber(selectedNumber, countryCode)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    phoneNumberSIM1Error = context.getString(R.string.login_sim1_number_unreadable)
                )
                return@launch
            }

            val e164Selected = PhoneNumberValidator.formatE164(selectedNumber, countryCode)
            val e164SIM1 = if (PhoneNumberValidator.isValidPhoneNumber(numberSIM1, countryCode)) {
                PhoneNumberValidator.formatE164(numberSIM1, countryCode)
            } else ""
            val e164SIM2 = if (currentState.isDualSim && PhoneNumberValidator.isValidPhoneNumber(numberSIM2, countryCode)) {
                PhoneNumberValidator.formatE164(numberSIM2, countryCode)
            } else ""

            val pairRequest = GatewayPairRequest(
                pairingCode = pairingCode,
                fcmToken = Settings.getFcmToken(context) ?: "",
                phoneNumber = e164Selected,
                simSlot = (selectedSim?.slotIndex ?: 0) + 1,
                simOperator = selectedSim?.displayName.orEmpty(),
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                deviceModel = Build.MODEL,
                appVersion = BuildConfig.VERSION_NAME
            )

            val pairResult = try {
                withContext(Dispatchers.IO) {
                    SmsGatewayApi.from(context).pair(pairRequest)
                }
            } catch (e: Exception) {
                Timber.e(e, "Login error")
                ApiResult.Failure(context.getString(R.string.login_unexpected_error, e.message))
            }

            val pairData = when (pairResult) {
                is ApiResult.Success -> pairResult.value
                is ApiResult.Failure -> {
                    // Report the message the request itself returned (the backend's
                    // own, or the transport failure) instead of a generic string.
                    _uiState.value = _uiState.value.copy(isLoading = false, requestError = pairResult.message)
                    return@launch
                }
            }

            // The device token returned by the pairing is the credential, stored
            // where the API key used to live so isLoggedIn keeps working.
            val credential = pairData.deviceToken ?: pairData.token ?: pairingCode
            Settings.setApiKeyAsync(context, credential)
            if (e164SIM1.isNotEmpty()) {
                Settings.setSIM1PhoneNumber(context, e164SIM1)
            }
            if (e164SIM2.isNotEmpty()) {
                Settings.setSIM2PhoneNumber(context, e164SIM2)
            }

            // The picked SIM is the default sending line; switch the others off.
            // The user can change this later from the home/settings screen.
            val selectedLabel = if (currentState.selectedSimIndex == 1) Constants.SIM2 else Constants.SIM1
            val otherLabel = if (selectedLabel == Constants.SIM1) Constants.SIM2 else Constants.SIM1
            Settings.setActiveStatusAsync(context, true, selectedLabel)
            Settings.setActiveStatusAsync(context, false, otherLabel)

            _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
        }
    }

}
