package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.SessionState

interface SessionRepository {
    suspend fun sessionState(): SessionState

    /**
     * Setzt nur Sellis eigenen gemerkten Verknüpfungsstatus zurück (eigenes + Partner-Konto
     * vergessen). Das Google-Konto selbst bleibt unberührt.
     */
    suspend fun resetSession()
}
