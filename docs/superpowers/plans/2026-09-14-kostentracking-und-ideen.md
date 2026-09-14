# Kostentracking & Ideen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zwei neue, unabhängige Bereiche in Selli hinzufügen: Kostentracking (gemeinsame Ausgaben 50/50 aufteilen, laufender Saldo) und "Ideen" (Ordner mit abhakbaren Punkten für Aktivitäten/Rezepte), inklusive Erweiterung der Bottom-Navigation von 3 auf 5 Tabs.

**Architecture:** Gleiches Muster wie das bestehende Standort-Feature — neue Supabase-Tabellen pro Feature, abgesichert über die bereits existierende `is_selli_member()`-Allowlist. Kein Realtime-Websocket (bewusste Abweichung vom Standort-Feature): beide Features laden beim Öffnen/Pull-to-Refresh neu und werden zusätzlich per Push-Benachrichtigung angestoßen. Owner-Trennung strikt nach Datei: Codex baut `data/`, `domain/`, Migrationen und deren Tests; Claude baut `ui/` und die Navigation.

**Tech Stack:** Kotlin + Jetpack Compose (bestehend), Supabase Postgrest (bestehend, `io.github.jan-tennert.supabase:postgrest-kt:3.1.4`), zwei neue Abhängigkeiten: `org.jsoup:jsoup:1.18.1` (Link-Vorschau-HTML-Parsing) und `io.coil-kt.coil3:coil-compose:3.0.4` + `io.coil-kt.coil3:coil-network-okhttp:3.0.4` (Vorschaubilder laden — **nicht** 3.5.0+ verwenden, das verlangt `compileSdk 36`, dieses Projekt steht auf `compileSdk 35`, siehe Task 1).

**Spec:**
- `docs/superpowers/specs/2026-09-14-kostentracking-design.md`
- `docs/superpowers/specs/2026-09-14-ideen-design.md`

## Global Constraints

- Ausgaben: immer feste 50/50-Aufteilung, keine Kategorien, keine Mehrwährung (nur EUR).
- Ausgleichen ist sofort wirksam (ein Tap), kein Bestätigungs-Handshake, Saldo geht auf Null zurück, Historie bleibt sichtbar.
- Beide Personen dürfen jede Ausgabe, jeden Ordner und jeden Punkt bearbeiten/löschen — keine Owner-Beschränkung auf Datenebene.
- Kein Realtime-Websocket-Sync für beide neuen Features — Nachladen beim Öffnen des jeweiligen Tabs + Pull-to-Refresh (Ausnahme: Kalender/Standort-Feature bleiben unverändert).
- "Ideen"-Ordner sind genau eine Ebene tief (Ordner → Punkte), keine Unterordner.
- Ein Punkt-Typ: Freitext mit optionalem Link, keine getrennten Text-/Link-Punkttypen.
- Abgehakte Punkte wandern in einen aufklappbaren Archiv-Bereich, werden nicht durchgestrichen stehen gelassen und nicht sofort gelöscht.
- Link-Vorschau (Titel + Bild) wird beim Anlegen automatisch geladen, Timeout 5s, bei Fehlschlag wird der Punkt trotzdem ohne Vorschau gespeichert (nie eine Exception nach oben werfen).
- Finale Tab-Reihenfolge der Bottom-Navigation: `Kalender | Ideen | Wir | Kosten | Standort` — "Wir" bleibt in der Mitte als Startziel.
- Alle neuen Supabase-Tabellen: RLS über die bestehende `public.is_selli_member()`-Funktion + `public.selli_members`-Allowlist, exakt wie bei `public.locations`.
- IDs werden **client-seitig** per `java.util.UUID.randomUUID().toString()` erzeugt und explizit mitgeschickt (nicht auf DB-generierte IDs mit Rückgabe-Roundtrip verlassen — das Projekt nutzt dieses Muster bisher nirgends).
- Icons ausschließlich aus `material-icons-core` (keine `material-icons-extended`-Abhängigkeit im Projekt) — verifizierte, tatsächlich vorhandene Icons für dieses Vorhaben: `Icons.Default.ShoppingCart` (Kosten), `Icons.AutoMirrored.Filled.List` (Ideen).
- Supabase-Postgrest-Filter-Syntax in diesem Projekt (Version 3.1.4, gegen die installierte AAR verifiziert): Filter stehen in einem verschachtelten `filter { ... }`-Block, z. B. `.select { filter { eq("id", id) } }`, `.update({ set("col", value) }) { filter { eq("id", id) } }`, `.delete { filter { eq("id", id) } }`. **Nicht** die in der offiziellen Wiki gezeigte Kurzform ohne `filter{}`-Wrapper verwenden — die kompiliert mit der hier installierten Version nicht.

---

## Teil A: Kostentracking

### Task 1: Domain-Modelle Expense/Settlement + Saldo-Berechnung (TDD)

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/model/Expense.kt`
- Create: `app/src/main/java/com/prehmus/selli/domain/finance/ExpenseBalance.kt`
- Create: `app/src/main/java/com/prehmus/selli/domain/finance/Settlement.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/finance/ExpenseBalanceTest.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/finance/SettlementCalculationTest.kt`

**Interfaces:**
- Produces: `data class Expense(id, amount, description, paidBy: Person, createdBy: Person, spentAt: LocalDate, createdAt: Instant, settlementId: String?)`, `data class Settlement(id, settledBy: Person, settledAt: Instant, balanceSnapshot: Double)`, `fun calculateBalance(expenses: List<Expense>): Double`, `enum class BalanceDirection { BASTI_OWES_MELLI, MELLI_OWES_BASTI, SETTLED }`, `fun balanceDirection(balance: Double): BalanceDirection`, `data class SettlementResult(settlement: Settlement, settledExpenseIds: List<String>)`, `fun prepareSettlement(expenses: List<Expense>, settledBy: Person, settlementId: String, now: Instant): SettlementResult`. Diese Signaturen werden von Task 4 (Repository) und Task 6 (ViewModel) verwendet.

- [ ] **Schritt 1: Modelle anlegen (kein Test nötig — reine Datenklassen)**

`app/src/main/java/com/prehmus/selli/domain/model/Expense.kt`:

```kotlin
package com.prehmus.selli.domain.model

import java.time.Instant
import java.time.LocalDate

data class Expense(
    val id: String,
    val amount: Double,
    val description: String,
    val paidBy: Person,
    val createdBy: Person,
    val spentAt: LocalDate,
    val createdAt: Instant,
    val settlementId: String?,
)

data class Settlement(
    val id: String,
    val settledBy: Person,
    val settledAt: Instant,
    val balanceSnapshot: Double,
)
```

- [ ] **Schritt 2: Test für die Saldo-Berechnung schreiben**

`app/src/test/java/com/prehmus/selli/domain/finance/ExpenseBalanceTest.kt`:

```kotlin
package com.prehmus.selli.domain.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseBalanceTest {
    private fun expense(
        id: String,
        amount: Double,
        paidBy: Person,
        settlementId: String? = null,
    ) = Expense(
        id = id,
        amount = amount,
        description = "Testausgabe",
        paidBy = paidBy,
        createdBy = paidBy,
        spentAt = LocalDate.of(2026, 9, 14),
        createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        settlementId = settlementId,
    )

    @Test
    fun `only Basti pays, Melli owes half`() {
        val balance = calculateBalance(listOf(expense("1", 40.0, Person.BASTI)))
        assertEquals(20.0, balance, 0.001)
    }

    @Test
    fun `only Melli pays, balance is negative`() {
        val balance = calculateBalance(listOf(expense("1", 30.0, Person.MELLI)))
        assertEquals(-15.0, balance, 0.001)
    }

    @Test
    fun `mixed expenses net out correctly`() {
        val balance = calculateBalance(
            listOf(
                expense("1", 40.0, Person.BASTI),
                expense("2", 30.0, Person.MELLI),
            ),
        )
        assertEquals(5.0, balance, 0.001)
    }

    @Test
    fun `already settled expenses are excluded`() {
        val balance = calculateBalance(
            listOf(
                expense("1", 100.0, Person.BASTI, settlementId = "old-settlement"),
                expense("2", 10.0, Person.MELLI),
            ),
        )
        assertEquals(-5.0, balance, 0.001)
    }

    @Test
    fun `no expenses means settled`() {
        assertEquals(0.0, calculateBalance(emptyList()), 0.001)
        assertEquals(BalanceDirection.SETTLED, balanceDirection(0.0))
    }

    @Test
    fun `balance direction reflects sign`() {
        assertEquals(BalanceDirection.MELLI_OWES_BASTI, balanceDirection(5.0))
        assertEquals(BalanceDirection.BASTI_OWES_MELLI, balanceDirection(-5.0))
        assertEquals(BalanceDirection.SETTLED, balanceDirection(0.001))
    }
}
```

- [ ] **Schritt 3: Test ausführen, Fehlschlag bestätigen**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew testDebugUnitTest --tests "*.ExpenseBalanceTest"
```

Erwartet: Kompilierfehler ("unresolved reference: calculateBalance" etc.) — die Funktionen existieren noch nicht.

- [ ] **Schritt 4: `ExpenseBalance.kt` implementieren**

`app/src/main/java/com/prehmus/selli/domain/finance/ExpenseBalance.kt`:

```kotlin
package com.prehmus.selli.domain.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person

/**
 * Laufender Saldo aus allen offenen (noch nicht abgerechneten) Ausgaben. Jede Ausgabe wird
 * zur Hälfte der jeweils anderen Person zugeordnet (feste 50/50-Aufteilung, siehe Spec).
 *
 * Vorzeichenkonvention: positiv -> Melli schuldet Basti [amount] €.
 * negativ -> Basti schuldet Melli [amount] €. Null -> ausgeglichen.
 */
fun calculateBalance(expenses: List<Expense>): Double {
    val open = expenses.filter { it.settlementId == null }
    val paidByBasti = open.filter { it.paidBy == Person.BASTI }.sumOf { it.amount }
    val paidByMelli = open.filter { it.paidBy == Person.MELLI }.sumOf { it.amount }
    return (paidByBasti - paidByMelli) / 2
}

/** Wer aktuell wem schuldet — abgeleitet aus [calculateBalance] für die UI. */
enum class BalanceDirection { BASTI_OWES_MELLI, MELLI_OWES_BASTI, SETTLED }

fun balanceDirection(balance: Double): BalanceDirection = when {
    balance > BALANCE_EPSILON -> BalanceDirection.MELLI_OWES_BASTI
    balance < -BALANCE_EPSILON -> BalanceDirection.BASTI_OWES_MELLI
    else -> BalanceDirection.SETTLED
}

// Rundungsfehler bei Centbeträgen (z.B. 0.1 + 0.2) sollen nicht als "offener Saldo" durchgehen.
private const val BALANCE_EPSILON = 0.005
```

- [ ] **Schritt 5: Test ausführen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --tests "*.ExpenseBalanceTest"
```

Erwartet: alle 6 Tests grün.

- [ ] **Schritt 6: Test für die Ausgleichen-Berechnung schreiben**

`app/src/test/java/com/prehmus/selli/domain/finance/SettlementCalculationTest.kt`:

```kotlin
package com.prehmus.selli.domain.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SettlementCalculationTest {
    private fun expense(id: String, amount: Double, paidBy: Person, settlementId: String? = null) = Expense(
        id = id,
        amount = amount,
        description = "Testausgabe",
        paidBy = paidBy,
        createdBy = paidBy,
        spentAt = LocalDate.of(2026, 9, 14),
        createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        settlementId = settlementId,
    )

    @Test
    fun `prepareSettlement covers only currently open expenses`() {
        val expenses = listOf(
            expense("1", 40.0, Person.BASTI),
            expense("2", 10.0, Person.MELLI, settlementId = "already-settled"),
        )
        val result = prepareSettlement(
            expenses = expenses,
            settledBy = Person.MELLI,
            settlementId = "new-settlement",
            now = Instant.parse("2026-09-14T12:00:00Z"),
        )

        assertEquals(listOf("1"), result.settledExpenseIds)
        assertEquals(20.0, result.settlement.balanceSnapshot, 0.001)
        assertEquals("new-settlement", result.settlement.id)
        assertEquals(Person.MELLI, result.settlement.settledBy)
    }

    @Test
    fun `prepareSettlement with no open expenses yields zero balance and empty id list`() {
        val result = prepareSettlement(
            expenses = listOf(expense("1", 40.0, Person.BASTI, settlementId = "old")),
            settledBy = Person.BASTI,
            settlementId = "new",
            now = Instant.parse("2026-09-14T12:00:00Z"),
        )

        assertEquals(emptyList<String>(), result.settledExpenseIds)
        assertEquals(0.0, result.settlement.balanceSnapshot, 0.001)
    }
}
```

- [ ] **Schritt 7: Test ausführen, Fehlschlag bestätigen** (analog Schritt 3)

- [ ] **Schritt 8: `Settlement.kt` implementieren**

`app/src/main/java/com/prehmus/selli/domain/finance/Settlement.kt`:

```kotlin
package com.prehmus.selli.domain.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
import java.time.Instant

/** Ergebnis eines Ausgleichs: die neue Settlement-Zeile plus die IDs der jetzt abgerechneten Ausgaben. */
data class SettlementResult(
    val settlement: Settlement,
    val settledExpenseIds: List<String>,
)

/**
 * Reine Berechnung dessen, was beim Ausgleichen passieren soll — welche Ausgaben-IDs auf die
 * neue Settlement-ID gestempelt werden und welchen Saldo-Snapshot das Settlement bekommt.
 * Das eigentliche Schreiben (Update + Insert) übernimmt das Repository (Task 4).
 */
fun prepareSettlement(
    expenses: List<Expense>,
    settledBy: Person,
    settlementId: String,
    now: Instant,
): SettlementResult {
    val open = expenses.filter { it.settlementId == null }
    val balance = calculateBalance(open)
    return SettlementResult(
        settlement = Settlement(
            id = settlementId,
            settledBy = settledBy,
            settledAt = now,
            balanceSnapshot = balance,
        ),
        settledExpenseIds = open.map { it.id },
    )
}
```

- [ ] **Schritt 9: Test ausführen, Erfolg bestätigen**

```bash
./gradlew testDebugUnitTest --tests "*.SettlementCalculationTest"
```

Erwartet: beide Tests grün.

- [ ] **Schritt 10: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/model/Expense.kt \
        app/src/main/java/com/prehmus/selli/domain/finance/ExpenseBalance.kt \
        app/src/main/java/com/prehmus/selli/domain/finance/Settlement.kt \
        app/src/test/java/com/prehmus/selli/domain/finance/ExpenseBalanceTest.kt \
        app/src/test/java/com/prehmus/selli/domain/finance/SettlementCalculationTest.kt
git commit -m "Domain: Expense/Settlement-Modelle, Saldo- und Ausgleichen-Berechnung"
```

---

### Task 2: Neue Abhängigkeiten hinzufügen (Jsoup + Coil)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `libs.jsoup`, `libs.coil.compose`, `libs.coil.network.okhttp` als Gradle-Version-Catalog-Referenzen für alle folgenden Tasks.

- [ ] **Schritt 1: Versionen und Bibliotheken im Catalog ergänzen**

In `gradle/libs.versions.toml`, im `[versions]`-Block nach der Zeile `mapsCompose = "6.6.0"` einfügen:

```toml
jsoup = "1.18.1"
coil = "3.0.4"
```

Im `[libraries]`-Block nach der Zeile `play-services-location = { ... }` einfügen:

```toml
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
```

**Wichtig:** `coil = "3.0.4"` bewusst nicht auf eine neuere 3.x-Version anheben, ohne vorher zu prüfen — ab Coil `3.2.0`/`3.5.0` verlangt `coil-compose-core-android` `compileSdk 36`, dieses Projekt steht auf `compileSdk 35` (`app/build.gradle.kts`). Ein Versions-Bump wäre ein eigenes, größeres Vorhaben (SDK-Upgrade prüfen), nicht Teil dieses Plans.

- [ ] **Schritt 2: Abhängigkeiten einbinden**

In `app/build.gradle.kts`, im `dependencies { ... }`-Block nach der Zeile `implementation(libs.play.services.location)` einfügen:

