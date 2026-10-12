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
 * class's. A failed request retries so the event is not lost.
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
            is ApiResult.Failure -> Result.retry()
        }
    }
}
