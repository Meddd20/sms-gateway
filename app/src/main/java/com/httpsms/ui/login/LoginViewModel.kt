package com.httpsms.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.httpsms.BuildConfig
import com.httpsms.Constants
import com.httpsms.HttpSmsApiService
import com.httpsms.R
import com.httpsms.Settings
import com.httpsms.SimInfo
import com.httpsms.SmsManagerService
import com.httpsms.validators.PhoneNumberValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URI

data class LoginUiState(
    val apiKey: String = "",
    val phoneNumberSIM1: String = "",
    val phoneNumberSIM2: String = "",
    val isLoading: Boolean = false,
    val apiKeyError: String? = null,
    val phoneNumberSIM1Error: String? = null,
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

    fun onApiKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(apiKey = value, apiKeyError = null)
    }

    fun onSimSelected(index: Int) {
        _uiState.value = _uiState.value.copy(selectedSimIndex = index, phoneNumberSIM1Error = null)
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
        
        // Validation logic from LoginActivity.onLoginClick
        if (Settings.getFcmToken(context) == null) {
            onFcmTokenMissing()
            return
        }

        _uiState.value = currentState.copy(isLoading = true)

        viewModelScope.launch {
            val apiKey = currentState.apiKey.trim()
            val phone1 = currentState.phoneNumberSIM1.trim()
            val phone2 = currentState.phoneNumberSIM2.trim()
            val hasSim2Number = currentState.isDualSim && phone2.isNotEmpty()

            // Numbers come from the device, never typed. SIM 1 must be readable.
            if (!PhoneNumberValidator.isValidPhoneNumber(phone1, countryCode)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    phoneNumberSIM1Error = context.getString(R.string.login_sim1_number_unreadable)
                )
                return@launch
            }

            // SIM 2 is registered only when the device exposes a readable number for it.
            val registerSim2 = hasSim2Number && PhoneNumberValidator.isValidPhoneNumber(phone2, countryCode)
            if (hasSim2Number && !registerSim2) {
                Timber.w("SIM 2 number [$phone2] is not valid, SIM 2 will not be registered")
            }

            val e164Phone1 = PhoneNumberValidator.formatE164(phone1, countryCode)
            val e164Phone2 = if (registerSim2) PhoneNumberValidator.formatE164(phone2, countryCode) else ""

            val authResult = try {
                withContext(Dispatchers.IO) {
                    val service = HttpSmsApiService(apiKey, URI(BuildConfig.SERVER_URL))
                    val response1 = service.updateFcmToken(e164Phone1, Constants.SIM1, Settings.getFcmToken(context) ?: "")

                    if (response1.second != null || response1.third != null) {
                        return@withContext Pair(response1.second, response1.third)
                    }

                    if (registerSim2) {
                        val response2 = service.updateFcmToken(e164Phone2, Constants.SIM2, Settings.getFcmToken(context) ?: "")
                        return@withContext Pair(response2.second, response2.third)
                    }

                    Pair(null, null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Login error")
                Pair(null, context.getString(R.string.login_unexpected_error, e.message))
            }

            if (authResult.first != null) {
                _uiState.value = _uiState.value.copy(isLoading = false, apiKeyError = authResult.first)
                return@launch
            }

            if (authResult.second != null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            // Save settings
            Settings.setApiKeyAsync(context, apiKey)
            Settings.setSIM1PhoneNumber(context, e164Phone1)
            if (registerSim2) {
                Settings.setSIM2PhoneNumber(context, e164Phone2)
            }

            // The picked SIM is the default sending line; switch the others off.
            // The user can change this later from the home/settings screen.
            val selectedLabel = if (_uiState.value.selectedSimIndex == 1) Constants.SIM2 else Constants.SIM1
            val otherLabel = if (selectedLabel == Constants.SIM1) Constants.SIM2 else Constants.SIM1
            Settings.setActiveStatusAsync(context, true, selectedLabel)
            Settings.setActiveStatusAsync(context, false, otherLabel)

            _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
        }
    }
}
