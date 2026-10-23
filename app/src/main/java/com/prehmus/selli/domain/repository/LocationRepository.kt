package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.LocationFeedEvent
import com.prehmus.selli.domain.model.PersonLocation
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

interface LocationRepository {
    /** Liefert Daten oder CloudSyncException; erholt sich nach Fehlern ohne neues Abonnement. */
    fun observeLocationFeed(): Flow<LocationFeedEvent>

    /** Kompatibilität: nur erfolgreiche Listen, kein falscher Leerzustand bei Netzfehlern. */
    fun observeLocations(): Flow<List<PersonLocation>> = observeLocationFeed()
        .filterIsInstance<LocationFeedEvent.Loaded>()
        .map { it.locations }

    /** Setzt nur die eigene Endzeit; false bedeutet, dass noch keine eigene Zeile existiert. */
    suspend fun publishLiveUntil(liveUntil: Instant?): Result<Boolean>

    /** Eigene Position hochladen; Fehler als CloudSyncException, Abbruch wird durchgereicht. */
    suspend fun publishOwnLocation(location: PersonLocation): Result<Unit>
}
