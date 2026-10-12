package com.httpsms.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The platform's entry point for inbound SMS/MMS.
 *
 * Deliberately only an adapter: both intents are handed straight to
 * [IncomingMessageHandler], which owns the decoding and the forwarding.
 */
class ReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        IncomingMessageHandler.handle(context, intent)
    }
}
