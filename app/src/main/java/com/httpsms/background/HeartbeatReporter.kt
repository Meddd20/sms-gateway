package com.httpsms.background

import android.content.Context
import com.httpsms.core.Constants
import com.httpsms.core.DeviceStatus
import com.httpsms.core.Settings
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Sends the device's heartbeat to the backend - the one place that knows which lines
 * to report and how to report them.
 *
 * Callers describe *why* they are reporting; this module owns the rest: the login
 * gate, which SIM lines count as active, the device snapshot, the request and the
 * heartbeat timestamp. Nothing else in the app builds a heartbeat body.
 *
 * The active-line rule lives here so it cannot drift: a line is reported only while
 * its own active flag is set, which is what `docs/API-CONTRACT.md` §6 promises the
 * backend for `phone_numbers`.
 */
internal object HeartbeatReporter {

    /** What happened, so a caller that must react (a worker deciding on retry) can. */
    sealed interface Outcome {
        /** The backend accepted the heartbeat. */
        data object Sent : Outcome

        /** There is no credential, so there is nothing to report to. */
        data object NotLoggedIn : Outcome

        /** No line is active, so the backend would learn nothing new. */
        data object NoActiveLine : Outcome

        /** The request was attempted and did not succeed. */
        data class Failed(val message: String) : Outcome
    }

    /**
     * Blocking. Use from a worker or any caller that must know the result; the
     * caller picks the thread.
     *
     * Never throws. A status report must not be able to take down the work it
     * accompanies, and the platform reads it performs (reading the device's SIM
     * lines needs READ_PHONE_STATE) can raise `SecurityException` when a permission
     * is denied or revoked mid-flight. Those surface as [Outcome.Failed], which a
     * worker can retry, instead of as an uncaught exception.
     */
    fun send(context: Context, reason: String): Outcome {
        return try {
            attempt(context, reason)
        } catch (e: Exception) {
            Timber.w(e, "[$reason] heartbeat failed")
            Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun attempt(context: Context, reason: String): Outcome {
        if (!Settings.isLoggedIn(context)) {
            Timber.d("[$reason] not logged in, skipping heartbeat")
            return Outcome.NotLoggedIn
        }

        val phoneNumbers = activePhoneNumbers(context)
        if (phoneNumbers.isEmpty()) {
            Timber.d("[$reason] no active line, skipping heartbeat")
            return Outcome.NoActiveLine
        }

        return when (val result = SmsGatewayApi.from(context).storeHeartbeat(DeviceStatus.read(context), phoneNumbers)) {
            is ApiResult.Success -> {
                Settings.setHeartbeatTimestampAsync(context, System.currentTimeMillis())
                Timber.d("[$reason] heartbeat sent for [${phoneNumbers.joinToString()}]")
                Outcome.Sent
            }
            is ApiResult.Failure -> {
                Timber.w("[$reason] could not send heartbeat: [${result.message}]")
                Outcome.Failed(result.message)
            }
        }
    }

    /**
     * Fire-and-forget, for callers that must never wait or fail - a broadcast
     * receiver returning, a screen being finished. Detached from any caller's
     * lifetime on purpose, so the report still completes after they are gone.
     */
    fun sendAsync(context: Context, reason: String) {
        val appContext = context.applicationContext
        scope.launch {
            send(appContext, reason)
        }
    }

    /**
     * The lines the backend is told are active, per the active flag on each SIM.
     * A line whose active flag is off is omitted rather than reported.
     */
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

    /**
     * The handler is the second line of defence for [sendAsync]: an uncaught
     * exception in a launched coroutine reaches the thread's default handler and
     * would crash the process, which a background status report must never do.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Timber.w(error, "heartbeat failed in the background")
        }
    )
}
