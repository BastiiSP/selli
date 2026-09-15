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
