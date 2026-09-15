package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person

interface NoteRepository {
    /** Alle Ordner, neueste zuerst. Liefert bei Problemen eine leere Liste statt zu werfen. */
    suspend fun loadFolders(): List<NoteFolder>

    /**
     * Wie [loadFolders], meldet einen echten Ladefehler (z. B. offline) aber als [Result.failure]
     * statt ihn zu einer leeren Liste zu tarnen. Für Aufrufer, die "wirklich keine Ordner" nicht
     * mit "Laden ist fehlgeschlagen" verwechseln dürfen — sonst überschreibt ein einzelner
     * Netzwerk-Hänger eine bereits geladene, nicht-leere Ansicht mit einem falschen Leerzustand.
     */
    suspend fun loadFoldersResult(): Result<List<NoteFolder>>

    suspend fun addFolder(name: String, createdBy: Person): Result<NoteFolder>
    suspend fun renameFolder(id: String, name: String): Result<Unit>

    /** `on delete cascade` in der Migration räumt zugehörige `note_items` serverseitig mit auf. */
    suspend fun deleteFolder(id: String): Result<Unit>

    /** Alle Punkte eines Ordners, neueste zuerst. Liefert bei Problemen eine leere Liste. */
    suspend fun loadItems(folderId: String): List<NoteItem>

    /** Wie [loadItems], aber siehe [loadFoldersResult] — Ladefehler bleiben als solche erkennbar. */
    suspend fun loadItemsResult(folderId: String): Result<List<NoteItem>>

    /** Legt einen Punkt an; lädt bei gesetzter [url] zuerst die Link-Vorschau (siehe Task 12). */
    suspend fun addItem(
        folderId: String,
        text: String,
        url: String?,
        createdBy: Person,
    ): Result<NoteItem>

    suspend fun updateItem(id: String, text: String, url: String?): Result<Unit>
    suspend fun setChecked(id: String, checked: Boolean): Result<Unit>
    suspend fun deleteItem(id: String): Result<Unit>
}
