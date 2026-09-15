package com.prehmus.selli.ui.preview

import com.prehmus.selli.AppDependencies
import com.prehmus.selli.domain.CalendarMergeService
import com.prehmus.selli.domain.finance.prepareSettlement
import com.prehmus.selli.domain.model.Account
import com.prehmus.selli.domain.model.AuthResult
import com.prehmus.selli.domain.model.CalendarEvent
import com.prehmus.selli.domain.model.CalendarSource
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.DateRange
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.FreeTimeBlock
import com.prehmus.selli.domain.model.NewCalendarEvent
import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.PersonLocation
import com.prehmus.selli.domain.model.SessionState
import com.prehmus.selli.domain.model.Settlement
import com.prehmus.selli.domain.places.LocationSuggestion
import com.prehmus.selli.domain.repository.CalendarRepository
import com.prehmus.selli.domain.repository.EventCustomizationRepository
import com.prehmus.selli.domain.repository.ExpenseRepository
import com.prehmus.selli.domain.repository.GoogleCalendarRepository
import com.prehmus.selli.domain.repository.IcsCalendarRepository
import com.prehmus.selli.domain.repository.LocationRepository
import com.prehmus.selli.domain.repository.NoteRepository
import com.prehmus.selli.domain.repository.PlaceSuggestionRepository
import com.prehmus.selli.domain.repository.SessionRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-Memory-Fakes für Compose-Previews und für die Übergangszeit, bis die
 * echten Codex-Implementierungen verdrahtet sind. Liefert Beispieltermine rund
 * um den heutigen Tag, damit Grid, Pills und Maskottchen-Reaktion sichtbar sind.
 */
class PreviewDependencies : AppDependencies {

    private val createdEvents = mutableListOf<CalendarEvent>()
    private val idCounter = AtomicInteger()

    private fun sampleEvents(): List<CalendarEvent> {
        val today = LocalDate.now()
        return listOf(
            CalendarEvent(
                id = "sample-1",
                title = "Zahnärztin",
                start = today.atTime(9, 30),
                end = today.atTime(10, 15),
                isAllDay = false,
                source = CalendarSource.GOOGLE_PARTNER,
                owner = Person.MELLI,
                isSharedEvent = false,
            ),
            CalendarEvent(
                id = "sample-2",
                title = "Sprint Review",
                start = today.atTime(14, 0),
                end = today.atTime(15, 0),
                isAllDay = false,
                source = CalendarSource.WORK_ICS,
                owner = Person.BASTI,
                isSharedEvent = false,
                location = "Teams",
            ),
            CalendarEvent(
                id = "sample-3",
                title = "Gemeinsames Wochenende",
                start = today.plusDays(4).atStartOfDay(),
                end = today.plusDays(6).atStartOfDay(),
                isAllDay = true,
                source = CalendarSource.GOOGLE_OWN,
                owner = Person.BASTI,
                isSharedEvent = true,
            ),
            CalendarEvent(
                id = "sample-4",
                title = "Yoga",
                start = today.plusDays(1).atTime(18, 30),
                end = today.plusDays(1).atTime(19, 30),
                isAllDay = false,
                source = CalendarSource.GOOGLE_PARTNER,
                owner = Person.MELLI,
                isSharedEvent = false,
            ),
        )
    }

    override val googleCalendarRepository: GoogleCalendarRepository = object : GoogleCalendarRepository {
        override suspend fun signIn(): AuthResult = AuthResult.Success(
            Account(id = "preview", email = "basti@example.org", displayName = "Basti", person = Person.BASTI)
        )

        override suspend fun grantMutualAccess(ownAccount: Account, partnerAccount: Account): Result<Unit> =
            Result.success(Unit)

        override suspend fun fetchEvents(range: DateRange): List<CalendarEvent> =
            (sampleEvents() + createdEvents).filter { it.source != CalendarSource.WORK_ICS }
    }

    override val icsCalendarRepository: IcsCalendarRepository = object : IcsCalendarRepository {
        override suspend fun fetchEvents(range: DateRange): List<CalendarEvent> =
            sampleEvents().filter { it.source == CalendarSource.WORK_ICS }
    }

