package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.CloudReconfirmResult
import kotlinx.coroutines.flow.StateFlow

interface CloudSessionRepository {
    val accessState: StateFlow<CloudAccessState>
    /** Nicht-interaktiv: Sitzung prüfen, ggf. refreshen oder still neu koppeln. Fehler als CloudSyncException. */
    suspend fun ensureAccess(): Result<Unit>
    /** Interaktiv, nur aus der UI (Activity nötig): bestehendes Google-Konto bestätigen und Supabase neu koppeln. */
    suspend fun reconfirmInteractively(): CloudReconfirmResult
}
