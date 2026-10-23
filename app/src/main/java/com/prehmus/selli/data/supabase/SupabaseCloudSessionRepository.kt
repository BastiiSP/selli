package com.prehmus.selli.data.supabase

import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudReconfirmResult
import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.repository.CloudSessionRepository
import com.prehmus.selli.domain.repository.GoogleReconfirmation
import com.prehmus.selli.domain.repository.InteractiveGoogleIdTokenProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

class SupabaseCloudSessionRepository(
    private val client: SelliSupabaseClient,
    private val interactive: InteractiveGoogleIdTokenProvider?,
) : CloudSessionRepository {
    override val accessState: StateFlow<CloudAccessState> get() = client.accessState

    // Expliziter UI-Retry umgeht die Drosselung. Hintergrundzugriffe verwenden withSession().
    override suspend fun ensureAccess(): Result<Unit> = client.ensureSignedIn(force = true)

    override suspend fun reconfirmInteractively(): CloudReconfirmResult {
        val provider = interactive
            ?: return CloudReconfirmResult.Failed(CloudSyncException.ReconfirmationRequired())
        if (!client.isConfigured) return CloudReconfirmResult.Failed(CloudSyncException.NotConfigured())
        return try {
            when (val result = provider.reconfirmOwnGoogleAccount()) {
                is GoogleReconfirmation.Success -> client.signInWithGoogleIdToken(result.idToken).fold(
                    onSuccess = { CloudReconfirmResult.Success },
                    onFailure = { CloudReconfirmResult.Failed(classifyCloudError(it)) },
                )
                GoogleReconfirmation.Cancelled -> CloudReconfirmResult.Cancelled
                is GoogleReconfirmation.WrongAccount -> CloudReconfirmResult.WrongAccount(result.expectedEmail)
                is GoogleReconfirmation.Failed -> CloudReconfirmResult.Failed(classifyCloudError(result.cause))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            CloudReconfirmResult.Failed(classifyCloudError(error))
        }
    }
}