    override val calendarMergeService: CalendarMergeService = object : CalendarMergeService {
        override suspend fun mergedEvents(range: DateRange): List<CalendarEvent> =
            (sampleEvents() + createdEvents)
                .filter { it.start.toLocalDate() <= range.endInclusive && it.end.toLocalDate() >= range.start }
                .sortedWith(compareBy({ !it.isAllDay }, { it.start }))

        override suspend fun freeBlocks(day: LocalDate): List<FreeTimeBlock> {
            val windowStart = day.atTime(9, 0)
            val windowEnd = day.atTime(22, 0)
            val minBlock = Duration.ofHours(3)
            val busy = (sampleEvents() + createdEvents)
                .filterNot { it.isAllDay }
                .mapNotNull { event ->
                    val start = maxOf(event.start, windowStart)
                    val end = minOf(event.end, windowEnd)
                    if (start < end) start to end else null
                }
                .sortedBy { it.first }
            val free = mutableListOf<FreeTimeBlock>()
            var cursor = windowStart
            for ((start, end) in busy) {
                if (Duration.between(cursor, start) >= minBlock) free += FreeTimeBlock(cursor, start)
                if (end > cursor) cursor = end
            }
            if (Duration.between(cursor, windowEnd) >= minBlock) free += FreeTimeBlock(cursor, windowEnd)
            return free
        }

        override suspend fun freeBlocksInRange(
            range: DateRange,
        ): Map<LocalDate, List<FreeTimeBlock>> {
            val freeBlocksByDay = linkedMapOf<LocalDate, List<FreeTimeBlock>>()
            var day = range.start
            while (!day.isAfter(range.endInclusive)) {
                freeBlocksByDay[day] = freeBlocks(day)
                day = day.plusDays(1)
            }
            return freeBlocksByDay
        }
    }

    override val sessionRepository: SessionRepository = object : SessionRepository {
        override suspend fun sessionState(): SessionState = SessionState.SignedOut
        override suspend fun resetSession() = Unit
    }

    override val eventCustomizationRepository: EventCustomizationRepository =
        object : EventCustomizationRepository {
            private val stored = mutableListOf<EventCustomization>()

            override suspend fun save(customization: EventCustomization) {
                stored.removeAll { it.target == customization.target }
                stored += customization
            }

            override suspend fun remove(target: CustomizationTarget) {
                stored.removeAll { it.target == target }
            }

            override suspend fun all(): List<EventCustomization> = stored.toList()
        }

    // Zwei feste Beispieladressen, damit die Vorschlagsliste in der Preview sichtbar ist —
    // ohne Netz, ohne Schlüssel.
    override val placeSuggestionRepository: PlaceSuggestionRepository =
        object : PlaceSuggestionRepository {
            override suspend fun suggest(
                query: String,
                sessionToken: String?,
            ): List<LocationSuggestion> {
                if (query.trim().length < 3) return emptyList()
                return listOf(
                    LocationSuggestion(
                        placeId = "preview-cafe",
                        primaryText = "Café Ohnesorg",
                        secondaryText = "Hauptstraße 12, 34117 Kassel",
                        fullText = "Café Ohnesorg, Hauptstraße 12, 34117 Kassel",
                    ),
                    LocationSuggestion(
                        placeId = "preview-bahnhof",
                        primaryText = "Bahnhof Kassel-Wilhelmshöhe",
                        secondaryText = "Willy-Brandt-Platz 5, 34131 Kassel",
                        fullText = "Bahnhof Kassel-Wilhelmshöhe, Willy-Brandt-Platz 5, 34131 Kassel",
                    ),
                )
            }

            override suspend fun resolveFullAddress(
                placeId: String,
                sessionToken: String?,
            ): String? = when (placeId) {
                "preview-cafe" -> "Café Ohnesorg, Hauptstraße 12, 34117 Kassel, Deutschland"
                "preview-bahnhof" ->
                    "Bahnhof Kassel-Wilhelmshöhe, Willy-Brandt-Platz 5, 34131 Kassel, Deutschland"
                else -> null
            }
        }

    // Zwei feste Positionen (Kassel / Osnabrück – die echte Fernbeziehungs-Distanz), damit die
    // Karten-Preview beide Marker zeigt, ohne Supabase und ohne Standort-Berechtigung.
    override val locationRepository: LocationRepository = object : LocationRepository {
        override fun observeLocations(): Flow<List<PersonLocation>> = flowOf(
            listOf(
                PersonLocation(
                    person = Person.BASTI,
                    latitude = 51.3127,
                    longitude = 9.4797,
                    accuracyMeters = 12.0,
                    speedMetersPerSecond = null,
                    isMoving = false,
                    updatedAt = Instant.now(),
                ),
                PersonLocation(
                    person = Person.MELLI,
                    latitude = 52.2799,
                    longitude = 8.0472,
                    accuracyMeters = 25.0,
                    speedMetersPerSecond = 21.4,
                    isMoving = true,
                    updatedAt = Instant.now().minusSeconds(240),
                ),
            ),
        )

        override suspend fun publishOwnLocation(location: PersonLocation): Result<Unit> =
            Result.success(Unit)
    }

    override val isLocationSharingConfigured: Boolean = true

