package com.httpsms.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.SendReq
import com.sevanam.androidsmsgateway.BuildConfig
import com.httpsms.background.MessageEvent
import com.httpsms.background.MessageEventInput
import com.httpsms.background.WorkQueue
import com.httpsms.core.Constants
import com.httpsms.core.Settings
import com.httpsms.data.api.ApiResult
import com.httpsms.data.api.SmsGatewayApi
import com.httpsms.data.io.AttachmentDownloader
import com.httpsms.data.model.Message
import okhttp3.MediaType
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Sends one outstanding message: the whole outgoing path, from the backend fetch to
 * the SMS/MMS handoff and the status event.
 *
 * Live here rather than nested inside the FCM service, because this is the gateway's
 * send core - a push is only one of the ways it is triggered. Everything it touches
 * (fetch, decrypt, MMS assembly, multipart split, failure reporting) is in this file.
 */
    internal class SendMessageWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
        override fun doWork(): Result {
            if (!Settings.isLoggedIn(applicationContext)) {
                Timber.w("user is not logged in, stopping processing")
                return Result.failure()
            }

            val messageID = this.inputData.getString(Constants.KEY_MESSAGE_ID)
            if (messageID == null) {
                Timber.e("cannot get outstanding message for work [${this.id}]")
                return Result.failure()
            }

            if (Settings.isMessageProcessed(applicationContext, messageID)) {
                Timber.w("Message [$messageID] has already been processed on this device, ignoring duplicate request")
                return Result.success()
            }

            val message = getMessage(applicationContext, messageID) ?: return Result.failure()

            if (message.status != "outstanding") {
                Timber.w("Message [$messageID] status is [${message.status}], not [outstanding], skipping duplicate send")
                Settings.markMessageProcessed(applicationContext, messageID)
                return Result.success()
            }

            if (message.contact.isBlank()) {
                Timber.w("message contact is blank, stopping processing")
                handleFailed(applicationContext, messageID, "The contact phone number is empty.")
                return Result.failure()
            }

            if (!Settings.getActiveStatus(applicationContext, message.sim)) {
                Timber.w("[${message.sim}] SIM is not active, stopping processing")
                handleFailed(applicationContext, messageID, "Outgoing messages have been disabled on the mobile app")
                return Result.failure()
            }

            Settings.markMessageProcessed(applicationContext, messageID)
            Receiver.register(applicationContext)

            if (message.attachments != null && message.attachments.isNotEmpty()) {
                return handleMmsMessage(message)
            }

            val parts = getMessageParts(applicationContext, message)
            if (parts.size == 1) {
                return handleSingleMessage(message, parts.first())
            }
            return handleMultipartMessage(message, parts)
        }

        fun extractFileName(url: String, prefix: String, mimeType: String? = null): String {
            val fileName = url.substringAfterLast("/")
                .substringBefore("?")
                .takeIf { it.isNotBlank() && it.contains(".") }
                ?: run {
                    val extension = mimeType?.let { mime ->
                        val ext = mime.substringAfterLast("/")
                        if (ext.isNotBlank()) ".$ext" else ".bin"
                    } ?: ""
                    "attachment$extension"
                }

            return "${prefix}_$fileName"
        }

        private fun handleMmsMessage(message: Message): Result {
            Timber.d("Processing MMS for message ID [${message.id}]")

            val downloadedFiles = mutableListOf<Pair<File, MediaType>>()

            try {
                for ((index, attachment) in message.attachments!!.withIndex()) {
                    val file = AttachmentDownloader.download(applicationContext, attachment, message.id, index)
                    if (file.first == null || file.second == null) {
                        handleFailed(applicationContext, message.id, "Failed to download attachment or file size exceeded 1.5MB.")
                        return Result.failure()
                    }
                    downloadedFiles.add(Pair(file.first!!, file.second!!))
                }

                val sendReq = SendReq()

                val encodedContact = EncodedStringValue(message.contact)
                sendReq.to = arrayOf(encodedContact)

                val pduBody = PduBody()

                if (message.content.isNotEmpty()) {
                    val textPart = PduPart()
                    textPart.setCharset(CharacterSets.UTF_8)
                    textPart.contentType = "text/plain".toByteArray()
                    textPart.name = "text".toByteArray()
                    textPart.contentId = "text".toByteArray()
                    textPart.contentLocation = "text".toByteArray()

                    textPart.data = message.content.toByteArray(Charsets.UTF_8)

                    pduBody.addPart(textPart)
                }

                for ((index, file) in downloadedFiles.withIndex()) {
                    val fileBytes = file.first.readBytes()

                    val mediaPart = PduPart()
                    mediaPart.contentType = file.second.toString().toByteArray()


                    val fileName = extractFileName(message.attachments[index], index.toString(), file.second.toString())
                    mediaPart.name = fileName.toByteArray()
                    mediaPart.contentId = fileName.toByteArray()
                    mediaPart.contentLocation = fileName.toByteArray()
                    mediaPart.data = fileBytes

                    Timber.d("Adding MMS attachment with name [$fileName] and size [${fileBytes.size}] and type [${file.second}]")

                    pduBody.addPart(mediaPart)
                }

                sendReq.body = pduBody

                val pduComposer = PduComposer(applicationContext, sendReq)
                val pduBytes = pduComposer.make()

                if (pduBytes == null) {
                    Timber.e("PduComposer failed to generate PDU byte array")
                    handleFailed(applicationContext, message.id, "Failed to compose MMS PDU.")
                    return Result.failure()
                }

                val mmsDir = File(applicationContext.cacheDir, "mms_attachments")
                if (!mmsDir.exists()) {
                    mmsDir.mkdirs()
                }

                val pduFile = File(mmsDir, "pdu_${message.id}.dat")
                FileOutputStream(pduFile).use { it.write(pduBytes) }

                val pduUri = FileProvider.getUriForFile(
                    applicationContext,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    pduFile
                )

                val sentIntent = createPendingIntent(message.id, SmsManagerService.sentAction())
                SmsManagerService.sendMultimediaMessage(applicationContext, pduUri, message.sim, sentIntent)

                Timber.d("Successfully dispatched MMS for message ID [${message.id}]")
                return Result.success()

            } catch (e: Exception) {
                Timber.e(e, "Failed to send MMS for message ID [${message.id}]")
                handleFailed(applicationContext, message.id, e.message ?: "Internal error while building or sending MMS.")
                return Result.failure()
            } finally {
                // Clean up any downloaded temporary files
                downloadedFiles.forEach { file ->
                    if (file.first.exists()) {
                        file.first.delete()
                    }
                }

                // Also clean up the MMS PDU file to avoid cache buildup in cases where
                // sendMultimediaMessage fails before the sent broadcast is delivered.
                try {
                    // The PDU file is stored under the "mms_attachments" cache subdirectory;
                    // delete it from the same location to ensure cleanup is effective.
                    val pduDir = File(applicationContext.cacheDir, "mms_attachments")
                    val pduFile = File(pduDir, "pdu_${message.id}.dat")
                    if (pduFile.exists()) {
                        val deleted = pduFile.delete()
                        if (!deleted) {
                            Timber.w("Failed to delete MMS PDU file for message ID [${message.id}] at [${pduFile.absolutePath}]")
                        }
                    }
                } catch (cleanupException: Exception) {
                    // Best-effort cleanup; log but do not change the original result.
                    Timber.w(cleanupException, "Error while cleaning up MMS PDU file for message ID [${message.id}]")
                }
            }
        }

        private fun handleMultipartMessage(message: Message, parts: ArrayList<String>): Result {
            Timber.d("sending multipart SMS for message with ID [${message.id}]")
            return try {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()

                for (i in 0 until parts.size) {
                    var id = "${message.id}.$i"

                    // Listen for 'delivered' and 'sent' intents only on the last part in the
                    // multipart SMS message
                    if (i == parts.size -1) {
                        id = message.id
                    }

                    sentIntents.add(createPendingIntent(id, SmsManagerService.sentAction()))
                    deliveredIntents.add(createPendingIntent(id, SmsManagerService.deliveredAction()))
                }
                SmsManagerService.sendMultipartMessage(this.applicationContext,message.contact, parts, message.sim, sentIntents, deliveredIntents)
                Timber.d("sent SMS for message with ID [${message.id}] in [${parts.size}] parts")
                Result.success()
            } catch (e: Exception) {
                Timber.e(e)
                Timber.d("could not send SMS for message with ID [${message.id}] in [${parts.size}] parts")
                handleFailed(this.applicationContext, message.id, e.message ?: e.javaClass.simpleName)
                Result.failure()
            }
        }

        private fun handleSingleMessage(message: Message, content: String): Result {
            sendMessage(
                message,
                content,
                createPendingIntent(message.id, SmsManagerService.sentAction()),
                createPendingIntent(message.id, SmsManagerService.deliveredAction())
            )
            return Result.success()
        }

        private fun handleFailed(context: Context, messageID: String, reason: String) {
            Timber.d("sending [FAILED] event for message with ID [${messageID}]")

            WorkQueue.reportMessageEvent(
                context,
                MessageEventInput(
                    messageId = messageID,
                    timestamp = Settings.currentTimestamp(),
                    event = MessageEvent.FAILED,
                    reason = reason
                )
            )
        }

        private fun getMessage(context: Context, messageID: String): Message? {
            Timber.d("fetching message with ID [${messageID}]")

            return when (val result = SmsGatewayApi.from(context).getOutstandingMessage(messageID)) {
                is ApiResult.Success -> {
                    Timber.d("fetched message with ID [${result.value.id}]")
                    result.value
                }
                is ApiResult.Failure -> {
                    Timber.e("cannot get message from API with ID [${messageID}]: [${result.message}]")
                    null
                }
            }
        }

        private fun sendMessage(message: Message, content: String, sentIntent: PendingIntent, deliveredIntent: PendingIntent) {
            Timber.d("sending SMS for message with ID [${message.id}]")
            try {
                SmsManagerService.sendTextMessage(this.applicationContext,message.contact, content, message.sim, sentIntent, deliveredIntent)
            } catch (e: Exception) {
                Timber.e(e)
                Timber.d("could not send SMS for message with ID [${message.id}]")
                handleFailed(this.applicationContext, message.id, e.message ?: e.javaClass.simpleName)
                return
            }
            Timber.d("sent SMS for message with ID [${message.id}]")
        }

        private fun getMessageParts(context: Context, message: Message): ArrayList<String> {
            Timber.d("getting parts for message with ID [${message.id}]")

            return try {
                val parts = SmsManagerService.messageParts(context, message.content)
                Timber.d("message with ID [${message.id}] has [${parts.size}] parts")
                parts
            } catch (e: Exception) {
                Timber.e(e)
                Timber.d("could not get parts message with ID [${message.id}] returning [1] part with entire content")
                val list = ArrayList<String>()
                list.add(message.content)
                list
            }
        }

        private fun createPendingIntent(id: String, action: String): PendingIntent {
            val intent = Intent(action)
            intent.putExtra(Constants.KEY_MESSAGE_ID, id)

            return PendingIntent.getBroadcast(
                this.applicationContext,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
