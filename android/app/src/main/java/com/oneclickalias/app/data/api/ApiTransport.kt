package com.oneclickalias.app.data.api

import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Thin suspend layer over OkHttp shared by all API clients: sends a request,
 * reads the body off the main thread and turns the outcome into an [ApiResult].
 *
 * Nothing here logs. Request and response bodies carry account numbers and
 * tokens; exceptions are mapped to [NetworkErrorKind] and dropped.
 */
class ApiTransport(private val client: OkHttpClient, private val json: Json = ApiJson) {

    /** Sends [request] and decodes a 2xx body with [success]. */
    suspend fun <T> call(request: Request, success: DeserializationStrategy<T>): ApiResult<T> =
        execute(request) { body ->
            runCatching { json.decodeFromString(success, body) }.getOrNull()
        }

    /** Sends [request] and ignores any 2xx body (e.g. `204 No Content`). */
    suspend fun callNoContent(request: Request): ApiResult<Unit> = execute(request) { }

    /** JSON request body for [value], encoded with the shared [ApiJson] settings. */
    fun <T> jsonBody(serializer: KSerializer<T>, value: T): RequestBody =
        json.encodeToString(serializer, value).toRequestBody(JSON_MEDIA_TYPE)

    private suspend fun <T> execute(request: Request, decode: (String) -> T?): ApiResult<T> {
        val (status, body) = try {
            client.newCall(request).await()
        } catch (e: InterruptedIOException) {
            // SocketTimeoutException and OkHttp's call timeout both land here.
            return ApiResult.NetworkError(NetworkErrorKind.Timeout)
        } catch (e: IOException) {
            return ApiResult.NetworkError(NetworkErrorKind.Unreachable)
        }
        if (status !in 200..299) return ApiResult.HttpError(status, errorCode(body))
        val value = decode(body) ?: return ApiResult.HttpError(status, ApiResult.UNKNOWN_ERROR_CODE)
        return ApiResult.Success(value)
    }

    /** Reads `error` from an `{error, message}` body; anything else is "unknown". */
    private fun errorCode(body: String): String {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val code = ((element as? JsonObject)?.get("error") as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
        return if (code.isNullOrBlank()) ApiResult.UNKNOWN_ERROR_CODE else code
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Status code and body text, read on OkHttp's thread so callers never block. */
private suspend fun Call.await(): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { it.code to it.body.string() }
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                    return
                }
                continuation.resume(result)
            }
        }
    )
}