    // In-Memory-Fake für die Ideen, damit die Preview ohne Supabase auskommt.
    override val noteRepository: NoteRepository = object : NoteRepository {
        private val folders = mutableListOf<NoteFolder>()
        private val items = mutableListOf<NoteItem>()

        override suspend fun loadFolders(): List<NoteFolder> = folders.sortedBy { it.name }

        override suspend fun addFolder(name: String, createdBy: Person): Result<NoteFolder> {
            val folder = NoteFolder(
                id = "folder-${idCounter.incrementAndGet()}",
                name = name,
                createdBy = createdBy,
                createdAt = Instant.now(),
            )
            folders += folder
            return Result.success(folder)
        }

        override suspend fun renameFolder(id: String, name: String): Result<Unit> {
            val index = folders.indexOfFirst { it.id == id }
            if (index >= 0) folders[index] = folders[index].copy(name = name)
            return Result.success(Unit)
        }

        override suspend fun deleteFolder(id: String): Result<Unit> {
            folders.removeAll { it.id == id }
            items.removeAll { it.folderId == id }
            return Result.success(Unit)
        }

        override suspend fun loadItems(folderId: String): List<NoteItem> =
            items.filter { it.folderId == folderId }

        override suspend fun addItem(
            folderId: String,
            text: String,
            url: String?,
            createdBy: Person,
        ): Result<NoteItem> {
            val item = NoteItem(
                id = "item-${idCounter.incrementAndGet()}",
                folderId = folderId,
                text = text,
                url = url,
                previewTitle = null,
                previewImageUrl = null,
                isChecked = false,
                checkedAt = null,
                createdBy = createdBy,
                createdAt = Instant.now(),
            )
            items += item
            return Result.success(item)
        }

        override suspend fun updateItem(id: String, text: String, url: String?): Result<Unit> {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(text = text, url = url)
            return Result.success(Unit)
        }

        override suspend fun setChecked(id: String, checked: Boolean): Result<Unit> {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                items[index] = items[index].copy(
                    isChecked = checked,
                    checkedAt = if (checked) Instant.now() else null,
                )
            }
            return Result.success(Unit)
        }

        override suspend fun deleteItem(id: String): Result<Unit> {
            items.removeAll { it.id == id }
            return Result.success(Unit)
        }
    }

    // In-Memory-Fake fürs Kostentracking, damit die Preview ohne Supabase auskommt.
    override val expenseRepository: ExpenseRepository = object : ExpenseRepository {
        private val stored = mutableListOf<Expense>()
        private val storedSettlements = mutableListOf<Settlement>()

        override suspend fun loadExpenses(): List<Expense> = stored.sortedByDescending { it.createdAt }

        override suspend fun addExpense(
            amount: Double,
            description: String,
            paidBy: Person,
            createdBy: Person,
            spentAt: LocalDate,
        ): Result<Expense> {
            val expense = Expense(
                id = "expense-${idCounter.incrementAndGet()}",
                amount = amount,
                description = description,
                paidBy = paidBy,
                createdBy = createdBy,
                spentAt = spentAt,
                createdAt = Instant.now(),
                settlementId = null,
            )
            stored += expense
            return Result.success(expense)
        }

        override suspend fun updateExpense(
            id: String,
            amount: Double,
            description: String,
            paidBy: Person,
            spentAt: LocalDate,
        ): Result<Unit> {
            val index = stored.indexOfFirst { it.id == id }
            if (index >= 0) {
                stored[index] = stored[index].copy(
                    amount = amount,
                    description = description,
                    paidBy = paidBy,
                    spentAt = spentAt,
                )
            }
            return Result.success(Unit)
        }

        override suspend fun deleteExpense(id: String): Result<Unit> {
            stored.removeAll { it.id == id }
            return Result.success(Unit)
        }

        override suspend fun settle(settledBy: Person): Result<Unit> {
            // Spiegelt SupabaseExpenseRepository.settle(): dieselbe reine Vorbereitung,
            // damit die Preview denselben Saldo-Schnappschuss zeigt wie die echte App.
            val result = prepareSettlement(
                expenses = stored,
                settledBy = settledBy,
                settlementId = "settlement-${idCounter.incrementAndGet()}",
                now = Instant.now(),
            )
            if (result.settledExpenseIds.isEmpty()) return Result.success(Unit)
            storedSettlements += result.settlement
            stored.replaceAll { expense ->
                if (expense.id in result.settledExpenseIds) {
                    expense.copy(settlementId = result.settlement.id)
                } else {
                    expense
                }
            }
            return Result.success(Unit)
        }

        override suspend fun loadSettlements(): List<Settlement> =
            storedSettlements.sortedByDescending { it.settledAt }
    }

    override val calendarRepository: CalendarRepository = object : CalendarRepository {
        override suspend fun createEvent(event: NewCalendarEvent): Result<CalendarEvent> {
            val created = CalendarEvent(
                id = "created-${idCounter.incrementAndGet()}",
                title = event.title,
                start = event.start,
                end = event.end,
                isAllDay = event.isAllDay,
                source = CalendarSource.GOOGLE_OWN,
                owner = Person.BASTI,
                isSharedEvent = event.invitePartner,
                location = event.location,
                description = event.description,
            )
            createdEvents += created
            return Result.success(created)
        }
    }
}
