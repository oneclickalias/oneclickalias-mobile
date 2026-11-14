package com.oneclickalias.app.data.api

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * JSON configuration shared by every API call. Unknown fields are ignored so a
 * server can add fields without breaking older app versions.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Builds the app's single [OkHttpClient]. No logging interceptor on purpose:
 * request and response bodies carry account numbers and tokens, which must
 * never end up in logcat.
 */
fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()
