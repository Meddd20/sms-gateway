package com.httpsms.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.httpsms.background.MessageEvent
import com.httpsms.background.MessageEventInput
import com.httpsms.background.WorkQueue
import com.httpsms.core.Constants
import com.httpsms.core.Settings

internal class DeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(Constants.KEY_MESSAGE_ID)

        when (resultCode) {
            Activity.RESULT_OK -> report(context, messageId, MessageEvent.DELIVERED)
            else -> report(context, messageId, MessageEvent.FAILED, "CANNOT BE DELIVERED")
        }
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
}