```kotlin
implementation(libs.jsoup)
implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)
```

- [ ] **Schritt 3: Build verifizieren**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew compileDebugKotlin
```

Erwartet: `BUILD SUCCESSFUL`. Bricht der Build mit einem `compileSdk`-Fehler zu Coil ab, wurde versehentlich eine zu neue Coil-Version eingetragen — auf `3.0.4` zurücksetzen.

- [ ] **Schritt 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "Jsoup und Coil als Abhängigkeiten für Kostentracking/Ideen ergänzen"
```

---

### Task 3: Supabase-Migration — `expenses` und `settlements`

**Files:**
- Create: `supabase/migrations/20260914150000_selli_expenses.sql`

**Interfaces:**
- Produces: Tabellen `public.expenses`, `public.settlements` mit RLS, erreichbar über den bestehenden Supabase-Client (`SelliSupabaseClient`) aus Task 4.

- [ ] **Schritt 1: Migrationsdatei schreiben**

```sql
-- Selli: Kostentracking zwischen genau zwei bekannten Konten.
-- Nutzt dieselbe Allowlist wie das Standort-Feature (public.selli_members / is_selli_member()).
-- Design-Entscheidungen: docs/superpowers/specs/2026-09-14-kostentracking-design.md

create table if not exists public.settlements (
  id                uuid primary key default gen_random_uuid(),
  settled_by        text not null check (settled_by in ('BASTI', 'MELLI')),
  settled_at        timestamptz not null default now(),
  balance_snapshot  numeric(10,2) not null
);

create table if not exists public.expenses (
  id             uuid primary key default gen_random_uuid(),
  amount         numeric(10,2) not null check (amount > 0),
  description    text not null,
  paid_by        text not null check (paid_by in ('BASTI', 'MELLI')),
  created_by     text not null check (created_by in ('BASTI', 'MELLI')),
  spent_at       date not null,
  created_at     timestamptz not null default now(),
  settlement_id  uuid references public.settlements(id)
);

alter table public.settlements enable row level security;
alter table public.expenses enable row level security;

drop policy if exists "members read settlements" on public.settlements;
create policy "members read settlements" on public.settlements
  for select to authenticated using (public.is_selli_member());

drop policy if exists "members insert settlements" on public.settlements;
create policy "members insert settlements" on public.settlements
  for insert to authenticated with check (public.is_selli_member());

drop policy if exists "members read expenses" on public.expenses;
create policy "members read expenses" on public.expenses
  for select to authenticated using (public.is_selli_member());

drop policy if exists "members insert expenses" on public.expenses;
create policy "members insert expenses" on public.expenses
  for insert to authenticated with check (public.is_selli_member());

drop policy if exists "members update expenses" on public.expenses;
create policy "members update expenses" on public.expenses
  for update to authenticated using (public.is_selli_member()) with check (public.is_selli_member());

drop policy if exists "members delete expenses" on public.expenses;
create policy "members delete expenses" on public.expenses
  for delete to authenticated using (public.is_selli_member());

-- Data-API-Grants — ohne diese antwortet PostgREST mit "permission denied" (siehe locations-Migration).
grant select, insert on public.settlements to authenticated;
grant select, insert, update, delete on public.expenses to authenticated;
```

**Hinweis zu `created_by`:** Das Spec-Dokument listet nur `paid_by`. `created_by` ist eine kleine, bewusste Ergänzung gegenüber der Spec — nötig, damit die Partner-Benachrichtigung (Task 9) korrekt erkennt, wer die Ausgabe *eingetragen* hat (nicht wer *bezahlt* hat — bei stellvertretendem Eintragen können beide auseinanderfallen). Ohne dieses Feld würde die Benachrichtigungslogik fälschlich auf `paid_by` ausweichen müssen.

- [ ] **Schritt 2: Migration anwenden**

Bevorzugt über das Supabase-MCP-Tool (`mcp__plugin_supabase_supabase__apply_migration`, `project_id` aus `mcp__plugin_supabase_supabase__list_projects`, Projektname "selli"). Ist das MCP nicht verfügbar: Dashboard → SQL Editor → Inhalt der Datei einfügen → Run.

- [ ] **Schritt 3: Verifizieren**

```sql
select
  (select count(*) from pg_tables where schemaname='public' and tablename in ('expenses','settlements')) as tabellen,
  (select count(*) from pg_policies where schemaname='public' and tablename='expenses') as policies_expenses,
  (select count(*) from pg_policies where schemaname='public' and tablename='settlements') as policies_settlements;
```

Erwartet: `tabellen = 2`, `policies_expenses = 4`, `policies_settlements = 2`.

- [ ] **Schritt 4: Commit**

```bash
git add supabase/migrations/20260914150000_selli_expenses.sql
git commit -m "Migration: expenses und settlements Tabellen für Kostentracking"
```

---

### Task 4: Row-Mapper + `ExpenseRepository`-Interface + `SupabaseExpenseRepository` (TDD für Mapper)

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/repository/ExpenseRepository.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/finance/ExpenseRow.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/finance/SettlementRow.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/finance/SupabaseExpenseRepository.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/finance/ExpenseRowTest.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/finance/SettlementRowTest.kt`

**Interfaces:**
- Consumes: `Expense`, `Settlement`, `calculateBalance`, `prepareSettlement` aus Task 1; `SelliSupabaseClient` (bestehend, `data/supabase/SelliSupabaseClient.kt`) mit `isConfigured: Boolean`, `client: SupabaseClient?`, `suspend fun ensureSignedIn(): Result<Unit>`.
- Produces: `interface ExpenseRepository { suspend fun loadExpenses(): List<Expense>; suspend fun addExpense(amount: Double, description: String, paidBy: Person, createdBy: Person, spentAt: LocalDate): Result<Expense>; suspend fun updateExpense(id: String, amount: Double, description: String, paidBy: Person, spentAt: LocalDate): Result<Unit>; suspend fun deleteExpense(id: String): Result<Unit>; suspend fun settle(settledBy: Person): Result<Unit> }`. Wird von Task 5 (Wiring) und Task 6 (ViewModel) konsumiert.

- [ ] **Schritt 1: `ExpenseRepository`-Interface anlegen**

```kotlin
package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.LocalDate

interface ExpenseRepository {
    /** Alle Ausgaben, neueste zuerst. Liefert bei Problemen eine leere Liste statt zu werfen. */
    suspend fun loadExpenses(): List<Expense>

    /** Neue Ausgabe anlegen. Wirft nie, meldet Fehler über [Result]. */
    suspend fun addExpense(
        amount: Double,
        description: String,
        paidBy: Person,
        createdBy: Person,
        spentAt: LocalDate,
    ): Result<Expense>

    suspend fun updateExpense(
        id: String,
        amount: Double,
        description: String,
        paidBy: Person,
        spentAt: LocalDate,
    ): Result<Unit>

    suspend fun deleteExpense(id: String): Result<Unit>

    /** Gleicht alle offenen Ausgaben aus (stempelt sie auf ein neues Settlement). */
    suspend fun settle(settledBy: Person): Result<Unit>
}
```

- [ ] **Schritt 2: Test für `ExpenseRow`-Mapper schreiben**

```kotlin
package com.prehmus.selli.data.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseRowTest {
    @Test
    fun `from and toDomain preserve every field`() {
        val expense = Expense(
            id = "expense-1",
            amount = 24.5,
            description = "Einkauf",
            paidBy = Person.BASTI,
            createdBy = Person.MELLI,
            spentAt = LocalDate.of(2026, 9, 14),
            createdAt = Instant.parse("2026-09-14T10:00:00Z"),
            settlementId = null,
        )

        val mapped = ExpenseRow.from(expense).toDomain()

        assertEquals(expense, mapped)
    }

    @Test
    fun `settlementId round-trips when set`() {
        val expense = Expense(
            id = "expense-2",
            amount = 10.0,
            description = "Tanken",
            paidBy = Person.MELLI,
            createdBy = Person.MELLI,
            spentAt = LocalDate.of(2026, 9, 1),
            createdAt = Instant.parse("2026-09-01T08:00:00Z"),
            settlementId = "settlement-1",
        )

        val mapped = ExpenseRow.from(expense).toDomain()

        assertEquals("settlement-1", mapped?.settlementId)
    }

    @Test
    fun `invalid person yields null instead of throwing`() {
        val row = ExpenseRow(
            id = "expense-3",
            amount = 5.0,
            description = "Kaputt",
            paidBy = "UNKNOWN",
            createdBy = "BASTI",
            spentAt = "2026-09-14",
            createdAt = "2026-09-14T10:00:00Z",
        )

        assertNull(row.toDomain())
    }
}
```

- [ ] **Schritt 3: Test ausführen, Fehlschlag bestätigen**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew testDebugUnitTest --tests "*.ExpenseRowTest"
```

Erwartet: Kompilierfehler, `ExpenseRow` existiert noch nicht.

- [ ] **Schritt 4: `ExpenseRow.kt` implementieren**

```kotlin
package com.prehmus.selli.data.finance

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseRow(
    @SerialName("id") val id: String,
    @SerialName("amount") val amount: Double,
    @SerialName("description") val description: String,
    @SerialName("paid_by") val paidBy: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("spent_at") val spentAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("settlement_id") val settlementId: String? = null,
) {
    companion object
}

fun ExpenseRow.toDomain(): Expense? {
    val mappedPaidBy = runCatching { Person.valueOf(paidBy) }.getOrNull() ?: return null
    val mappedCreatedBy = runCatching { Person.valueOf(createdBy) }.getOrNull() ?: return null
    val mappedSpentAt = runCatching { LocalDate.parse(spentAt) }.getOrNull() ?: return null
    val mappedCreatedAt = runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull()
        ?: return null

    return Expense(
        id = id,
        amount = amount,
        description = description,
        paidBy = mappedPaidBy,
        createdBy = mappedCreatedBy,
        spentAt = mappedSpentAt,
        createdAt = mappedCreatedAt,
        settlementId = settlementId,
    )
}

fun ExpenseRow.Companion.from(expense: Expense): ExpenseRow = ExpenseRow(
    id = expense.id,
    amount = expense.amount,
    description = expense.description,
    paidBy = expense.paidBy.name,
    createdBy = expense.createdBy.name,
    spentAt = expense.spentAt.toString(),
    createdAt = DateTimeFormatter.ISO_INSTANT.format(expense.createdAt),
    settlementId = expense.settlementId,
)
```

- [ ] **Schritt 5: Test ausführen, Erfolg bestätigen** — alle 3 Tests grün.

- [ ] **Schritt 6: Test für `SettlementRow`-Mapper schreiben**

```kotlin
package com.prehmus.selli.data.finance

import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettlementRowTest {
    @Test
    fun `from and toDomain preserve every field`() {
        val settlement = Settlement(
            id = "settlement-1",
            settledBy = Person.MELLI,
            settledAt = Instant.parse("2026-09-14T18:00:00Z"),
            balanceSnapshot = 20.0,
        )

        val mapped = SettlementRow.from(settlement).toDomain()

        assertEquals(settlement, mapped)
    }

    @Test
    fun `invalid person yields null instead of throwing`() {
        val row = SettlementRow(
            id = "settlement-2",
            settledBy = "UNKNOWN",
            settledAt = "2026-09-14T18:00:00Z",
            balanceSnapshot = 5.0,
        )

        assertNull(row.toDomain())
    }
}
```

- [ ] **Schritt 7: Test ausführen, Fehlschlag bestätigen** (analog Schritt 3)

- [ ] **Schritt 8: `SettlementRow.kt` implementieren**

```kotlin
package com.prehmus.selli.data.finance

import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.model.Settlement
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SettlementRow(
    @SerialName("id") val id: String,
    @SerialName("settled_by") val settledBy: String,
    @SerialName("settled_at") val settledAt: String,
    @SerialName("balance_snapshot") val balanceSnapshot: Double,
) {
    companion object
}

fun SettlementRow.toDomain(): Settlement? {
    val mappedPerson = runCatching { Person.valueOf(settledBy) }.getOrNull() ?: return null
    val mappedSettledAt = runCatching { OffsetDateTime.parse(settledAt).toInstant() }.getOrNull()
        ?: return null

    return Settlement(
        id = id,
        settledBy = mappedPerson,
        settledAt = mappedSettledAt,
        balanceSnapshot = balanceSnapshot,
    )
}

fun SettlementRow.Companion.from(settlement: Settlement): SettlementRow = SettlementRow(
    id = settlement.id,
    settledBy = settlement.settledBy.name,
    settledAt = DateTimeFormatter.ISO_INSTANT.format(settlement.settledAt),
    balanceSnapshot = settlement.balanceSnapshot,
)
```

- [ ] **Schritt 9: Test ausführen, Erfolg bestätigen** — beide Tests grün.

- [ ] **Schritt 10: `SupabaseExpenseRepository.kt` implementieren** (kein Unit-Test — reiner Netzwerk-Wrapper, wie `SupabaseLocationRepository`; RLS/Netzwerk-Verhalten wird in Task 19 am Gerät verifiziert)

```kotlin
package com.prehmus.selli.data.finance

import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.domain.finance.prepareSettlement
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.ExpenseRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException

class SupabaseExpenseRepository(
    private val client: SelliSupabaseClient,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) : ExpenseRepository {

    override suspend fun loadExpenses(): List<Expense> {
        if (!client.isConfigured) return emptyList()
        if (client.ensureSignedIn().isFailure) return emptyList()
        val supabase = client.client ?: return emptyList()

        return try {
            supabase.from(EXPENSES_TABLE)
                .select { order("created_at", order = Order.DESCENDING) }
                .decodeList<ExpenseRow>()
                .mapNotNull(ExpenseRow::toDomain)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logError(SOURCE_SELECT, error)
            emptyList()
        }
    }

    override suspend fun addExpense(
        amount: Double,
        description: String,
        paidBy: Person,
        createdBy: Person,
        spentAt: LocalDate,
    ): Result<Expense> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            amount = amount,
            description = description,
            paidBy = paidBy,
            createdBy = createdBy,
            spentAt = spentAt,
            createdAt = Instant.now(),
            settlementId = null,
        )
        supabase.from(EXPENSES_TABLE).insert(ExpenseRow.from(expense))
        expense
    }.onFailure { error -> logError(SOURCE_INSERT, error) }

    override suspend fun updateExpense(
        id: String,
        amount: Double,
        description: String,
        paidBy: Person,
        spentAt: LocalDate,
    ): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        supabase.from(EXPENSES_TABLE).update(
            {
                set("amount", amount)
                set("description", description)
                set("paid_by", paidBy.name)
                set("spent_at", spentAt.toString())
            },
        ) {
            filter { eq("id", id) }
        }
    }.onFailure { error -> logError(SOURCE_UPDATE, error) }

    override suspend fun deleteExpense(id: String): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        supabase.from(EXPENSES_TABLE).delete { filter { eq("id", id) } }
    }.onFailure { error -> logError(SOURCE_DELETE, error) }

    override suspend fun settle(settledBy: Person): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        val openExpenses = loadExpenses().filter { it.settlementId == null }
        val result = prepareSettlement(
            expenses = openExpenses,
            settledBy = settledBy,
            settlementId = UUID.randomUUID().toString(),
            now = Instant.now(),
        )
        if (result.settledExpenseIds.isEmpty()) return@runCatching

        supabase.from(SETTLEMENTS_TABLE).insert(SettlementRow.from(result.settlement))
        supabase.from(EXPENSES_TABLE).update(
            { set("settlement_id", result.settlement.id) },
        ) {
            filter { isIn("id", result.settledExpenseIds) }
        }
    }.onFailure { error -> logError(SOURCE_SETTLE, error) }

    private fun logError(source: String, error: Throwable) {
        runCatching { logger.error(source, error) }
    }

    private companion object {
        const val EXPENSES_TABLE = "expenses"
        const val SETTLEMENTS_TABLE = "settlements"
        const val SOURCE_SELECT = "Supabase-Ausgaben laden"
        const val SOURCE_INSERT = "Supabase-Ausgabe anlegen"
        const val SOURCE_UPDATE = "Supabase-Ausgabe bearbeiten"
        const val SOURCE_DELETE = "Supabase-Ausgabe löschen"
        const val SOURCE_SETTLE = "Supabase-Ausgleichen"
    }
}
```

