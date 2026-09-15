package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person

data class NewNoteItemDetectionResult(
    val newItems: List<NoteItem>,
    val updatedSeenIds: Set<String>,
)

/** Erkennt Ideen-Punkte, die der Partner neu angelegt hat und noch nicht gemeldet wurden. */
fun detectNewNoteItems(
    currentItems: List<NoteItem>,
    self: Person,
    alreadySeenIds: Set<String>,
): NewNoteItemDetectionResult {
    val newFromPartner = currentItems.filter { item ->
        item.createdBy != self && item.id !in alreadySeenIds
    }
    return NewNoteItemDetectionResult(
        newItems = newFromPartner,
        updatedSeenIds = currentItems.map { it.id }.toSet(),
    )
}
