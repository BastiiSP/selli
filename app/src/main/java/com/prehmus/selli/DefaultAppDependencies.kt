package com.prehmus.selli

import androidx.activity.ComponentActivity
import com.prehmus.selli.data.customization.FileEventCustomizationRepository
import com.prehmus.selli.data.google.GoogleCalendarDataRepository
import com.prehmus.selli.data.ics.IcsCalendarParser
import com.prehmus.selli.data.ics.OkHttpIcsCalendarRepository
import com.prehmus.selli.data.logging.AndroidCalendarLogger
import com.prehmus.selli.data.places.PlacesAutocompleteRepository
import com.prehmus.selli.data.places.readAndroidAppIdentity
import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.merge.DefaultCalendarMergeService
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.CalendarRepository
import com.prehmus.selli.domain.repository.EventCustomizationRepository
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import com.prehmus.selli.domain.repository.PlaceSuggestionRepository
import com.prehmus.selli.domain.repository.SessionRepository
import java.util.concurrent.atomic.AtomicReference

/**
 * Produktive Verdrahtung der Codex-Implementierungen. Die Zuordnung, wer die
 * angemeldete Person ist, trifft die UI ("Wer bist du?" im Sign-in) und meldet
 * sie über [rememberOwnPerson], bevor der Sign-in läuft — der personResolver
 * der Google-Anbindung liest den zuletzt gemerkten Wert.
 */
class DefaultAppDependencies(activity: ComponentActivity) : AppDependencies {

    private val ownPerson = AtomicReference(Person.BASTI)

    fun rememberOwnPerson(person: Person) {
        ownPerson.set(person)
    }

    private val googleRepository = GoogleCalendarDataRepository(
        context = activity.applicationContext,
        activity = activity,
        personResolver = { ownPerson.get() },
        logger = AndroidCalendarLogger,
    )

    override val googleCalendarRepository: GoogleCalendarRepository = googleRepository

    override val icsCalendarRepository: IcsCalendarRepository = OkHttpIcsCalendarRepository()

    private val melliIcsCalendarRepository: IcsCalendarRepository =
        OkHttpIcsCalendarRepository(
            feedUrl = BuildConfig.MELLI_ICS_FEED_URL,
            parser = IcsCalendarParser(owner = Person.MELLI, preferCalendarNameAsLocation = true),
        )

    // Lokale Ausblendungen/Anpassungen: liegen nur auf dem Gerät und werden im
    // Merge über die frischen Rohdaten gelegt — der Google-Kalender bleibt unberührt.
    override val eventCustomizationRepository: EventCustomizationRepository =
        FileEventCustomizationRepository(context = activity.applicationContext)

    override val calendarMergeService: CalendarMergeService =
        DefaultCalendarMergeService(
            googleCalendarRepository = googleRepository,
            icsCalendarRepository = icsCalendarRepository,
            customizationRepository = eventCustomizationRepository,
            logger = AndroidCalendarLogger,
            melliIcsCalendarRepository = melliIcsCalendarRepository,
        )

    override val calendarRepository: CalendarRepository = googleRepository

    // Die Google-Anbindung persistiert die Konten bereits — sie ist zugleich die Session-Quelle.
    override val sessionRepository: SessionRepository = googleRepository

    // Adressvorschläge fürs Ortsfeld. Ohne hinterlegten Schlüssel bleibt die Liste leer,
    // das Feld verhält sich dann wie ein normales Textfeld.
    //
    // Der Places-Schlüssel ist in der Cloud Console auf diese App beschränkt. Google
    // erkennt sie nur, wenn jede Anfrage Paketname und Signatur-Fingerabdruck
    // mitschickt — ohne die beiden Werte antwortet die API mit 403. Beide werden zur
    // Laufzeit aus der eigenen Signatur gelesen, damit ein späterer Release-Keystore
    // automatisch mitgeht, statt hier hart zu stehen.
    private val appIdentity = readAndroidAppIdentity(activity.applicationContext)

    override val placeSuggestionRepository: PlaceSuggestionRepository =
        PlacesAutocompleteRepository(
            apiKey = BuildConfig.PLACES_API_KEY,
            androidPackageName = appIdentity?.packageName,
            androidCertSha1 = appIdentity?.certSha1,
            logger = AndroidCalendarLogger,
        )
}
