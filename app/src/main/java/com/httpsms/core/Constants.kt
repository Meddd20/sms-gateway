package com.httpsms.core

class Constants {
    companion object {
        const val KEY_MESSAGE_ID = "KEY_MESSAGE_ID"
        const val KEY_MESSAGE_FROM = "KEY_MESSAGE_FROM"
        const val KEY_MESSAGE_TO = "KEY_MESSAGE_TO"
        const val KEY_MESSAGE_SIM = "KEY_MESSAGE_SIM"
        const val KEY_MESSAGE_CONTENT = "KEY_MESSAGE_CONTENT"
        const val KEY_MESSAGE_TIMESTAMP = "KEY_MESSAGE_TIMESTAMP"
        const val KEY_MESSAGE_REASON = "KEY_MESSAGE_REASON"
        const val KEY_MESSAGE_EVENT = "KEY_MESSAGE_EVENT"
        const val KEY_MESSAGE_ATTACHMENTS = "KEY_MESSAGE_ATTACHMENTS"


        const val KEY_HEARTBEAT_ID = "KEY_HEARTBEAT_ID"

        /**
         * The heartbeat cadence, shared by the periodic schedule (WorkQueue) and the
         * rate limit (HeartbeatReporter) so the two can never drift apart: the
         * periodic worker fires every [HEARTBEAT_INTERVAL_MINUTES], and no two
         * heartbeat POSTs may land closer than one interval apart.
         */
        const val HEARTBEAT_INTERVAL_MINUTES = 15L
        const val HEARTBEAT_MIN_INTERVAL_MS = HEARTBEAT_INTERVAL_MINUTES * 60 * 1000

        const val SIM1 = "SIM1"
        const val SIM2 = "SIM2"

        const val TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'000000'ZZZZZ"

        const val MAX_MMS_ATTACHMENT_SIZE: Long = (3L * 1024 * 1024) / 2
    }
}