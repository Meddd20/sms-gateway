package com.httpsms.sms

import android.content.Context
import com.httpsms.core.Constants
import com.httpsms.core.DeviceStatus
import com.httpsms.core.Settings
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi
import timber.log.Timber

/**
 * Reports a message's status (SENT / DELIVERED / FAILED) to the backend.
 *
 * Every report is paired with a fresh heartbeat: a send attempt is exactly the
 * moment the backend's view of the device (last seen, active SIMs, battery) goes
 * stale, so the two travel together. The heartbeat is best effort - it never
 * changes the event result the caller retries on.
 */
internal object MessageStatusReporter {

    fun sent(context: Context, messageId: String, timestamp: String): ApiResult<Unit> {
        return report(context, "SENT", messageId) { api -> api.sendSentEvent(messageId, timestamp) }
    }

    fun delivered(context: Context, messageId: String, timestamp: String): ApiResult<Unit> {
        return report(context, "DELIVERED", messageId) { api -> api.sendDeliveredEvent(messageId, timestamp) }
    }

    fun failed(context: Context, messageId: String, timestamp: String, reason: String): ApiResult<Unit> {
        return report(context, "FAILED", messageId) { api -> api.sendFailedEvent(messageId, timestamp, reason) }
    }

    private fun report(
        context: Context,
        event: String,
        messageId: String,
        send: (SmsGatewayApi) -> ApiResult<Unit>
    ): ApiResult<Unit> {
        val api = SmsGatewayApi.from(context)
        val result = send(api)

        refreshHeartbeat(context, api, event, messageId)

        return result
    }

    private fun refreshHeartbeat(context: Context, api: SmsGatewayApi, event: String, messageId: String) {
        val phoneNumbers = activePhoneNumbers(context)
        if (phoneNumbers.isEmpty()) {
            Timber.d("[$event] no active SIM, skipping the heartbeat for message [$messageId]")
            return
        }

        when (val heartbeat = api.storeHeartbeat(DeviceStatus.read(context), phoneNumbers)) {
            is ApiResult.Success -> Timber.d("[$event] heartbeat refreshed alongside message [$messageId]")
            is ApiResult.Failure -> Timber.w("[$event] could not refresh heartbeat for message [$messageId]: [${heartbeat.message}]")
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
