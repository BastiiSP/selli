package com.prehmus.selli.data.google

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.credentials.CustomCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.AclRule
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.Event.ExtendedProperties
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import com.prehmus.selli.BuildConfig
import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.domain.model.AuthResult
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.NewCalendarEvent
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.CalendarRepository
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import java.time.LocalDateTime
import java.time.ZoneId

class GoogleCalendarDataRepository(
    context: Context,
    private val activity: Activity,
    private val personResolver: (email: String) -> Person,
    private val serviceFactory: GoogleCalendarServiceFactory = AndroidGoogleCalendarServiceFactory(context),
    private val mapper: GoogleCalendarEventMapper = GoogleCalendarEventMapper(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    ),
) : GoogleCalendarRepository, CalendarRepository {
    private val credentialManager = CredentialManager.create(context.applicationContext)

    override suspend fun signIn(): AuthResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .build(),
            )
            .build()

        return try {
            val response = credentialManager.getCredential(activity, request)
            val customCredential = response.credential as? CustomCredential
                ?: return AuthResult.Error("Google sign-in returned an unsupported credential type.")
            if (customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return AuthResult.Error("Google sign-in returned an unsupported credential type.")
            }

            val credential = GoogleIdTokenCredential.createFrom(customCredential.data)
            val email = credential.email
                ?: return AuthResult.Error("Google sign-in did not return an email address.")
            val account = Account(
                id = credential.uniqueId ?: credential.id ?: email,
                email = email,
                displayName = credential.displayName ?: email,
                person = personResolver(email),
            )
            persistAccount(OWN_PREFIX, account)
            AuthResult.Success(account)
        } catch (cancelled: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (parseError: GoogleIdTokenParsingException) {
            AuthResult.Error("Google sign-in response could not be parsed.", parseError)
        } catch (credentialError: GetCredentialException) {
            AuthResult.Error("Google sign-in failed.", credentialError)
        } catch (error: Throwable) {
            AuthResult.Error("Google sign-in failed.", error)
        }
    }

    /**
     * Grants the partner read access to the signed-in user's primary Google Calendar.
     *
     * Selli stores the partner account locally after this succeeds, because follow-up reads and
     * writes need the partner calendar ID and invite email. Mutual sharing is intentionally not a
     * remote two-sided operation: Basti and Melli each run this method on their own device/account,
     * so each primary calendar gets an ACL rule for the other person.
     */
    override suspend fun grantMutualAccess(ownAccount: Account, partnerAccount: Account): Result<Unit> =
        runCatching {
            val aclRule = AclRule()
                .setRole("reader")
                .setScope(
                    AclRule.Scope()
                        .setType("user")
                        .setValue(partnerAccount.email),
                )

            try {
                calendar(ownAccount.email).acl().insert(PRIMARY_CALENDAR_ID, aclRule).execute()
            } catch (error: GoogleJsonResponseException) {
                if (error.statusCode != HTTP_CONFLICT) {
                    throw error
                }
            }

            persistAccount(OWN_PREFIX, ownAccount)
            persistAccount(PARTNER_PREFIX, partnerAccount)
        }

    override suspend fun fetchEvents(range: DateRange): List<CalendarEvent> {
        val ownAccount = requireStoredAccount(OWN_PREFIX)
        val partnerAccount = requireStoredAccount(PARTNER_PREFIX)
        val service = calendar(ownAccount.email)
        val timeMin = range.start.atStartOfDay().toGoogleDateTime()
        val timeMax = range.endInclusive.plusDays(1).atStartOfDay().toGoogleDateTime()

        val ownEvents = service.fetchEvents(
            calendarId = PRIMARY_CALENDAR_ID,
            timeMin = timeMin,
            timeMax = timeMax,
        ).map { event ->
            mapper.toCalendarEvent(
                event = event,
                source = CalendarSource.GOOGLE_OWN,
                owner = ownAccount.person,
                ownEmail = ownAccount.email,
                partnerEmail = partnerAccount.email,
            )
        }

        val partnerEvents = service.fetchEvents(
            calendarId = partnerAccount.email,
            timeMin = timeMin,
            timeMax = timeMax,
        ).map { event ->
            mapper.toCalendarEvent(
                event = event,
                source = CalendarSource.GOOGLE_PARTNER,
                owner = partnerAccount.person,
                ownEmail = ownAccount.email,
                partnerEmail = partnerAccount.email,
            )
        }

        return ownEvents + partnerEvents
    }

    override suspend fun createEvent(event: NewCalendarEvent): Result<CalendarEvent> =
        runCatching {
            val ownAccount = requireStoredAccount(OWN_PREFIX)
            val partnerAccount = storedAccount(PARTNER_PREFIX)
            val inserted = calendar(ownAccount.email)
                .events()
                .insert(PRIMARY_CALENDAR_ID, event.toGoogleEvent(partnerAccount?.email))
                .execute()

            mapper.toCalendarEvent(
                event = inserted,
                source = CalendarSource.GOOGLE_OWN,
                owner = ownAccount.person,
                ownEmail = ownAccount.email,
                partnerEmail = partnerAccount?.email,
            )
        }

    private fun calendar(accountEmail: String): Calendar = serviceFactory.create(accountEmail)

    private fun Calendar.fetchEvents(
        calendarId: String,
        timeMin: DateTime,
        timeMax: DateTime,
    ): List<Event> {
        val fetchedEvents = mutableListOf<Event>()
        var pageToken: String? = null

        do {
            val response = events()
                .list(calendarId)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setPageToken(pageToken)
                .execute()

            fetchedEvents += response.items.orEmpty()
            pageToken = response.nextPageToken
        } while (pageToken != null)

        return fetchedEvents
    }

    private fun NewCalendarEvent.toGoogleEvent(partnerEmail: String?): Event {
        val googleEvent = Event()
            .setSummary(title)
            .setLocation(location)
            .setDescription(description)
            .setStart(start.toEventDateTime(isAllDay))
            .setEnd(end.toEventDateTime(isAllDay))

        if (invitePartner) {
            require(!partnerEmail.isNullOrBlank()) {
                "Partner account is required before creating a shared Google Calendar event."
            }
            googleEvent
                .setAttendees(listOf(EventAttendee().setEmail(partnerEmail)))
                .setExtendedProperties(
                    ExtendedProperties()
                        .setShared(mapOf(GoogleCalendarEventMapper.SELLI_SHARED_PROPERTY to "true")),
                )
        }

        return googleEvent
    }

    private fun LocalDateTime.toEventDateTime(isAllDay: Boolean): EventDateTime =
        if (isAllDay) {
            EventDateTime().setDate(DateTime(toLocalDate().toString()))
        } else {
            EventDateTime()
                .setDateTime(toGoogleDateTime())
                .setTimeZone(zoneId.id)
        }

    private fun LocalDateTime.toGoogleDateTime(): DateTime =
        DateTime(atZone(zoneId).toInstant().toEpochMilli())

    private fun persistAccount(prefix: String, account: Account) {
        preferences.edit()
            .putString("$prefix$ID_SUFFIX", account.id)
            .putString("$prefix$EMAIL_SUFFIX", account.email)
            .putString("$prefix$DISPLAY_NAME_SUFFIX", account.displayName)
            .putString("$prefix$PERSON_SUFFIX", account.person.name)
            .apply()
    }

    private fun requireStoredAccount(prefix: String): Account =
        requireNotNull(storedAccount(prefix)) {
            "Missing stored Google account for '$prefix'. Call signIn and grantMutualAccess first."
        }

    private fun storedAccount(prefix: String): Account? {
        val id = preferences.getString("$prefix$ID_SUFFIX", null) ?: return null
        val email = preferences.getString("$prefix$EMAIL_SUFFIX", null) ?: return null
        val displayName = preferences.getString("$prefix$DISPLAY_NAME_SUFFIX", null) ?: email
        val personName = preferences.getString("$prefix$PERSON_SUFFIX", null) ?: return null

        return Account(
            id = id,
            email = email,
            displayName = displayName,
            person = Person.valueOf(personName),
        )
    }

    private companion object {
        const val PREFS_NAME = "selli_google_calendar"
        const val OWN_PREFIX = "own_"
        const val PARTNER_PREFIX = "partner_"
        const val PRIMARY_CALENDAR_ID = "primary"
        const val HTTP_CONFLICT = 409
        const val ID_SUFFIX = "id"
        const val EMAIL_SUFFIX = "email"
        const val DISPLAY_NAME_SUFFIX = "display_name"
        const val PERSON_SUFFIX = "person"
    }
}
