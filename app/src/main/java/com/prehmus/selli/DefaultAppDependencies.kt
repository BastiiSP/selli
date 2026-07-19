package com.prehmus.selli

import androidx.activity.ComponentActivity
import com.prehmus.selli.data.google.GoogleCalendarDataRepository
import com.prehmus.selli.data.ics.OkHttpIcsCalendarRepository
import com.prehmus.selli.data.logging.AndroidCalendarLogger
import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.merge.DefaultCalendarMergeService
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.CalendarRepository
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository
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

    override val calendarMergeService: CalendarMergeService =
        DefaultCalendarMergeService(
            googleCalendarRepository = googleRepository,
            icsCalendarRepository = icsCalendarRepository,
            logger = AndroidCalendarLogger,
        )

    override val calendarRepository: CalendarRepository = googleRepository
}
