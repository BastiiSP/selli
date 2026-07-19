package com.prehmus.selli.domain.model

sealed interface SessionState {
    data object SignedOut : SessionState

    /** Eigenes Konto bekannt, Partner-Verknüpfung noch nicht abgeschlossen. */
    data class NeedsPartner(val ownAccount: Account) : SessionState

    /** Vollständig verknüpft — direkt zur Kalenderansicht. */
    data class Linked(val ownAccount: Account, val partnerAccount: Account) : SessionState
}
