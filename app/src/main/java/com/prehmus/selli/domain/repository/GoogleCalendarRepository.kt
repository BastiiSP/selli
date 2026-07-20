package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.domain.model.AuthResult
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.DateRange

interface GoogleCalendarRepository {
    suspend fun signIn(): AuthResult
    suspend fun grantMutualAccess(ownAccount: Account, partnerAccount: Account): Result<Unit>
    suspend fun fetchEvents(range: DateRange): List<CalendarEvent>
    suspend fun fetchEvents(range: DateRange, forceRefresh: Boolean): List<CalendarEvent> =
        fetchEvents(range)
}
