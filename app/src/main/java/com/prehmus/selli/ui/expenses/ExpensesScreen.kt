package com.prehmus.selli.ui.expenses

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.prehmus.selli.R
import com.prehmus.selli.domain.finance.BalanceDirection
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.ui.components.PersonPill
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ExpenseDateFormat = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)

/**
 * Sitzt in `SelliShell` zwischen `SelliTopBar`/`SelliBottomBar` — `contentWindowInsets`
 * bleibt deshalb auf 0 (siehe Begründung im Kalender-Bugfix vom 14.09.2026: dieses
 * Scaffold berührt nie die echten Bildschirmkanten, ein Reservieren von Systemleisten-
 * Insets hier würde nur einen ungenutzten schwarzen Balken erzeugen).
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
                    items(items = uiState.expenses, key = { it.id }) { expense ->
                        ExpenseCard(
                            expense = expense,
                            onClick = { viewModel.beginEditing(expense) },
                        )
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
            onDelete = { viewModel.deleteExpense(expense.id) },
            onDismiss = viewModel::dismissEditing,
        )
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    direction: BalanceDirection,
    ownPerson: Person,
    onSettle: () -> Unit,
) {
    val amountText = "%.2f".format(kotlin.math.abs(balance)).replace('.', ',')
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

@Composable
private fun ExpenseCard(expense: Expense, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = expense.description, style = MaterialTheme.typography.titleSmall)
                PersonPill(person = expense.paidBy)
            }
            Text(
                text = "${"%.2f".format(expense.amount).replace('.', ',')} € · " +
                    expense.spentAt.format(ExpenseDateFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
