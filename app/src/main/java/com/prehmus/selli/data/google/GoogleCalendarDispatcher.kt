package com.prehmus.selli.data.google

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal suspend fun <T> withGoogleCalendarDispatcher(
    dispatcher: CoroutineDispatcher,
    block: suspend () -> T,
): T = withContext(dispatcher) {
    block()
}
