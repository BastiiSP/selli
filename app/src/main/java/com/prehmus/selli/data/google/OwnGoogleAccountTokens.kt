package com.prehmus.selli.data.google

import com.prehmus.selli.domain.model.CloudSyncException
import com.prehmus.selli.domain.repository.GoogleReconfirmation
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Persistiert ausschließlich Tokens; besitzt absichtlich keinen Zugriff auf Person, Partner oder ACL. */
internal class OwnGoogleAccountTokens(
    private val ownEmail: () -> String?,
    private val persistToken: (String) -> Unit,
    private val silentSource: SilentGoogleIdTokenSource,
) {
    suspend fun freshSilently(): Result<String> = try {
        val expectedEmail = ownEmail()?.takeUnless(String::isBlank)
            ?: throw CloudSyncException.ReconfirmationRequired()
        val token = silentSource.fetch(expectedEmail).takeUnless(String::isBlank)
            ?: throw CloudSyncException.ReconfirmationRequired()
        // Konto könnte während der suspendierenden Google-Anfrage gewechselt worden sein.
        if (!expectedEmail.equals(ownEmail(), ignoreCase = true)) {
            throw CloudSyncException.ReconfirmationRequired()
        }
        persistToken(token)
        Result.success(token)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        // UserRecoverableAuthException ist nicht direkt im App-Classpath auflösbar.
        Result.failure(
            when (error) {
                is CloudSyncException -> error
                is IOException -> CloudSyncException.Offline(error)
                else -> CloudSyncException.ReconfirmationRequired(error)
            },
        )
    }

    fun acceptReconfirmation(expectedEmail: String, selectedEmail: String?, idToken: String): GoogleReconfirmation {
        if (!expectedEmail.equals(selectedEmail, ignoreCase = true)) {
            return GoogleReconfirmation.WrongAccount(expectedEmail)
        }
        if (!expectedEmail.equals(ownEmail(), ignoreCase = true) || idToken.isBlank()) {
            return GoogleReconfirmation.Failed(CloudSyncException.ReconfirmationRequired())
        }
        persistToken(idToken)
        return GoogleReconfirmation.Success(idToken)
    }
}
