package com.httpsms.background

import android.content.Context
import com.httpsms.core.Constants
import com.httpsms.core.DeviceStatus
import com.httpsms.core.Settings
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi
import timber.log.Timber

/**
 * Sends the device's heartbeat to the backend.
 *
 * Anything the app reports to the backend - a message status, a received message,
 * an FCM trigger, a login - travels with a fresh heartbeat, so the backend's view
 * of the device (last seen, active SIMs, battery) is never stale by the time it
 * handles the report.
 *
 * Best effort: failures are only logged and never surface to the caller, so a
 * heartbeat can never fail - or delay - the work it accompanies. Callers decide
 * the thread; this performs a blocking network call.
 */
internal object HeartbeatReporter {

    fun send(context: Context, reason: String) {
        if (!Settings.isLoggedIn(context)) {
            Timber.d("[$reason] not logged in, skipping heartbeat")
            return
        }

        val phoneNumbers = activePhoneNumbers(context)
        if (phoneNumbers.isEmpty()) {
            Timber.d("[$reason] no active SIM, skipping heartbeat")
            return
        }

        when (val result = SmsGatewayApi.from(context).storeHeartbeat(DeviceStatus.read(context), phoneNumbers)) {
            is ApiResult.Success -> {
                Settings.setHeartbeatTimestampAsync(context, System.currentTimeMillis())
                Timber.d("[$reason] heartbeat sent")
            }
            is ApiResult.Failure -> Timber.w("[$reason] could not send heartbeat: [${result.message}]")
        }
    }

    /** The lines the backend should consider active, per the active flag on each SIM. */
    private fun activePhoneNumbers(context: Context): List<String> {
        val phoneNumbers = mutableListOf<String>()
        if (Settings.getActiveStatus(context, Constants.SIM1)) {
            phoneNumbers.add(Settings.getSIM1PhoneNumber(context))
        }
        if (Settings.getActiveStatus(context, Constants.SIM2)) {
            phoneNumbers.add(Settings.getSIM2PhoneNumber(context))
        }
        return phoneNumbers
    }
}
