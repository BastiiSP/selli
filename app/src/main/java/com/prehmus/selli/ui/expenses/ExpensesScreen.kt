package com.prehmus.selli.ui.expenses

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.prehmus.selli.R
import com.prehmus.selli.domain.finance.BalanceDirection
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.components.PersonPill
import com.prehmus.selli.ui.theme.personColor
import com.prehmus.selli.ui.theme.personSoftColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ExpenseDateFormat = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)
private val SettlementDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

/**
 * Sitzt in `SelliShell` zwischen `SelliTopBar`/`SelliBottomBar` — `contentWindowInsets`
 * bleibt deshalb auf 0 (siehe Begründung im Kalender-Bugfix vom 14.09.2026: dieses
 * Scaffold berührt nie die echten Bildschirmkanten, ein Reservieren von Systemleisten-
 * Insets hier würde nur einen ungenutzten schwarzen Balken erzeugen).
 *
 * Seit dem 15.09.2026 trennt die Liste offene von bereits ausgeglichenen Ausgaben: offene
 * stehen immer sofort sichtbar oben, ausgeglichene stecken in einem eingeklappten Bereich
 * darunter — sonst wächst die Liste unbegrenzt und verdeckt die aktuellen Posten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUserMessage()
        }
    }

    // Kein Realtime-Sync (siehe Spec): Der Tab hängt an der NavHost-Backstack-Entry und
    // überlebt dank saveState/restoreState einen Tab-Wechsel — ohne diesen Hook würde nach
    // dem allerersten Besuch nie wieder automatisch nachgeladen, nur noch per Pull-to-Refresh.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openCreateSheet) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Ausgabe eintragen")
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (uiState.expenses.isEmpty() && !uiState.isRefreshing) {
                ExpensesEmptyHint(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "balance") {
                        BalanceCard(
                            balance = uiState.balance,
                            direction = uiState.balanceDirection,
                            ownPerson = uiState.ownPerson,
                            onSettle = viewModel::settle,
                        )
                    }

                    items(items = uiState.openExpenses, key = { it.id }) { expense ->
                        SwipeToDeleteExpense(
                            expense = expense,
                            isSettled = false,
                            onEdit = { viewModel.beginEditing(expense) },
                            onRequestDelete = { viewModel.beginDeleting(expense) },
                        )
                    }

                    if (uiState.settledGroups.isNotEmpty()) {
                        item(key = "settled-toggle") {
                            SettledSectionToggle(
                                count = uiState.settledCount,
                                isExpanded = uiState.isSettledSectionExpanded,
                                onToggle = viewModel::toggleSettledSection,
                            )
                        }
                        if (uiState.isSettledSectionExpanded) {
                            uiState.settledGroups.forEach { group ->
                                item(key = "settlement-${group.settlementId}") {
                                    SettlementSeparator(group = group)
                                }
                                items(items = group.expenses, key = { it.id }) { expense ->
                                    SwipeToDeleteExpense(
                                        expense = expense,
                                        isSettled = true,
                                        onEdit = { viewModel.beginEditing(expense) },
                                        onRequestDelete = { viewModel.beginDeleting(expense) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.isCreateSheetOpen) {
        AddExpenseSheet(
            ownPerson = uiState.ownPerson,
            onSave = viewModel::addExpense,
            onDismiss = viewModel::dismissCreateSheet,
        )
    }

    uiState.editingExpense?.let { expense ->
        ExpenseActionsSheet(
            expense = expense,
            onSave = viewModel::saveEdit,
            onDelete = { viewModel.beginDeleting(expense) },
            onDismiss = viewModel::dismissEditing,
        )
    }

    uiState.deletingExpense?.let { expense ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleting,
            title = { Text("Ausgabe löschen?") },
            text = { Text("\"${expense.description}\" wird endgültig entfernt.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleting) { Text("Abbrechen") }
            },
        )
    }
}

/**
 * Wischen von links nach rechts löscht — nach Bestätigung. Die Zeile federt dabei bewusst
 * zurück (`confirmValueChange` liefert immer `false`): Löschen ist unwiderruflich, also
 * entscheidet derselbe Bestätigungsdialog wie beim Entfernen eines Termins, und die Zeile
 * verschwindet erst, wenn das Löschen tatsächlich durch ist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteExpense(
    expense: Expense,
    isSettled: Boolean,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) onRequestDelete()
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = { DeleteSwipeBackground() },
    ) {
        ExpenseCard(expense = expense, isSettled = isSettled, onEdit = onEdit)
    }
}

/** Roter Hintergrund mit Mülleimer, der beim Wischen hinter der Karte sichtbar wird. */
@Composable
private fun DeleteSwipeBackground() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "Löschen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    direction: BalanceDirection,
    ownPerson: Person,
    onSettle: () -> Unit,
) {
    val amountText = formatEuro(balance)
    // Vier statt zwei Textvarianten: "schuldet"/"wird geschuldet" hängt zusätzlich davon ab,
    // wer gerade angemeldet ist (die App läuft symmetrisch auf beiden Geräten).
    val label = when (direction) {
        BalanceDirection.MELLI_OWES_BASTI -> if (ownPerson == Person.BASTI) {
            "Melli schuldet dir $amountText €"
        } else {
            "Du schuldest Basti $amountText €"
        }
        BalanceDirection.BASTI_OWES_MELLI -> if (ownPerson == Person.MELLI) {
            "Basti schuldet dir $amountText €"
        } else {
            "Du schuldest Melli $amountText €"
        }
        BalanceDirection.SETTLED -> "Ausgeglichen 🎉"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (direction != BalanceDirection.SETTLED) {
                Button(onClick = onSettle) { Text("Ausgleichen") }
            }
        }
    }
}

