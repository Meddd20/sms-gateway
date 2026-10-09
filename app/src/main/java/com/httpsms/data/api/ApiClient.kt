package com.httpsms.data.api

import android.content.Context
import com.sevanam.androidsmsgateway.BuildConfig
import com.httpsms.core.Settings
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URL

/**
 * The one place that performs HTTP.
 *
 * Owns the headers every request carries, URL resolution and - the part worth
 * centralising - the log -> execute -> read body -> close cycle, so no caller can
 * forget to close a response. It knows nothing about any specific endpoint; those
 * live in [SmsGatewayApi].
 *
 * [token] is the credential from pairing (`device_token`). It is presented as
 * `Authorization: Bearer <token>` once it exists; the calls that run before login
 * build an unauthenticated client.
 */
internal class ApiClient(
    private val token: String,
    private val baseUrl: URI
) {
    companion object {
        private const val CLIENT_VERSION_HEADER = "X-Client-Version"
        private const val AUTHORIZATION_HEADER = "Authorization"

        /** A client that presents the stored credential, for every call after login. */
        fun authenticated(context: Context): ApiClient {
            return ApiClient(Settings.getApiKeyOrDefault(context), URI(BuildConfig.SERVER_URL))
        }

        /** A client with no credential, for the pairing call that produces one. */
        fun unauthenticated(): ApiClient {
            return ApiClient("", URI(BuildConfig.SERVER_URL))
        }
    }

    private val jsonMediaType: MediaType = "application/json; charset=utf-8".toMediaType()

    fun get(path: String): HttpExchange {
        return send(request(path).get().build(), jsonBody = null)
    }

    fun post(path: String, jsonBody: String): HttpExchange {
        return send(request(path).post(jsonBody.toRequestBody(jsonMediaType)).build(), jsonBody)
    }

    fun put(path: String, jsonBody: String): HttpExchange {
        return send(request(path).put(jsonBody.toRequestBody(jsonMediaType)).build(), jsonBody)
    }

    private fun request(path: String): Request.Builder {
        val builder = Request.Builder()
            .url(resolve(path))
            .header(CLIENT_VERSION_HEADER, BuildConfig.VERSION_NAME)

        if (token.isNotEmpty()) {
            builder.header(AUTHORIZATION_HEADER, "Bearer $token")
        }

        return builder
    }

    private fun send(request: Request, jsonBody: String?): HttpExchange {
        TrafficLog.request(request, jsonBody)

        return try {
            HttpClient.instance.newCall(request).execute().use { response ->
                val body = response.body.string()
                TrafficLog.response(response, body)
                HttpExchange.Response(
                    isSuccessful = response.isSuccessful,
                    code = response.code,
                    statusMessage = response.message,
                    body = body
                )
            }
        } catch (exception: Exception) {
            TrafficLog.failure(request.url.toString(), exception)
            HttpExchange.TransportFailure(exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun resolve(path: String): URL {
        return baseUrl.resolve(baseUrl.path + path).toURL()
    }
}
