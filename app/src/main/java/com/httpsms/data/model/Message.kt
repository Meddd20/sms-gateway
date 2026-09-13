package com.httpsms.data.model

import com.beust.klaxon.Json

/** A message the backend wants this device to send (fetch via `messages/outstanding`). */
data class Message (
    val contact: String,
    val content: String,
    val sim: String,

    @Json(name = "created_at")
    val createdAt: String,

    @Json(name = "failure_reason")
    val failureReason: String?,

    val id: String,

    @Json(name = "last_attempted_at")
    val lastAttemptedAt: String?,

    @Json(name = "order_timestamp")
    val orderTimestamp: String,

    val owner: String,

    @Json(name = "received_at")
    val receivedAt: String?,

    val encrypted: Boolean,

    @Json(name = "request_received_at")
    val requestReceivedAt: String,

    @Json(name = "send_time")
    val sendTime: Long?,

    @Json(name = "sent_at")
    val sentAt: String?,

    val status: String,
    val type: String,

    @Json(name = "updated_at")
    val updatedAt: String,

    val attachments: List<String>? = null
)

/** An MMS attachment travelling inbound, base64 encoded. */
data class ReceivedAttachment(
    val name: String,
    @Json(name = "content_type")
    val contentType: String,
    val content: String
)
