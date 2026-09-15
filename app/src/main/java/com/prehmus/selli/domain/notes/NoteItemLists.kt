package com.prehmus.selli.domain.notes

import com.prehmus.selli.domain.model.NoteItem

/** Offene Punkte, neueste zuerst — für die Hauptliste der Ordner-Detailansicht. */
fun List<NoteItem>.openItems(): List<NoteItem> =
    filter { !it.isChecked }.sortedByDescending { it.createdAt }

/** Abgehakte Punkte für den aufklappbaren Archiv-Bereich, zuletzt abgehakt zuerst. */
fun List<NoteItem>.archivedItems(): List<NoteItem> =
    filter { it.isChecked }.sortedByDescending { it.checkedAt ?: it.createdAt }
