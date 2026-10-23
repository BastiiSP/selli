package com.prehmus.selli.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.finance.BalanceDirection
import com.prehmus.selli.domain.finance.balanceDirection
import com.prehmus.selli.domain.finance.calculateBalance
import com.prehmus.selli.domain.model.CloudAccessState
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
import com.prehmus.selli.domain.repository.CloudSessionRepository
import com.prehmus.selli.domain.repository.ExpenseRepository
import com.prehmus.selli.ui.components.CloudIssue
import com.prehmus.selli.ui.components.cloudActionMessage
import com.prehmus.selli.ui.components.toCloudIssue
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ein abgeschlossener Ausgleich mit den Ausgaben, die er abgerechnet hat. [settlement] ist
 * `null`, wenn eine Ausgabe zwar eine `settlementId` trägt, die zugehörige Zeile aber nicht
 * (mehr) geladen werden konnte — die Ausgaben bleiben dann trotzdem sichtbar, nur ohne
 * Datum/Saldo in der Überschrift.
 */
data class SettledExpenseGroup(
    val settlementId: String,
    val settlement: Settlement?,
    val expenses: List<Expense>,
)

data class ExpensesUiState(
    val expenses: List<Expense> = emptyList(),
    /** Noch nicht abgerechnete Ausgaben — stehen immer ungeklappt oben in der Liste. */
    val openExpenses: List<Expense> = emptyList(),
    /** Bereits ausgeglichene Ausgaben, nach Ausgleichsvorgang gruppiert, neueste zuerst. */
    val settledGroups: List<SettledExpenseGroup> = emptyList(),
    val settledCount: Int = 0,
    /** Der Bereich mit den ausgeglichenen Ausgaben startet bewusst eingeklappt. */
    val isSettledSectionExpanded: Boolean = false,
    val balance: Double = 0.0,
    val balanceDirection: BalanceDirection = BalanceDirection.SETTLED,
    val isRefreshing: Boolean = false,
    /** Erst nach dem ersten erfolgreichen Laden ist eine leere Liste ein echter Leerzustand. */
    val hasLoaded: Boolean = false,
    /** Warum der angezeigte Stand nicht frisch ist; null nach erfolgreichem Laden. */
    val loadIssue: CloudIssue? = null,
    val isCreateSheetOpen: Boolean = false,
    val editingExpense: Expense? = null,
    /** Ausgabe, für die gerade die Löschbestätigung offen ist (Wisch-Geste oder Sheet). */
    val deletingExpense: Expense? = null,
    val userMessage: String? = null,
    // Wer gerade angemeldet ist — die Saldo-Karte braucht das, um "Du"/"dir" korrekt
    // aus Sicht des jeweiligen Geräts zu formulieren (siehe BalanceCard).
    val ownPerson: Person = Person.BASTI,
)

/**
 * Gruppiert die abgerechneten Ausgaben nach ihrem Ausgleichsvorgang. Reine Darstellungs-
 * aufbereitung für die Liste (Owner-Regel: Gruppierung/Darstellung liegt in `ui/`), die
 * Saldo-Berechnung selbst bleibt in `domain/finance`.
 */
private fun groupSettledExpenses(
    expenses: List<Expense>,
    settlements: List<Settlement>,
): List<SettledExpenseGroup> {
    val settlementById = settlements.associateBy { it.id }
    return expenses
        .mapNotNull { expense -> expense.settlementId?.let { it to expense } }
        .groupBy({ it.first }, { it.second })
        .map { (settlementId, grouped) ->
            SettledExpenseGroup(
                settlementId = settlementId,
                settlement = settlementById[settlementId],
                expenses = grouped,
            )
        }
        // Ausgleiche ohne geladene Settlement-Zeile ans Ende, statt sie nach vorn zu sortieren.
        .sortedByDescending { it.settlement?.settledAt ?: Instant.MIN }
}

/**
 * Kein Realtime-Sync (siehe Spec): [refresh] wird beim Öffnen des Tabs, per
 * Pull-to-Refresh und nach jeder eigenen Änderung aufgerufen.
 */
