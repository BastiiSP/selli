package com.prehmus.selli.data.notes

import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.GoogleIdTokenProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseNoteRepositoryTest {
    @Test
    fun `reads and writes retain typed configuration error`() = runTest {
        val client = SelliSupabaseClient("", "", object : GoogleIdTokenProvider {
            override fun lastGoogleIdToken(): String? = null
            override suspend fun freshGoogleIdTokenSilently(): Result<String> = error("must not fetch")
        })
        val repository = SupabaseNoteRepository(client, linkPreviewFetcher = { null })
        assertTrue(repository.loadFoldersResult().exceptionOrNull() is CloudSyncException.NotConfigured)
        assertTrue(repository.loadItemsResult("folder").exceptionOrNull() is CloudSyncException.NotConfigured)
        assertTrue(repository.addFolder("Folder", Person.BASTI).exceptionOrNull() is CloudSyncException.NotConfigured)
        assertTrue(repository.loadFolders().isEmpty())
        assertTrue(repository.loadItems("folder").isEmpty())
    }
}
