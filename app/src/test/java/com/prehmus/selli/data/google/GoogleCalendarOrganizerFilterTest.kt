package com.prehmus.selli.data.google

import com.google.api.services.calendar.model.Event
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarOrganizerFilterTest {
    private val partnerEmail = "melli@example.test"

    @Test
    fun `event without organizer is not treated as partner organized`() {
        assertFalse(Event().isOrganizedByPartner(partnerEmail))
    }

    @Test
    fun `event organized by the signed-in user is not treated as partner organized`() {
        val event = Event().setOrganizer(
            Event.Organizer().setSelf(true).setEmail("basti@example.test"),
        )

        assertFalse(event.isOrganizedByPartner(partnerEmail))
    }

    @Test
    fun `event organized by the partner is treated as partner organized`() {
        val event = Event().setOrganizer(
            Event.Organizer().setSelf(false).setEmail(partnerEmail),
        )

        assertTrue(event.isOrganizedByPartner(partnerEmail))
    }

    @Test
    fun `event organized by the partner without self flag is treated as partner organized`() {
        val event = Event().setOrganizer(Event.Organizer().setEmail(partnerEmail))

        assertTrue(event.isOrganizedByPartner(partnerEmail))
    }

    @Test
    fun `partner organizer email is matched case-insensitively`() {
        val event = Event().setOrganizer(
            Event.Organizer().setSelf(false).setEmail("  MELLI@Example.Test "),
        )

        assertTrue(event.isOrganizedByPartner("Melli@example.TEST"))
    }

    @Test
    fun `event organized by a third person stays visible`() {
        val event = Event().setOrganizer(
            Event.Organizer().setSelf(false).setEmail("kollegin@firma.test"),
        )

        assertFalse(event.isOrganizedByPartner(partnerEmail))
    }

    @Test
    fun `organizer without email is not treated as partner organized`() {
        val event = Event().setOrganizer(Event.Organizer().setSelf(false))

        assertFalse(event.isOrganizedByPartner(partnerEmail))
    }
}
