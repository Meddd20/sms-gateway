package com.httpsms.data.api

import android.content.Context
import com.beust.klaxon.Klaxon
import com.httpsms.BuildConfig
import com.httpsms.core.DeviceStatus
import com.httpsms.data.model.FcmTokenRequest
import com.httpsms.data.model.GatewayPairData
import com.httpsms.data.model.GatewayPairRequest
import com.httpsms.data.model.GatewayPairResponse
import com.httpsms.data.model.HeartbeatRequest
import com.httpsms.data.model.Message
import com.httpsms.data.model.MessageEventRequest
import com.httpsms.data.model.Phone
import com.httpsms.data.model.ReceivedMessageRequest
import com.httpsms.data.model.ResponseMessage
import com.httpsms.data.model.ResponsePhone

/**
 * The backend's endpoints, one function each.
 *
 * Every call returns an [ApiResult]. The wire concerns (headers, logging, status
 * codes, closing the response) belong to [ApiClient], and the bodies are Klaxon
 * models from `data/model` - nothing here touches the platform or the filesystem,
 * which keeps each function to the shape of its request and response.
 */
class SmsGatewayApi internal constructor(private val client: ApiClient) {

    companion object {
        fun from(context: Context): SmsGatewayApi {
            return SmsGatewayApi(ApiClient.authenticated(context))
        }
    }

    /**
     * First login: exchanges the pairing code for this device's credential.
     *
     * Deliberately unauthenticated - the pairing code *is* the credential being
     * created, so there is nothing to present yet.
     */
    fun pair(payload: GatewayPairRequest): ApiResult<GatewayPairData> {
        val body = Klaxon().toJsonString(payload)
        return decode(ApiClient.unauthenticated().post(ApiEndpoints.GATEWAY_PAIR, body)) { responseBody ->
            responseBody?.let { GatewayPairResponse.fromJson(it) }?.data
        }
    }

    /** The full message an FCM push pointed at. */
    fun getOutstandingMessage(messageId: String): ApiResult<Message> {
        return decode(client.get(ApiEndpoints.outstandingMessage(messageId))) { responseBody ->
            responseBody?.let { ResponseMessage.fromJson(it) }?.data
        }
    }

    /** Reports that an incoming SMS/MMS was forwarded. */
    fun receive(payload: ReceivedMessageRequest): ApiResult<Unit> {
        val exchange = client.post(ApiEndpoints.RECEIVE_MESSAGE, Klaxon().toJsonString(payload))
        return when (exchange) {
            is HttpExchange.TransportFailure -> ApiResult.Failure(exchange.message)
            is HttpExchange.Response -> when {
                exchange.isSuccessful -> ApiResult.Success(Unit)
                // A 4xx is a permanent rejection (the payload was refused), so
                // retrying cannot help - report it as done and let the worker stop.
                exchange.code in 400..499 -> ApiResult.Success(Unit)
                else -> ApiResult.Failure(exchange.errorMessage())
            }
        }
    }

    /** Reports the device's periodic status. */
    fun storeHeartbeat(status: DeviceStatus, phoneNumbers: List<String>): ApiResult<Unit> {
        val payload = HeartbeatRequest(
            deviceId = status.deviceId,
            appVersion = BuildConfig.VERSION_NAME,
            timestamp = System.currentTimeMillis() / 1000,
            smsPermission = status.smsPermissionGranted,
            batteryOptimizationDisabled = status.batteryOptimizationDisabled,
            batteryLevel = status.batteryLevel,
            isCharging = status.isCharging,
            activeSubscriptionId = status.activeSubscriptionId,
            simCarrier = status.simCarrier,
            networkType = status.networkType,
            phoneNumbers = phoneNumbers
        )
        return decode(client.post(ApiEndpoints.HEARTBEATS, Klaxon().toJsonString(payload))) { Unit }
    }

    fun sendSentEvent(messageId: String, timestamp: String): ApiResult<Unit> {
        return sendEvent(messageId, "SENT", timestamp)
    }

    fun sendDeliveredEvent(messageId: String, timestamp: String): ApiResult<Unit> {
        return sendEvent(messageId, "DELIVERED", timestamp)
    }

    fun sendFailedEvent(messageId: String, timestamp: String, reason: String): ApiResult<Unit> {
        return sendEvent(messageId, "FAILED", timestamp, reason)
    }

    /** Registers (or refreshes) the FCM token for a phone number + SIM. */
    fun updateFcmToken(phoneNumber: String, sim: String, fcmToken: String): ApiResult<Phone> {
        val payload = FcmTokenRequest(fcmToken = fcmToken, phoneNumber = phoneNumber, sim = sim)
        return decode(client.put(ApiEndpoints.FCM_TOKEN, Klaxon().toJsonString(payload))) { responseBody ->
            responseBody?.let { ResponsePhone.fromJson(it) }?.data
        }
    }

    private fun sendEvent(messageId: String, event: String, timestamp: String, reason: String? = null): ApiResult<Unit> {
        val payload = MessageEventRequest(eventName = event, reason = reason, timestamp = timestamp)
        val exchange = client.post(ApiEndpoints.messageEvents(messageId), Klaxon().toJsonString(payload))
        return when (exchange) {
            is HttpExchange.TransportFailure -> ApiResult.Failure(exchange.message)
            is HttpExchange.Response -> when {
                // The message was deleted server-side: the report can never be
                // delivered, so treat it as done rather than retrying forever.
                exchange.code == 404 -> ApiResult.Success(Unit)
                exchange.isSuccessful -> ApiResult.Success(Unit)
                else -> ApiResult.Failure(exchange.errorMessage())
            }
        }
    }

    /** Runs the exchange and parses a 2xx body with [parse]; anything else is a failure. */
    private fun <T> decode(exchange: HttpExchange, parse: (String?) -> T?): ApiResult<T> {
        return when (exchange) {
            is HttpExchange.TransportFailure -> ApiResult.Failure(exchange.message)
            is HttpExchange.Response -> {
                if (!exchange.isSuccessful) {
                    return ApiResult.Failure(exchange.errorMessage())
                }
                val value = parse(exchange.body)
                    ?: return ApiResult.Failure("Cannot read the server response.")
                ApiResult.Success(value)
            }
        }
    }
}
