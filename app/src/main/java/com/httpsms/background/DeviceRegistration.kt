package com.httpsms.background

import android.content.Context
import com.httpsms.core.Constants
import com.httpsms.core.Settings
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Registers this device's FCM token against the backend's phone lines - the one place
 * that knows which lines to register, how often, and what a success updates.
 *
 * `PUT /v1/phones/fcm-token` is the app's only "login" mechanism (`docs/API-CONTRACT.md`
 * §2), so this module also owns the credential's side effects: the account `user_id`
 * and the timestamp the daily refresh is measured from.
 *
 * Callers say *which* line changed or that the token rotated; the policy lives here.
 */
internal object DeviceRegistration {

    /** How long a registration stays fresh before the automatic refresh runs again. */
    private const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000

    /**
     * Re-registers every configured line when the daily interval has elapsed.
     * Fire-and-forget: callers are screen lifecycles that must not wait on the network.
     */
    fun refreshAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refresh(appContext) }
    }

    /**
     * Blocking form of [refreshAsync] - registers the configured lines when due.
     * Returns true when every line that needed registering was accepted.
     */
    fun refresh(context: Context): Boolean {
        if (!Settings.isLoggedIn(context)) {
            Timber.w("not logged in, skipping FCM token refresh")
            return false
        }

        val token = Settings.getFcmToken(context)
        if (token.isNullOrEmpty()) {
            Timber.w("no FCM token stored, skipping refresh")
            return false
        }

        val elapsed = System.currentTimeMillis() - Settings.getFcmTokenLastUpdateTimestamp(context)
        if (elapsed < REFRESH_INTERVAL_MS) {
            Timber.d("token refreshed [$elapsed]ms ago, not due for another [${REFRESH_INTERVAL_MS - elapsed}]ms")
            return false
        }

        return registerLines(context, token)
    }

    /**
     * Stores a rotated token and re-registers the configured lines with it, so routing
     * follows the new token immediately.
     */
    fun onTokenRefreshedAsync(context: Context, token: String) {
        val appContext = context.applicationContext
        Settings.setFcmTokenAsync(appContext, token)

        scope.launch {
            if (!Settings.isLoggedIn(appContext)) {
                Timber.w("not logged in, storing the token without registering it")
                return@launch
            }
            registerLines(appContext, token)
        }
    }

    /**
     * Registers one line immediately - the user just picked it, so routing must follow
     * without waiting for the daily refresh.
     */
    fun registerLineAsync(context: Context, phoneNumber: String, sim: String) {
        val appContext = context.applicationContext

        if (!Settings.isLoggedIn(appContext)) {
            Timber.w("not logged in, skipping FCM token registration for [$sim]")
            return
        }

        val token = Settings.getFcmToken(appContext)
        if (token.isNullOrEmpty()) {
            Timber.w("no FCM token stored, skipping registration for [$sim]")
            return
        }

        if (phoneNumber.isBlank()) {
            Timber.w("no phone number stored for [$sim], skipping registration")
            return
        }

        scope.launch { register(appContext, phoneNumber, sim, token) }
    }

    private fun registerLines(context: Context, token: String): Boolean {
        var allRegistered = true
        for ((sim, phoneNumber) in configuredLines(context)) {
            allRegistered = register(context, phoneNumber, sim, token) && allRegistered
        }
        return allRegistered
    }

    /**
     * The lines this device registers: SIM1 always, SIM2 only on a device that has one.
     * A line with no stored number has nothing to register.
     */
    private fun configuredLines(context: Context): List<Pair<String, String>> {
        val lines = mutableListOf(Constants.SIM1 to Settings.getSIM1PhoneNumber(context))
        if (Settings.isDualSIM(context)) {
            lines.add(Constants.SIM2 to Settings.getSIM2PhoneNumber(context))
        }
        return lines.filter { (_, phoneNumber) -> phoneNumber.isNotBlank() }
    }

    private fun register(context: Context, phoneNumber: String, sim: String, token: String): Boolean {
        return when (val result = SmsGatewayApi.from(context).updateFcmToken(phoneNumber, sim, token)) {
            is ApiResult.Success -> {
                Settings.setUserID(context, result.value.userID)
                Settings.setFcmTokenLastUpdateTimestampAsync(context, System.currentTimeMillis())
                Timber.i("[$sim] registered [$phoneNumber] with the backend")
                true
            }
            is ApiResult.Failure -> {
                Timber.e("[$sim] could not register [$phoneNumber]: [${result.message}]")
                false
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
