-- Selli: Standortfreigabe zwischen genau zwei bekannten Konten.
--
-- Einmalig im Supabase-Projekt ausführen (SQL-Editor oder `supabase db push`).
-- Danach die zwei echten E-Mail-Adressen in public.selli_members eintragen — siehe unten.
--
-- Design-Entscheidungen: docs/superpowers/specs/2026-08-26-grundgeruest-standort-design.md

-- ---------------------------------------------------------------------------
-- Allowlist: genau die zwei bekannten Konten. Von Hand befüllt, nie von der App.
-- Ohne Eintrag darf ein angemeldetes Google-Konto weder lesen noch schreiben.
-- ---------------------------------------------------------------------------
create table if not exists public.selli_members (
  email  text primary key,
  person text not null unique check (person in ('BASTI', 'MELLI'))
);

-- ---------------------------------------------------------------------------
-- Aktueller Standort: eine Zeile pro Person, per Upsert überschrieben.
-- Bewusst keine Historie — "Bewegung läuft mit" entsteht durch die Marker-Animation
-- in der App, nicht durch eine gespeicherte Spur.
-- ---------------------------------------------------------------------------
create table if not exists public.locations (
  user_id         uuid primary key references auth.users (id) on delete cascade,
  person          text not null check (person in ('BASTI', 'MELLI')),
  latitude        double precision not null,
  longitude       double precision not null,
  accuracy_meters double precision,
  speed_mps       double precision,
  is_moving       boolean not null default false,
  updated_at      timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- security definer, damit die Policies die Allowlist lesen können, ohne sie
-- für Clients zu öffnen.
-- ---------------------------------------------------------------------------
create or replace function public.is_selli_member()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.selli_members m
    where lower(m.email) = lower(auth.jwt() ->> 'email')
  )
$$;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
alter table public.selli_members enable row level security;
-- Absichtlich KEINE Policy: nur is_selli_member() (security definer) liest diese
-- Tabelle. Ohne Policy kommt kein Client an ihren Inhalt.

alter table public.locations enable row level security;

drop policy if exists "members read both positions" on public.locations;
create policy "members read both positions" on public.locations
  for select to authenticated
  using (public.is_selli_member());

drop policy if exists "members insert only their own row" on public.locations;
create policy "members insert only their own row" on public.locations
  for insert to authenticated
  with check (public.is_selli_member() and user_id = auth.uid());

drop policy if exists "members update only their own row" on public.locations;
create policy "members update only their own row" on public.locations
  for update to authenticated
  using (public.is_selli_member() and user_id = auth.uid())
  with check (public.is_selli_member() and user_id = auth.uid());

-- Kein delete: Positionen werden überschrieben, nicht gelöscht.

-- ---------------------------------------------------------------------------
-- Echtzeit für die Partnerzeile.
-- ---------------------------------------------------------------------------
alter table public.locations replica identity full;

do $$
begin
  alter publication supabase_realtime add table public.locations;
exception
  when duplicate_object then null;
end
$$;

-- ---------------------------------------------------------------------------
-- ZUM SCHLUSS VON HAND (die echten Adressen einsetzen, nicht committen):
--
--   insert into public.selli_members (email, person)
--   values ('basti@...', 'BASTI'), ('melli@...', 'MELLI')
--   on conflict (email) do update set person = excluded.person;
--
-- Ausserdem im Dashboard unter Authentication -> Providers:
--   Google aktivieren und die Client-ID aus local.properties
--   (selli.googleServerClientId) als erlaubte Client-ID eintragen.
--   Ein Provider-Secret ist fuer signInWithIdToken NICHT noetig.
-- ---------------------------------------------------------------------------
