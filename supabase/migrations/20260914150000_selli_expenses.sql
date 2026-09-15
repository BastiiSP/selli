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
