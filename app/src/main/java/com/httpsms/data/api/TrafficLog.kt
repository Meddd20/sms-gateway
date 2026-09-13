package com.httpsms.data.api

import com.httpsms.BuildConfig
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

/**
 * Single traffic log for every network exchange.
 *
 * Callers write through here so the same picture shows up in Logcat (and, when
 * enabled, in the remote [com.httpsms.core.LogzTree] sink).
 *
 * Every entry prints the FULL url (host + path + query) - a bare status code is
 * useless when the failure is a wrong host or a missing query parameter.
 *
 * Bodies carry credentials (`device_token`, `encryption_key`) and message content
 * (OTP text), so payloads are only printed on debug builds. Method, URL, status
 * codes and headers are always logged; credentials in headers are masked even in
 * debug.
 */
object TrafficLog {

    private const val MAX_BODY_CHARS = 4000

    /** Query parameters whose value is a credential, redacted from every logged url. */
    private val SENSITIVE_QUERY_PARAMS = listOf("token", "access_token", "api_key", "apikey", "secret")

    fun request(request: Request, body: String? = null) {
        Timber.i("-> ${request.method} ${maskUrl(request.url.toString())}")
        headers(request.headers)
        if (!body.isNullOrEmpty()) {
            payload(body)
        }
    }

    fun response(response: Response, body: String? = null) {
        Timber.i("<- ${response.code} ${response.message} (${response.request.method} ${maskUrl(response.request.url.toString())})")
        headers(response.headers)
        if (!body.isNullOrEmpty()) {
            payload(body)
        }
    }

    fun failure(url: String, error: Throwable) {
        Timber.e(error, "<- request to [${maskUrl(url)}] failed")
    }

    private fun headers(headers: Headers) {
        if (headers.size == 0) {
            return
        }
        headers.forEach { (name, value) ->
            Timber.i("   header: $name: ${mask(name, value)}")
        }
    }

    private fun payload(body: String) {
        if (!BuildConfig.DEBUG) {
            Timber.i("   body: (${body.length} chars, hidden on release builds)")
            return
        }
        Timber.i("   body: ${body.truncated()}")
    }

    private fun mask(name: String, value: String): String {
        val credential = name.equals("Authorization", ignoreCase = true) ||
            name.equals("x-api-key", ignoreCase = true)
        if (!credential) {
            return value
        }
        return maskValue(value)
    }

    /**
     * Redacts credential values from a url's query string, keeping the full url
     * (host + path + other parameters) readable - a bare status code is useless
     * when the failure is a wrong host.
     */
    private fun maskUrl(url: String): String {
        var masked = url
        SENSITIVE_QUERY_PARAMS.forEach { param ->
            val pattern = Regex("([?&]$param=)([^&\\s]+)", RegexOption.IGNORE_CASE)
            masked = pattern.replace(masked) { match ->
                match.groupValues[1] + maskValue(match.groupValues[2])
            }
        }
        return masked
    }

    private fun maskValue(value: String): String {
        return if (value.length <= 12) "***" else "${value.take(12)}...(${value.length} chars)"
    }

    private fun String.truncated(): String {
        return if (length <= MAX_BODY_CHARS) this else "${take(MAX_BODY_CHARS)}...(truncated)"
    }
}
