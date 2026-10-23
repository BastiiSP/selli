package com.prehmus.selli.data.google

import android.accounts.Account
import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Austauschbarer, strikt nicht-interaktiver Zugriff auf GoogleAuthUtil über dessen Credential-Adapter. */
fun interface SilentGoogleIdTokenSource {
    suspend fun fetch(accountEmail: String): String
}

class AndroidSilentGoogleIdTokenSource(
    context: Context,
    private val serverClientId: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SilentGoogleIdTokenSource {
    private val applicationContext = context.applicationContext

    override suspend fun fetch(accountEmail: String): String = withContext(ioDispatcher) {
        check(serverClientId.isNotBlank()) { "Google-Server-Client-ID fehlt." }
        val credential = GoogleAccountCredential.usingAudience(
            applicationContext,
            "server:client_id:$serverClientId",
        )
        // selectedAccountName fragt AccountManager ab und kann trotz Credential-Manager-Login
        // null liefern (Android 8+). Ein explizites Konto braucht diese Sichtbarkeit nicht.
        credential.selectedAccount = Account(accountEmail, "com.google")
        credential.token
    }
}
