package com.httpsms.sms

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.MultimediaMessagePdu
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.RetrieveConf
import com.httpsms.background.HeartbeatReporter
import com.httpsms.background.ReceivedMessageInput
import com.httpsms.background.WorkQueue
import com.httpsms.core.Constants
import com.httpsms.core.Settings
import com.httpsms.core.SimLines
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi
import com.httpsms.data.model.ReceivedMessageRequest
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A message that arrived on the device, decoded and ready to forward. */
internal data class IncomingMessage(
    val from: String,
    val to: String,
    val sim: String,
    val content: String,
    val attachments: List<String>? = null,
)

/**
 * The inbound half of the gateway.
 *
 * Turns an SMS/MMS broadcast into a [IncomingMessage] and hands it to the durable
 * retry path, so [ReceivedReceiver] is only the platform adapter. Everything the
 * inbound path decides lives here: which line it arrived on, whether that line takes
 * incoming messages, and what timestamp to stamp.
 */
internal object IncomingMessageHandler {

    private const val EXTRA_SLOT_INDEX = "android.telephony.extra.SLOT_INDEX"
    private const val EXTRA_MMS_DATA = "data"

    fun handle(context: Context, intent: Intent) {
        val message = decode(context, intent) ?: return

        if (!Settings.isIncomingMessageEnabled(context, message.sim)) {
            Timber.w("[${message.sim}] is not active for incoming messages")
            return
        }

        forward(context, message)
    }

    private fun decode(context: Context, intent: Intent): IncomingMessage? {
        return when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> decodeSms(context, intent)
            Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION -> decodeMms(context, intent)
            else -> {
                Timber.e("received invalid intent with action [${intent.action}]")
                null
            }
        }
    }

    /** An SMS arrives as N parts; the sender is the last one seen and the body the concatenation. */
    private fun decodeSms(context: Context, intent: Intent): IncomingMessage {
        var sender = ""
        var body = ""

        for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            sender = smsMessage.displayOriginatingAddress
            body += smsMessage.messageBody
        }

        return message(context, intent, sender, body, null)
    }

    private fun decodeMms(context: Context, intent: Intent): IncomingMessage? {
        val pushData = intent.getByteArrayExtra(EXTRA_MMS_DATA) ?: return null
        val pdu = PduParser(pushData, true).parse() ?: return null

        if (pdu !is MultimediaMessagePdu) {
            Timber.d("received PDU is not a MultimediaMessagePdu, ignoring")
            return null
        }

        val from = pdu.from?.string ?: ""
        var content = ""
        val attachmentFiles = mutableListOf<String>()

        // A RetrieveConf carries the actual message body; other PDU types do not.
        if (pdu is RetrieveConf) {
            val body = pdu.body
            if (body != null) {
                for (i in 0 until body.partsNum) {
                    val part = body.getPart(i)
                    val partData = part.data ?: continue
                    val contentType = String(part.contentType ?: "application/octet-stream".toByteArray())

                    if (contentType.startsWith("text/plain")) {
                        content += String(partData, Charset.forName(CharacterSets.getMimeName(part.charset)))
                    } else {
                        // Attachments are held as "path|contentType|fileName" for the worker.
                        val fileName = String(part.name ?: part.contentLocation ?: part.contentId ?: "attachment_$i".toByteArray())
                        val tempFile = File(context.cacheDir, "received_mms_${System.currentTimeMillis()}_$i")
                        FileOutputStream(tempFile).use { it.write(partData) }
                        attachmentFiles.add("${tempFile.absolutePath}|$contentType|$fileName")
                    }
                }
            }
        } else {
            Timber.d("received PDU is of type [${pdu.javaClass.simpleName}], body extraction not implemented")
        }

        return message(context, intent, from, content, attachmentFiles)
    }

    /** Which line it arrived on, and the number the user configured for that line. */
    private fun message(
        context: Context,
        intent: Intent,
        from: String,
        content: String,
        attachments: List<String>?
    ): IncomingMessage {
        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, 0)
        val sim = SimLines.lineForSlot(context, slotIndex)
        val owner = if (sim == Constants.SIM2) Settings.getSIM2PhoneNumber(context) else Settings.getSIM1PhoneNumber(context)

        return IncomingMessage(from = from, to = owner, sim = sim, content = content, attachments = attachments)
    }

    private fun forward(context: Context, message: IncomingMessage) {
        if (!Settings.isLoggedIn(context)) {
            Timber.w("[${message.sim}] user is not logged in")
            return
        }

        if (!Settings.getActiveStatus(context, message.sim)) {
            Timber.w("[${message.sim}] user is not active")
            return
        }

        WorkQueue.forwardReceivedMessage(
            context,
            ReceivedMessageInput(
                from = message.from,
                to = message.to,
                sim = message.sim,
                content = message.content,
                timestamp = DateTimeFormatter.ofPattern(Constants.TIMESTAMP_PATTERN)
                    .format(ZonedDateTime.now(ZoneOffset.UTC))
                    .replace("+", "Z"),
                attachments = message.attachments?.toTypedArray()
            )
        )
    }
}

/**
 * Posts one forwarded message, best-effort refreshing the heartbeat alongside it.
 * A failed request retries so an inbound message is not lost.
 */
internal class ForwardReceivedWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        // Forwarding a received message is a status report too: refresh the
        // heartbeat alongside it.
        HeartbeatReporter.sendAsync(applicationContext, "received message")

        val received = WorkQueue.Input.receivedMessage(inputData)
        if (received == null) {
            Timber.e("cannot read the received message for work [${id}]")
            return Result.failure()
        }

        Timber.i("[${received.sim}] forwarding received message from [${received.from}] to [${received.to}]")

        val base64Attachments = received.attachments?.mapNotNull { entry ->
            val path = entry.split("|")[0]
            val file = File(path)
            if (file.exists()) {
                android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
            } else {
                null
            }
        }

        val result = SmsGatewayApi.from(applicationContext).receive(
            ReceivedMessageRequest(
                sim = received.sim,
                fromNumber = received.from,
                toNumber = received.to,
                content = received.content,
                encrypted = false,
                attachments = if (base64Attachments.isNullOrEmpty()) null else base64Attachments,
                receivedTimestamp = received.timestamp
            )
        )

        // Cleanup temp files
        received.attachments?.forEach {
            val path = it.split("|")[0]
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }

        return when (result) {
            is ApiResult.Success -> Result.success()
            is ApiResult.Failure -> Result.retry()
        }
    }
}
