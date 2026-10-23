package com.prehmus.selli.ui.ideen

import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudReconfirmResult
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.CloudSessionRepository
import com.prehmus.selli.domain.repository.NoteRepository
import com.prehmus.selli.ui.calendar.MainDispatcherRule
import com.prehmus.selli.ui.components.CloudIssue
import com.prehmus.selli.ui.components.cloudActionMessage
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdeenViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `failed first load does not present an empty result`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeNotes().apply {
            folderResult = Result.failure(CloudSyncException.ReconfirmationRequired())
        }
        val viewModel = IdeenViewModel(repository, Person.BASTI, FakeSession())
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasLoaded)
        assertEquals(CloudIssue.RECONFIRM, viewModel.uiState.value.loadIssue)
        assertTrue(viewModel.uiState.value.folders.isEmpty())
    }

    @Test
    fun `successful empty load is a real empty result`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = IdeenViewModel(FakeNotes(), Person.BASTI, FakeSession())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasLoaded)
        assertTrue(viewModel.uiState.value.folders.isEmpty())
        assertNull(viewModel.uiState.value.loadIssue)
    }

    @Test
    fun `offline refresh preserves previously loaded folders`() = runTest(mainDispatcherRule.dispatcher) {
        val folder = folder()
        val repository = FakeNotes().apply { folderResult = Result.success(listOf(folder)) }
        val viewModel = IdeenViewModel(repository, Person.BASTI, FakeSession())
        advanceUntilIdle()

        repository.folderResult = Result.failure(CloudSyncException.Offline())
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(folder), viewModel.uiState.value.folders)
        assertTrue(viewModel.uiState.value.hasLoaded)
        assertEquals(CloudIssue.OFFLINE, viewModel.uiState.value.loadIssue)
    }

    @Test
    fun `connected access retries a failed load automatically`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeNotes().apply { folderResult = Result.failure(CloudSyncException.Offline()) }
        val session = FakeSession()
        val viewModel = IdeenViewModel(repository, Person.BASTI, session)
        advanceUntilIdle()
        assertEquals(CloudIssue.OFFLINE, viewModel.uiState.value.loadIssue)

        repository.folderResult = Result.success(listOf(folder()))
        session.state.value = CloudAccessState.CONNECTED
        advanceUntilIdle()

        assertEquals(2, repository.loadCalls)
        assertTrue(viewModel.uiState.value.hasLoaded)
        assertNull(viewModel.uiState.value.loadIssue)
    }

    @Test
    fun `retry checks access and loads again`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeNotes().apply { folderResult = Result.failure(CloudSyncException.Offline()) }
        val session = FakeSession()
        val viewModel = IdeenViewModel(repository, Person.BASTI, session)
        advanceUntilIdle()

        repository.folderResult = Result.success(emptyList())
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(1, session.ensureCalls)
        assertEquals(2, repository.loadCalls)
        assertTrue(viewModel.uiState.value.hasLoaded)
        assertNull(viewModel.uiState.value.loadIssue)
    }

    @Test
    fun `failed add folder shows friendly text without raw token error`() = runTest(mainDispatcherRule.dispatcher) {
        val error = CloudSyncException.ReconfirmationRequired(IllegalArgumentException("Bad ID token"))
        val repository = FakeNotes().apply { addResult = Result.failure(error) }
        val viewModel = IdeenViewModel(repository, Person.BASTI, FakeSession())
        advanceUntilIdle()

        viewModel.createFolder("Reise")
        advanceUntilIdle()

        assertEquals(
            error.cloudActionMessage("Ordner konnte nicht angelegt werden."),
            viewModel.uiState.value.userMessage,
        )
        assertFalse(viewModel.uiState.value.userMessage.orEmpty().contains("Bad ID token"))
    }

    private fun folder() = NoteFolder("folder-1", "Reise", Person.BASTI, Instant.EPOCH)

    private class FakeSession : CloudSessionRepository {
        val state = MutableStateFlow(CloudAccessState.OFFLINE)
        override val accessState: StateFlow<CloudAccessState> = state
        var ensureCalls = 0
        override suspend fun ensureAccess(): Result<Unit> {
            ensureCalls++
            return Result.success(Unit)
        }
        override suspend fun reconfirmInteractively(): CloudReconfirmResult = CloudReconfirmResult.Success
    }

    private class FakeNotes : NoteRepository {
        var folderResult: Result<List<NoteFolder>> = Result.success(emptyList())
        var addResult: Result<NoteFolder> = Result.failure(UnsupportedOperationException())
        var loadCalls = 0
        override suspend fun loadFolders(): List<NoteFolder> = folderResult.getOrDefault(emptyList())
        override suspend fun loadFoldersResult(): Result<List<NoteFolder>> {
            loadCalls++
            return folderResult
        }
        override suspend fun addFolder(name: String, createdBy: Person): Result<NoteFolder> = addResult
        override suspend fun renameFolder(id: String, name: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteFolder(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun loadItems(folderId: String): List<NoteItem> = emptyList()
        override suspend fun loadItemsResult(folderId: String): Result<List<NoteItem>> = Result.success(emptyList())
        override suspend fun addItem(
            folderId: String,
            text: String,
            url: String?,
            createdBy: Person,
        ): Result<NoteItem> = Result.failure(UnsupportedOperationException())
        override suspend fun updateItem(id: String, text: String, url: String?): Result<Unit> = Result.success(Unit)
        override suspend fun setChecked(id: String, checked: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun deleteItem(id: String): Result<Unit> = Result.success(Unit)
    }
}
