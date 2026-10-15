package com.httpsms.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.httpsms.core.Constants
import com.httpsms.sms.SendMessageWorker
import com.httpsms.sms.ForwardReceivedWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Which status a send attempt ended in. */
internal enum class MessageEvent(val wireName: String) {
    SENT("SENT"),
    DELIVERED("DELIVERED"),
    FAILED("FAILED")
}

/** A status report for one message, as the worker needs to read it back. */
internal data class MessageEventInput(
    val messageId: String,
    val timestamp: String,
    val event: MessageEvent,
    val reason: String? = null
)

/** A forwarded incoming message, as the worker needs to read it back. */
internal data class ReceivedMessageInput(
    val from: String,
    val to: String,
    val sim: String,
    val content: String,
    val timestamp: String,
    val attachments: Array<String>?
)

/**
 * The one place that hands work to WorkManager.
 *
 * Callers describe *what* should happen; this module owns *how* it gets there - the
 * network constraint, the one-time request, the unique-work policy, and the [Input]
 * encoding the workers read back. Nothing else calls WorkManager or builds these
 * jobs, so the enqueue recipe and the input keys exist once instead of at every
 * call site.
 */
internal object WorkQueue {

    private const val HEARTBEAT_TAG = "TAG_HEARTBEAT_WORKER"

    /** Fetch and send the message an FCM push pointed at; unique per message. */
    fun sendMessage(context: Context, messageId: String) {
        enqueue(
            context = context,
            worker = SendMessageWorker::class.java,
            input = Input.sendMessage(messageId),
            tag = messageId,
            uniqueName = "send_sms_$messageId"
        )
    }

    /**
     * The retry rhythm for a worker that reports to the backend: exponential
     * backoff starting at one minute (1, 2, 4, 8, 16... minutes), with no give-up.
     * An inbound message or an event report is never dropped by the app - the
     * exponential growth is what keeps a long outage from becoming a request
     * storm. WorkManager caps a single gap at 5 hours; once the curve reaches that
     * cap, the worker re-enqueues itself as a fresh request (see
     * [BACKOFF_RESET_AFTER_ATTEMPTS]) so the wait drops back to one minute instead
     * of settling at 5 hours forever.
     */
    private val MESSAGE_RETRY_BACKOFF: Pair<BackoffPolicy, Pair<Long, TimeUnit>> =
        BackoffPolicy.EXPONENTIAL to (1L to TimeUnit.MINUTES)

    /**
     * The run attempt at which the exponential curve is considered capped. With a
     * 1-minute initial delay the waits are 1, 2, 4 ... 256 minutes, and the next
     * one would be WorkManager's 5-hour maximum - so from this attempt on, the
     * worker hands its work to a fresh request and the curve starts over. One
     * full cycle is therefore roughly 1+2+...+256+300 ≈ 13.5 hours, with the
     * longest single wait being the one 5-hour gap.
     */
    internal const val BACKOFF_RESET_AFTER_ATTEMPTS = 10

    /** Report how a send attempt ended. */
    fun reportMessageEvent(context: Context, input: MessageEventInput) {
        enqueue(
            context = context,
            worker = MessageEventWorker::class.java,
            input = Input.messageEvent(input),
            tag = "event_${input.messageId}",
            backoff = MESSAGE_RETRY_BACKOFF
        )
    }

    /** Forward an SMS/MMS that arrived on the device. */
    fun forwardReceivedMessage(context: Context, input: ReceivedMessageInput) {
        enqueue(
            context = context,
            worker = ForwardReceivedWorker::class.java,
            input = Input.receivedMessage(input),
            tag = "received_${input.sim}",
            backoff = MESSAGE_RETRY_BACKOFF
        )
    }

    /** The heartbeat, unique so repeated calls do not stack. */
    fun scheduleHeartbeat(context: Context) {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(Constants.HEARTBEAT_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkRequired())
            .addTag(HEARTBEAT_TAG)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(HEARTBEAT_TAG, ExistingPeriodicWorkPolicy.KEEP, request)

        Timber.d("heartbeat worker scheduled with ID [${request.id}]")
    }

    private fun enqueue(
        context: Context,
        worker: Class<out ListenableWorker>,
        input: Data,
        tag: String? = null,
        uniqueName: String? = null,
        backoff: Pair<BackoffPolicy, Pair<Long, TimeUnit>> =
            BackoffPolicy.EXPONENTIAL to (30L to TimeUnit.SECONDS)
    ) {
        val builder = OneTimeWorkRequest.Builder(worker)
            .setConstraints(networkRequired())
            .setInputData(input)
            .setBackoffCriteria(backoff.first, backoff.second.first, backoff.second.second)

        if (tag != null) {
            builder.addTag(tag)
        }

        val request = builder.build()
        val workManager = WorkManager.getInstance(context)

        if (uniqueName != null) {
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        } else {
            workManager.enqueue(request)
        }

        Timber.d("work enqueued with ID [${request.id}] for [$worker]")
    }

    private fun networkRequired(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    /**
     * The typed wire between an enqueuer and its worker. Encoding and decoding sit
     * together so a producer cannot rename a key without its consumer noticing.
     */
    object Input {

        fun sendMessage(messageId: String): Data {
            return workDataOf(Constants.KEY_MESSAGE_ID to messageId)
        }

        fun messageEvent(input: MessageEventInput): Data {
            return workDataOf(
                Constants.KEY_MESSAGE_ID to input.messageId,
                Constants.KEY_MESSAGE_EVENT to input.event.wireName,
                Constants.KEY_MESSAGE_TIMESTAMP to input.timestamp,
                Constants.KEY_MESSAGE_REASON to input.reason
            )
        }

        fun receivedMessage(input: ReceivedMessageInput): Data {
            return workDataOf(
                Constants.KEY_MESSAGE_FROM to input.from,
                Constants.KEY_MESSAGE_TO to input.to,
                Constants.KEY_MESSAGE_SIM to input.sim,
                Constants.KEY_MESSAGE_CONTENT to input.content,
                Constants.KEY_MESSAGE_TIMESTAMP to input.timestamp,
                Constants.KEY_MESSAGE_ATTACHMENTS to input.attachments
            )
        }

        fun messageId(data: Data): String? {
            return data.getString(Constants.KEY_MESSAGE_ID)
        }

        fun messageEvent(data: Data): MessageEventInput? {
            val messageId = data.getString(Constants.KEY_MESSAGE_ID) ?: return null
            val timestamp = data.getString(Constants.KEY_MESSAGE_TIMESTAMP) ?: return null
            val event = MessageEvent.entries.firstOrNull { it.wireName == data.getString(Constants.KEY_MESSAGE_EVENT) } ?: return null
            return MessageEventInput(messageId, timestamp, event, data.getString(Constants.KEY_MESSAGE_REASON))
        }

        fun receivedMessage(data: Data): ReceivedMessageInput? {
            val from = data.getString(Constants.KEY_MESSAGE_FROM) ?: return null
            return ReceivedMessageInput(
                from = from,
                to = data.getString(Constants.KEY_MESSAGE_TO).orEmpty(),
                sim = data.getString(Constants.KEY_MESSAGE_SIM).orEmpty(),
                content = data.getString(Constants.KEY_MESSAGE_CONTENT).orEmpty(),
                timestamp = data.getString(Constants.KEY_MESSAGE_TIMESTAMP).orEmpty(),
                attachments = data.getStringArray(Constants.KEY_MESSAGE_ATTACHMENTS)
            )
        }
    }
}
