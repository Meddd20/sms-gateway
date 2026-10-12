package com.httpsms.sms

import android.content.Context
import com.httpsms.background.HeartbeatReporter
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi

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
        val result = send(SmsGatewayApi.from(context))

        // The report and the heartbeat travel together; the heartbeat module owns
        // which lines are announced and never affects the result being returned.
        HeartbeatReporter.send(context, "$event message $messageId")

        return result
    }
}