- [ ] **Schritt 11: Build verifizieren**

```bash
./gradlew compileDebugKotlin testDebugUnitTest
```

Erwartet: `BUILD SUCCESSFUL`, alle bisherigen Tests weiterhin grün.

- [ ] **Schritt 12: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/repository/ExpenseRepository.kt \
        app/src/main/java/com/prehmus/selli/data/finance/ExpenseRow.kt \
        app/src/main/java/com/prehmus/selli/data/finance/SettlementRow.kt \
        app/src/main/java/com/prehmus/selli/data/finance/SupabaseExpenseRepository.kt \
        app/src/test/java/com/prehmus/selli/data/finance/ExpenseRowTest.kt \
        app/src/test/java/com/prehmus/selli/data/finance/SettlementRowTest.kt
git commit -m "Data: Expense/Settlement Row-Mapper + SupabaseExpenseRepository"
```

---

### Task 5: `ExpenseRepository` in `AppDependencies` verdrahten

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/AppDependencies.kt`
- Modify: `app/src/main/java/com/prehmus/selli/DefaultAppDependencies.kt`

**Interfaces:**
- Consumes: `SupabaseExpenseRepository`, `ExpenseRepository` aus Task 4; das bereits vorhandene private Feld `supabaseClient: SelliSupabaseClient` in `DefaultAppDependencies.kt`.
- Produces: `AppDependencies.expenseRepository: ExpenseRepository`, konsumiert von Task 6 (ViewModel) über `SelliShell`.

- [ ] **Schritt 1: Interface erweitern**

In `app/src/main/java/com/prehmus/selli/AppDependencies.kt` das Import `com.prehmus.selli.domain.repository.ExpenseRepository` ergänzen und im Interface-Body (nach `val locationRepository: LocationRepository`) ergänzen:

```kotlin
    /** Gemeinsame Ausgaben (Supabase). Ohne Zugangsdaten eine dauerhaft leere Quelle. */
    val expenseRepository: ExpenseRepository
```

- [ ] **Schritt 2: Implementierung ergänzen**

In `app/src/main/java/com/prehmus/selli/DefaultAppDependencies.kt` die Imports `com.prehmus.selli.data.finance.SupabaseExpenseRepository` und `com.prehmus.selli.domain.repository.ExpenseRepository` ergänzen, und nach dem bestehenden Block für `locationRepository`/`isLocationSharingConfigured` (kurz vor der schließenden `}` der Klasse) ergänzen:

```kotlin
    // Wiederverwendet denselben Supabase-Client wie das Standort-Feature — eine Anmeldung
    // (per Google-ID-Token) genügt für alle drei Bereiche (Standort, Kosten, Ideen).
    override val expenseRepository: ExpenseRepository = SupabaseExpenseRepository(
        client = supabaseClient,
        logger = AndroidCalendarLogger,
    )
```

- [ ] **Schritt 3: Build verifizieren**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew compileDebugKotlin
```

Erwartet: `BUILD SUCCESSFUL`. Schlägt der Build fehl, weil `supabaseClient` außerhalb des Sichtbereichs liegt: `supabaseClient` ist bereits ein `private val` auf Klassenebene in `DefaultAppDependencies` (verwendet auch von `locationRepository`) — sicherstellen, dass die neue Zeile innerhalb derselben Klasse steht, nicht in einer separaten Datei.

- [ ] **Schritt 4: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/AppDependencies.kt \
        app/src/main/java/com/prehmus/selli/DefaultAppDependencies.kt
git commit -m "ExpenseRepository in AppDependencies verdrahten"
```

---

### Task 6: `ExpensesViewModel` + `ExpensesScreen` (Owner: Claude)

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/expenses/ExpensesViewModel.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/expenses/ExpensesScreen.kt`

**Interfaces:**
- Consumes: `ExpenseRepository` aus Task 4/5; `Expense`, `BalanceDirection`, `calculateBalance`, `balanceDirection` aus Task 1; `PersonPill` (bestehend, `ui/components/PersonAvatar.kt`).
- Produces: `class ExpensesViewModel(repository: ExpenseRepository, ownPerson: Person) : ViewModel()` mit `uiState: StateFlow<ExpensesUiState>`, `fun refresh()`, `fun openCreateSheet()`, `fun dismissCreateSheet()`, `fun addExpense(amount: Double, description: String, paidBy: Person, spentAt: LocalDate)`, `fun beginEditing(expense: Expense)`, `fun dismissEditing()`, `fun saveEdit(...)`, `fun deleteExpense(id: String)`, `fun settle()`, `companion object { fun factory(repository, ownPerson) }`. `@Composable fun ExpensesScreen(viewModel: ExpensesViewModel, modifier: Modifier = Modifier)`. Wird von Task 7 (Sheets) ergänzt und von Task 8 (Navigation) in `SelliShell` eingehängt.

- [ ] **Schritt 1: `ExpensesViewModel.kt` implementieren**

```kotlin
package com.prehmus.selli.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.finance.BalanceDirection
import com.prehmus.selli.domain.finance.balanceDirection
import com.prehmus.selli.domain.finance.calculateBalance
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.ExpenseRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpensesUiState(
    val expenses: List<Expense> = emptyList(),
    val balance: Double = 0.0,
    val balanceDirection: BalanceDirection = BalanceDirection.SETTLED,
    val isRefreshing: Boolean = false,
    val isCreateSheetOpen: Boolean = false,
    val editingExpense: Expense? = null,
)

/**
 * Kein Realtime-Sync (siehe Spec): [refresh] wird beim Öffnen des Tabs, per
 * Pull-to-Refresh und nach jeder eigenen Änderung aufgerufen.
 */
class ExpensesViewModel(
    private val repository: ExpenseRepository,
    private val ownPerson: Person,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExpensesUiState())
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val expenses = repository.loadExpenses()
            val balance = calculateBalance(expenses)
            _uiState.update {
                it.copy(
                    expenses = expenses,
                    balance = balance,
                    balanceDirection = balanceDirection(balance),
                    isRefreshing = false,
                )
            }
        }
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
            )
            _uiState.update { it.copy(isCreateSheetOpen = false) }
            refresh()
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
            )
            _uiState.update { it.copy(editingExpense = null) }
            refresh()
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            repository.deleteExpense(id)
            refresh()
        }
    }

    fun settle() {
        viewModelScope.launch {
            repository.settle(settledBy = ownPerson)
            refresh()
        }
    }

    companion object {
        fun factory(repository: ExpenseRepository, ownPerson: Person) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExpensesViewModel(repository, ownPerson) as T
            }
    }
}
```

- [ ] **Schritt 2: `ExpensesScreen.kt` implementieren**

```kotlin
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R
import com.prehmus.selli.domain.finance.BalanceDirection
import com.prehmus.selli.domain.model.Expense
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
    onSettle: () -> Unit,
) {
    val amountText = "%.2f".format(kotlin.math.abs(balance)).replace('.', ',')
    val label = when (direction) {
        BalanceDirection.MELLI_OWES_BASTI -> "Melli schuldet dir $amountText €"
        BalanceDirection.BASTI_OWES_MELLI -> "Du schuldest Melli $amountText €"
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
```

**Hinweis:** `AddExpenseSheet`/`ExpenseActionsSheet` werden erst in Task 7 erstellt — bis dahin schlägt `compileDebugKotlin` mit "unresolved reference" fehl, das ist zwischen Schritt 2 und Task 7 erwartet und kein Fehler in diesem Schritt. `Icons.Default.ShoppingCart` wird erst in Task 8 (Navigation) für das Bottom-Bar-Icon gebraucht, nicht hier.

- [ ] **Schritt 3: Commit** (zusammen mit Task 7, da `ExpensesScreen` erst mit den Sheets kompiliert — siehe Task 7 Schritt 3)

---

### Task 7: `AddExpenseSheet` + `ExpenseActionsSheet` (Owner: Claude)

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/expenses/AddExpenseSheet.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/expenses/ExpenseActionsSheet.kt`

**Interfaces:**
- Consumes: `Person`, `Expense` aus Task 1/Task 3 (Domain). Wird von `ExpensesScreen` (Task 6) aufgerufen.
- Produces: `@Composable fun AddExpenseSheet(onSave: (amount: Double, description: String, paidBy: Person, spentAt: LocalDate) -> Unit, onDismiss: () -> Unit)`, `@Composable fun ExpenseActionsSheet(expense: Expense, onSave: (amount: Double, description: String, paidBy: Person, spentAt: LocalDate) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit)`.

- [ ] **Schritt 1: `AddExpenseSheet.kt` implementieren**

```kotlin
package com.prehmus.selli.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onSave: (amount: Double, description: String, paidBy: Person, spentAt: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf(Person.BASTI) }
    var spentAt by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val canSave = amount != null && amount > 0.0 && description.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Ausgabe eintragen", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Betrag (€)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Beschreibung") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = "Bezahlt von", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Person.entries.forEachIndexed { index, person ->
                    SegmentedButton(
                        selected = paidBy == person,
                        onClick = { paidBy = person },
                        shape = SegmentedButtonDefaults.itemShape(index, Person.entries.size),
                    ) {
                        Text(if (person == Person.MELLI) "Melli" else "Basti")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "Datum: ${spentAt.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN))}")
                TextButton(onClick = { showDatePicker = true }) { Text("Ändern") }
            }

            Button(
                onClick = { onSave(amount!!, description, paidBy, spentAt) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Speichern")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = spentAt.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        spentAt = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

- [ ] **Schritt 2: `ExpenseActionsSheet.kt` implementieren**

```kotlin
package com.prehmus.selli.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseActionsSheet(
    expense: Expense,
    onSave: (amount: Double, description: String, paidBy: Person, spentAt: LocalDate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf(expense.amount.toString()) }
    var description by remember { mutableStateOf(expense.description) }
    var paidBy by remember { mutableStateOf(expense.paidBy) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val canSave = amount != null && amount > 0.0 && description.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Ausgabe bearbeiten", style = MaterialTheme.typography.titleLarge)

            if (expense.settlementId != null) {
                Text(
                    text = "Teil einer bereits ausgeglichenen Abrechnung — Änderungen wirken " +
                        "sich nicht auf den historischen Saldo aus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Betrag (€)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Beschreibung") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = "Bezahlt von", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Person.entries.forEachIndexed { index, person ->
                    SegmentedButton(
                        selected = paidBy == person,
                        onClick = { paidBy = person },
                        shape = SegmentedButtonDefaults.itemShape(index, Person.entries.size),
                    ) {
                        Text(if (person == Person.MELLI) "Melli" else "Basti")
                    }
                }
            }

            Button(
                onClick = { onSave(amount!!, description, paidBy, expense.spentAt) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Speichern")
            }
            TextButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Löschen", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Ausgabe löschen?") },
            text = { Text("\"${expense.description}\" wird endgültig entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Abbrechen") }
            },
        )
    }
}
```

- [ ] **Schritt 3: Build verifizieren**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew compileDebugKotlin testDebugUnitTest
```

Erwartet: `BUILD SUCCESSFUL`, `ExpensesScreen.kt` kompiliert jetzt mit `AddExpenseSheet`/`ExpenseActionsSheet`, alle bisherigen Tests weiterhin grün. Kosten-Feature ist damit UI-seitig vollständig (noch nicht in der Navigation eingehängt — folgt in Task 8).

- [ ] **Schritt 4: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/expenses/
git commit -m "UI: ExpensesScreen, AddExpenseSheet, ExpenseActionsSheet"
```

---

### Task 8: "Kosten" als vierten Tab in die Navigation einhängen (Owner: Claude)

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliDestination.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliBottomBar.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliShell.kt`

**Interfaces:**
- Consumes: `ExpensesViewModel`, `ExpensesScreen` aus Task 6/7; `dependencies.expenseRepository` aus Task 5; `ownPerson: Person` (bereits Parameter von `SelliShell`).
- Produces: Bottom-Navigation zeigt vier Ziele: `Kalender | Wir | Kosten | Standort`. Task 17 (Ideen-Navigation) erweitert dies auf fünf Ziele und ordnet final um — diese Zwischenreihenfolge ist also bewusst noch nicht final, aber nach diesem Task bereits ein vollständig funktionierender Zwischenstand.

- [ ] **Schritt 1: `SelliDestination.kt` um `EXPENSES` erweitern**

In `app/src/main/java/com/prehmus/selli/ui/shell/SelliDestination.kt` die Enum-Deklaration ändern zu:

```kotlin
enum class SelliDestination(val route: String, val label: String) {
    CALENDAR(route = "calendar", label = "Kalender"),
    HOME(route = "home", label = "Wir"),
    EXPENSES(route = "expenses", label = "Kosten"),
    LOCATION(route = "location", label = "Standort"),
    ;

    companion object {
        fun fromRoute(route: String?): SelliDestination? =
            entries.firstOrNull { destination -> destination.route == route }
    }
}
```

(Der Rest der Datei — `SETTINGS_ROUTE`, `SelliStartDestination` — bleibt unverändert.)

- [ ] **Schritt 2: `SelliBottomBar.kt` um das Icon ergänzen**

In `app/src/main/java/com/prehmus/selli/ui/shell/SelliBottomBar.kt` den Import `androidx.compose.material.icons.filled.ShoppingCart` ergänzen und die `icon()`-Extension erweitern:

```kotlin
private fun SelliDestination.icon(): ImageVector = when (this) {
    SelliDestination.CALENDAR -> Icons.Default.DateRange
    SelliDestination.HOME -> Icons.Default.Favorite
    SelliDestination.EXPENSES -> Icons.Default.ShoppingCart
    SelliDestination.LOCATION -> Icons.Default.Place
}
```

- [ ] **Schritt 3: `SelliShell.kt` um den Kosten-Screen ergänzen**

In `app/src/main/java/com/prehmus/selli/ui/shell/SelliShell.kt` die Imports `com.prehmus.selli.ui.expenses.ExpensesScreen` und `com.prehmus.selli.ui.expenses.ExpensesViewModel` ergänzen, und im `NavHost { ... }`-Block nach dem `composable(SelliDestination.HOME.route) { ... }`-Block (vor `composable(SelliDestination.LOCATION.route)`) ergänzen:

```kotlin
            composable(SelliDestination.EXPENSES.route) {
                val expensesViewModel: ExpensesViewModel = viewModel(
                    factory = ExpensesViewModel.factory(
                        repository = dependencies.expenseRepository,
                        ownPerson = ownPerson,
                    ),
                )
                ExpensesScreen(viewModel = expensesViewModel)
            }
```

- [ ] **Schritt 4: Build verifizieren**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew testDebugUnitTest assembleDebug
```

Erwartet: `BUILD SUCCESSFUL`, alle Tests grün.

- [ ] **Schritt 5: Release-Build + Gerätetest**

```bash
cp app/build/outputs/apk/debug/app-debug.apk selli.apk
```

Installieren (WLAN-Debugging oder USB) und live prüfen: Bottom-Navigation zeigt vier Ziele inkl. "Kosten" mit Einkaufswagen-Icon, Tab öffnet eine leere Liste mit "Noch keine Ausgaben", FAB öffnet `AddExpenseSheet`, eine angelegte Ausgabe erscheint in der Liste, Saldo-Karte zeigt den korrekten Betrag/die korrekte Richtung, "Ausgleichen" setzt den Saldo auf 0.

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/shell/
git commit -m "Kosten-Tab in die Bottom-Navigation einhängen"
```

---

