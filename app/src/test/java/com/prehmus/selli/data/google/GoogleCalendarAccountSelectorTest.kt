package com.prehmus.selli.data.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleCalendarAccountSelectorTest {
    @Test
    fun `selects explicit google account without account manager name lookup`() {
        val credential = AccountManagerLookupFailingCredential()

        GoogleCalendarAccountSelector.select(
            credential = credential,
            accountEmail = "basti@example.test",
        )

        assertEquals(
            GoogleCalendarAccount(
                name = "basti@example.test",
                type = "com.google",
            ),
            credential.recordedSelectedAccount,
        )
        assertNull(credential.lookedUpAccountName)
    }

    private class AccountManagerLookupFailingCredential : GoogleCalendarCredential {
        var recordedSelectedAccount: GoogleCalendarAccount? = null
        var lookedUpAccountName: String? = null

        override fun setSelectedAccount(account: GoogleCalendarAccount) {
            recordedSelectedAccount = account
        }

        @Suppress("unused")
        fun setSelectedAccountName(accountName: String) {
            lookedUpAccountName = accountName
            throw AssertionError("AccountManager lookup must not be used")
        }
    }
}
