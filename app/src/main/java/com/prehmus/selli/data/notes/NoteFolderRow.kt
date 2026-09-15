package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.Person
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoteFolderRow(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
) {
    companion object
}

fun NoteFolderRow.toDomain(): NoteFolder? {
    val mappedPerson = runCatching { Person.valueOf(createdBy) }.getOrNull() ?: return null
    val mappedCreatedAt = runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull()
        ?: return null
    return NoteFolder(id = id, name = name, createdBy = mappedPerson, createdAt = mappedCreatedAt)
}

fun NoteFolderRow.Companion.from(folder: NoteFolder): NoteFolderRow = NoteFolderRow(
    id = folder.id,
    name = folder.name,
    createdBy = folder.createdBy.name,
    createdAt = DateTimeFormatter.ISO_INSTANT.format(folder.createdAt),
)
