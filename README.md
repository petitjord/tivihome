# Tivihome

Lecteur IPTV pour **Android TV**, pensé pour la télécommande, compatible **Xtream Codes**.

## Fonctions

- **TV en direct** : groupes à gauche, chaînes avec programme en cours et barre de progression, aperçu vidéo qui démarre tout seul à droite.
- **Plein écran sans coupure** : OK passe l'aperçu en plein écran sans recharger le flux.
- **Liste par-dessus la vidéo** : OK ou ◀ en plein écran ouvre la liste des chaînes, la lecture continue derrière.
- **Zapping** : ▲ / ▼ (ou CH+ / CH−), ▶ pour le bandeau d'infos.
- **Menu latéral rétractable**, transitions animées.
- **Guide TV** : grille horaire sur 12 h, toutes les lignes défilent ensemble.
- **Films** et **Séries** (saisons, épisodes) avec affiches.
- **Favoris** : maintiens OK sur une chaîne, un film ou une série.
- Format de flux au choix (MPEG-TS ou HLS), avec bascule automatique si une chaîne ne démarre pas.

## Installer sur la TV

1. Ouvre l'onglet **Releases** du dépôt et récupère le lien de `Tivihome.apk` (dernière version).
2. Sur la TV, installe l'appli **Downloader** (Play Store), autorise-la à installer des applis inconnues.
3. Dans Downloader, saisis le lien de l'APK, télécharge, puis **Installer**.
4. Lance **Tivihome** et saisis l'adresse du serveur, l'identifiant et le mot de passe de ton abonnement.

Chaque nouvelle version s'installe par-dessus l'ancienne (même clé de signature) : tes identifiants et favoris sont conservés.

## Compilation

L'APK est compilé automatiquement par GitHub Actions à chaque push sur `main`
(`.github/workflows/build-apk.yml`) et publié dans les Releases.

En local : Android Studio (JDK 17) → ouvrir le dossier → *Build › Build APK*.

## Technique

Kotlin, Jetpack Compose, Media3 ExoPlayer (TS, HLS, MP4, MKV), OkHttp, Coil.
L'appli parle uniquement à l'API publique `player_api.php` de ton fournisseur ; aucune donnée n'est envoyée ailleurs.
