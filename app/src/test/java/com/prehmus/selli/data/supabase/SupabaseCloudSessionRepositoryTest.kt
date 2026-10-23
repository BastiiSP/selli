package com.prehmus.selli.data.supabase

import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudReconfirmResult
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.repository.GoogleIdTokenProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseCloudSessionRepositoryTest {
    @Test
    fun `background repository cannot open interactive sign in`() = runTest {
        val client = SelliSupabaseClient("", "", object : GoogleIdTokenProvider {
            override fun lastGoogleIdToken(): String? = null
            override suspend fun freshGoogleIdTokenSilently(): Result<String> = error("must not fetch")
        })
        val repository = SupabaseCloudSessionRepository(client, interactive = null)
        val result = repository.reconfirmInteractively()
        assertTrue(result is CloudReconfirmResult.Failed)
        assertTrue((result as CloudReconfirmResult.Failed).error is CloudSyncException.ReconfirmationRequired)
        assertEquals(CloudAccessState.NOT_CONFIGURED, repository.accessState.value)
        assertTrue(repository.ensureAccess().exceptionOrNull() is CloudSyncException.NotConfigured)
    }
}
