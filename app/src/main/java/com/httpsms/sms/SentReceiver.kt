package com.httpsms.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.httpsms.background.MessageEvent
import com.httpsms.background.MessageEventInput
import com.httpsms.background.WorkQueue
import com.httpsms.core.Constants
import com.httpsms.core.Settings
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
            Activity.RESULT_OK -> report(context, messageId, MessageEvent.SENT)
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> report(context, messageId, MessageEvent.FAILED, reason("GENERIC_FAILURE", errorCode))
            SmsManager.RESULT_ERROR_NO_SERVICE -> report(context, messageId, MessageEvent.FAILED, reason("NO_SERVICE", errorCode))
            SmsManager.RESULT_ERROR_NULL_PDU -> report(context, messageId, MessageEvent.FAILED, reason("NULL_PDU", errorCode))
            SmsManager.RESULT_ERROR_RADIO_OFF -> report(context, messageId, MessageEvent.FAILED, reason("RADIO_OFF", errorCode))
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> report(context, messageId, MessageEvent.FAILED, reason("LIMIT_EXCEEDED", errorCode))
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> report(context, messageId, MessageEvent.FAILED, reason("FDN_CHECK_FAILURE", errorCode))
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> report(context, messageId, MessageEvent.FAILED, reason("SHORT_CODE_NOT_ALLOWED", errorCode))
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> report(context, messageId, MessageEvent.FAILED, reason("SHORT_CODE_NEVER_ALLOWED", errorCode))
            else -> report(context, messageId, MessageEvent.FAILED, reason("UNKNOWN:$resultCode", errorCode))
        }
    }

    private fun reason(name: String, errorCode: Int): String {
        return if (errorCode > 0) "$name (errorCode=$errorCode)" else name
    }

    /**
     * Hands the outcome to the one enqueue module: this receiver knows the result
     * code and nothing about constraints, worker classes or input keys.
     */
    private fun report(context: Context, messageId: String?, event: MessageEvent, reason: String? = null) {
        if (!Receiver.isValid(context, messageId)) {
            return
        }

        WorkQueue.reportMessageEvent(
            context,
            MessageEventInput(
                messageId = messageId!!,
                timestamp = Settings.currentTimestamp(),
                event = event,
                reason = reason
            )
        )
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
}
