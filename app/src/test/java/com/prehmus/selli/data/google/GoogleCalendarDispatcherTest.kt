package com.prehmus.selli.data.google

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleCalendarDispatcherTest {
    @Test
    fun `runs blocking google work on injected dispatcher`() = runTest {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "google-calendar-test-io")
        }.asCoroutineDispatcher().use { dispatcher ->
            val executionThread = withGoogleCalendarDispatcher(dispatcher) {
                Thread.currentThread().name
            }

            assertEquals("google-calendar-test-io", executionThread)
        }
    }
}
