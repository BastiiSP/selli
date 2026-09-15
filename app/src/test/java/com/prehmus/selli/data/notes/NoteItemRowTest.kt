package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteItemRowTest {
    @Test
    fun `from and toDomain preserve every field including optional ones`() {
        val item = NoteItem(
            id = "item-1",
            folderId = "folder-1",
            text = "Pasta Carbonara",
            url = "https://example.com/pasta",
            previewTitle = "Pasta Carbonara Rezept",
            previewImageUrl = "https://example.com/pasta.jpg",
            isChecked = true,
            checkedAt = Instant.parse("2026-09-14T12:00:00Z"),
            createdBy = Person.BASTI,
            createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        )

        assertEquals(item, NoteItemRow.from(item).toDomain())
    }

    @Test
    fun `from and toDomain handle all-null optional fields`() {
        val item = NoteItem(
            id = "item-2",
            folderId = "folder-1",
            text = "Nur Text",
            url = null,
            previewTitle = null,
            previewImageUrl = null,
            isChecked = false,
            checkedAt = null,
            createdBy = Person.MELLI,
            createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        )

        assertEquals(item, NoteItemRow.from(item).toDomain())
    }

    @Test
    fun `invalid person yields null instead of throwing`() {
        val row = NoteItemRow(
            id = "item-3",
            folderId = "folder-1",
            text = "Kaputt",
            createdBy = "UNKNOWN",
            createdAt = "2026-09-14T10:00:00Z",
        )

        assertNull(row.toDomain())
    }
}
