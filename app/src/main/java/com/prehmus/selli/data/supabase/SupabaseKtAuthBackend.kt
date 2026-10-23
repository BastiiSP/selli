package com.prehmus.selli.data.supabase

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseKtAuthBackend(private val auth: Auth) : SupabaseAuthBackend {
    override val status: SupabaseAuthStatus get() = auth.sessionStatus.value.toBackendStatus()
    override val statusChanges: Flow<SupabaseAuthStatus> = auth.sessionStatus.map { it.toBackendStatus() }

    override suspend fun awaitInitialization() = auth.awaitInitialization()
    override suspend fun refreshCurrentSession() = auth.refreshCurrentSession()
    override suspend fun clearSession() = auth.clearSession()
    override suspend fun signInWithIdToken(idToken: String) {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }
    }
}

private fun SessionStatus.toBackendStatus(): SupabaseAuthStatus = when (this) {
    SessionStatus.Initializing -> SupabaseAuthStatus.Initializing
    is SessionStatus.Authenticated -> SupabaseAuthStatus.Authenticated(session.expiresAt.toEpochMilliseconds())
    is SessionStatus.NotAuthenticated -> SupabaseAuthStatus.NotAuthenticated
    is SessionStatus.RefreshFailure -> when (cause) {
        is RefreshFailureCause.NetworkError -> SupabaseAuthStatus.NetworkFailure
        is RefreshFailureCause.InternalServerError -> SupabaseAuthStatus.ServerFailure
    }
}
