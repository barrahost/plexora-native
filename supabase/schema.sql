-- Table d'association appareil -> playlist Xtream (equivalent MAC HotPlayer)
create table if not exists devices (
  id uuid primary key default gen_random_uuid(),
  device_id text not null unique,   -- ANDROID_ID de l'appareil client
  server_url text not null,
  username text not null,
  password text not null,
  label text,                        -- ex: "Client X - Salon"
  created_at timestamptz not null default now()
);

-- Lecture seule via la cle "anon" depuis l'app (aucune ecriture cote client) :
-- la gestion des associations se fait depuis le dashboard Supabase (table
-- editor) ou une future page d'admin, jamais depuis l'app installee chez le client.
alter table devices enable row level security;

create policy "Lecture publique par device_id"
  on devices for select
  using (true);

-- Aucune policy insert/update/delete pour le role anon : uniquement gerable
-- via le dashboard Supabase (authentifie en tant que proprietaire du projet)
-- ou la cle service_role (jamais exposee dans l'app).
