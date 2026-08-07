package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.EventCategory
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.key
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedEventDeletionRequestDetectorTest {
    private val detector = SharedEventDeletionRequestDetector()
    private val now = LocalDateTime.of(2026, 8, 7, 12, 0)

    private fun event(
        id: String = "e1",
        owner: Person = Person.MELLI,
        category: EventCategory = EventCategory.TOGETHER,
        deleteRequestedBy: Person? = Person.BASTI,
    ): CalendarEvent = CalendarEvent(
        id = id,
        title = "Wir-Zeit",
        start = now.plusDays(1),
        end = now.plusDays(1).plusHours(2),
        isAllDay = false,
        source = CalendarSource.GOOGLE_OWN,
        owner = owner,
        isSharedEvent = true,
        category = category,
        deleteRequestedBy = deleteRequestedBy,
    )

    @Test
    fun `detects new delete request on own together event`() {
        val target = event()
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = emptySet())

        assertEquals(1, result.changes.size)
        assertEquals(target, result.changes.single().event)
        assertEquals(Person.BASTI, result.changes.single().requestedBy)
        assertTrue(target.key() in result.updatedNotified)
    }

    @Test
    fun `ignores already notified requests`() {
        val target = event()
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = setOf(target.key()))

        assertTrue(result.changes.isEmpty())
        assertTrue(target.key() in result.updatedNotified)
    }

    @Test
    fun `ignores events without a request`() {
        val target = event(deleteRequestedBy = null)
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = emptySet())

        assertTrue(result.changes.isEmpty())
        assertTrue(result.updatedNotified.isEmpty())
    }

    @Test
    fun `ignores requests on events not owned by self`() {
        val target = event(owner = Person.BASTI)
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = emptySet())

        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `ignores requests on non together events`() {
        val target = event(category = EventCategory.PRIVATE)
        val result = detector.detect(listOf(target), self = Person.MELLI, alreadyNotified = emptySet())

        assertTrue(result.changes.isEmpty())
    }
}
