package com.oneclickalias.app.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue

/** Polls [condition] in real time; API calls complete on OkHttp threads. */
suspend fun eventually(message: String = "condition not met within 5 s", condition: () -> Boolean) =
    withContext(Dispatchers.Default) {
        withTimeoutOrNull(5_000) { while (!condition()) delay(10) }
        assertTrue(message, condition())
    }
