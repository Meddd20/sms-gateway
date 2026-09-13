package com.httpsms.data.api

/**
 * The outcome of one HTTP round trip: either the server answered, or the request
 * never completed (no network, timeout, DNS failure, ...).
 *
 * The response body is already read and the connection released by the time this
 * is returned, so callers never touch OkHttp types or have to remember to close
 * anything.
 */
internal sealed class HttpExchange {

    data class Response(
        val isSuccessful: Boolean,
        val code: Int,
        val statusMessage: String,
        val body: String?
    ) : HttpExchange() {
        /** The server's own message when the body carries one, else the status line. */
        fun errorMessage(): String {
            return ServerMessage.from(body) ?: "HTTP $code $statusMessage"
        }
    }

    data class TransportFailure(val message: String) : HttpExchange()
}
