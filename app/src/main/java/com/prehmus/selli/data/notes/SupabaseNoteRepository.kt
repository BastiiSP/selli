package com.prehmus.selli.data.notes

import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.data.supabase.logCloudError
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.notes.LinkPreview
import com.prehmus.selli.domain.notes.LinkPreviewFetcher
import com.prehmus.selli.domain.repository.NoteRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.util.UUID

class SupabaseNoteRepository(
    private val client: SelliSupabaseClient,
    private val linkPreviewFetcher: LinkPreviewFetcher,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) : NoteRepository {

    override suspend fun loadFolders(): List<NoteFolder> =
        loadFoldersResult().getOrElse { emptyList() }

    override suspend fun loadFoldersResult(): Result<List<NoteFolder>> = client.withSession { supabase ->
        supabase.from(FOLDERS_TABLE)
            .select { order("created_at", order = Order.DESCENDING) }
            .decodeList<NoteFolderRow>()
            .mapNotNull(NoteFolderRow::toDomain)
    }

    override suspend fun addFolder(name: String, createdBy: Person): Result<NoteFolder> = client.withSession { supabase ->
        val folder = NoteFolder(
            id = UUID.randomUUID().toString(),
            name = name,
            createdBy = createdBy,
            createdAt = Instant.now(),
        )
        supabase.from(FOLDERS_TABLE).insert(NoteFolderRow.from(folder))
        folder
    }.onFailure { error -> logError(SOURCE_ADD_FOLDER, error) }

    override suspend fun renameFolder(id: String, name: String): Result<Unit> = client.withSession { supabase ->
        supabase.from(FOLDERS_TABLE).update({ set("name", name) }) {
            filter { eq("id", id) }
        }
        Unit
    }.onFailure { error -> logError(SOURCE_RENAME_FOLDER, error) }

    override suspend fun deleteFolder(id: String): Result<Unit> = client.withSession { supabase ->
        supabase.from(FOLDERS_TABLE).delete { filter { eq("id", id) } }
        Unit
    }.onFailure { error -> logError(SOURCE_DELETE_FOLDER, error) }

    override suspend fun loadItems(folderId: String): List<NoteItem> =
        loadItemsResult(folderId).getOrElse { emptyList() }

    override suspend fun loadItemsResult(folderId: String): Result<List<NoteItem>> = client.withSession { supabase ->
        supabase.from(ITEMS_TABLE)
            .select {
                order("created_at", order = Order.DESCENDING)
                filter { eq("folder_id", folderId) }
            }
            .decodeList<NoteItemRow>()
            .mapNotNull(NoteItemRow::toDomain)
    }

    override suspend fun addItem(
        folderId: String,
        text: String,
        url: String?,
        createdBy: Person,
    ): Result<NoteItem> = client.withSession { supabase ->
        val normalizedUrl = normalizeUrl(url)
        // Vorschau vor dem Schreiben laden — schlägt sie fehl/läuft in den Timeout, liefert
        // der Fetcher laut Vertrag `null`, der Punkt wird trotzdem ohne Vorschau gespeichert.
        val preview: LinkPreview? = normalizedUrl?.let { linkPreviewFetcher.fetch(it) }
        val item = NoteItem(
            id = UUID.randomUUID().toString(),
            folderId = folderId,
            text = text,
            url = normalizedUrl,
            previewTitle = preview?.title,
            previewImageUrl = preview?.imageUrl,
            isChecked = false,
            checkedAt = null,
            createdBy = createdBy,
            createdAt = Instant.now(),
        )
        supabase.from(ITEMS_TABLE).insert(NoteItemRow.from(item))
        item
    }.onFailure { error -> logError(SOURCE_ADD_ITEM, error) }

    override suspend fun updateItem(id: String, text: String, url: String?): Result<Unit> = client.withSession { supabase ->
        val normalizedUrl = normalizeUrl(url)
        supabase.from(ITEMS_TABLE).update(
            {
                set("text", text)
                set("url", normalizedUrl)
            },
        ) {
            filter { eq("id", id) }
        }
        Unit
    }.onFailure { error -> logError(SOURCE_UPDATE_ITEM, error) }

    override suspend fun setChecked(id: String, checked: Boolean): Result<Unit> = client.withSession { supabase ->
        val checkedAtValue: String? = if (checked) Instant.now().toString() else null
        supabase.from(ITEMS_TABLE).update(
            {
                set("is_checked", checked)
                set("checked_at", checkedAtValue)
            },
        ) {
            filter { eq("id", id) }
        }
        Unit
    }.onFailure { error -> logError(SOURCE_SET_CHECKED, error) }

    override suspend fun deleteItem(id: String): Result<Unit> = client.withSession { supabase ->
        supabase.from(ITEMS_TABLE).delete { filter { eq("id", id) } }
        Unit
    }.onFailure { error -> logError(SOURCE_DELETE_ITEM, error) }

    private fun logError(source: String, error: Throwable) {
        logCloudError(logger, source, error)
    }

    private fun normalizeUrl(url: String?): String? {
        val nonBlankUrl = url?.takeUnless(String::isBlank) ?: return null
        return if (
            nonBlankUrl.startsWith("http://", ignoreCase = true) ||
            nonBlankUrl.startsWith("https://", ignoreCase = true)
        ) {
            nonBlankUrl
        } else {
            "https://$nonBlankUrl"
        }
    }

    private companion object {
        const val FOLDERS_TABLE = "note_folders"
        const val ITEMS_TABLE = "note_items"
        const val SOURCE_ADD_FOLDER = "Supabase-Ordner anlegen"
        const val SOURCE_RENAME_FOLDER = "Supabase-Ordner umbenennen"
        const val SOURCE_DELETE_FOLDER = "Supabase-Ordner löschen"
        const val SOURCE_ADD_ITEM = "Supabase-Punkt anlegen"
        const val SOURCE_UPDATE_ITEM = "Supabase-Punkt bearbeiten"
        const val SOURCE_SET_CHECKED = "Supabase-Punkt abhaken"
        const val SOURCE_DELETE_ITEM = "Supabase-Punkt löschen"
    }
}
