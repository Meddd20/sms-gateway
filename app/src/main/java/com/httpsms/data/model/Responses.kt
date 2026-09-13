package com.httpsms.data.model

import com.beust.klaxon.Klaxon

/**
 * The backend's standard envelope: `{ "data": ..., "message": "...", "status": "..." }`.
 * One type per `data` shape, so `fromJson` is type-safe at the call site.
 */
data class ResponseMessage (
    val data: Message,
    val message: String,
    val status: String
) {
    companion object {
        fun fromJson(json: String) = Klaxon().parse<ResponseMessage>(json)
    }
}

data class ResponsePhone (
    val data: Phone,
    val message: String,
    val status: String,
) {
    companion object {
        fun fromJson(json: String) = Klaxon().parse<ResponsePhone>(json)
    }
}

data class GatewayPairResponse (
    val data: GatewayPairData,
    val message: String,
    val status: String,
) {
    companion object {
        fun fromJson(json: String) = Klaxon().parse<GatewayPairResponse>(json)
    }
}
