package com.httpsms.data.model

import com.beust.klaxon.Json

/** Body of `POST messages/receive`: an SMS/MMS that arrived on the device. */
data class ReceivedMessageRequest(
    val sim: String,
    val from: String,
    val to: String,
    val content: String,
    val encrypted: Boolean,
    val timestamp: String,
    val attachments: List<ReceivedAttachment>? = null
)

/** Body of `POST gateway/pair`: first login, exchanges a pairing code for a device token. */
data class GatewayPairRequest(
    @Json(name = "pairing_code")
    val pairingCode: String,

    @Json(name = "fcm_token")
    val fcmToken: String,

    @Json(name = "phone_number")
    val phoneNumber: String,

    @Json(name = "sim_slot")
    val simSlot: Int,

    @Json(name = "sim_operator")
    val simOperator: String,

    @Json(name = "device_name")
    val deviceName: String,

    @Json(name = "device_model")
    val deviceModel: String,

    @Json(name = "app_version")
    val appVersion: String
)

/** Body of `POST heartbeats`: the device's periodic status report. */
data class HeartbeatRequest(
    @Json(name = "device_id")
    val deviceId: String,

    @Json(name = "app_version")
    val appVersion: String,

    val timestamp: Long,

    @Json(name = "sms_permission")
    val smsPermission: Boolean,

    @Json(name = "battery_optimization_disabled")
    val batteryOptimizationDisabled: Boolean,

    @Json(name = "battery_level")
    val batteryLevel: Int,

    @Json(name = "is_charging")
    val isCharging: Boolean,

    @Json(name = "active_subscription_id")
    val activeSubscriptionId: Int,

    @Json(name = "sim_carrier")
    val simCarrier: String,

    @Json(name = "network_type")
    val networkType: String,

    @Json(name = "phone_numbers")
    val phoneNumbers: List<String>
)

/** Body of `POST messages/{id}/events`. `reason` is only set for a FAILED event. */
data class MessageEventRequest(
    @Json(name = "event_name")
    val eventName: String,

    val reason: String? = null,

    val timestamp: String
)

/** Body of `PUT phones/fcm-token`. */
data class FcmTokenRequest(
    @Json(name = "fcm_token")
    val fcmToken: String,

    @Json(name = "phone_number")
    val phoneNumber: String,

    val sim: String
)
