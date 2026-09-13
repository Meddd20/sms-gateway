package com.httpsms.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.httpsms.core.Constants
import com.httpsms.core.Settings
import com.httpsms.data.api.ApiResult
import timber.log.Timber
import java.io.File

internal class SentReceiver : BroadcastReceiver() {
    companion object {
        // Documented as SmsManager.EXTRA_ERROR_CODE, but the constant is not part
        // of the public SDK - the extra key on the sent intent is "errorCode".
        private const val EXTRA_ERROR_CODE = "errorCode"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(Constants.KEY_MESSAGE_ID)
        cleanupPduFile(context, messageId)

        // Modem / network specific cause code, when the device exposes it.
        val errorCode = intent.getIntExtra(EXTRA_ERROR_CODE, 0)

        when (resultCode) {
            Activity.RESULT_OK -> handleMessageSent(context, messageId)
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> handleMessageFailed(context, messageId, reason("GENERIC_FAILURE", errorCode))
            SmsManager.RESULT_ERROR_NO_SERVICE -> handleMessageFailed(context, messageId, reason("NO_SERVICE", errorCode))
            SmsManager.RESULT_ERROR_NULL_PDU -> handleMessageFailed(context, messageId, reason("NULL_PDU", errorCode))
            SmsManager.RESULT_ERROR_RADIO_OFF -> handleMessageFailed(context, messageId, reason("RADIO_OFF", errorCode))
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> handleMessageFailed(context, messageId, reason("LIMIT_EXCEEDED", errorCode))
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> handleMessageFailed(context, messageId, reason("FDN_CHECK_FAILURE", errorCode))
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> handleMessageFailed(context, messageId, reason("SHORT_CODE_NOT_ALLOWED", errorCode))
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> handleMessageFailed(context, messageId, reason("SHORT_CODE_NEVER_ALLOWED", errorCode))
            else -> handleMessageFailed(context, messageId, reason("UNKNOWN:$resultCode", errorCode))
        }
    }

    private fun reason(name: String, errorCode: Int): String {
        return if (errorCode > 0) "$name (errorCode=$errorCode)" else name
    }

    private fun cleanupPduFile(context: Context, messageId: String?) {
                if (messageId == null) return

        try {
            val baseMessageId = messageId.substringBefore(".")
            val mmsDir = File(context.cacheDir, "mms_attachments")
            val pduFile = File(mmsDir, "pdu_$baseMessageId.dat")

            if (pduFile.exists()) {
                if (pduFile.delete()) {
                    Timber.d("Cleaned up PDU file for message ID [$baseMessageId]")
                } else {
                    Timber.w("Failed to delete PDU file for message ID [$baseMessageId]")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up PDU file for message ID [$messageId]")
        }
    }

    private fun handleMessageSent(context: Context, messageId: String?) {
        if (!Receiver.isValid(context, messageId)) {
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData: Data = workDataOf(
            Constants.KEY_MESSAGE_ID to messageId,
            Constants.KEY_MESSAGE_TIMESTAMP to Settings.currentTimestamp()
        )

        val work = OneTimeWorkRequest
            .Builder(SentMessageWorker::class.java)
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        WorkManager
            .getInstance(context)
            .enqueue(work)

        Timber.d("work enqueued with ID [${work.id}] for [SENT] message with ID [${messageId}]")
    }

    private fun handleMessageFailed(context: Context, messageId: String?, reason: String) {
        if (!Receiver.isValid(context, messageId)) {
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData: Data = workDataOf(
            Constants.KEY_MESSAGE_ID to messageId,
            Constants.KEY_MESSAGE_REASON to reason,
            Constants.KEY_MESSAGE_TIMESTAMP to Settings.currentTimestamp()
        )

        val work = OneTimeWorkRequest
            .Builder(FailedMessageWorker::class.java)
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        WorkManager
            .getInstance(context)
            .enqueue(work)

        Timber.d("work enqueued with ID [${work.id}] for [FAILED] message with ID [${messageId}]")
    }

    internal class SentMessageWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
        override fun doWork(): Result {
            val messageId = this.inputData.getString(Constants.KEY_MESSAGE_ID)
            val timestamp = this.inputData.getString(Constants.KEY_MESSAGE_TIMESTAMP)

            Timber.i("[${timestamp}] sending [SENT] message event with ID [${messageId}]")

            return when (MessageStatusReporter.sent(applicationContext, messageId!!, timestamp!!)) {
                is ApiResult.Success -> Result.success()
                is ApiResult.Failure -> Result.retry()
            }
        }
    }

    internal class FailedMessageWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
        override fun doWork(): Result {
            val messageId = this.inputData.getString(Constants.KEY_MESSAGE_ID)
            val reason = this.inputData.getString(Constants.KEY_MESSAGE_REASON)
            val timestamp = this.inputData.getString(Constants.KEY_MESSAGE_TIMESTAMP)

            Timber.i("[${timestamp}] sending [FAILED] message event with ID [${messageId}] and reason [$reason]")

            return when (MessageStatusReporter.failed(applicationContext, messageId!!, timestamp!!, reason!!)) {
                is ApiResult.Success -> Result.success()
                is ApiResult.Failure -> Result.retry()
            }
        }
    }
}
