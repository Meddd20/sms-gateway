package com.httpsms.data.io

import android.content.Context
import com.httpsms.core.Constants
import com.httpsms.data.api.HttpClient
import com.httpsms.data.api.TrafficLog
import okhttp3.MediaType
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Downloads an MMS attachment into the app cache.
 *
 * This is file transport against whatever URL the backend put in a message's
 * `attachments`, not a call to one of our endpoints, so it sits outside the API
 * layer. Reads are capped at [Constants.MAX_MMS_ATTACHMENT_SIZE] to keep a hostile
 * or mistaken URL from filling the device.
 */
object AttachmentDownloader {

    private const val CACHE_DIRECTORY = "mms_attachments"

    fun download(context: Context, url: String, messageId: String, attachmentIndex: Int): Pair<File?, MediaType?> {
        val request = Request.Builder().url(url).build()
        TrafficLog.request(request)

        return try {
            HttpClient.instance.newCall(request).execute().use { response ->
                TrafficLog.response(response)

                if (!response.isSuccessful) {
                    Timber.e("Failed to download attachment from [${request.url}]: ${response.code}")
                    return Pair(null, null)
                }

                val body = response.body
                val contentLength = body.contentLength()
                if (contentLength > Constants.MAX_MMS_ATTACHMENT_SIZE) {
                    Timber.e("Attachment is too large ($contentLength bytes).")
                    return Pair(null, null)
                }

                val directory = File(context.cacheDir, CACHE_DIRECTORY)
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val file = File(directory, "mms_${messageId}_$attachmentIndex")
                val inputStream = body.byteStream()
                FileOutputStream(file).use { outputStream ->
                    inputStream.use { input ->
                        input.copyToWithLimit(outputStream, Constants.MAX_MMS_ATTACHMENT_SIZE)
                    }
                }

                Pair(file, body.contentType())
            }
        } catch (e: Exception) {
            TrafficLog.failure(url, e)
            Pair(null, null)
        }
    }

    /** Copies [this] to [out], aborting once more than [limit] bytes have arrived. */
    private fun InputStream.copyToWithLimit(
        out: OutputStream,
        limit: Long,
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): Long {
        var bytesCopied = 0L
        val buffer = ByteArray(bufferSize)
        var bytes = read(buffer)

        while (bytes >= 0) {
            bytesCopied += bytes

            if (bytesCopied > limit) {
                throw IOException("Download aborted: file exceeded the maximum allowed size of $limit bytes.")
            }

            out.write(buffer, 0, bytes)
            bytes = read(buffer)
        }
        return bytesCopied
    }
}
