package com.prehmus.selli.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventCustomizationTest {
    @Test
    fun `field overrides are empty when every field is null`() {
        assertTrue(EventFieldOverrides().isEmpty())
    }

    @Test
    fun `field overrides are not empty when a field is set`() {
        assertFalse(EventFieldOverrides(title = "Changed").isEmpty())
    }

    @Test
    fun `field overrides are not empty when only category is set`() {
        assertFalse(EventFieldOverrides(category = EventCategory.TOGETHER).isEmpty())
    }

    @Test
    fun `field overrides are not empty when all-day event explicitly does not block free time`() {
        assertFalse(EventFieldOverrides(blocksSharedFreeTime = false).isEmpty())
    }
}
