package com.prehmus.selli

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.prehmus.selli.data.customization.FileEventCustomizationRepository
import com.prehmus.selli.data.google.GoogleCalendarDataRepository
import com.prehmus.selli.data.ics.IcsCalendarParser
import com.prehmus.selli.data.ics.OkHttpIcsCalendarRepository
import com.prehmus.selli.data.logging.AndroidCalendarLogger
import com.prehmus.selli.data.location.LocationRuntime
import com.prehmus.selli.data.location.SupabaseLocationRepository
import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.data.widget.WidgetRefreshScheduler
import com.prehmus.selli.data.widget.WidgetRuntime
import com.prehmus.selli.domain.merge.DefaultCalendarMergeService
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.SessionState
import com.prehmus.selli.ui.widget.SelliWidget
import java.util.concurrent.atomic.AtomicReference

/**
 * Prozess-Einstieg: verdrahtet die Hintergrund-Datenversorgung für Widget und Standort.
 * Android startet Worker und Services immer erst nach [onCreate], deshalb sind die Hooks hier
 * zuverlässig gesetzt — auch wenn keine Activity den Prozess weckt.
 */
class SelliApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        WidgetRuntime.mergeServiceFactory = { context ->
            DefaultCalendarMergeService(
                // Ohne Activity: der Widget-Worker liest nur (kein Sign-in nötig);
                // der personResolver greift ausschließlich beim Sign-in und bleibt hier ungenutzt.
                GoogleCalendarDataRepository(
                    context = context,
                    personResolver = { Person.BASTI },
                ),
                OkHttpIcsCalendarRepository(),
                customizationRepository = FileEventCustomizationRepository(context = context),
                logger = AndroidCalendarLogger,
                // Ohne diesen Feed fehlten Mellis Arbeitstermine bislang komplett im Widget —
                // dieselbe Verdrahtung wie in DefaultAppDependencies.kt für die Haupt-App.
                melliIcsCalendarRepository = OkHttpIcsCalendarRepository(
                    feedUrl = BuildConfig.MELLI_ICS_FEED_URL,
                    parser = IcsCalendarParser(owner = Person.MELLI, preferCalendarNameAsLocation = true),
                ),
            )
        }
        WidgetRuntime.onSnapshotUpdated = { context -> SelliWidget().updateAll(context) }

        val locationGoogleRepository = GoogleCalendarDataRepository(
            context = this,
            personResolver = { Person.BASTI },
            logger = AndroidCalendarLogger,
        )
        val locationOwnPerson = AtomicReference<Person?>(null)
        LocationRuntime.ownPersonProvider = {
            when (val session = locationGoogleRepository.sessionState()) {
                is SessionState.Linked -> session.ownAccount.person.also(locationOwnPerson::set)
                else -> null
            }
        }
        LocationRuntime.repositoryFactory = {
            SupabaseLocationRepository(
                client = SelliSupabaseClient(
                    supabaseUrl = BuildConfig.SUPABASE_URL,
                    supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
                    idTokenProvider = locationGoogleRepository,
                    logger = AndroidCalendarLogger,
                ),
                ownPerson = {
                    checkNotNull(locationOwnPerson.get()) {
                        "Standort-Repository wurde vor der Sitzungsauflösung angefordert."
                    }
                },
                logger = AndroidCalendarLogger,
            )
        }

        WidgetRefreshScheduler.ensureScheduled(this)
    }
}
