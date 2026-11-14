package com.oneclickalias.app.data.api

import com.oneclickalias.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointsTest {
    @Test
    fun `endpoints default to the production origin`() {
        // Holds for builds without -PocaApiBase/-PocaAuthBase/-PocaWebBase overrides.
        assertEquals("https://oneclickalias.com", BuildConfig.API_BASE)
        assertEquals("https://oneclickalias.com", BuildConfig.AUTH_BASE)
        assertEquals("https://oneclickalias.com", BuildConfig.WEB_BASE)
    }

    @Test
    fun `endpoints expose the compiled-in origins`() {
        val endpoints = Endpoints.fromBuildConfig()
        assertEquals(BuildConfig.API_BASE, endpoints.apiBase.toString().removeSuffix("/"))
        assertEquals(BuildConfig.AUTH_BASE, endpoints.authBase.toString().removeSuffix("/"))
        assertEquals(BuildConfig.WEB_BASE, endpoints.webBase.toString().removeSuffix("/"))
    }
}
