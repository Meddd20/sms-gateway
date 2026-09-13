package com.httpsms.data.model

import com.beust.klaxon.Json

/** A registered line: the phone number + SIM the backend routes messages through. */
data class Phone (
    val id: String,

    @Json(name = "user_id")
    val userID: String,
)

/**
 * The pairing response. Only the credential is modelled - it is the one field the
 * app acts on. The rest of the payload (`device_id`, `device_name`,
 * `registered_lines`, `server_time`) is informational and Klaxon ignores it.
 */
data class GatewayPairData (
    @Json(name = "device_token")
    val deviceToken: String? = null,

    val token: String? = null,
)
