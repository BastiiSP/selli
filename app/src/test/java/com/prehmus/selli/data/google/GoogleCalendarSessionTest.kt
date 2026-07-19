package com.prehmus.selli.data.google

import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleCalendarSessionTest {
    private val ownAccount = Account(
        id = "own-id",
        email = "basti@example.test",
        displayName = "Basti",
        person = Person.BASTI,
    )
    private val partnerAccount = Account(
        id = "partner-id",
        email = "melli@example.test",
        displayName = "Melli",
        person = Person.MELLI,
    )

    @Test
    fun `session is signed out without a complete own account`() {
        assertEquals(SessionState.SignedOut, sessionState(null, partnerAccount))
    }

    @Test
    fun `session needs partner when only own account is complete`() {
        assertEquals(SessionState.NeedsPartner(ownAccount), sessionState(ownAccount, null))
    }

    @Test
    fun `session is linked when both accounts are complete`() {
        assertEquals(
            SessionState.Linked(ownAccount, partnerAccount),
            sessionState(ownAccount, partnerAccount),
        )
    }

    @Test
    fun `reset selects every own and partner preference key only`() {
        assertEquals(
            setOf("own_id", "own_custom", "partner_email", "partner_custom"),
            sessionKeysToReset(
                setOf(
                    "own_id",
                    "own_custom",
                    "partner_email",
                    "partner_custom",
                    "unrelated_key",
                ),
            ),
        )
    }
}
