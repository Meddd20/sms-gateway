package com.httpsms.data.api

/**
 * Uniform outcome of a backend call, so callers never have to reason about
 * booleans, nullable payloads and error strings at once.
 *
 * [Failure.message] is always something worth showing a user: the backend's own
 * `message` when the response carried one, otherwise the transport text (timeout,
 * DNS, no network) or the bare status line.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()
}
