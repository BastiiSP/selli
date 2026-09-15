package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NewNoteItemDetectorTest {
    private fun item(id: String, createdBy: Person) = NoteItem(
        id = id,
        folderId = "folder-1",
        text = "Punkt $id",
        url = null,
        previewTitle = null,
        previewImageUrl = null,
        isChecked = false,
        checkedAt = null,
        createdBy = createdBy,
        createdAt = Instant.parse("2026-09-14T10:00:00Z"),
    )

    @Test
    fun `item created by partner is reported as new`() {
        val result = detectNewNoteItems(
            currentItems = listOf(item("1", Person.MELLI)),
            self = Person.BASTI,
            alreadySeenIds = emptySet(),
        )

        assertEquals(listOf("1"), result.newItems.map { it.id })
        assertEquals(setOf("1"), result.updatedSeenIds)
    }

    @Test
    fun `own item is never reported`() {
        val result = detectNewNoteItems(
            currentItems = listOf(item("1", Person.BASTI)),
            self = Person.BASTI,
            alreadySeenIds = emptySet(),
        )

        assertEquals(emptyList<NoteItem>(), result.newItems)
    }

    @Test
    fun `already seen item is not reported again`() {
        val result = detectNewNoteItems(
            currentItems = listOf(item("1", Person.MELLI)),
            self = Person.BASTI,
            alreadySeenIds = setOf("1"),
        )

        assertEquals(emptyList<NoteItem>(), result.newItems)
        assertEquals(setOf("1"), result.updatedSeenIds)
    }
}
