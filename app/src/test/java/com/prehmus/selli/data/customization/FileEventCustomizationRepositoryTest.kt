package com.prehmus.selli.data.customization

import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.EventFieldOverrides
import com.prehmus.selli.domain.model.EventKey
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileEventCustomizationRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `save persists customizations across repository instances`() = runTest {
        val directory = temporaryFolder.newFolder("customizations")
        val customization = occurrence("event-1", title = "Changed")

        repository(directory).save(customization)

        assertEquals(listOf(customization), repository(directory).all())
    }

    @Test
    fun `save replaces customization with same target`() = runTest {
        val repository = repository(temporaryFolder.newFolder("replace"))
        val original = occurrence("event-1", title = "First")
        val replacement = original.copy(
            hidden = true,
            overrides = EventFieldOverrides(),
            label = "Updated label",
        )

        repository.save(original)
        repository.save(replacement)

        assertEquals(listOf(replacement), repository.all())
    }

    @Test
    fun `save keeps customizations with different targets`() = runTest {
        val repository = repository(temporaryFolder.newFolder("multiple"))
        val occurrence = occurrence("event-1", title = "Changed")
        val series = EventCustomization(
            target = CustomizationTarget.SeriesFrom(
                source = CalendarSource.GOOGLE_OWN,
                seriesId = "series-1",
                fromStart = LocalDateTime.of(2026, 7, 20, 10, 0),
            ),
            hidden = true,
        )

        repository.save(occurrence)
        repository.save(series)

        assertEquals(listOf(occurrence, series), repository.all())
    }

    @Test
    fun `remove deletes only matching target`() = runTest {
        val repository = repository(temporaryFolder.newFolder("remove"))
        val removed = occurrence("event-1", title = "Removed")
        val retained = occurrence("event-2", title = "Retained")
        repository.save(removed)
        repository.save(retained)

        repository.remove(removed.target)

        assertEquals(listOf(retained), repository.all())
    }

    @Test
    fun `all returns empty list before file exists`() = runTest {
        val repository = repository(temporaryFolder.newFolder("empty"))

        assertEquals(emptyList<EventCustomization>(), repository.all())
    }

    private fun repository(directory: File): FileEventCustomizationRepository =
        FileEventCustomizationRepository(directory = directory)

    private fun occurrence(eventId: String, title: String): EventCustomization =
        EventCustomization(
            target = CustomizationTarget.Occurrence(
                EventKey(CalendarSource.GOOGLE_OWN, eventId),
            ),
            hidden = false,
            overrides = EventFieldOverrides(title = title),
        )
}