/**
 * Ausgabenkarte. Offene Posten tragen zusätzlich zur Namens-Pill den sanften Container-Ton
 * der Person, die bezahlt hat — ausgeglichene treten neutral und durchgestrichen zurück,
 * damit der Unterschied auch ohne Lesen der Überschrift sofort sichtbar ist.
 */
@Composable
private fun ExpenseCard(expense: Expense, isSettled: Boolean, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isSettled) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            personSoftColor(expense.paidBy)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (isSettled) TextDecoration.LineThrough else null,
                    color = if (isSettled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = "${formatEuro(expense.amount)} € · " +
                        expense.spentAt.format(ExpenseDateFormat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersonPill(person = expense.paidBy)
                EditCircleButton(person = expense.paidBy, onClick = onEdit)
            }
        }
    }
}

/**
 * Runder Direkteinstieg ins Bearbeiten, direkt neben der Namens-Pill. Sichtbar 32 dp, damit
 * er die Zeile nicht dominiert — `minimumInteractiveComponentSize()` zieht die Trefferfläche
 * trotzdem auf die empfohlenen 48 dp auf.
 */
@Composable
private fun EditCircleButton(person: Person, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, personColor(person)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Ausgabe bearbeiten",
                tint = personColor(person),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Kopfzeile des eingeklappten Bereichs mit den bereits ausgeglichenen Ausgaben. */
@Composable
private fun SettledSectionToggle(count: Int, isExpanded: Boolean, onToggle: () -> Unit) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "settledChevron",
    )
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ausgeglichen ($count)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) {
                    "Ausgeglichene Ausgaben einklappen"
                } else {
                    "Ausgeglichene Ausgaben aufklappen"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                // Wert erst im Layout-/Draw-Schritt lesen statt im Composable-Rumpf.
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
    }
}

/** Trennzeile je Ausgleichsvorgang: „Ausgeglichen am 14.09.2026 · Saldo war 23,50 €". */
@Composable
private fun SettlementSeparator(group: SettledExpenseGroup) {
    val settlement = group.settlement
    val label = if (settlement == null) {
        "Früher ausgeglichen"
    } else {
        val day = settlement.settledAt.atZone(ZoneId.systemDefault()).toLocalDate()
        "Ausgeglichen am ${day.format(SettlementDateFormat)} · " +
            "Saldo war ${formatEuro(settlement.balanceSnapshot)} €"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ExpensesEmptyHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_empty_state),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Text(text = "Noch keine Ausgaben", style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Beträge einheitlich mit Komma und zwei Nachkommastellen, immer ohne Vorzeichen. */
private fun formatEuro(amount: Double): String =
    "%.2f".format(kotlin.math.abs(amount)).replace('.', ',')
