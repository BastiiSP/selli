package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.PersonLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    /**
     * Aktuelle Positionen beider Personen, live nachgeliefert. Emittiert bei fehlender
     * Konfiguration oder fehlgeschlagener Anmeldung eine leere Liste statt zu werfen.
     */
    fun observeLocations(): Flow<List<PersonLocation>>

    /** Eigene Position hochladen (Upsert auf die eigene Zeile). Wirft nie. */
    suspend fun publishOwnLocation(location: PersonLocation): Result<Unit>
}
