package com.httpsms.background

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.httpsms.data.api.ApiResult
import com.httpsms.sms.MessageStatusReporter
import timber.log.Timber

/**
 * Reports how one send attempt ended: SENT, DELIVERED or FAILED.
 *
 * One worker for all three, because they differ only in which report fires and
 * whether a reason travels with them - that is [MessageEvent]'s job to know, not a
 * class's. A failed request retries on the exponential backoff indefinitely, like
 * [ForwardReceivedWorker]; a 404 never reaches the retry path because the API layer
 * already treats "message deleted server-side" as done.
 */
internal class MessageEventWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val event = WorkQueue.Input.messageEvent(inputData)
        if (event == null) {
            Timber.e("cannot read the message event for work [${id}]")
            return Result.failure()
        }

        Timber.i("[${event.timestamp}] sending [${event.event.wireName}] event for message [${event.messageId}]")

        val result = when (event.event) {
            MessageEvent.SENT -> MessageStatusReporter.sent(applicationContext, event.messageId, event.timestamp)
            MessageEvent.DELIVERED -> MessageStatusReporter.delivered(applicationContext, event.messageId, event.timestamp)
            MessageEvent.FAILED -> MessageStatusReporter.failed(applicationContext, event.messageId, event.timestamp, event.reason.orEmpty())
        }

        return when (result) {
            is ApiResult.Success -> Result.success()
            is ApiResult.Failure -> {
                // Same sawtooth as ForwardReceivedWorker: at the 5-hour cap,
                // re-enqueue as a fresh request so the wait restarts at one minute.
                if (runAttemptCount >= WorkQueue.BACKOFF_RESET_AFTER_ATTEMPTS) {
                    WorkQueue.reportMessageEvent(applicationContext, event)
                    Timber.w(
                        "retry backoff reached the cap, restarting the curve for the " +
                            "[${event.event.wireName}] event of message [${event.messageId}]"
                    )
                    return Result.success()
                }

                Timber.e(
                    "event report failed (attempt [${runAttemptCount + 1}]), " +
                        "WorkManager will retry: [${result.message}]"
                )
                Result.retry()
            }
        }
    }
}
