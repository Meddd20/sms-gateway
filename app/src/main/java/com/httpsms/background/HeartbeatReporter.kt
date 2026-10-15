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
 *
 * Sends are rate limited to one POST per [Constants.HEARTBEAT_MIN_INTERVAL_MS]
 * (measured from the last *successful* send), because the piggyback triggers fire far
 * more often than the backend needs - a worker retrying every backoff step used to
 * fire a heartbeat per attempt.
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

        /** A heartbeat was sent less than one interval ago, so the backend's view is still fresh. */
        data object Skipped : Outcome

        /** The request was attempted and did not succeed. */
        data class Failed(val message: String) : Outcome
    }

    /**
     * Blocking. Use from a worker or any caller that must know the result; the
     * caller picks the thread.
     *
     * Rate limited: unless [force] is set, a heartbeat is only POSTed when the last
     * *successful automatic* one is at least [Constants.HEARTBEAT_MIN_INTERVAL_MS]
     * old. The many piggyback triggers (every FCM push, every event report, every
     * received message, every worker retry) would otherwise flood the backend with
     * near-identical status reports; the timestamp is only written on success, so a
     * failing heartbeat is never blocked from retrying.
     *
     * [force] is for explicit requests - the manual "Send Heartbeat" button and the
     * backend's FCM heartbeat ping. A forced send bypasses the window AND does not
     * write the timestamp, so it can never push the automatic cadence back.
     *
     * Never throws. A status report must not be able to take down the work it
     * accompanies, and the platform reads it performs (reading the device's SIM
     * lines needs READ_PHONE_STATE) can raise `SecurityException` when a permission
     * is denied or revoked mid-flight. Those surface as [Outcome.Failed], which a
     * worker can retry, instead of as an uncaught exception.
     */
    fun send(context: Context, reason: String, force: Boolean = false): Outcome {
        return try {
            attempt(context, reason, force)
        } catch (e: Exception) {
            Timber.w(e, "[$reason] heartbeat failed")
            Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun attempt(context: Context, reason: String, force: Boolean): Outcome {
        if (!Settings.isLoggedIn(context)) {
            Timber.d("[$reason] not logged in, skipping heartbeat")
            return Outcome.NotLoggedIn
        }

        val phoneNumbers = activePhoneNumbers(context)
        if (phoneNumbers.isEmpty()) {
            Timber.d("[$reason] no active line, skipping heartbeat")
            return Outcome.NoActiveLine
        }

        val elapsedMs = System.currentTimeMillis() - Settings.getHeartbeatTimestamp(context)
        if (!force && elapsedMs < Constants.HEARTBEAT_MIN_INTERVAL_MS) {
            Timber.d(
                "[$reason] last heartbeat was [${elapsedMs / 1000}]s ago, under the " +
                    "[${Constants.HEARTBEAT_MIN_INTERVAL_MS / 1000}]s limit, skipping"
            )
            return Outcome.Skipped
        }

        return when (val result = SmsGatewayApi.from(context).storeHeartbeat(DeviceStatus.read(context), phoneNumbers)) {
            is ApiResult.Success -> {
                // Only an automatic send moves the rate-limit window: a forced send
                // (manual button, backend ping) is an explicit request on top of the
                // cadence, and it must not push the next automatic heartbeat out by
                // a whole interval.
                if (!force) {
                    Settings.setHeartbeatTimestampAsync(context, System.currentTimeMillis())
                }
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
     *
     * [force] means an explicit request (the backend's FCM heartbeat ping), which
     * bypasses the rate limit and does not move the automatic window.
     */
    fun sendAsync(context: Context, reason: String, force: Boolean = false) {
        val appContext = context.applicationContext
        scope.launch {
            send(appContext, reason, force)
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
