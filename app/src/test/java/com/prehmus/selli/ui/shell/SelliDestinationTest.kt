package com.prehmus.selli.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelliDestinationTest {

    @Test
    fun `routes are unique and stable`() {
        val routes = SelliDestination.entries.map { it.route }
        assertEquals(listOf("calendar", "ideen", "home", "expenses", "location"), routes)
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `fromRoute maps every known route back`() {
        SelliDestination.entries.forEach { destination ->
            assertEquals(destination, SelliDestination.fromRoute(destination.route))
        }
    }

    @Test
    fun `fromRoute returns null for the settings route and for unknown input`() {
        assertNull(SelliDestination.fromRoute(SETTINGS_ROUTE))
        assertNull(SelliDestination.fromRoute(null))
        assertNull(SelliDestination.fromRoute("nope"))
    }

    @Test
    fun `bottom navigation order puts the shared home in the middle`() {
        assertEquals(SelliDestination.HOME, SelliDestination.entries[2])
        assertEquals(SelliDestination.HOME, SelliStartDestination)
    }
}
