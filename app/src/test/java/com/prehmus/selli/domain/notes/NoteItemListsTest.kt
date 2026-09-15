package com.prehmus.selli.domain.notes

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteItemListsTest {
    private fun item(
        id: String,
        isChecked: Boolean,
        createdAt: Instant,
        checkedAt: Instant? = null,
    ) = NoteItem(
        id = id,
        folderId = "folder-1",
        text = "Punkt $id",
        url = null,
        previewTitle = null,
        previewImageUrl = null,
        isChecked = isChecked,
        checkedAt = checkedAt,
        createdBy = Person.BASTI,
        createdAt = createdAt,
    )

    @Test
    fun `openItems excludes checked items and sorts newest first`() {
        val items = listOf(
            item("1", isChecked = false, createdAt = Instant.parse("2026-09-01T10:00:00Z")),
            item("2", isChecked = true, createdAt = Instant.parse("2026-09-02T10:00:00Z")),
            item("3", isChecked = false, createdAt = Instant.parse("2026-09-03T10:00:00Z")),
        )

        assertEquals(listOf("3", "1"), items.openItems().map { it.id })
    }

    @Test
    fun `archivedItems includes only checked items sorted by checkedAt`() {
        val items = listOf(
            item("1", isChecked = true, createdAt = Instant.parse("2026-09-01T10:00:00Z"), checkedAt = Instant.parse("2026-09-05T10:00:00Z")),
            item("2", isChecked = false, createdAt = Instant.parse("2026-09-02T10:00:00Z")),
            item("3", isChecked = true, createdAt = Instant.parse("2026-09-03T10:00:00Z"), checkedAt = Instant.parse("2026-09-10T10:00:00Z")),
        )

        assertEquals(listOf("3", "1"), items.archivedItems().map { it.id })
    }

    @Test
    fun `archivedItems falls back to createdAt when checkedAt is missing`() {
        val items = listOf(
            item("1", isChecked = true, createdAt = Instant.parse("2026-09-01T10:00:00Z"), checkedAt = null),
        )

        assertEquals(listOf("1"), items.archivedItems().map { it.id })
    }
}
