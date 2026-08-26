package com.prehmus.selli.data.location

import android.content.Context
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.LocationRepository

/**
 * Wird von der App-Verdrahtung beim Prozessstart gesetzt - gleiches Muster wie
 * [com.prehmus.selli.data.widget.WidgetRuntime]. Android startet den Foreground-Service
 * erst nach Application.onCreate, die Factories stehen also zuverlässig bereit.
 */
object LocationRuntime {
    @Volatile
    var repositoryFactory: ((Context) -> LocationRepository)? = null

    /** Welche Person am Gerät angemeldet ist - aus der persistierten Sitzung, daher suspend. */
    @Volatile
    var ownPersonProvider: (suspend (Context) -> Person?)? = null
}