### Task 9: Partner-Benachrichtigung bei neuer Ausgabe

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/notification/NewExpenseDetector.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/notification/ExpenseSeenStore.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/notification/PartnerActivityNotifier.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetRuntime.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt`
- Modify: `app/src/main/java/com/prehmus/selli/SelliApplication.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/notification/NewExpenseDetectorTest.kt`

**Interfaces:**
- Consumes: `Expense`, `Person`, `.other()` (bestehend, `domain/model/Models.kt`); `ExpenseRepository` aus Task 4.
- Produces: `data class NewExpenseDetectionResult(newExpenses: List<Expense>, updatedSeenIds: Set<String>)`, `fun detectNewExpenses(currentExpenses: List<Expense>, self: Person, alreadySeenIds: Set<String>): NewExpenseDetectionResult`, `class ExpenseSeenStore(context: Context) { fun load(): Set<String>; fun save(ids: Set<String>) }`, `class PartnerActivityNotifier(context: Context) { fun areNotificationsEnabled(): Boolean; fun notifyNewExpense(expense: Expense, partnerDisplayName: String); fun notifyNewNoteItem(item: NoteItem, folderName: String, partnerDisplayName: String) }`. `PartnerActivityNotifier` wird bereits hier vollständig (inkl. `notifyNewNoteItem`) angelegt — Task 18 verdrahtet nur den zweiten Aufruf, ändert die Klasse nicht mehr. `notifyNewNoteItem` referenziert `NoteItem` aus Task 11 — da Task 9 vor Task 11 läuft, muss `NoteItem` zu diesem Zeitpunkt bereits als leeres Grundgerüst existieren oder dieser Task nutzt vorübergehend nur den Expense-Teil (siehe Schritt 4 Hinweis).

- [ ] **Schritt 1: Test für den Erkennungs-Algorithmus schreiben**

```kotlin
package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NewExpenseDetectorTest {
    private fun expense(id: String, paidBy: Person, createdBy: Person) = Expense(
        id = id,
        amount = 10.0,
        description = "Test",
        paidBy = paidBy,
        createdBy = createdBy,
        spentAt = LocalDate.of(2026, 9, 14),
        createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        settlementId = null,
    )

    @Test
    fun `expense created by partner is reported as new`() {
        val result = detectNewExpenses(
            currentExpenses = listOf(expense("1", Person.MELLI, Person.MELLI)),
            self = Person.BASTI,
            alreadySeenIds = emptySet(),
        )

        assertEquals(listOf("1"), result.newExpenses.map { it.id })
        assertEquals(setOf("1"), result.updatedSeenIds)
    }

    @Test
    fun `own expense is never reported, even when paid by partner`() {
        // Stellvertretendes Eintragen: Basti trägt eine Ausgabe ein, die Melli bezahlt hat.
        val result = detectNewExpenses(
            currentExpenses = listOf(expense("1", Person.MELLI, Person.BASTI)),
            self = Person.BASTI,
            alreadySeenIds = emptySet(),
        )

        assertEquals(emptyList<Expense>(), result.newExpenses)
    }

    @Test
    fun `already seen expense is not reported again`() {
        val result = detectNewExpenses(
            currentExpenses = listOf(expense("1", Person.MELLI, Person.MELLI)),
            self = Person.BASTI,
            alreadySeenIds = setOf("1"),
        )

        assertEquals(emptyList<Expense>(), result.newExpenses)
        assertEquals(setOf("1"), result.updatedSeenIds)
    }
}
```

- [ ] **Schritt 2: Test ausführen, Fehlschlag bestätigen**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew testDebugUnitTest --tests "*.NewExpenseDetectorTest"
```

- [ ] **Schritt 3: `NewExpenseDetector.kt` implementieren**

```kotlin
package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.Person

data class NewExpenseDetectionResult(
    val newExpenses: List<Expense>,
    val updatedSeenIds: Set<String>,
)

/**
 * Erkennt Ausgaben, die der Partner neu angelegt hat und die noch nicht gemeldet wurden.
 * Reine Funktion, kein Netzwerk. Vergleicht über [Expense.createdBy] (wer eingetragen hat),
 * nicht über [Expense.paidBy] (wer bezahlt hat) — beim stellvertretenden Eintragen können
 * beide auseinanderfallen (siehe Migration Task 3, `created_by`-Spalte).
 */
fun detectNewExpenses(
    currentExpenses: List<Expense>,
    self: Person,
    alreadySeenIds: Set<String>,
): NewExpenseDetectionResult {
    val newFromPartner = currentExpenses.filter { expense ->
        expense.createdBy != self && expense.id !in alreadySeenIds
    }
    return NewExpenseDetectionResult(
        newExpenses = newFromPartner,
        updatedSeenIds = currentExpenses.map { it.id }.toSet(),
    )
}
```

- [ ] **Schritt 4: Test ausführen, Erfolg bestätigen** — alle 3 Tests grün.

- [ ] **Schritt 5: `ExpenseSeenStore.kt` implementieren** (kein Unit-Test — reiner `SharedPreferences`-Wrapper ohne Android-Kontext im JVM-Unit-Test testbar, analog zu bestehenden Stores wie `DeleteRequestNotificationStore`)

```kotlin
package com.prehmus.selli.data.notification

import android.content.Context
import android.content.SharedPreferences

/** Merkt sich, welche Ausgaben-IDs schon einmal gesehen/gemeldet wurden. */
class ExpenseSeenStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Set<String> = preferences.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty()

    fun save(ids: Set<String>) {
        preferences.edit().putStringSet(KEY_SEEN_IDS, ids).apply()
    }

    private companion object {
        const val PREFS_NAME = "selli_expense_notifications"
        const val KEY_SEEN_IDS = "seen_expense_ids"
    }
}
```

- [ ] **Schritt 6: `PartnerActivityNotifier.kt` implementieren**

**Hinweis:** Diese Klasse referenziert `NoteItem` aus Task 11, das erst später in diesem Plan entsteht. Wird dieser Task strikt in Plan-Reihenfolge abgearbeitet, `NoteItem` existiert also noch nicht: entweder Task 11 (Domain-Modelle Ideen) vorziehen (reine Datenklasse, keine Abhängigkeit zurück auf Kostentracking) und danach hierher zurückkehren, oder — einfacher — `notifyNewNoteItem` vorerst mit einem lokalen Platzhaltertyp anlegen und in Task 18 durch den echten `NoteItem`-Import ersetzen. Empfohlen: **`NoteItem`-Datenklasse aus Task 11 Schritt 1 vorziehen**, dann unten direkt den echten Typ verwenden — vermeidet Doppelarbeit.

```kotlin
package com.prehmus.selli.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prehmus.selli.MainActivity
import com.prehmus.selli.R
import com.prehmus.selli.domain.model.Expense
import com.prehmus.selli.domain.model.NoteItem

/**
 * Benachrichtigt über neue Ausgaben und neue Ideen-Punkte des Partners — eigener,
 * schlanker Kanal statt Wiederverwendung von `SharedEventNotifier`, dessen Vertrag
 * (`SharedEventChange`, Tag/Termin-Deep-Link) auf Kalender-Termine zugeschnitten ist.
 */
class PartnerActivityNotifier(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    fun areNotificationsEnabled(): Boolean = notificationManager.areNotificationsEnabled()

    fun notifyNewExpense(expense: Expense, partnerDisplayName: String) {
        if (!areNotificationsEnabled()) return
        val amountText = "%.2f".format(expense.amount).replace('.', ',')
        notify(
            notificationId = EXPENSE_NOTIFICATION_ID_OFFSET + expense.id.hashCode(),
            title = "Neue Ausgabe",
            text = "$partnerDisplayName hat $amountText € für \"${expense.description}\" eingetragen",
            intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_DESTINATION, DESTINATION_EXPENSES)
            },
        )
    }

    fun notifyNewNoteItem(item: NoteItem, folderName: String, partnerDisplayName: String) {
        if (!areNotificationsEnabled()) return
        notify(
            notificationId = NOTE_NOTIFICATION_ID_OFFSET + item.id.hashCode(),
            title = "Neue Idee",
            text = "$partnerDisplayName hat \"${item.text}\" zu $folderName hinzugefügt",
            intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_DESTINATION, DESTINATION_IDEEN)
                putExtra(EXTRA_FOLDER_ID, item.folderId)
            },
        )
    }

    private fun notify(notificationId: Int, title: String, text: String, intent: Intent) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_selli)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Fehlende Laufzeit-Berechtigung darf den Hintergrund-Job nicht abbrechen.
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Selli-Updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Neue gemeinsame Ausgaben und Ideen"
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "selli_activity_updates"
        const val EXTRA_DESTINATION = "selli_deeplink_destination"
        const val EXTRA_FOLDER_ID = "selli_deeplink_folder_id"
        const val DESTINATION_EXPENSES = "expenses"
        const val DESTINATION_IDEEN = "ideen"
        private const val EXPENSE_NOTIFICATION_ID_OFFSET = 20_000_000
        private const val NOTE_NOTIFICATION_ID_OFFSET = 30_000_000
    }
}
```

**Hinweis zum Deep-Link:** `EXTRA_DESTINATION`/`EXTRA_FOLDER_ID` werden mit diesem Task nur erzeugt, aber noch nicht ausgewertet (kein Code liest sie bisher aus `MainActivity`/`SelliApp`). Antippen der Benachrichtigung öffnet also vorerst nur die App auf ihrem zuletzt aktiven Tab, ohne automatisch zu "Kosten" bzw. zum passenden Ordner zu springen. Das vollständige Deep-Link-Handling (analog zum bestehenden `SelliDeepLink`-Mechanismus für Wir-Zeit-Termine) ist als Folge-Task außerhalb dieses Plans zu ergänzen, falls gewünscht — Basti hat im Brainstorming nur "Antippen öffnet den Kosten-Tab" gefordert, ohne die genaue Deep-Link-Tiefe zu spezifizieren; dieser Plan liefert die Benachrichtigung selbst vollständig, die exakte Sprungziel-Navigation ist ein kleiner, klar abgegrenzter Nachtrag.

- [ ] **Schritt 7: `WidgetRuntime.kt` um eine Repository-Factory erweitern**

In `app/src/main/java/com/prehmus/selli/data/widget/WidgetRuntime.kt` den Import `com.prehmus.selli.domain.repository.ExpenseRepository` ergänzen und das Objekt erweitern:

```kotlin
object WidgetRuntime {
    @Volatile
    var mergeServiceFactory: ((Context) -> CalendarMergeService)? = null

    @Volatile
    var onSnapshotUpdated: (suspend (Context) -> Unit)? = null

    @Volatile
    var expenseRepositoryFactory: (() -> ExpenseRepository)? = null
}
```

- [ ] **Schritt 8: `SelliApplication.kt` verdrahten**

In `app/src/main/java/com/prehmus/selli/SelliApplication.kt` die Imports `com.prehmus.selli.data.finance.SupabaseExpenseRepository` ergänzen und nach dem bestehenden `LocationRuntime.repositoryFactory = { ... }`-Block (vor `WidgetRefreshScheduler.ensureScheduled(this)`) ergänzen:

```kotlin
        WidgetRuntime.expenseRepositoryFactory = {
            SupabaseExpenseRepository(
                client = SelliSupabaseClient(
                    supabaseUrl = BuildConfig.SUPABASE_URL,
                    supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
                    idTokenProvider = locationGoogleRepository,
                    logger = AndroidCalendarLogger,
                ),
                logger = AndroidCalendarLogger,
            )
        }
```

- [ ] **Schritt 9: `WidgetRefreshWorker.kt` um den Ausgaben-Check erweitern**

In `app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt` die Imports `com.prehmus.selli.data.notification.ExpenseSeenStore`, `com.prehmus.selli.data.notification.PartnerActivityNotifier`, `com.prehmus.selli.domain.notification.detectNewExpenses` ergänzen. In `doWork()`, direkt nach der bestehenden Zeile `runCatching { notifySharedEventChanges(events = events, partner = partner) }`, ergänzen:

```kotlin
            runCatching { notifyNewExpenses(partner) }
```

Und eine neue private Methode ergänzen (z. B. direkt nach `notifySharedEventChanges`):

```kotlin
    private suspend fun notifyNewExpenses(partner: PartnerInfo) {
        val repositoryFactory = WidgetRuntime.expenseRepositoryFactory ?: return
        val notifier = PartnerActivityNotifier(applicationContext)
        if (!notifier.areNotificationsEnabled()) return

        val expenses = repositoryFactory().loadExpenses()
        val store = ExpenseSeenStore(applicationContext)
        val result = detectNewExpenses(
            currentExpenses = expenses,
            self = partner.person.other(),
            alreadySeenIds = store.load(),
        )
        result.newExpenses.forEach { expense -> notifier.notifyNewExpense(expense, partner.displayName) }
        store.save(result.updatedSeenIds)
    }
```

- [ ] **Schritt 10: Build verifizieren**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew compileDebugKotlin testDebugUnitTest
```

Erwartet: `BUILD SUCCESSFUL`. Schlägt der Build wegen `NoteItem` (Schritt 6) fehl: sicherstellen, dass Task 11 Schritt 1 (Domain-Modelle `NoteFolder`/`NoteItem`) vorgezogen wurde.

- [ ] **Schritt 11: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/notification/NewExpenseDetector.kt \
        app/src/main/java/com/prehmus/selli/data/notification/ExpenseSeenStore.kt \
        app/src/main/java/com/prehmus/selli/data/notification/PartnerActivityNotifier.kt \
        app/src/main/java/com/prehmus/selli/data/widget/WidgetRuntime.kt \
        app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt \
        app/src/main/java/com/prehmus/selli/SelliApplication.kt \
        app/src/test/java/com/prehmus/selli/domain/notification/NewExpenseDetectorTest.kt
git commit -m "Partner-Benachrichtigung bei neuer Ausgabe"
```

**Damit ist Teil A (Kostentracking) vollständig.** Basti kann ab hier bereits produktiv Ausgaben tracken, unabhängig davon ob Teil B schon existiert.

---

## Teil B: "Ideen"

### Task 10: Supabase-Migration — `note_folders` und `note_items`

**Files:**
- Create: `supabase/migrations/20260914160000_selli_notes.sql`

**Interfaces:**
- Produces: Tabellen `public.note_folders`, `public.note_items` mit RLS, erreichbar über `SelliSupabaseClient` (Task 13).

- [ ] **Schritt 1: Migrationsdatei schreiben**

```sql
-- Selli: "Ideen" — Ordner mit abhakbaren Punkten für Aktivitäten/Rezepte.
-- Nutzt dieselbe Allowlist wie das Standort-Feature (public.selli_members / is_selli_member()).
-- Design-Entscheidungen: docs/superpowers/specs/2026-09-14-ideen-design.md

create table if not exists public.note_folders (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,
  created_by  text not null check (created_by in ('BASTI', 'MELLI')),
  created_at  timestamptz not null default now()
);

create table if not exists public.note_items (
  id                  uuid primary key default gen_random_uuid(),
  folder_id           uuid not null references public.note_folders(id) on delete cascade,
  text                text not null,
  url                 text,
  preview_title       text,
  preview_image_url   text,
  is_checked          boolean not null default false,
  checked_at          timestamptz,
  created_by          text not null check (created_by in ('BASTI', 'MELLI')),
  created_at          timestamptz not null default now()
);

alter table public.note_folders enable row level security;
alter table public.note_items enable row level security;

drop policy if exists "members read folders" on public.note_folders;
create policy "members read folders" on public.note_folders
  for select to authenticated using (public.is_selli_member());
drop policy if exists "members insert folders" on public.note_folders;
create policy "members insert folders" on public.note_folders
  for insert to authenticated with check (public.is_selli_member());
drop policy if exists "members update folders" on public.note_folders;
create policy "members update folders" on public.note_folders
  for update to authenticated using (public.is_selli_member()) with check (public.is_selli_member());
drop policy if exists "members delete folders" on public.note_folders;
create policy "members delete folders" on public.note_folders
  for delete to authenticated using (public.is_selli_member());

drop policy if exists "members read items" on public.note_items;
create policy "members read items" on public.note_items
  for select to authenticated using (public.is_selli_member());
drop policy if exists "members insert items" on public.note_items;
create policy "members insert items" on public.note_items
  for insert to authenticated with check (public.is_selli_member());
drop policy if exists "members update items" on public.note_items;
create policy "members update items" on public.note_items
  for update to authenticated using (public.is_selli_member()) with check (public.is_selli_member());
drop policy if exists "members delete items" on public.note_items;
create policy "members delete items" on public.note_items
  for delete to authenticated using (public.is_selli_member());

grant select, insert, update, delete on public.note_folders to authenticated;
grant select, insert, update, delete on public.note_items to authenticated;
```

- [ ] **Schritt 2: Migration anwenden** (analog Task 3 Schritt 2 — MCP-Tool oder Dashboard-SQL-Editor)

