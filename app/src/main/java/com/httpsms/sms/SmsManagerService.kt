package com.httpsms.sms

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.sevanam.androidsmsgateway.BuildConfig
import com.httpsms.core.Constants
import com.httpsms.core.SimLines
import timber.log.Timber

/**
 * Sends SMS/MMS through the line a message is addressed to.
 *
 * Stateless, so it is an object rather than something every caller constructs. Which
 * subscription a line maps to - and whether the device has one at all - is
 * [SimLines]' business; this module only picks the manager and runs the send.
 */
object SmsManagerService {
    private const val ACTION_SMS_SENT = "SMS_SENT"
    private const val ACTION_SMS_DELIVERED = "SMS_DELIVERED"

    fun sentAction(): String {
        return "${BuildConfig.APPLICATION_ID}.$ACTION_SMS_SENT"
    }

    fun deliveredAction(): String {
        return "${BuildConfig.APPLICATION_ID}.$ACTION_SMS_DELIVERED"
    }

    fun messageParts(context: Context, content: String): ArrayList<String> {
        return getSmsManager(context).divideMessage(content)
    }

    fun sendMultipartMessage(context: Context, contact: String, parts: ArrayList<String>, sim: String, sendIntents: ArrayList<PendingIntent>, deliveryIntents: ArrayList<PendingIntent>) {
        runSmsAction(context, sim) { smsManager ->
            smsManager.sendMultipartTextMessage(contact, null, parts, sendIntents, deliveryIntents)
        }
    }

    fun sendTextMessage(context: Context, contact: String, content: String, sim: String, sentIntent: PendingIntent, deliveryIntent: PendingIntent) {
        runSmsAction(context, sim) { smsManager ->
            smsManager.sendTextMessage(contact, null, content, sentIntent, deliveryIntent)
        }
    }

    // Wrapper for the smsManager's sendMultimediaMessage
    fun sendMultimediaMessage(context: Context, pduUri: Uri, sim: String, sentIntent: PendingIntent) {
        runSmsAction(context, sim) { smsManager ->
            smsManager.sendMultimediaMessage(context, pduUri, null, null, sentIntent)
        }
    }

    private fun runSmsAction(context: Context, sim: String, action: (SmsManager) -> Unit) {
        try {
            action(getSmsManager(context, sim))
        } catch (e: NullPointerException) {
            if (e.isEmergencyNumberBug()) {
                Timber.w(e, "Caught EmergencyNumber NPE, falling back to default SmsManager")
                action(getDefaultSmsManager(context))
            } else {
                throw e
            }
        }
    }

    private fun Throwable.isEmergencyNumberBug(): Boolean {
        return this is NullPointerException && this.message?.contains("EmergencyNumber.getNumber()") == true
    }

    @Suppress("DEPRECATION")
    private fun getDefaultSmsManager(context: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun getSmsManager(context: Context, sim: String = Constants.SIM1): SmsManager {
        val subscriptionId = SimLines.subscriptionFor(context, sim)
        Timber.d("sending on [$sim] with subscription [$subscriptionId]")

        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return getDefaultSmsManager(context)
        }

        return if (Build.VERSION.SDK_INT < 31) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        }
    }
}
