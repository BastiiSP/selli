package com.prehmus.selli.domain.model

import java.time.Instant

data class NoteFolder(
    val id: String,
    val name: String,
    val createdBy: Person,
    val createdAt: Instant,
)

data class NoteItem(
    val id: String,
    val folderId: String,
    val text: String,
    val url: String?,
    val previewTitle: String?,
    val previewImageUrl: String?,
    val isChecked: Boolean,
    val checkedAt: Instant?,
    val createdBy: Person,
    val createdAt: Instant,
)