class ExpensesViewModel(
    private val repository: ExpenseRepository,
    private val ownPerson: Person,
    private val cloudSession: CloudSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExpensesUiState(ownPerson = ownPerson))
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    // Ein manuelles Pull-to-Refresh während ein Refresh noch läuft darf sich nicht stapeln:
    // Jeder neue Refresh bricht den vorherigen ab, damit eine ältere, langsamere Antwort nicht
    // die frische überschreibt (analog zu CalendarViewModel.refreshJob).
    private var refreshJob: Job? = null

    // Zuletzt erfolgreich geladene Ausgleiche — schlägt nur deren Laden fehl, bleiben die
    // Überschriften der ausgeglichenen Gruppen erhalten, statt datumslos zu werden.
    private var lastSettlements: List<Settlement> = emptyList()

    init {
        refresh()
        // Nach erfolgreicher Kontobestätigung (egal in welchem Tab) oder wiederhergestellter
        // Verbindung selbst nachladen, statt auf das nächste Pull-to-Refresh zu warten.
        viewModelScope.launch {
            cloudSession.accessState.collect { state ->
                // Nicht in einen laufenden Refresh grätschen: dessen eigener Erfolg meldet
                // ebenfalls CONNECTED und würde ihn sonst abbrechen und neu starten.
                val current = _uiState.value
                if (state == CloudAccessState.CONNECTED && current.loadIssue != null && !current.isRefreshing) {
                    refresh()
                }
            }
        }
    }

    /** „Erneut versuchen": erst die Anmeldung ausdrücklich prüfen lassen, dann neu laden. */
    fun retry() {
        viewModelScope.launch {
            cloudSession.ensureAccess()
            refresh()
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        _uiState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            // Bewusst die Result-Varianten: ein Ladefehler darf die sichtbare Liste und den
            // Saldo nicht mit „keine Ausgaben / ausgeglichen" überschreiben.
            val expensesResult = repository.loadExpensesResult()
            val expenses = expensesResult.getOrNull()
            if (expenses == null) {
                val issue = expensesResult.exceptionOrNull()?.toCloudIssue() ?: CloudIssue.FAILED
                _uiState.update { it.copy(isRefreshing = false, loadIssue = issue) }
                return@launch
            }
            val settlementsResult = repository.loadSettlementsResult()
            val settlements = settlementsResult.getOrNull() ?: lastSettlements
            lastSettlements = settlements
            val balance = calculateBalance(expenses)
            val settledGroups = groupSettledExpenses(expenses, settlements)
            _uiState.update {
                it.copy(
                    expenses = expenses,
                    openExpenses = expenses.filter { expense -> expense.settlementId == null },
                    settledGroups = settledGroups,
                    settledCount = settledGroups.sumOf { group -> group.expenses.size },
                    balance = balance,
                    balanceDirection = balanceDirection(balance),
                    isRefreshing = false,
                    hasLoaded = true,
                    loadIssue = settlementsResult.exceptionOrNull()?.toCloudIssue(),
                )
            }
        }
    }

    fun toggleSettledSection() {
        _uiState.update { it.copy(isSettledSectionExpanded = !it.isSettledSectionExpanded) }
    }

    fun openCreateSheet() {
        _uiState.update { it.copy(isCreateSheetOpen = true) }
    }

    fun dismissCreateSheet() {
        _uiState.update { it.copy(isCreateSheetOpen = false) }
    }

    fun addExpense(amount: Double, description: String, paidBy: Person, spentAt: LocalDate) {
        viewModelScope.launch {
            repository.addExpense(
                amount = amount,
                description = description,
                paidBy = paidBy,
                createdBy = ownPerson,
                spentAt = spentAt,
            ).onSuccess {
                _uiState.update { it.copy(isCreateSheetOpen = false) }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(userMessage = error.cloudActionMessage("Ausgabe konnte nicht gespeichert werden."))
                }
            }
        }
    }

    fun beginEditing(expense: Expense) {
        _uiState.update { it.copy(editingExpense = expense) }
    }

    fun dismissEditing() {
        _uiState.update { it.copy(editingExpense = null) }
    }

    fun saveEdit(amount: Double, description: String, paidBy: Person, spentAt: LocalDate) {
        val editing = _uiState.value.editingExpense ?: return
        viewModelScope.launch {
            repository.updateExpense(
                id = editing.id,
                amount = amount,
                description = description,
                paidBy = paidBy,
                spentAt = spentAt,
            ).onSuccess {
                _uiState.update { it.copy(editingExpense = null) }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(userMessage = error.cloudActionMessage("Änderung konnte nicht gespeichert werden."))
                }
            }
        }
    }

    /**
     * Löschen ist unwiderruflich und wird deshalb — wie beim Entfernen eines Termins —
     * immer erst über einen Bestätigungsdialog geführt, egal ob die Wisch-Geste oder das
     * Bearbeiten-Sheet ihn ausgelöst hat.
     */
    fun beginDeleting(expense: Expense) {
        _uiState.update { it.copy(deletingExpense = expense) }
    }

    fun dismissDeleting() {
        _uiState.update { it.copy(deletingExpense = null) }
    }

    fun confirmDelete() {
        val expense = _uiState.value.deletingExpense ?: return
        viewModelScope.launch {
            repository.deleteExpense(expense.id)
                .onSuccess {
                    // Das Bearbeiten-Sheet kann auf derselben Ausgabe stehen — mit wegräumen.
                    _uiState.update { it.copy(deletingExpense = null, editingExpense = null) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            deletingExpense = null,
                            userMessage = error.cloudActionMessage("Ausgabe konnte nicht gelöscht werden."),
                        )
                    }
                }
        }
    }

    fun settle() {
        viewModelScope.launch {
            repository.settle(settledBy = ownPerson)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(userMessage = error.cloudActionMessage("Ausgleichen ist fehlgeschlagen."))
                    }
                }
        }
    }

    /** Snackbar im Screen hat die Meldung gezeigt — State wieder leeren (analog CalendarViewModel). */
    fun consumeUserMessage() = _uiState.update { it.copy(userMessage = null) }

    companion object {
        fun factory(
            repository: ExpenseRepository,
            ownPerson: Person,
            cloudSession: CloudSessionRepository,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExpensesViewModel(repository, ownPerson, cloudSession) as T
            }
    }
}