- [ ] **Schritt 3: Verifizieren**

```sql
select
  (select count(*) from pg_tables where schemaname='public' and tablename in ('note_folders','note_items')) as tabellen,
  (select count(*) from pg_policies where schemaname='public' and tablename='note_folders') as policies_folders,
  (select count(*) from pg_policies where schemaname='public' and tablename='note_items') as policies_items;
```

Erwartet: `tabellen = 2`, `policies_folders = 4`, `policies_items = 4`.

- [ ] **Schritt 4: Commit**

```bash
git add supabase/migrations/20260914160000_selli_notes.sql
git commit -m "Migration: note_folders und note_items Tabellen für Ideen"
```

---

### Task 11: Domain-Modelle NoteFolder/NoteItem + Archivierungs-Logik (TDD)

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/model/NoteFolder.kt`
- Create: `app/src/main/java/com/prehmus/selli/domain/notes/NoteItemLists.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/notes/NoteItemListsTest.kt`

**Interfaces:**
- Produces: `data class NoteFolder(id: String, name: String, createdBy: Person, createdAt: Instant)`, `data class NoteItem(id: String, folderId: String, text: String, url: String?, previewTitle: String?, previewImageUrl: String?, isChecked: Boolean, checkedAt: Instant?, createdBy: Person, createdAt: Instant)`, `fun List<NoteItem>.openItems(): List<NoteItem>`, `fun List<NoteItem>.archivedItems(): List<NoteItem>`. `NoteItem` wird bereits von Task 9 (`PartnerActivityNotifier.notifyNewNoteItem`) referenziert — **dieser Task sollte vor Task 9 Schritt 6 umgesetzt werden**, siehe Hinweis dort.

- [ ] **Schritt 1: Modelle anlegen**

```kotlin
package com.prehmus.selli.domain.model

import java.time.Instant

data class NoteFolder(
    val id: String,
    val name: String,
    val createdBy: Person,
    val createdAt: Instant,
)

data class NoteItem(
    val id: String,
    val folderId: String,
    val text: String,
    val url: String?,
    val previewTitle: String?,
    val previewImageUrl: String?,
    val isChecked: Boolean,
    val checkedAt: Instant?,
    val createdBy: Person,
    val createdAt: Instant,
)
```

- [ ] **Schritt 2: Test für die Offen/Archiv-Trennung schreiben**

```kotlin
package com.prehmus.selli.domain.notes

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteItemListsTest {
    private fun item(
        id: String,
        isChecked: Boolean,
        createdAt: Instant,
        checkedAt: Instant? = null,
    ) = NoteItem(
        id = id,
        folderId = "folder-1",
        text = "Punkt $id",
        url = null,
        previewTitle = null,
        previewImageUrl = null,
        isChecked = isChecked,
        checkedAt = checkedAt,
        createdBy = Person.BASTI,
        createdAt = createdAt,
    )

    @Test
    fun `openItems excludes checked items and sorts newest first`() {
        val items = listOf(
            item("1", isChecked = false, createdAt = Instant.parse("2026-09-01T10:00:00Z")),
            item("2", isChecked = true, createdAt = Instant.parse("2026-09-02T10:00:00Z")),
            item("3", isChecked = false, createdAt = Instant.parse("2026-09-03T10:00:00Z")),
        )

        assertEquals(listOf("3", "1"), items.openItems().map { it.id })
    }

    @Test
    fun `archivedItems includes only checked items sorted by checkedAt`() {
        val items = listOf(
            item("1", isChecked = true, createdAt = Instant.parse("2026-09-01T10:00:00Z"), checkedAt = Instant.parse("2026-09-05T10:00:00Z")),
            item("2", isChecked = false, createdAt = Instant.parse("2026-09-02T10:00:00Z")),
            item("3", isChecked = true, createdAt = Instant.parse("2026-09-03T10:00:00Z"), checkedAt = Instant.parse("2026-09-10T10:00:00Z")),
        )

        assertEquals(listOf("3", "1"), items.archivedItems().map { it.id })
    }

    @Test
    fun `archivedItems falls back to createdAt when checkedAt is missing`() {
        val items = listOf(
            item("1", isChecked = true, createdAt = Instant.parse("2026-09-01T10:00:00Z"), checkedAt = null),
        )

        assertEquals(listOf("1"), items.archivedItems().map { it.id })
    }
}
```

- [ ] **Schritt 3: Test ausführen, Fehlschlag bestätigen**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew testDebugUnitTest --tests "*.NoteItemListsTest"
```

- [ ] **Schritt 4: `NoteItemLists.kt` implementieren**

```kotlin
package com.prehmus.selli.domain.notes

import com.prehmus.selli.domain.model.NoteItem

/** Offene Punkte, neueste zuerst — für die Hauptliste der Ordner-Detailansicht. */
fun List<NoteItem>.openItems(): List<NoteItem> =
    filter { !it.isChecked }.sortedByDescending { it.createdAt }

/** Abgehakte Punkte für den aufklappbaren Archiv-Bereich, zuletzt abgehakt zuerst. */
fun List<NoteItem>.archivedItems(): List<NoteItem> =
    filter { it.isChecked }.sortedByDescending { it.checkedAt ?: it.createdAt }
```

- [ ] **Schritt 5: Test ausführen, Erfolg bestätigen** — alle 3 Tests grün.

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/model/NoteFolder.kt \
        app/src/main/java/com/prehmus/selli/domain/notes/NoteItemLists.kt \
        app/src/test/java/com/prehmus/selli/domain/notes/NoteItemListsTest.kt
git commit -m "Domain: NoteFolder/NoteItem-Modelle, Offen/Archiv-Trennung"
```

---

### Task 12: Link-Vorschau — Parsing (TDD) + Jsoup-Fetcher

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/notes/LinkPreview.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/notes/JsoupLinkPreviewFetcher.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/notes/LinkPreviewParserTest.kt`

**Interfaces:**
- Consumes: `libs.jsoup` aus Task 2.
- Produces: `data class LinkPreview(title: String?, imageUrl: String?)`, `fun interface LinkPreviewFetcher { suspend fun fetch(url: String): LinkPreview? }`, `fun parseLinkPreview(document: org.jsoup.nodes.Document): LinkPreview`, `class JsoupLinkPreviewFetcher(logger: CalendarLogger = NoOpCalendarLogger) : LinkPreviewFetcher`. Wird von Task 13 (`SupabaseNoteRepository`) und Task 14 (Wiring) verwendet.

- [ ] **Schritt 1: Test für die reine Parsing-Funktion schreiben**

Bewusst getrennt vom Netzwerk-Fetch getestet — `Jsoup.parse(html, baseUri)` parst eine Zeichenkette direkt, kein echter HTTP-Request nötig.

```kotlin
package com.prehmus.selli.domain.notes

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewParserTest {
    @Test
    fun `prefers open graph title and image over plain title`() {
        val html = """
            <html><head>
              <title>Fallback Title</title>
              <meta property="og:title" content="Pasta Carbonara Rezept">
              <meta property="og:image" content="https://example.com/pasta.jpg">
            </head></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://example.com")

        val preview = parseLinkPreview(document)

        assertEquals("Pasta Carbonara Rezept", preview.title)
        assertEquals("https://example.com/pasta.jpg", preview.imageUrl)
    }

    @Test
    fun `falls back to plain title when no open graph tag exists`() {
        val html = "<html><head><title>Nur ein Titel</title></head></html>"
        val document = Jsoup.parse(html, "https://example.com")

        val preview = parseLinkPreview(document)

        assertEquals("Nur ein Titel", preview.title)
        assertNull(preview.imageUrl)
    }

    @Test
    fun `missing title and image yields nulls, never throws`() {
        val document = Jsoup.parse("<html><head></head><body></body></html>", "https://example.com")

        val preview = parseLinkPreview(document)

        assertNull(preview.title)
        assertNull(preview.imageUrl)
    }
}
```

- [ ] **Schritt 2: Test ausführen, Fehlschlag bestätigen**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew testDebugUnitTest --tests "*.LinkPreviewParserTest"
```

Erwartet: Kompilierfehler, `parseLinkPreview`/`LinkPreview` existieren noch nicht. (Jsoup selbst ist bereits als `implementation`-Abhängigkeit aus Task 2 automatisch auch im Unit-Test-Classpath verfügbar — kein separates `testImplementation` nötig.)

- [ ] **Schritt 3: `LinkPreview.kt` implementieren**

```kotlin
package com.prehmus.selli.domain.notes

import org.jsoup.nodes.Document

data class LinkPreview(
    val title: String?,
    val imageUrl: String?,
)

/** Lädt Titel/Vorschaubild einer URL. Wirft nie — Fehler/Timeout liefern `null`. */
fun interface LinkPreviewFetcher {
    suspend fun fetch(url: String): LinkPreview?
}

/**
 * Reine Extraktion aus einem bereits geladenen HTML-Dokument — getrennt vom Netzwerk-Fetch
 * (siehe `JsoupLinkPreviewFetcher`), damit sie ohne echten HTTP-Request testbar ist.
 * Bevorzugt Open-Graph-Tags, fällt auf `<title>` zurück.
 */
fun parseLinkPreview(document: Document): LinkPreview {
    val ogTitle = document.select("meta[property=og:title]").attr("content").takeUnless(String::isBlank)
    val title = ogTitle ?: document.title().takeUnless(String::isBlank)
    val imageUrl = document.select("meta[property=og:image]").attr("content").takeUnless(String::isBlank)
    return LinkPreview(title = title, imageUrl = imageUrl)
}
```

- [ ] **Schritt 4: Test ausführen, Erfolg bestätigen** — alle 3 Tests grün.

- [ ] **Schritt 5: `JsoupLinkPreviewFetcher.kt` implementieren** (kein Unit-Test — echter Netzwerk-Fetch, analog `SupabaseLocationRepository` bewusst ungetestet)

```kotlin
package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.notes.LinkPreview
import com.prehmus.selli.domain.notes.LinkPreviewFetcher
import com.prehmus.selli.domain.notes.parseLinkPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup

class JsoupLinkPreviewFetcher(
    private val logger: CalendarLogger = NoOpCalendarLogger,
) : LinkPreviewFetcher {
    override suspend fun fetch(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                val document = Jsoup.connect(url)
                    .timeout(TIMEOUT_MILLIS.toInt())
                    .get()
                parseLinkPreview(document)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logger.error(SOURCE, error)
            null
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val SOURCE = "Link-Vorschau"
    }
}
```

- [ ] **Schritt 6: Build verifizieren**

```bash
./gradlew compileDebugKotlin testDebugUnitTest
```

- [ ] **Schritt 7: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/notes/LinkPreview.kt \
        app/src/main/java/com/prehmus/selli/data/notes/JsoupLinkPreviewFetcher.kt \
        app/src/test/java/com/prehmus/selli/domain/notes/LinkPreviewParserTest.kt
git commit -m "Link-Vorschau: Parsing-Logik + Jsoup-Fetcher"
```

---

### Task 13: `NoteRepository`-Interface + Row-Mapper (TDD) + `SupabaseNoteRepository`

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/repository/NoteRepository.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/notes/NoteFolderRow.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/notes/NoteItemRow.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/notes/SupabaseNoteRepository.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/notes/NoteFolderRowTest.kt`
- Test: `app/src/test/java/com/prehmus/selli/data/notes/NoteItemRowTest.kt`

**Interfaces:**
- Consumes: `NoteFolder`, `NoteItem` aus Task 11; `LinkPreviewFetcher`, `LinkPreview` aus Task 12; `SelliSupabaseClient` (bestehend).
- Produces: `interface NoteRepository { suspend fun loadFolders(): List<NoteFolder>; suspend fun addFolder(name: String, createdBy: Person): Result<NoteFolder>; suspend fun renameFolder(id: String, name: String): Result<Unit>; suspend fun deleteFolder(id: String): Result<Unit>; suspend fun loadItems(folderId: String): List<NoteItem>; suspend fun addItem(folderId: String, text: String, url: String?, createdBy: Person): Result<NoteItem>; suspend fun updateItem(id: String, text: String, url: String?): Result<Unit>; suspend fun setChecked(id: String, checked: Boolean): Result<Unit>; suspend fun deleteItem(id: String): Result<Unit> }`. Wird von Task 14 (Wiring), Task 15/16 (ViewModels) und Task 18 (Benachrichtigung) konsumiert.

- [ ] **Schritt 1: `NoteRepository`-Interface anlegen**

```kotlin
package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person

interface NoteRepository {
    /** Alle Ordner, neueste zuerst. Liefert bei Problemen eine leere Liste statt zu werfen. */
    suspend fun loadFolders(): List<NoteFolder>

    suspend fun addFolder(name: String, createdBy: Person): Result<NoteFolder>
    suspend fun renameFolder(id: String, name: String): Result<Unit>

    /** `on delete cascade` in der Migration räumt zugehörige `note_items` serverseitig mit auf. */
    suspend fun deleteFolder(id: String): Result<Unit>

    /** Alle Punkte eines Ordners, neueste zuerst. Liefert bei Problemen eine leere Liste. */
    suspend fun loadItems(folderId: String): List<NoteItem>

    /** Legt einen Punkt an; lädt bei gesetzter [url] zuerst die Link-Vorschau (siehe Task 12). */
    suspend fun addItem(
        folderId: String,
        text: String,
        url: String?,
        createdBy: Person,
    ): Result<NoteItem>

    suspend fun updateItem(id: String, text: String, url: String?): Result<Unit>
    suspend fun setChecked(id: String, checked: Boolean): Result<Unit>
    suspend fun deleteItem(id: String): Result<Unit>
}
```

- [ ] **Schritt 2: Test für `NoteFolderRow`-Mapper schreiben**

```kotlin
package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteFolderRowTest {
    @Test
    fun `from and toDomain preserve every field`() {
        val folder = NoteFolder(
            id = "folder-1",
            name = "Rezepte",
            createdBy = Person.MELLI,
            createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        )

        assertEquals(folder, NoteFolderRow.from(folder).toDomain())
    }

    @Test
    fun `invalid person yields null instead of throwing`() {
        val row = NoteFolderRow(
            id = "folder-2",
            name = "Kaputt",
            createdBy = "UNKNOWN",
            createdAt = "2026-09-14T10:00:00Z",
        )

        assertNull(row.toDomain())
    }
}
```

- [ ] **Schritt 3: Test ausführen, Fehlschlag bestätigen**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew testDebugUnitTest --tests "*.NoteFolderRowTest"
```

- [ ] **Schritt 4: `NoteFolderRow.kt` implementieren**

```kotlin
package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.Person
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoteFolderRow(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
) {
    companion object
}

fun NoteFolderRow.toDomain(): NoteFolder? {
    val mappedPerson = runCatching { Person.valueOf(createdBy) }.getOrNull() ?: return null
    val mappedCreatedAt = runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull()
        ?: return null
    return NoteFolder(id = id, name = name, createdBy = mappedPerson, createdAt = mappedCreatedAt)
}

fun NoteFolderRow.Companion.from(folder: NoteFolder): NoteFolderRow = NoteFolderRow(
    id = folder.id,
    name = folder.name,
    createdBy = folder.createdBy.name,
    createdAt = DateTimeFormatter.ISO_INSTANT.format(folder.createdAt),
)
```

- [ ] **Schritt 5: Test ausführen, Erfolg bestätigen** — beide Tests grün.

- [ ] **Schritt 6: Test für `NoteItemRow`-Mapper schreiben**

```kotlin
package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteItemRowTest {
    @Test
    fun `from and toDomain preserve every field including optional ones`() {
        val item = NoteItem(
            id = "item-1",
            folderId = "folder-1",
            text = "Pasta Carbonara",
            url = "https://example.com/pasta",
            previewTitle = "Pasta Carbonara Rezept",
            previewImageUrl = "https://example.com/pasta.jpg",
            isChecked = true,
            checkedAt = Instant.parse("2026-09-14T12:00:00Z"),
            createdBy = Person.BASTI,
            createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        )

        assertEquals(item, NoteItemRow.from(item).toDomain())
    }

    @Test
    fun `from and toDomain handle all-null optional fields`() {
        val item = NoteItem(
            id = "item-2",
            folderId = "folder-1",
            text = "Nur Text",
            url = null,
            previewTitle = null,
            previewImageUrl = null,
            isChecked = false,
            checkedAt = null,
            createdBy = Person.MELLI,
            createdAt = Instant.parse("2026-09-14T10:00:00Z"),
        )

        assertEquals(item, NoteItemRow.from(item).toDomain())
    }

    @Test
    fun `invalid person yields null instead of throwing`() {
        val row = NoteItemRow(
            id = "item-3",
            folderId = "folder-1",
            text = "Kaputt",
            createdBy = "UNKNOWN",
            createdAt = "2026-09-14T10:00:00Z",
        )

        assertNull(row.toDomain())
    }
}
```

