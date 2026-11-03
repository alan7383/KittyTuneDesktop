# SoundCloud DRM sur desktop — comment ça marche, et où

Les pistes `MONETIZE` de SoundCloud (par exemple `soundcloud.com/bladee1000/it-girl`) n'existent
qu'en HLS chiffré CENC. Ce document décrit le protocole tel qu'il a été **vérifié** sur l'application
de référence (SoundCloud-Desktop 8.7.0), et comment KittyTuneDesktop les lit maintenant.

---

## 1. Le protocole, vérifié sur une vraie piste

**Transcodings** — il n'existe aucune variante non chiffrée :

```
aac_160k / cbc-encrypted-hls   ← FairPlay (Apple), inutilisable ici
aac_160k / ctr-encrypted-hls   ← Widevine
```

**Résolution** d'un `ctr-encrypted-hls` : une URL de playlist + un JWT.

```json
{ "url": "https://playback.media-streaming.soundcloud.cloud/cenc/…/playlist.m3u8?…",
  "licenseAuthToken": "eyJhbGciOiJkaXIiLC…" }
```

**Playlist** — fMP4 segmenté, deux clés déclarées, une par DRM :

```
#EXT-X-KEY:METHOD=SAMPLE-AES,
  URI="data:text/plain;base64,AAAAa3Bzc2gAAAAA7e+LqXnWSs6jyCfc1R0h7Q…",   ← PSSH (107 o)
  KEYID=0x16fb34888bfa5904a6716e32caa329ae,
  KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"                ← Widevine
#EXT-X-KEY:METHOD=SAMPLE-AES-CTR, … KEYFORMAT="com.microsoft.playready"
#EXT-X-MAP:URI="…/init.mp4"
```

Le `init.mp4` porte `enca` → `sinf` → `frma: mp4a`, `schm: cenc`, `schi: tenc`
(**`ivSize = 16`** — pas 8 comme le contenu produit par FFmpeg, `isProtected = 1`, KID).
Les fragments portent `senc` avec une IV de **16 octets** par sample, plus `saiz` et `saio`.

**Licence** :

```
POST https://license.media-streaming.soundcloud.cloud/playback/widevine?license_token=<JWT>
Content-Type: application/octet-stream
<signed license request>  →  <license>
```

---

## 2. Le point dur : il faut un CDM

Un *license request* est signé par le certificat d'un appareil, et la licence qui revient est chiffrée
vers ce même appareil. Impossible de la lire sans **CDM** (Content Decryption Module) — le composant
qui détient les clés et déchiffre, et qui ne les rend jamais.

Où il se trouve selon la plateforme :

| Où ça marche | Qui fournit le CDM |
|---|---|
| Android (KittyTune) | `MediaDrm`, fourni par l'OS |
| Windows (app UWP SoundCloud) | PlayReady, fourni par l'OS |
| Le `.exe` SoundCloud-Desktop | un CDM sur **leurs serveurs** (`scnative.dev` renvoie la clé) |
| **JVM desktop (Linux, macOS, Windows)** | personne — d'où le pont vers le CDM du navigateur |

Vérifié sur l'AppImage : **aucun CDM dedans**. Pas de `.wvd`, aucun symbole `decrypt::Engine`, et
aucun des 205 `.so` embarqués n'expose l'API CDM. Le commentaire du code amont est explicite : « le
serveur […] pour les chiffrés fait le handshake Widevine ».

---

## 3. Ce que fait KittyTuneDesktop

Il **pilote le CDM déjà présent sur la machine** (celui que Firefox a téléchargé) via son API
publique, sans jamais extraire de clé.

```
Kotlin                                          natif (libkittytune_widevine.so)
──────────────────────────────────────────────  ─────────────────────────────────
HlsEncryptionInfo.parse(m3u8) → PSSH, KID
SoundCloudDrm.session()                         ktwv_create()        ← dlopen du CDM
  ├─ session.licenseRequest()  ───────────────► ktwv_license_request()  → 1759 o
  ├─ POST vers SoundCloud                      ktwv_provide_license() → clés « usable »
  └─ session gardée ouverte
CencDecrypter.decryptSegment()  ─────────────► ktwv_decrypt()  par sample (AES-CTR CENC)
  └─ retire senc/saiz/saio, corrige sizes
HlsStreamAdapter → FFmpeg
```

