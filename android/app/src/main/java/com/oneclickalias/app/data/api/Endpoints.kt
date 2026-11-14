package com.oneclickalias.app.data.api

import com.oneclickalias.app.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Origins the app talks to, compiled in at build time (Gradle properties
 * `ocaApiBase`, `ocaAuthBase`, `ocaWebBase`).
 *
 * @property apiBase Alias Management Service, paths `/api/...`.
 * @property authBase Authentication service, paths `/auth/...` and `/devices`.
 * @property webBase Website, e.g. `/dashboard/billing`.
 */
data class Endpoints(val apiBase: HttpUrl, val authBase: HttpUrl, val webBase: HttpUrl) {
    companion object {
        fun fromBuildConfig(): Endpoints = Endpoints(
            apiBase = BuildConfig.API_BASE.toHttpUrl(),
            authBase = BuildConfig.AUTH_BASE.toHttpUrl(),
            webBase = BuildConfig.WEB_BASE.toHttpUrl()
        )
    }
}
