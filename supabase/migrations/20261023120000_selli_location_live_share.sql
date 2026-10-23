-- Selli: befristete Live-Freigabe, unabhängig vom Messzeitpunkt der Position.
--
-- Additiv und wiederholbar im SQL-Editor oder per `supabase db push` ausführen.
-- Design: docs/superpowers/specs/2026-10-23-standort-live-freigabe-design.md
alter table public.locations add column if not exists live_until timestamptz;

-- Bestehende SELECT-/UPDATE-Policies erlauben Mitgliedern das Lesen beider Zeilen
-- und das Schreiben ausschließlich der eigenen Zeile (USING und WITH CHECK).
-- Die tabellenweiten Grants für authenticated gelten auch für diese neue Spalte.
-- RLS, Grants und Realtime-Publication bleiben deshalb unverändert.