- [ ] **Schritt 7: Test ausführen, Fehlschlag bestätigen** (analog Schritt 3)

- [ ] **Schritt 8: `NoteItemRow.kt` implementieren**

```kotlin
package com.prehmus.selli.data.notes

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoteItemRow(
    @SerialName("id") val id: String,
    @SerialName("folder_id") val folderId: String,
    @SerialName("text") val text: String,
    @SerialName("url") val url: String? = null,
    @SerialName("preview_title") val previewTitle: String? = null,
    @SerialName("preview_image_url") val previewImageUrl: String? = null,
    @SerialName("is_checked") val isChecked: Boolean = false,
    @SerialName("checked_at") val checkedAt: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
) {
    companion object
}

fun NoteItemRow.toDomain(): NoteItem? {
    val mappedPerson = runCatching { Person.valueOf(createdBy) }.getOrNull() ?: return null
    val mappedCreatedAt = runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull()
        ?: return null
    val mappedCheckedAt = checkedAt?.let { raw ->
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    }

    return NoteItem(
        id = id,
        folderId = folderId,
        text = text,
        url = url,
        previewTitle = previewTitle,
        previewImageUrl = previewImageUrl,
        isChecked = isChecked,
        checkedAt = mappedCheckedAt,
        createdBy = mappedPerson,
        createdAt = mappedCreatedAt,
    )
}

fun NoteItemRow.Companion.from(item: NoteItem): NoteItemRow = NoteItemRow(
    id = item.id,
    folderId = item.folderId,
    text = item.text,
    url = item.url,
    previewTitle = item.previewTitle,
    previewImageUrl = item.previewImageUrl,
    isChecked = item.isChecked,
    checkedAt = item.checkedAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
    createdBy = item.createdBy.name,
    createdAt = DateTimeFormatter.ISO_INSTANT.format(item.createdAt),
)
```

- [ ] **Schritt 9: Test ausführen, Erfolg bestätigen** — alle 3 Tests grün.

- [ ] **Schritt 10: `SupabaseNoteRepository.kt` implementieren** (kein Unit-Test — reiner Netzwerk-Wrapper, analog Task 4 Schritt 10)

```kotlin
package com.prehmus.selli.data.notes

import com.prehmus.selli.data.supabase.SelliSupabaseClient
import com.prehmus.selli.domain.logging.CalendarLogger
import com.prehmus.selli.domain.logging.NoOpCalendarLogger
import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.notes.LinkPreview
import com.prehmus.selli.domain.notes.LinkPreviewFetcher
import com.prehmus.selli.domain.repository.NoteRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException

class SupabaseNoteRepository(
    private val client: SelliSupabaseClient,
    private val linkPreviewFetcher: LinkPreviewFetcher,
    private val logger: CalendarLogger = NoOpCalendarLogger,
) : NoteRepository {

    override suspend fun loadFolders(): List<NoteFolder> {
        if (!client.isConfigured) return emptyList()
        if (client.ensureSignedIn().isFailure) return emptyList()
        val supabase = client.client ?: return emptyList()

        return try {
            supabase.from(FOLDERS_TABLE)
                .select { order("created_at", order = Order.DESCENDING) }
                .decodeList<NoteFolderRow>()
                .mapNotNull(NoteFolderRow::toDomain)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logError(SOURCE_LOAD_FOLDERS, error)
            emptyList()
        }
    }

    override suspend fun addFolder(name: String, createdBy: Person): Result<NoteFolder> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        val folder = NoteFolder(
            id = UUID.randomUUID().toString(),
            name = name,
            createdBy = createdBy,
            createdAt = Instant.now(),
        )
        supabase.from(FOLDERS_TABLE).insert(NoteFolderRow.from(folder))
        folder
    }.onFailure { error -> logError(SOURCE_ADD_FOLDER, error) }

    override suspend fun renameFolder(id: String, name: String): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        supabase.from(FOLDERS_TABLE).update({ set("name", name) }) {
            filter { eq("id", id) }
        }
    }.onFailure { error -> logError(SOURCE_RENAME_FOLDER, error) }

    override suspend fun deleteFolder(id: String): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        supabase.from(FOLDERS_TABLE).delete { filter { eq("id", id) } }
    }.onFailure { error -> logError(SOURCE_DELETE_FOLDER, error) }

    override suspend fun loadItems(folderId: String): List<NoteItem> {
        if (!client.isConfigured) return emptyList()
        if (client.ensureSignedIn().isFailure) return emptyList()
        val supabase = client.client ?: return emptyList()

        return try {
            supabase.from(ITEMS_TABLE)
                .select {
                    order("created_at", order = Order.DESCENDING)
                    filter { eq("folder_id", folderId) }
                }
                .decodeList<NoteItemRow>()
                .mapNotNull(NoteItemRow::toDomain)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logError(SOURCE_LOAD_ITEMS, error)
            emptyList()
        }
    }

    override suspend fun addItem(
        folderId: String,
        text: String,
        url: String?,
        createdBy: Person,
    ): Result<NoteItem> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        // Vorschau vor dem Schreiben laden — schlägt sie fehl/läuft in den Timeout, liefert
        // der Fetcher laut Vertrag `null`, der Punkt wird trotzdem ohne Vorschau gespeichert.
        val preview: LinkPreview? = url?.takeUnless(String::isBlank)?.let { linkPreviewFetcher.fetch(it) }
        val item = NoteItem(
            id = UUID.randomUUID().toString(),
            folderId = folderId,
            text = text,
            url = url,
            previewTitle = preview?.title,
            previewImageUrl = preview?.imageUrl,
            isChecked = false,
            checkedAt = null,
            createdBy = createdBy,
            createdAt = Instant.now(),
        )
        supabase.from(ITEMS_TABLE).insert(NoteItemRow.from(item))
        item
    }.onFailure { error -> logError(SOURCE_ADD_ITEM, error) }

    override suspend fun updateItem(id: String, text: String, url: String?): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        supabase.from(ITEMS_TABLE).update(
            {
                set("text", text)
                set("url", url)
            },
        ) {
            filter { eq("id", id) }
        }
    }.onFailure { error -> logError(SOURCE_UPDATE_ITEM, error) }

    override suspend fun setChecked(id: String, checked: Boolean): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        val checkedAtValue: String? = if (checked) Instant.now().toString() else null
        supabase.from(ITEMS_TABLE).update(
            {
                set("is_checked", checked)
                set("checked_at", checkedAtValue)
            },
        ) {
            filter { eq("id", id) }
        }
    }.onFailure { error -> logError(SOURCE_SET_CHECKED, error) }

    override suspend fun deleteItem(id: String): Result<Unit> = runCatching {
        client.ensureSignedIn().getOrThrow()
        val supabase = client.client ?: throw IllegalStateException("Supabase ist nicht konfiguriert.")
        supabase.from(ITEMS_TABLE).delete { filter { eq("id", id) } }
    }.onFailure { error -> logError(SOURCE_DELETE_ITEM, error) }

    private fun logError(source: String, error: Throwable) {
        runCatching { logger.error(source, error) }
    }

    private companion object {
        const val FOLDERS_TABLE = "note_folders"
        const val ITEMS_TABLE = "note_items"
        const val SOURCE_LOAD_FOLDERS = "Supabase-Ordner laden"
        const val SOURCE_ADD_FOLDER = "Supabase-Ordner anlegen"
        const val SOURCE_RENAME_FOLDER = "Supabase-Ordner umbenennen"
        const val SOURCE_DELETE_FOLDER = "Supabase-Ordner löschen"
        const val SOURCE_LOAD_ITEMS = "Supabase-Punkte laden"
        const val SOURCE_ADD_ITEM = "Supabase-Punkt anlegen"
        const val SOURCE_UPDATE_ITEM = "Supabase-Punkt bearbeiten"
        const val SOURCE_SET_CHECKED = "Supabase-Punkt abhaken"
        const val SOURCE_DELETE_ITEM = "Supabase-Punkt löschen"
    }
}
```

**Wichtig zu `set("checked_at", checkedAtValue)`:** `checkedAtValue` muss als typisierte `String?`-Variable übergeben werden (nicht als Literal `null`) — gegen die installierte Supabase-Postgrest-Version 3.1.4 verifiziert, dass `set(column, nullableValue)` so funktioniert und `checked_at` beim Ausblenden korrekt auf `NULL` zurücksetzt.

- [ ] **Schritt 11: Build verifizieren**

```bash
./gradlew compileDebugKotlin testDebugUnitTest
```

- [ ] **Schritt 12: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/repository/NoteRepository.kt \
        app/src/main/java/com/prehmus/selli/data/notes/NoteFolderRow.kt \
        app/src/main/java/com/prehmus/selli/data/notes/NoteItemRow.kt \
        app/src/main/java/com/prehmus/selli/data/notes/SupabaseNoteRepository.kt \
        app/src/test/java/com/prehmus/selli/data/notes/NoteFolderRowTest.kt \
        app/src/test/java/com/prehmus/selli/data/notes/NoteItemRowTest.kt
git commit -m "Data: NoteFolder/NoteItem Row-Mapper + SupabaseNoteRepository"
```

---

### Task 14: `NoteRepository` in `AppDependencies` verdrahten

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/AppDependencies.kt`
- Modify: `app/src/main/java/com/prehmus/selli/DefaultAppDependencies.kt`

**Interfaces:**
- Consumes: `SupabaseNoteRepository`, `NoteRepository` aus Task 13; `JsoupLinkPreviewFetcher` aus Task 12; das bereits vorhandene private Feld `supabaseClient`.
- Produces: `AppDependencies.noteRepository: NoteRepository`, konsumiert von Task 15/16 (ViewModels).

- [ ] **Schritt 1: Interface erweitern**

In `app/src/main/java/com/prehmus/selli/AppDependencies.kt` den Import `com.prehmus.selli.domain.repository.NoteRepository` ergänzen und ergänzen:

```kotlin
    /** Gemeinsame Ideen-Ordner/-Punkte (Supabase). Ohne Zugangsdaten eine dauerhaft leere Quelle. */
    val noteRepository: NoteRepository
```

- [ ] **Schritt 2: Implementierung ergänzen**

In `app/src/main/java/com/prehmus/selli/DefaultAppDependencies.kt` die Imports `com.prehmus.selli.data.notes.SupabaseNoteRepository`, `com.prehmus.selli.data.notes.JsoupLinkPreviewFetcher`, `com.prehmus.selli.domain.repository.NoteRepository` ergänzen und nach dem `expenseRepository`-Block (Task 5) ergänzen:

```kotlin
    override val noteRepository: NoteRepository = SupabaseNoteRepository(
        client = supabaseClient,
        linkPreviewFetcher = JsoupLinkPreviewFetcher(logger = AndroidCalendarLogger),
        logger = AndroidCalendarLogger,
    )
```

- [ ] **Schritt 3: Build verifizieren**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew compileDebugKotlin
```

- [ ] **Schritt 4: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/AppDependencies.kt \
        app/src/main/java/com/prehmus/selli/DefaultAppDependencies.kt
git commit -m "NoteRepository in AppDependencies verdrahten"
```

---

### Task 15: `IdeenViewModel` + `IdeenScreen` — Ebene 1 (Ordner-Übersicht) (Owner: Claude)

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/ideen/IdeenViewModel.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/ideen/IdeenScreen.kt`

**Interfaces:**
- Consumes: `NoteRepository` aus Task 13/14; `NoteFolder` aus Task 11.
- Produces: `class IdeenViewModel(repository: NoteRepository, ownPerson: Person) : ViewModel()` mit `uiState: StateFlow<IdeenUiState>`, `fun refresh()`, `fun openCreateFolderSheet()`, `fun dismissCreateFolderSheet()`, `fun createFolder(name: String)`, `fun beginRenaming(folder: NoteFolder)`, `fun dismissRenaming()`, `fun renameFolder(name: String)`, `fun beginDeleting(folder: NoteFolder)`, `fun dismissDeleting()`, `fun confirmDelete()`, `companion object { fun factory(repository, ownPerson) }`. `@Composable fun IdeenScreen(viewModel: IdeenViewModel, onOpenFolder: (folderId: String) -> Unit, modifier: Modifier = Modifier)`, sowie das intern genutzte, aber außerhalb der Datei wiederverwendbare `@Composable internal fun IdeenEmptyHint(message: String, modifier: Modifier = Modifier)` (wird von Task 16 für die Ordner-Detailansicht wiederverwendet). Wird von Task 17 (Navigation) eingehängt.

- [ ] **Schritt 1: `IdeenViewModel.kt` implementieren**

```kotlin
package com.prehmus.selli.ui.ideen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.model.NoteFolder
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdeenUiState(
    val folders: List<NoteFolder> = emptyList(),
    val openCountByFolder: Map<String, Int> = emptyMap(),
    val totalCountByFolder: Map<String, Int> = emptyMap(),
    val isRefreshing: Boolean = false,
    val isCreateFolderSheetOpen: Boolean = false,
    val renamingFolder: NoteFolder? = null,
    val deletingFolder: NoteFolder? = null,
)

class IdeenViewModel(
    private val repository: NoteRepository,
    private val ownPerson: Person,
) : ViewModel() {
    private val _uiState = MutableStateFlow(IdeenUiState())
    val uiState: StateFlow<IdeenUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val folders = repository.loadFolders()
            val openCounts = mutableMapOf<String, Int>()
            val totalCounts = mutableMapOf<String, Int>()
            folders.forEach { folder ->
                val items = repository.loadItems(folder.id)
                openCounts[folder.id] = items.count { !it.isChecked }
                totalCounts[folder.id] = items.size
            }
            _uiState.update {
                it.copy(
                    folders = folders,
                    openCountByFolder = openCounts,
                    totalCountByFolder = totalCounts,
                    isRefreshing = false,
                )
            }
        }
    }

    fun openCreateFolderSheet() { _uiState.update { it.copy(isCreateFolderSheetOpen = true) } }
    fun dismissCreateFolderSheet() { _uiState.update { it.copy(isCreateFolderSheetOpen = false) } }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.addFolder(name = name, createdBy = ownPerson)
            _uiState.update { it.copy(isCreateFolderSheetOpen = false) }
            refresh()
        }
    }

    fun beginRenaming(folder: NoteFolder) { _uiState.update { it.copy(renamingFolder = folder) } }
    fun dismissRenaming() { _uiState.update { it.copy(renamingFolder = null) } }

    fun renameFolder(name: String) {
        val folder = _uiState.value.renamingFolder ?: return
        viewModelScope.launch {
            repository.renameFolder(id = folder.id, name = name)
            _uiState.update { it.copy(renamingFolder = null) }
            refresh()
        }
    }

    fun beginDeleting(folder: NoteFolder) { _uiState.update { it.copy(deletingFolder = folder) } }
    fun dismissDeleting() { _uiState.update { it.copy(deletingFolder = null) } }

    fun confirmDelete() {
        val folder = _uiState.value.deletingFolder ?: return
        viewModelScope.launch {
            repository.deleteFolder(folder.id)
            _uiState.update { it.copy(deletingFolder = null) }
            refresh()
        }
    }

    companion object {
        fun factory(repository: NoteRepository, ownPerson: Person) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    IdeenViewModel(repository, ownPerson) as T
            }
    }
}
```

**Hinweis zur Effizienz:** `refresh()` lädt pro Ordner einmal `loadItems()`, um sowohl `openCountByFolder` als auch `totalCountByFolder` zu befüllen (N+1-Anfragen bei N Ordnern). Für die erwartete Ordnerzahl (einstellig, zwei Personen) unkritisch — bewusst nicht optimiert, da eine serverseitige Zähl-Abfrage eine weitere Repository-Methode nur für diesen Zweck bräuchte.

- [ ] **Schritt 2: `IdeenScreen.kt` implementieren**

```kotlin
package com.prehmus.selli.ui.ideen

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prehmus.selli.R
import com.prehmus.selli.domain.model.NoteFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeenScreen(
    viewModel: IdeenViewModel,
    onOpenFolder: (folderId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openCreateFolderSheet) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Ordner anlegen")
            }
        },
    ) { innerPadding ->
        if (uiState.folders.isEmpty() && !uiState.isRefreshing) {
            IdeenEmptyHint(
                message = "Noch keine Ordner",
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.folders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        openCount = uiState.openCountByFolder[folder.id] ?: 0,
                        onClick = { onOpenFolder(folder.id) },
                        onRename = { viewModel.beginRenaming(folder) },
                        onDelete = { viewModel.beginDeleting(folder) },
                    )
                }
            }
        }
    }

    if (uiState.isCreateFolderSheetOpen) {
        NameInputSheet(
            title = "Ordner anlegen",
            initialValue = "",
            onSave = viewModel::createFolder,
            onDismiss = viewModel::dismissCreateFolderSheet,
        )
    }

    uiState.renamingFolder?.let { folder ->
        NameInputSheet(
            title = "Ordner umbenennen",
            initialValue = folder.name,
            onSave = viewModel::renameFolder,
            onDismiss = viewModel::dismissRenaming,
        )
    }

    uiState.deletingFolder?.let { folder ->
        val total = uiState.totalCountByFolder[folder.id] ?: 0
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleting,
            title = { Text("Ordner löschen?") },
            text = {
                Text("\"${folder.name}\" und alle $total Punkte darin werden endgültig gelöscht.")
            },
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

@Composable
private fun FolderCard(
    folder: NoteFolder,
    openCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = folder.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "$openCount offen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Optionen")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Umbenennen") },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Wiederverwendet von `FolderDetailScreen` (Task 16) für den "Noch keine Punkte"-Leerzustand. */
@Composable
internal fun IdeenEmptyHint(message: String, modifier: Modifier = Modifier) {
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
            Text(text = message, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameInputSheet(
    title: String,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(value) },
                enabled = value.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern") }
        }
    }
}
```

- [ ] **Schritt 3: Build verifizieren**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew compileDebugKotlin testDebugUnitTest
```