| Fichier | Rôle |
|---|---|
| `src/main/cpp/widevine/WidevineBridge.cpp` | hôte CDM (Host_10), licence, `Decrypt` par sample, API C |
| `src/main/cpp/widevine/content_decryption_module*.h` | en-têtes d'interface CDM de Chromium (BSD) |
| `data/drm/NativeWidevineCdm.kt` | liaison JNA, découverte du CDM, session |
| `data/drm/CencDecrypter.kt` | parsing ISOBMFF, `SampleDecryptor`, réécriture init/fragments |
| `data/drm/HlsEncryptionInfo.kt` | `#EXT-X-KEY`, sélection Widevine, décodage du `data:` URI |
| `data/drm/SoundCloudWidevineLicense.kt` | POST de licence |
| `data/drm/SoundCloudDrm.kt` | orchestration, session courante, cache |
| `data/StreamResolver.kt` | autorise `ctr-encrypted-hls` quand `SoundCloudDrm.isAvailable` |
| `audio/HlsStreamAdapter.kt` | réécrit l'init, déchiffre chaque fragment avant FFmpeg |
| `audio/DrmStreamDownloader.kt` | lit une playlist protégée et écrit un fichier **clair** |

### Trois pièges qui font « ça marche mais on n'entend rien »

- **`ivSize`** vient du `tenc` de l'init. Le lire en dur à 8 quand SoundCloud en utilise 16 fait
  renvoyer `kSuccess` au déchiffrement avec du bruit en sortie.
- **`SetTimer` est réentrant** : le CDM appelle le host en tenant son verrou. Rappeler `TimerExpired`
  sur le même thread deadlocke le processus ; il faut un thread dédié.
- **Ne jamais tenir notre mutex en appelant dans le CDM.** `ktwv_reset_session` prenait
  `bridge->mutex` puis appelait `CloseSession`, qui ne revenait jamais : au deuxième morceau protégé
  d'affilée (test puis téléchargement, ou simplement deux pistes), l'app restait bloquée, affichée
  comme un téléchargement à 0 %. Le verrou ne protège plus que notre propre état.

---

## 4. Validation

| Test | Ce qu'il prouve |
|---|---|
| `CencDecrypterTest` | le déchiffrement CENC et la réécriture des boîtes sont identiques à `ffmpeg -decryption_key` (hash SHA-256 des samples) |
| `SoundCloudDrmPipelineTest` | la chaîne complète playlist → CDM → licence → déchiffrement, avec un CDM de test |
| `NativeWidevineLiveTest` | **sur la vraie piste** : CDM de Firefox, licence SoundCloud, 50 frames audio décodées — et le téléchargement complet (2,8 Mo, 135,5 s, remuxé puis redécodé). Désactivé par défaut, lancé par `KITTYTUNE_DRM_LIVE=1 ./gradlew :test --tests "*NativeWidevineLiveTest*"` |

Résultat mesuré sur `bladee1000/it-girl` : 431 samples déchiffrés, 0 échec, fragment clair qui se
décode en `aac (LC) 44100 Hz stéréo, 160 kb/s`.

---

## 5. Limites

- **`cbc-encrypted-hls` (FairPlay) n'est pas jouable** et ne le sera pas : autre écosystème. Refusé
  explicitement (`UnsupportedSchemeException`).
