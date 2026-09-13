package com.httpsms.data.api

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import java.io.StringReader

/**
 * Reads the message the backend returned with a failed request, so the UI can
 * show that instead of a bare status code.
 *
 * Covers both shapes the backend uses:
 *  - `{"message": "..."}`
 *  - `{"message": "...", "errors": {"field": "reason"}}` - the field reasons are
 *    appended so validation failures stay actionable.
 */
object ServerMessage {

    fun from(responseBody: String?): String? {
        if (responseBody.isNullOrBlank()) {
            return null
        }

        // NOTE: `Klaxon().parse<JsonObject>(...)` does NOT work - Klaxon 5.6 resolves
        // the reified T and tries to instantiate JsonObject through its constructor,
        // which needs a backing map, so it throws. parseJsonObject is the real API.
        val json = try {
            Klaxon().parseJsonObject(StringReader(responseBody))
        } catch (exception: Exception) {
            null
        } ?: return null

        val message = json["message"] as? String
        val errors = json["errors"] as? JsonObject

        return when {
            errors != null && errors.isNotEmpty() -> {
                val details = errors.toMap().entries.joinToString("; ") { "${it.key}: ${it.value}" }
                listOfNotNull(message, details).joinToString(" ")
            }
            message != null -> message
            else -> null
        }
    }
}