- [ ] **Schritt 4: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/ideen/IdeenViewModel.kt \
        app/src/main/java/com/prehmus/selli/ui/ideen/IdeenScreen.kt
git commit -m "UI: IdeenViewModel + IdeenScreen (Ordner-Übersicht)"
```

---

### Task 16: `FolderDetailViewModel` + `FolderDetailScreen` — Ebene 2 (Owner: Claude)

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/ui/ideen/FolderDetailViewModel.kt`
- Create: `app/src/main/java/com/prehmus/selli/ui/ideen/FolderDetailScreen.kt`

**Interfaces:**
- Consumes: `NoteRepository` aus Task 13/14; `openItems()`/`archivedItems()` aus Task 11; `IdeenEmptyHint` aus Task 15.
- Produces: `class FolderDetailViewModel(repository: NoteRepository, folderId: String, ownPerson: Person) : ViewModel()` mit `uiState: StateFlow<FolderDetailUiState>`, `fun refresh()`, `fun toggleArchiveExpanded()`, `fun openAddItemSheet()`, `fun dismissAddItemSheet()`, `fun addItem(text: String, url: String?)`, `fun setChecked(item: NoteItem, checked: Boolean)`, `fun beginEditing(item: NoteItem)`, `fun dismissEditing()`, `fun saveEdit(text: String, url: String?)`, `fun deleteItem(id: String)`, `companion object { fun factory(repository, folderId, ownPerson) }`. `@Composable fun FolderDetailScreen(viewModel: FolderDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier)`. Wird von Task 17 (Navigation) als eigene Route (`ideen/{folderId}`) eingehängt.

- [ ] **Schritt 1: `FolderDetailViewModel.kt` implementieren**

```kotlin
package com.prehmus.selli.ui.ideen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import com.prehmus.selli.domain.notes.archivedItems
import com.prehmus.selli.domain.notes.openItems
import com.prehmus.selli.domain.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderDetailUiState(
    val folderName: String = "",
    val openItems: List<NoteItem> = emptyList(),
    val archivedItems: List<NoteItem> = emptyList(),
    val isArchiveExpanded: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAddItemSheetOpen: Boolean = false,
    val editingItem: NoteItem? = null,
    val isSavingItem: Boolean = false,
)

/**
 * Der Ordnername kommt bewusst nicht als Navigations-Argument (Ordnernamen können
 * URL-unsichere Zeichen enthalten), sondern wird in [refresh] durch Abgleich mit der
 * geladenen Ordnerliste aufgelöst.
 */
class FolderDetailViewModel(
    private val repository: NoteRepository,
    private val folderId: String,
    private val ownPerson: Person,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FolderDetailUiState())
    val uiState: StateFlow<FolderDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val folder = repository.loadFolders().firstOrNull { it.id == folderId }
            val items = repository.loadItems(folderId)
            _uiState.update {
                it.copy(
                    folderName = folder?.name ?: it.folderName,
                    openItems = items.openItems(),
                    archivedItems = items.archivedItems(),
                    isRefreshing = false,
                )
            }
        }
    }

    fun toggleArchiveExpanded() {
        _uiState.update { it.copy(isArchiveExpanded = !it.isArchiveExpanded) }
    }

    fun openAddItemSheet() { _uiState.update { it.copy(isAddItemSheetOpen = true) } }
    fun dismissAddItemSheet() { _uiState.update { it.copy(isAddItemSheetOpen = false) } }

    fun addItem(text: String, url: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingItem = true) }
            repository.addItem(folderId = folderId, text = text, url = url, createdBy = ownPerson)
            _uiState.update { it.copy(isSavingItem = false, isAddItemSheetOpen = false) }
            refresh()
        }
    }

    fun setChecked(item: NoteItem, checked: Boolean) {
        viewModelScope.launch {
            repository.setChecked(item.id, checked)
            refresh()
        }
    }

    fun beginEditing(item: NoteItem) { _uiState.update { it.copy(editingItem = item) } }
    fun dismissEditing() { _uiState.update { it.copy(editingItem = null) } }

    fun saveEdit(text: String, url: String?) {
        val item = _uiState.value.editingItem ?: return
        viewModelScope.launch {
            repository.updateItem(id = item.id, text = text, url = url)
            _uiState.update { it.copy(editingItem = null) }
            refresh()
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteItem(id)
            refresh()
        }
    }

    companion object {
        fun factory(repository: NoteRepository, folderId: String, ownPerson: Person) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FolderDetailViewModel(repository, folderId, ownPerson) as T
            }
    }
}
```

- [ ] **Schritt 2: `FolderDetailScreen.kt` implementieren**

```kotlin
package com.prehmus.selli.ui.ideen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.prehmus.selli.domain.model.NoteItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: FolderDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirmationFor by remember { mutableStateOf<NoteItem?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zurück")
                }
                Text(text = uiState.folderName, style = MaterialTheme.typography.titleLarge)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddItemSheet) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Punkt hinzufügen")
            }
        },
    ) { innerPadding ->
        if (uiState.openItems.isEmpty() && uiState.archivedItems.isEmpty() && !uiState.isRefreshing) {
            IdeenEmptyHint(
                message = "Noch keine Punkte",
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.openItems, key = { it.id }) { item ->
                    NoteItemCard(
                        item = item,
                        onCheckedChange = { checked -> viewModel.setChecked(item, checked) },
                        onClick = { viewModel.beginEditing(item) },
                    )
                }
                if (uiState.archivedItems.isNotEmpty()) {
                    item(key = "archive-toggle") {
                        TextButton(onClick = viewModel::toggleArchiveExpanded) {
                            Text(
                                if (uiState.isArchiveExpanded) {
                                    "Erledigt (${uiState.archivedItems.size}) ausblenden"
                                } else {
                                    "Erledigt (${uiState.archivedItems.size})"
                                },
                            )
                        }
                    }
                    if (uiState.isArchiveExpanded) {
                        items(items = uiState.archivedItems, key = { "archived-${it.id}" }) { item ->
                            NoteItemCard(
                                item = item,
                                onCheckedChange = { checked -> viewModel.setChecked(item, checked) },
                                onClick = { viewModel.beginEditing(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isAddItemSheetOpen) {
        NoteItemSheet(
            title = "Punkt hinzufügen",
            initialText = "",
            initialUrl = "",
            isSaving = uiState.isSavingItem,
            onSave = viewModel::addItem,
            onDismiss = viewModel::dismissAddItemSheet,
        )
    }

    uiState.editingItem?.let { item ->
        NoteItemSheet(
            title = "Punkt bearbeiten",
            initialText = item.text,
            initialUrl = item.url.orEmpty(),
            isSaving = false,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::dismissEditing,
            onDelete = { showDeleteConfirmationFor = item },
        )
    }

    showDeleteConfirmationFor?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationFor = null },
            title = { Text("Punkt löschen?") },
            text = { Text("\"${item.text}\" wird endgültig entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(item.id)
                    showDeleteConfirmationFor = null
                    viewModel.dismissEditing()
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationFor = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun NoteItemCard(
    item: NoteItem,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick),
                )
            }
            item.url?.takeUnless(String::isBlank)?.let { url ->
                LinkPreviewCard(
                    url = url,
                    title = item.previewTitle,
                    imageUrl = item.previewImageUrl,
                    modifier = Modifier
                        .padding(start = 48.dp, end = 12.dp, bottom = 8.dp)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            runCatching { context.startActivity(intent) }
                        },
                )
            }
        }
    }
}

@Composable
private fun LinkPreviewCard(
    url: String,
    title: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
            Text(
                text = title ?: shortenUrlForDisplay(url),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun shortenUrlForDisplay(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteItemSheet(
    title: String,
    initialText: String,
    initialUrl: String,
    isSaving: Boolean,
    onSave: (text: String, url: String?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initialText) }
    var url by remember { mutableStateOf(initialUrl) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(text, url.takeUnless(String::isBlank)) },
                enabled = text.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isSaving) "Wird gespeichert …" else "Speichern") }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
```

- [ ] **Schritt 3: Build verifizieren**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew compileDebugKotlin testDebugUnitTest
```

Erwartet: `BUILD SUCCESSFUL`. Schlägt der Import `coil3.compose.AsyncImage` fehl: Task 2 (Coil-Abhängigkeit) prüfen, insbesondere dass `coil = "3.0.4"` (nicht `coil` 3.2.0+) im Versionskatalog steht.

- [ ] **Schritt 4: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/ideen/FolderDetailViewModel.kt \
        app/src/main/java/com/prehmus/selli/ui/ideen/FolderDetailScreen.kt
git commit -m "UI: FolderDetailViewModel + FolderDetailScreen (Punkte, Archiv, Link-Vorschau)"
```

---

### Task 17: "Ideen" als fünften Tab einhängen + finale Tab-Reihenfolge (Owner: Claude)

**Files:**
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliDestination.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliBottomBar.kt`
- Modify: `app/src/main/java/com/prehmus/selli/ui/shell/SelliShell.kt`

**Interfaces:**
- Consumes: `IdeenScreen`, `IdeenViewModel` aus Task 15; `FolderDetailScreen`, `FolderDetailViewModel` aus Task 16; `dependencies.noteRepository` aus Task 14.
- Produces: Bottom-Navigation zeigt die finale Reihenfolge `Kalender | Ideen | Wir | Kosten | Standort`. Eine zusätzliche, nicht in der Bottom-Bar sichtbare Route `ideen/{folderId}` für die Ordner-Detailansicht (wie `SETTINGS_ROUTE` ein Vollbild-Screen ohne globale Top-/Bottom-Bar).

- [ ] **Schritt 1: `SelliDestination.kt` — Reihenfolge final setzen + Routen-Konstanten für die Ordner-Detailansicht ergänzen**

```kotlin
package com.prehmus.selli.ui.shell

/**
 * Die fünf gleichwertigen Ziele der Bottom-Navigation. Die Reihenfolge der Einträge ist
 * zugleich die Reihenfolge in der Leiste — „Wir" sitzt bewusst in der Mitte, weil es das
 * zentrale „auf einen Blick"-Ziel ist.
 */
enum class SelliDestination(val route: String, val label: String) {
    CALENDAR(route = "calendar", label = "Kalender"),
    IDEEN(route = "ideen", label = "Ideen"),
    HOME(route = "home", label = "Wir"),
    EXPENSES(route = "expenses", label = "Kosten"),
    LOCATION(route = "location", label = "Standort"),
    ;

    companion object {
        /**
         * Ziel zur Route, oder `null` für unbekannte Routen und für Einstellungen/
         * Ordner-Detail — die sind bewusst keine Bottom-Navigation-Ziele.
         */
        fun fromRoute(route: String?): SelliDestination? =
            entries.firstOrNull { destination -> destination.route == route }
    }
}

/** Profil-/Einstellungsbereich: eigener Vollbild-Screen über dem Bottom-Navigation-Gerüst. */
const val SETTINGS_ROUTE = "settings"

/**
 * Ordner-Detailansicht (Ebene 2 von "Ideen"): wie [SETTINGS_ROUTE] ein Vollbild-Screen ohne
 * globale Top-/Bottom-Bar, mit eigenem Header (siehe `FolderDetailScreen`). Der Ordnername
 * wird bewusst nicht als Argument mitgegeben (URL-unsichere Zeichen möglich) — siehe
 * `FolderDetailViewModel.refresh()`.
 */
const val IDEEN_FOLDER_ROUTE = "ideen/{folderId}"

fun ideenFolderRoute(folderId: String): String = "ideen/$folderId"

/** Ziel, mit dem die App startet — „Wir" ist der zentrale „auf einen Blick"-Bildschirm. */
val SelliStartDestination: SelliDestination = SelliDestination.HOME
```

- [ ] **Schritt 2: `SelliBottomBar.kt` um das Icon ergänzen**

In `app/src/main/java/com/prehmus/selli/ui/shell/SelliBottomBar.kt` den Import `androidx.compose.material.icons.automirrored.filled.List` ergänzen und die `icon()`-Extension erweitern:

```kotlin
private fun SelliDestination.icon(): ImageVector = when (this) {
    SelliDestination.CALENDAR -> Icons.Default.DateRange
    SelliDestination.IDEEN -> Icons.AutoMirrored.Filled.List
    SelliDestination.HOME -> Icons.Default.Favorite
    SelliDestination.EXPENSES -> Icons.Default.ShoppingCart
    SelliDestination.LOCATION -> Icons.Default.Place
}
```

- [ ] **Schritt 3: `SelliShell.kt` — Vollbild-Erkennung verallgemeinern + beide Ideen-Routen einhängen**

Imports ergänzen: `com.prehmus.selli.ui.ideen.IdeenScreen`, `com.prehmus.selli.ui.ideen.IdeenViewModel`, `com.prehmus.selli.ui.ideen.FolderDetailScreen`, `com.prehmus.selli.ui.ideen.FolderDetailViewModel`, `androidx.navigation.NavType`, `androidx.navigation.navArgument`.

Die Zeile `val isSettings = currentRoute == SETTINGS_ROUTE` ersetzen durch:

```kotlin
    // Sowohl Einstellungen als auch die Ordner-Detailansicht sind Vollbild-Screens ohne
    // globale Top-/Bottom-Bar — die Ordner-Detailansicht bringt ihren eigenen Header mit
    // Zurück-Pfeil mit (siehe FolderDetailScreen).
    val isFullScreenRoute = currentRoute == SETTINGS_ROUTE || currentRoute == IDEEN_FOLDER_ROUTE