- **Dépendance au CDM du navigateur** : KittyTune ne peut pas embarquer un CDM (non redistribuable,
  licence liée au produit qui l'intègre). Il faut qu'un navigateur en ait téléchargé un sur la
  machine ; sinon `SoundCloudDrm.isAvailable == false` et les pistes protégées sont ignorées comme
  avant, sans régression.
- **CDM cherché ici (rapide)** : Firefox (`~/.mozilla/firefox`, `~/.config/mozilla/firefox`),
  Chrome/Chromium/Edge/Brave/Vivaldi/Yandex, Opera et Opera GX (`~/.config/opera-gx`, Windows
  `%APPDATA%\Opera Software`, macOS `com.operasoftware.*`), Flatpak et Snap, `/opt/google/chrome`,
  `/usr/lib*/chromium`. `KITTYTUNE_WIDEVINE_CDM=/chemin/libwidevinecdm.so` force un emplacement.
- **Balayage complet** : le bouton « Analyser automatiquement » ne se limite pas à cette liste — il
  parcourt la machine entière (budget 8 s, plafond 400 000 entrées, caches et liens symboliques
  ignorés) et trouve donc un navigateur qu'on n'a pas listé, un profil d'un autre utilisateur ou un
  Chromium dans un conteneur. `CdmDiscoveryTest` vérifie le cas Opera GX avec une arborescence
  temporaire, sans avoir besoin d'Opera installé.
- **Pont natif sur les trois OS** : `dlopen` sur Linux/macOS, `LoadLibrary` sur Windows. Le pont se
  compile en `libkittytune_widevine.so` / `.dylib` / `.dll` et le CDM Windows est le
  `widevinecdm.dll` qu'Edge (dossier `EdgeCore\<version>\WidevineCdm` ou WebView2) ou Chrome
  laissent sur la machine. Le `.dll` est lié statiquement (`-static`) pour ne pas dépendre de
  `libwinpthread-1.dll`, que l'app portable n'embarque pas.
  État mesuré sous Windows : le pont charge le CDM d'Edge comme celui de Firefox et produit une
  requête de licence valide ; l'échange de licence SoundCloud, lui, a répondu `HTTP 403` depuis ce
  réseau (reproduit hors application, avec les deux CDM), donc la fin de chaîne reste à confirmer.

---

## 6. Télécharger une piste protégée

Le téléchargement n'est pas une copie du flux chiffré : chaque fragment est déchiffré par le CDM au
moment où il est écrit, et le fichier final est **clair**. C'est ce qui rend le téléchargement utile :
la licence est demandée une seule fois, et le fichier se rejoue ensuite sur n'importe quelle machine,
sans CDM.

```
StreamResolver.resolveStreamWithDrm(forDownload = true)  → playlist + licenseAuthToken
DrmStreamDownloader.download()                          → HlsStreamAdapter (session de téléchargement)
  └─ init clair + fragments déchiffrés → fMP4
DownloadManager.remuxHls(fMP4 local)                    → un seul MP4 propre (copie de flux)
AudioCache                                              → même chemin, pour le cache d'écoute
                                                          (fMP4 gardé tel quel)
```

Trois points qui ont demandé un vrai correctif :

- **Deux instances de CDM.** `ktwv_reset_session` vide l'instance : un téléchargement qui emprunterait
  celle de la lecture couperait le son de la piste en cours. Le téléchargement a donc sa propre
  instance (`NativeWidevineCdm.secondaryCdm()`), donc sa propre session.
- **L'hôte CDM doit être par instance.** Il était global (`g_bridge` + un `BridgeHost` statique) : le
  deuxième CDM renvoyait ses callbacks dans l'état du premier, et son `Initialize` n'arrivait jamais
  (« the CDM never reported that it was initialised »). `CreateCdmInstance` reçoit maintenant le
  `Bridge*` en `user_data`, et `GetCdmHost` construit un hôte par instance.
- **`InitializeCdmModule_4` une seule fois par bibliothèque.** Réinitialiser le module pour la
  deuxième instance empêchait aussi son `Initialize` d'aboutir. La bibliothèque est suivie dans un
  `std::set` protégé par un mutex.

Le remux FFmpeg à partir du fichier local est volontairement tolérant : s'il échoue, le fMP4
déchiffré est conservé tel quel — il se décode très bien (c'est ce que vérifie le test live), mais il
lui manque la durée globale dans le `moov`.
