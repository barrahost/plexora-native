# Plexora Native — Contexte du projet

Application IPTV Android **100% native** (Kotlin + Jetpack Compose + ExoPlayer/Media3),
en cours de reconstruction pour remplacer la version web/Capacitor (`../iptv-web`).

## Pourquoi le natif

La version Capacitor (WebView) avait des limites structurelles impossibles à contourner :
- Codecs Dolby (AC3/EAC3) et HEVC/4K non lus par le WebView Android
- CORS bloquant certains appels API malgré `CapacitorHttp`
- Navigation D-pad télécommande bricolée en JS (peu fiable)

ExoPlayer (Media3) lit nativement ces codecs. Compose gère le focus D-pad
nativement (pas de bricolage). D'où la reconstruction complète.

## Stack

- Kotlin, Jetpack Compose, Material3
- ExoPlayer (androidx.media3) pour la lecture vidéo/audio
- Retrofit + Moshi (avec adaptateurs "tolérants" custom — voir plus bas) pour l'API Xtream Codes
- Coil pour le chargement d'images (affiches, logos)
- DataStore Preferences pour le stockage local (identifiants, préférences)
- Supabase (REST API, PostgREST) pour le provisioning par identifiant d'appareil

## Écrans construits

- **Login** : connexion manuelle OU provisioning automatique par identifiant d'appareil (voir plus bas)
- **Live TV** : catégories + chaînes + lecteur
- **Films** : catégories + grille d'affiches + détail + lecteur
- **Séries** : catégories + grille + saisons/épisodes + lecteur
- **Radio** : chaînes filtrées par catégorie contenant "radio", lecture audio
- **Paramètres** : réglage du tampon vidéo (Faible/Moyen/Élevé → `DefaultLoadControl` ExoPlayer), déconnexion

## Ce qui manque encore (parité avec la version web)

- Navigation D-pad testée/affinée sur tous les écrans (Compose gère le focus nativement,
  mais pas encore validé en conditions réelles sur chaque grille)
- Reprise de lecture (Continuer à regarder)
- Catch-up TV (replay programmes passés)
- Grille EPG façon guide TV
- Cache local des catalogues (stale-while-revalidate, comme la version web)
- Gestion multi-playlists dans Paramètres (actuellement un seul compte à la fois)
- Vérification de mise à jour automatique (bannière in-app)
- Compatibilité Android TV testée en profondeur (bannière leanback déjà en place)

## Provisioning par identifiant d'appareil (équivalent "MAC Address" HotPlayer)

**Contrainte technique importante** : Android bloque la lecture de la vraie adresse MAC
matérielle depuis une app classique depuis Android 6 (retourne une valeur bidon).
On utilise à la place `Settings.Secure.ANDROID_ID` — stable par appareil+app, mais qui
change si l'appareil est réinitialisé ou l'app réinstallée sur un ID de signature différent.

Flux : l'app lit son `ANDROID_ID` au démarrage → interroge une fonction Postgres (RPC)
sur Supabase → si une association existe, configuration automatique → sinon,
formulaire de connexion manuel classique (avec l'ID affiché pour l'associer).

### Config Supabase

- Projet : `https://cnsgyoirnhkjmklmzklh.supabase.co`
- Clé publique ("publishable") intégrée dans `DeviceProvisioning.kt` — sans risque,
  ce type de clé est conçu pour être embarqué dans un client (comme Firebase)
- Schéma SQL : `supabase/schema.sql` — table `devices` (device_id, server_url,
  username, password, label)
- **Sécurité critique** : la table n'est PAS exposée en lecture directe via l'API REST
  (pas de policy SELECT). Le seul point d'accès est la fonction `get_device_playlist(device_id)`,
  qui ne renvoie jamais qu'UNE ligne — empêche quiconque possédant la clé publique
  de lister/dumper les identifiants de tous les clients.
- Gestion des associations : uniquement depuis le dashboard Supabase (Table Editor),
  jamais depuis l'app.

## Bugs déjà rencontrés et corrigés

- **Liste de chaînes vide silencieusement** : le fournisseur IPTV renvoie parfois des
  nombres sous forme de texte selon les entrées (comme observé sur la version web pour
  l'encodage et l'EPG). Moshi rejetait alors TOUT le lot dès qu'une seule entrée était
  mal typée. Fix : adaptateurs Moshi "tolérants" (`LenientAdapters.kt`) qui acceptent
  string OU nombre pour les champs numériques, plus filtrage des entrées avec id=0
  après parsing, plus affichage d'un message d'erreur explicite au lieu d'un vide silencieux.
- **Menu/logo invisibles sur TV** : overscan de certains téléviseurs (ex. TCL) qui
  rogne les bords de l'image. Fix : marge de sécurité TV (`TvSafeArea`) autour de
  tout le contenu dans `MainActivity.kt`.

## Déploiement

- Dépôt GitHub : `barrahost/plexora-native` (public)
- `.cpanel.yml` copie `releases/plexora-native.apk` vers
  `public_html/plexora/plexora-native-apk/plexora-native.apk` sur chaque déploiement
- URL de téléchargement (à utiliser dans l'app Downloader sur la TV) :
  `http://plexora.d-infras.com/plexora-native-apk/plexora-native.apk`
- Workflow : je compile localement (Android Studio + SDK installés sur la machine
  Windows), copie l'APK dans `releases/`, commit, push → l'utilisateur déploie
  depuis cPanel (Git Version Control → `plexora-native` → Update from Remote →
  Deploy HEAD Commit)

## Projet web/Capacitor (référence, ne pas confondre)

L'ancienne version (`../iptv-web`) reste fonctionnelle et déployée en parallèle
(`http://plexora.d-infras.com`, dépôt `barrahost/plexora`). Beaucoup de fonctionnalités
qui manquent encore côté natif (cache, reprise de lecture, EPG, multi-playlists...)
y sont déjà implémentées et peuvent servir de référence de comportement/UX à reproduire.

## Identifiants IPTV de test

Ne jamais committer en clair dans le code. Fournis par l'utilisateur au besoin en
conversation. Serveur de test principal utilisé pendant le développement :
`flixigo-premiumpro2.fr:8789` (compte k6bkh3Sd) — serveur connu pour être
instable/lent, avec des données parfois mal formées (d'où les correctifs de
tolérance mentionnés ci-dessus).
