package com.httpsms.data.api

import okhttp3.OkHttpClient
import java.util.logging.Level
import java.util.logging.Logger.getLogger

/**
 * The single OkHttp client for the process.
 *
 * An OkHttpClient is thread-safe and owns a connection pool plus a thread pool, so
 * one is shared instead of built per request - otherwise every call opens fresh
 * pools and throws them away.
 */
internal object HttpClient {
    val instance: OkHttpClient by lazy {
        getLogger(OkHttpClient::class.java.name).level = Level.FINE
        OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    }
}
