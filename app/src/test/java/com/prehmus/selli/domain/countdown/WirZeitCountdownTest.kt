package com.prehmus.selli.domain.countdown

import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.Person
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WirZeitCountdownTest {
    private val now = LocalDateTime.of(2026, 8, 1, 12, 0)

    private fun event(
        start: LocalDateTime,
        created: LocalDateTime? = null,
    ): CalendarEvent = CalendarEvent(
        id = "e1",
        title = "Wir-Zeit",
        start = start,
        end = start.plusHours(2),
        isAllDay = false,
        source = CalendarSource.GOOGLE_OWN,
        owner = Person.BASTI,
        isSharedEvent = true,
        created = created,
    )

    @Test
    fun `no event means none planned`() {
        val result = calculateWirZeitCountdown(event = null, now = now)
        assertEquals(WirZeitCountdownState.NONE_PLANNED, result.state)
        assertEquals("", result.remainingText)
    }

    @Test
    fun `event starting today means arrived`() {
        val result = calculateWirZeitCountdown(event(start = now.plusHours(3)), now = now)
        assertEquals(WirZeitCountdownState.ARRIVED_TODAY, result.state)
    }

    @Test
    fun `event that already started today still counts as arrived`() {
        val result = calculateWirZeitCountdown(event(start = now.minusHours(2)), now = now)
        assertEquals(WirZeitCountdownState.ARRIVED_TODAY, result.state)
    }

    @Test
    fun `future event without created has zero progress`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusDays(3), created = null),
            now = now,
        )
        assertEquals(WirZeitCountdownState.WALKING, result.state)
        assertEquals(0f, result.progress, 0.0001f)
    }

    @Test
    fun `progress is halfway between created and start`() {
        val created = now.minusDays(2)
        val start = now.plusDays(2)
        val result = calculateWirZeitCountdown(event(start = start, created = created), now = now)
        assertEquals(0.5f, result.progress, 0.01f)
    }

    @Test
    fun `progress clamps to zero when now is before created`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusDays(5), created = now.plusDays(1)),
            now = now,
        )
        assertEquals(0f, result.progress, 0.0001f)
    }

    @Test
    fun `progress clamps to one when created equals start`() {
        val same = now.plusDays(3)
        val result = calculateWirZeitCountdown(event(start = same, created = same), now = now)
        assertEquals(1f, result.progress, 0.0001f)
    }

    @Test
    fun `remaining text includes days hours and minutes with minutes enabled`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusDays(3).plusHours(4).plusMinutes(22)),
            now = now,
            includeMinutes = true,
        )
        assertEquals("noch 3 Tage, 4 Std., 22 Min.", result.remainingText)
    }

    @Test
    fun `remaining text omits minutes when disabled for widget`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusDays(3).plusHours(4).plusMinutes(22)),
            now = now,
            includeMinutes = false,
        )
        assertEquals("noch 3 Tage, 4 Std.", result.remainingText)
    }

    @Test
    fun `remaining text drops zero-valued larger units`() {
        val result = calculateWirZeitCountdown(event(start = now.plusMinutes(22)), now = now)
        assertEquals("noch 22 Min.", result.remainingText)
    }

    @Test
    fun `remaining text uses singular Tag for exactly one day`() {
        val result = calculateWirZeitCountdown(event(start = now.plusDays(1)), now = now)
        assertEquals("noch 1 Tag", result.remainingText)
    }

    @Test
    fun `remaining text falls back to gleich when under a minute and minutes enabled`() {
        val result = calculateWirZeitCountdown(event(start = now.plusSeconds(30)), now = now)
        assertEquals("gleich", result.remainingText)
    }

    @Test
    fun `remaining text falls back to unter einer Stunde when under an hour and minutes disabled`() {
        val result = calculateWirZeitCountdown(
            event(start = now.plusMinutes(30)),
            now = now,
            includeMinutes = false,
        )
        assertEquals("< 1 Std.", result.remainingText)
    }
}
