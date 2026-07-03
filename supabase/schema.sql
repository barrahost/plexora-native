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

alter table devices enable row level security;
-- Volontairement AUCUNE policy select sur la table : l'API REST generique
-- (PostgREST) n'expose donc rien, meme avec la cle publishable (qui est de
-- toute facon extractible de l'APK par n'importe qui). Le seul point d'acces
-- est la fonction ci-dessous, qui ne renvoie jamais qu'UNE ligne (celle du
-- device_id demande) — impossible de lister/dumper tous les clients d'un coup.

create or replace function get_device_playlist(p_device_id text)
returns table(server_url text, username text, password text)
language sql
security definer
set search_path = public
as $$
  select server_url, username, password
  from devices
  where device_id = p_device_id
  limit 1;
$$;

-- Executable par la cle publishable (role anon), mais uniquement via cette
-- fonction bornee — jamais un select libre sur la table.
grant execute on function get_device_playlist(text) to anon;

-- Gestion des associations : uniquement depuis le dashboard Supabase
-- (authentifie proprietaire) ou la cle service_role, jamais depuis l'app.
