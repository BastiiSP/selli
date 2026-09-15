package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteFolderRowTest {
    @Test
    fun `from and toDomain preserve every field`() {
        val folder = NoteFolder(
            id = "folder-1",
            name = "Rezepte",
            createdBy = Person.MELLI,
            createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        )

        assertEquals(folder, NoteFolderRow.from(folder).toDomain())
    }

    @Test
    fun `invalid person yields null instead of throwing`() {
        val row = NoteFolderRow(
            id = "folder-2",
            name = "Kaputt",
            createdBy = "UNKNOWN",
            createdAt = "2026-09-14T10:00:00Z",
        )

        assertNull(row.toDomain())
    }
}