```

Die beiden Vorkommen von `if (!isSettings)` (einmal vor `SelliTopBar`, einmal vor `SelliBottomBar`) jeweils zu `if (!isFullScreenRoute)` ändern.

Im `NavHost { ... }`-Block, nach `composable(SelliDestination.CALENDAR.route) { ... }` und vor `composable(SelliDestination.HOME.route) { ... }`, ergänzen:

```kotlin
            composable(SelliDestination.IDEEN.route) {
                val ideenViewModel: IdeenViewModel = viewModel(
                    factory = IdeenViewModel.factory(
                        repository = dependencies.noteRepository,
                        ownPerson = ownPerson,
                    ),
                )
                IdeenScreen(
                    viewModel = ideenViewModel,
                    onOpenFolder = { folderId -> navController.navigate(ideenFolderRoute(folderId)) },
                )
            }
            composable(
                route = IDEEN_FOLDER_ROUTE,
                arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
                val folderDetailViewModel: FolderDetailViewModel = viewModel(
                    factory = FolderDetailViewModel.factory(
                        repository = dependencies.noteRepository,
                        folderId = folderId,
                        ownPerson = ownPerson,
                    ),
                )
                FolderDetailScreen(
                    viewModel = folderDetailViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
```

**Wichtig:** `composable(IDEEN_FOLDER_ROUTE, ...)` ist eine eigenständige Route außerhalb der Bottom-Navigation (wie `SETTINGS_ROUTE`) — sie wird per normalem `navController.navigate(...)` erreicht (Push auf den Back-Stack, System-Zurück funktioniert), **nicht** über `switchTo(...)` (das ist nur für die fünf gleichwertigen Bottom-Bar-Ziele gedacht, siehe die bestehende `switchTo`-Dokumentation am Dateiende).

- [ ] **Schritt 4: Build verifizieren**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew testDebugUnitTest assembleDebug
```

Erwartet: `BUILD SUCCESSFUL`, alle Tests grün.

- [ ] **Schritt 5: Release-Build + Gerätetest**

```bash
cp app/build/outputs/apk/debug/app-debug.apk selli.apk
```

Installieren und live prüfen: Bottom-Navigation zeigt die finale Reihenfolge `Kalender | Ideen | Wir | Kosten | Standort` mit "Wir" in der Mitte als Startziel; "Ideen"-Tab zeigt eine leere Ordnerliste, FAB legt einen Ordner an, Antippen öffnet die Detailansicht mit eigenem Header + Zurück-Pfeil (keine doppelte Top-/Bottom-Bar sichtbar), ein Punkt mit Link zeigt nach kurzem Laden eine Vorschau-Karte, Abhaken verschiebt ihn ins "Erledigt"-Archiv, Ordner-Löschen fragt vorher nach Bestätigung mit korrekter Gesamtzahl.

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/ui/shell/
git commit -m "Ideen-Tab einhängen, finale Tab-Reihenfolge: Kalender | Ideen | Wir | Kosten | Standort"
```

---

### Task 18: Partner-Benachrichtigung bei neuem Ideen-Punkt

**Files:**
- Create: `app/src/main/java/com/prehmus/selli/domain/notification/NewNoteItemDetector.kt`
- Create: `app/src/main/java/com/prehmus/selli/data/notification/NoteItemSeenStore.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetRuntime.kt`
- Modify: `app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt`
- Modify: `app/src/main/java/com/prehmus/selli/SelliApplication.kt`
- Test: `app/src/test/java/com/prehmus/selli/domain/notification/NewNoteItemDetectorTest.kt`

**Interfaces:**
- Consumes: `NoteItem`, `Person` (bestehend); `NoteRepository` aus Task 13; `PartnerActivityNotifier.notifyNewNoteItem` aus Task 9 (bereits vollständig implementiert, hier nur konsumiert, keine Änderung an der Klasse).
- Produces: `data class NewNoteItemDetectionResult(newItems: List<NoteItem>, updatedSeenIds: Set<String>)`, `fun detectNewNoteItems(currentItems: List<NoteItem>, self: Person, alreadySeenIds: Set<String>): NewNoteItemDetectionResult`, `class NoteItemSeenStore(context: Context) { fun load(): Set<String>; fun save(ids: Set<String>) }`.

- [ ] **Schritt 1: Test für den Erkennungs-Algorithmus schreiben** (spiegelt `NewExpenseDetectorTest` aus Task 9 exakt)

```kotlin
package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NewNoteItemDetectorTest {
    private fun item(id: String, createdBy: Person) = NoteItem(
        id = id,
        folderId = "folder-1",
        text = "Punkt $id",
        url = null,
        previewTitle = null,
        previewImageUrl = null,
        isChecked = false,
        checkedAt = null,
        createdBy = createdBy,
        createdAt = Instant.parse("2026-09-14T10:00:00Z"),
    )

    @Test
    fun `item created by partner is reported as new`() {
        val result = detectNewNoteItems(
            currentItems = listOf(item("1", Person.MELLI)),
            self = Person.BASTI,
            alreadySeenIds = emptySet(),
        )

        assertEquals(listOf("1"), result.newItems.map { it.id })
        assertEquals(setOf("1"), result.updatedSeenIds)
    }

    @Test
    fun `own item is never reported`() {
        val result = detectNewNoteItems(
            currentItems = listOf(item("1", Person.BASTI)),
            self = Person.BASTI,
            alreadySeenIds = emptySet(),
        )

        assertEquals(emptyList<NoteItem>(), result.newItems)
    }

    @Test
    fun `already seen item is not reported again`() {
        val result = detectNewNoteItems(
            currentItems = listOf(item("1", Person.MELLI)),
            self = Person.BASTI,
            alreadySeenIds = setOf("1"),
        )

        assertEquals(emptyList<NoteItem>(), result.newItems)
        assertEquals(setOf("1"), result.updatedSeenIds)
    }
}
```

- [ ] **Schritt 2: Test ausführen, Fehlschlag bestätigen**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli && ./gradlew testDebugUnitTest --tests "*.NewNoteItemDetectorTest"
```

- [ ] **Schritt 3: `NewNoteItemDetector.kt` implementieren**

```kotlin
package com.prehmus.selli.domain.notification

import com.prehmus.selli.domain.model.NoteItem
import com.prehmus.selli.domain.model.Person

data class NewNoteItemDetectionResult(
    val newItems: List<NoteItem>,
    val updatedSeenIds: Set<String>,
)

/** Erkennt Ideen-Punkte, die der Partner neu angelegt hat und noch nicht gemeldet wurden. */
fun detectNewNoteItems(
    currentItems: List<NoteItem>,
    self: Person,
    alreadySeenIds: Set<String>,
): NewNoteItemDetectionResult {
    val newFromPartner = currentItems.filter { item ->
        item.createdBy != self && item.id !in alreadySeenIds
    }
    return NewNoteItemDetectionResult(
        newItems = newFromPartner,
        updatedSeenIds = currentItems.map { it.id }.toSet(),
    )
}
```

- [ ] **Schritt 4: Test ausführen, Erfolg bestätigen** — alle 3 Tests grün.

- [ ] **Schritt 5: `NoteItemSeenStore.kt` implementieren** (kein Unit-Test, analog `ExpenseSeenStore` aus Task 9)

```kotlin
package com.prehmus.selli.data.notification

import android.content.Context
import android.content.SharedPreferences

/** Merkt sich, welche Ideen-Punkt-IDs schon einmal gesehen/gemeldet wurden. */
class NoteItemSeenStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Set<String> = preferences.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty()

    fun save(ids: Set<String>) {
        preferences.edit().putStringSet(KEY_SEEN_IDS, ids).apply()
    }

    private companion object {
        const val PREFS_NAME = "selli_note_notifications"
        const val KEY_SEEN_IDS = "seen_note_item_ids"
    }
}
```

- [ ] **Schritt 6: `WidgetRuntime.kt` um eine Repository-Factory erweitern**

Import `com.prehmus.selli.domain.repository.NoteRepository` ergänzen, Objekt erweitern:

```kotlin
    @Volatile
    var noteRepositoryFactory: (() -> NoteRepository)? = null
```

- [ ] **Schritt 7: `SelliApplication.kt` verdrahten**

Imports `com.prehmus.selli.data.notes.SupabaseNoteRepository`, `com.prehmus.selli.data.notes.JsoupLinkPreviewFetcher` ergänzen, nach dem `expenseRepositoryFactory`-Block (Task 9 Schritt 8) ergänzen:

```kotlin
        WidgetRuntime.noteRepositoryFactory = {
            SupabaseNoteRepository(
                client = SelliSupabaseClient(
                    supabaseUrl = BuildConfig.SUPABASE_URL,
                    supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
                    idTokenProvider = locationGoogleRepository,
                    logger = AndroidCalendarLogger,
                ),
                linkPreviewFetcher = JsoupLinkPreviewFetcher(logger = AndroidCalendarLogger),
                logger = AndroidCalendarLogger,
            )
        }
```

- [ ] **Schritt 8: `WidgetRefreshWorker.kt` um den Ideen-Check erweitern**

Imports `com.prehmus.selli.data.notification.NoteItemSeenStore`, `com.prehmus.selli.domain.notification.detectNewNoteItems` ergänzen. In `doWork()`, direkt nach `runCatching { notifyNewExpenses(partner) }` (Task 9 Schritt 9), ergänzen:

```kotlin
            runCatching { notifyNewNoteItems(partner) }
```

Neue private Methode ergänzen (nach `notifyNewExpenses`):

```kotlin
    private suspend fun notifyNewNoteItems(partner: PartnerInfo) {
        val repositoryFactory = WidgetRuntime.noteRepositoryFactory ?: return
        val notifier = PartnerActivityNotifier(applicationContext)
        if (!notifier.areNotificationsEnabled()) return

        val self = partner.person.other()
        val store = NoteItemSeenStore(applicationContext)
        val repository = repositoryFactory()
        val folders = repository.loadFolders().associateBy { it.id }
        val allItems = folders.keys.flatMap { folderId -> repository.loadItems(folderId) }

        val result = detectNewNoteItems(currentItems = allItems, self = self, alreadySeenIds = store.load())
        result.newItems.forEach { item ->
            val folderName = folders[item.folderId]?.name ?: "Ideen"
            notifier.notifyNewNoteItem(item, folderName, partner.displayName)
        }
        store.save(result.updatedSeenIds)
    }
```

**Bekannte, bewusst in Kauf genommene Ineffizienz:** `loadItems()` läuft einmal pro Ordner (N+1-Anfragen) — für die erwartete kleine Ordnerzahl unkritisch, wie schon in Task 15 vermerkt.

- [ ] **Schritt 9: Build verifizieren**

```bash
./gradlew compileDebugKotlin testDebugUnitTest
```

- [ ] **Schritt 10: Commit**

```bash
git add app/src/main/java/com/prehmus/selli/domain/notification/NewNoteItemDetector.kt \
        app/src/main/java/com/prehmus/selli/data/notification/NoteItemSeenStore.kt \
        app/src/main/java/com/prehmus/selli/data/widget/WidgetRuntime.kt \
        app/src/main/java/com/prehmus/selli/data/widget/WidgetRefreshWorker.kt \
        app/src/main/java/com/prehmus/selli/SelliApplication.kt \
        app/src/test/java/com/prehmus/selli/domain/notification/NewNoteItemDetectorTest.kt
git commit -m "Partner-Benachrichtigung bei neuem Ideen-Punkt"
```

---

### Task 19: Gesamtabnahme — Build, Tests, Release-APK, Claudian-Update

**Files:** keine neuen — reine Verifikation des Gesamtstands.

- [ ] **Schritt 1: Vollständigen Testlauf + Release-Build ausführen**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/Workspace/selli
./gradlew clean testDebugUnitTest assembleDebug
```

Erwartet: `BUILD SUCCESSFUL`, alle Unit-Tests grün (bestehende ~264 plus alle in diesem Plan neu hinzugekommenen: `ExpenseBalanceTest`, `SettlementCalculationTest`, `ExpenseRowTest`, `SettlementRowTest`, `NewExpenseDetectorTest`, `NoteItemListsTest`, `LinkPreviewParserTest`, `NoteFolderRowTest`, `NoteItemRowTest`, `NewNoteItemDetectorTest`).

- [ ] **Schritt 2: Secret-Scan**

```bash
git diff main --stat
git log --oneline main..HEAD
```

Prüfen, dass keine der neuen/geänderten Dateien echte Zugangsdaten enthält (Migrationen referenzieren nur `is_selli_member()`, keine E-Mail-Adressen oder Keys; `local.properties` wird durch diesen Plan nicht verändert).

- [ ] **Schritt 3: APK an den festen Ablageort kopieren**

```bash
cp app/build/outputs/apk/debug/app-debug.apk selli.apk
```

- [ ] **Schritt 4: Kompletter Gerätetest (Basti, danach Melli)**

- Bottom-Navigation: `Kalender | Ideen | Wir | Kosten | Standort`, "Wir" startet zentriert.
- **Kosten:** Ausgabe anlegen (eigene + stellvertretend für Partner), Saldo-Karte zeigt korrekte Richtung/Betrag, Bearbeiten/Löschen funktionieren, "Ausgleichen" setzt Saldo auf 0 und die Ausgabe bleibt in der Liste sichtbar, Partner erhält beim nächsten 30-Minuten-Hintergrundlauf (oder durch manuelles Auslösen des Workers am Gerät) eine Benachrichtigung für eine fremd angelegte Ausgabe.
- **Ideen:** Ordner anlegen/umbenennen/löschen (mit korrekter Zähl-Angabe im Lösch-Dialog), Punkt mit und ohne Link anlegen, Link-Vorschau lädt Titel/Bild oder fällt sauber auf die reine URL zurück, Abhaken verschiebt in den Archiv-Bereich, von dort wieder aufhakbar/löschbar, Partner erhält eine Benachrichtigung für einen fremd angelegten Punkt.
- Beide Features funktionieren unabhängig voneinander und beeinträchtigen Kalender/Wir/Standort nicht (Owner-Regel `ui/DayDetail`/`bothFreeOnSelectedDay` unangetastet, wie in früheren Plänen dieses Projekts festgelegt).

- [ ] **Schritt 5: Claudian-Update ausgeben**

Format aus der Projekt-`CLAUDE.md` (Abschnitt "Claudian-Update-Format") verwenden, mit Implementiert/Getestet/Offen/Technische Notizen für beide Features.

---

## Self-Review dieses Plans

- **Spec-Abdeckung:** Jede Entscheidung aus beiden Spec-Dokumenten (Kostentracking: 50/50-Aufteilung, laufender Saldo, sofortiges Ausgleichen, vierter Tab, Benachrichtigung, Randfälle inkl. "Teil einer bereits ausgeglichenen Abrechnung"-Hinweis; Ideen: eine Ordner-Ebene, ein Punkttyp mit optionalem Link, automatische Vorschau mit Timeout/Fallback, Archiv-Bereich, fünfter Tab, Benachrichtigung, Lösch-Bestätigung mit Gesamtzahl) hat eine konkrete Task/Schritt-Zuordnung — siehe Tasks 1–18.
- **Bewusste, dokumentierte Abweichung von den Spec-Dokumenten:** `created_by`-Spalte auf `expenses` (Task 3) — in keiner Spec explizit gefordert, aber notwendig, damit die in beiden Specs geforderte Partner-Benachrichtigung korrekt zwischen "bezahlt von" und "eingetragen von" unterscheiden kann.
- **Typkonsistenz:** `ExpenseRepository`/`NoteRepository`-Signaturen (Task 4/13) stimmen mit den Aufrufen in `ExpensesViewModel`/`IdeenViewModel`/`FolderDetailViewModel` (Task 6/15/16) überein; `PartnerActivityNotifier` (Task 9) wird in Task 18 nur konsumiert, nicht verändert; Supabase-Filter-Syntax (`filter { eq(...) }`-Wrapper) wurde gegen die tatsächlich installierte Version 3.1.4 kompiliert verifiziert, nicht nur aus der Dokumentation übernommen.
- **Bekannte, bewusst nicht gelöste Lücken (außerhalb des Scopes dieses Plans):** vollständiges Deep-Link-Handling der Benachrichtigungen (Antippen öffnet aktuell nur die App, springt aber noch nicht automatisch zum Kosten-Tab bzw. zum passenden Ordner — siehe Hinweis in Task 9 Schritt 6); N+1-Ladeverhalten in `IdeenViewModel.refresh()` und `notifyNewNoteItems()` bei vielen Ordnern (für zwei Personen mit einstelliger Ordnerzahl unkritisch).

