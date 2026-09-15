package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoteItemRow(
    @SerialName("id") val id: String,
    @SerialName("folder_id") val folderId: String,
    @SerialName("text") val text: String,
    @SerialName("url") val url: String? = null,
    @SerialName("preview_title") val previewTitle: String? = null,
    @SerialName("preview_image_url") val previewImageUrl: String? = null,
    @SerialName("is_checked") val isChecked: Boolean = false,
    @SerialName("checked_at") val checkedAt: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
) {
    companion object
}

fun NoteItemRow.toDomain(): NoteItem? {
    val mappedPerson = runCatching { Person.valueOf(createdBy) }.getOrNull() ?: return null
    val mappedCreatedAt = runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull()
        ?: return null
    val mappedCheckedAt = checkedAt?.let { raw ->
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    }

    return NoteItem(
        id = id,
        folderId = folderId,
        text = text,
        url = url,
        previewTitle = previewTitle,
        previewImageUrl = previewImageUrl,
        isChecked = isChecked,
        checkedAt = mappedCheckedAt,
        createdBy = mappedPerson,
        createdAt = mappedCreatedAt,
    )
}

fun NoteItemRow.Companion.from(item: NoteItem): NoteItemRow = NoteItemRow(
    id = item.id,
    folderId = item.folderId,
    text = item.text,
    url = item.url,
    previewTitle = item.previewTitle,
    previewImageUrl = item.previewImageUrl,
    isChecked = item.isChecked,
    checkedAt = item.checkedAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
    createdBy = item.createdBy.name,
    createdAt = DateTimeFormatter.ISO_INSTANT.format(item.createdAt),
)
