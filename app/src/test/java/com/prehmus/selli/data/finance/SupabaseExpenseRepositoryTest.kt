package com.prehmus.selli.data.finance

import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.GoogleIdTokenProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseExpenseRepositoryTest {
    private val repository = SupabaseExpenseRepository(
        SelliSupabaseClient("", "", object : GoogleIdTokenProvider {
            override fun lastGoogleIdToken(): String? = null
            override suspend fun freshGoogleIdTokenSilently(): Result<String> =
                error("Unconfigured client must never request a token")
        }),
    )

    @Test
    fun `loadExpensesResult returns failure rather than a false empty list`() = runTest {
        assertTrue(repository.loadExpensesResult().exceptionOrNull() is CloudSyncException.NotConfigured)
        assertTrue(repository.loadExpenses().isEmpty())
    }

    @Test
    fun `loadSettlementsResult preserves failure and legacy wrapper remains compatible`() = runTest {
        assertTrue(repository.loadSettlementsResult().exceptionOrNull() is CloudSyncException.NotConfigured)
        assertTrue(repository.loadSettlements().isEmpty())
    }

    @Test
    fun `settle propagates load failure instead of succeeding with no expenses`() = runTest {
        assertTrue(repository.settle(Person.BASTI).exceptionOrNull() is CloudSyncException.NotConfigured)
    }
}
