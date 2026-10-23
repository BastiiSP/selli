package com.prehmus.selli.data.supabase

import kotlinx.coroutines.flow.Flow

/** SDK-unabhängiger Blick auf die Sitzung; niemals Initializing als abgemeldet interpretieren. */
sealed interface SupabaseAuthStatus {
    data object Initializing : SupabaseAuthStatus
    data class Authenticated(val expiresAtMillis: Long) : SupabaseAuthStatus
    data object NotAuthenticated : SupabaseAuthStatus
    data object NetworkFailure : SupabaseAuthStatus
    data object ServerFailure : SupabaseAuthStatus
}

interface SupabaseAuthBackend {
    val status: SupabaseAuthStatus
    val statusChanges: Flow<SupabaseAuthStatus>
    suspend fun awaitInitialization()
    suspend fun refreshCurrentSession()
    suspend fun signInWithIdToken(idToken: String)
    suspend fun clearSession()
}
