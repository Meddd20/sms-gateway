package com.httpsms.data.api

/**
 * Every backend endpoint path in one place.
 *
 * Paths only - the host comes from configuration (`BuildConfig.SERVER_URL`,
 * fed by local.properties).
 */
object ApiEndpoints {

    /** GET: fetch the full message an FCM push pointed at. */
    const val OUTSTANDING_MESSAGES = "messages/outstanding"

    fun outstandingMessage(messageId: String): String {
        return "$OUTSTANDING_MESSAGES?message_id=$messageId"
    }

    const val RECEIVE_MESSAGE = "messages/receive"

    const val HEARTBEATS = "heartbeats"

    fun messageEvents(messageId: String): String {
        return "messages/$messageId/events"
    }

    const val FCM_TOKEN = "phones/fcm-token"

    /** POST: exchange the login pairing code for this device. */
    const val GATEWAY_PAIR = "gateway/pair"
}
