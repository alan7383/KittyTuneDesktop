# SoundCloud DRM sous Windows — où ça s'arrête, et pourquoi

> **Mise à jour du 7 novembre 2026 : la lecture native fonctionne désormais dans
> un lecteur Win32 sans paquet, sans Store et sans Mode développeur.** Voir
> [le correctif PlayReady et ses vérifications](SOUNDCLOUD_PLAYREADY_WINDOWS.md).
> Les conclusions générales d'impossibilité ci-dessous sont historiques et ne
> s'appliquent pas à ce nouveau pipeline de lecture protégée. Les limitations
> concernant les échantillons clairs restent distinctes de la lecture.

Ce document remplace l'enquête précédente (« du `403` Widevine à PlayReady ») et la clôt. Il existe
pour qu'un prochain agent n'ait pas à refaire ces mesures : elles ont été refaites, complétées, et
elles donnent une réponse définitive.

**TL;DR — sur Windows, KittyTune ne peut pas obtenir d'échantillons clairs pour une piste protégée.**
La licence PlayReady s'obtient (mesuré, reproductible), mais le déchiffrement est derrière une
frontière d'architecture que Microsoft n'ouvre pas aux applications non empaquetées, et le serveur
Widevine accepte la requête d'un navigateur (4 297 o, `200`, dix fois) tout en refusant celle d'un CDM
piloté hors navigateur (4 409 o, `403`) — ce n'est ni la taille ni le stockage, et le seul levier
restant est de se faire passer pour un navigateur, ce que ce projet ne fait pas (§3.4).

**Ce qui est livré à la place (§6)** : le premier `403` est retenu, `StreamResolver` cesse de proposer
les flux chiffrés, et la chaîne de repli qui existait déjà — fournisseurs configurés, YouTube, extrait
de 30 s — prend le relais. La piste joue ce qui est disponible au lieu d'échouer au premier fragment.
Le déchiffrement local, lui, ne marche que sur Linux et macOS.

---

## 1. Ce qui marche : obtenir la licence PlayReady

Acquis et reproductible sur cette machine, avec le `licenseAuthToken` que SoundCloud émet pour la
piste. Chaîne exacte (harnais : `build/playready_mf_probe2.cpp`, transcript conservé dans
`build/probe.out`) :

| Étape | Appel | Résultat mesuré |
|---|---|---|
| 1 | `CoCreateInstance(CLSID_MFMediaEngineClassFactory, IMFMediaEngineClassFactory4)` | `S_OK` |
| 2 | `CreateContentDecryptionModuleFactory(L"com.microsoft.playready", …)` | `S_OK` |
| 3 | `CreateContentDecryptionModuleAccess(...)` puis `CreateContentDecryptionModule(props)` | `S_OK` |
| 4 | `GetService(MF_CONTENTDECRYPTIONMODULE_SERVICE, IMFPMPHost)` + `SetPMPHostApp` | `S_OK` |
| 5 | `CreateSession(TEMPORARY)` + `GenerateRequest(L"cenc", pssh)` | `S_OK`, message de 26 750 o |
| 6 | `POST https://license.media-streaming.soundcloud.cloud/playback/playready?license_token=…` (`text/xml`, challenge SOAP de 9 720 o) | **`200`**, licence de 1 146 o |
| 7 | `session->Update(license)` puis `GetKeyStatuses` | `S_OK`, **1 clé de 16 o, statut `USABLE`** |

Le challenge rendu par le CDM est un `PlayReadyKeyMessage` UTF-16LE ; c'est la partie
`<Challenge encoding="base64encoded">` décodée qui part en SOAP. Rien d'illégal ici : on demande à
SoundCloud une licence PlayReady avec le jeton que SoundCloud a émis, et c'est le CDM de Windows qui
la valide.

**Ce que ça ne donne pas : la clé.** PlayReady ne l'expose jamais. Le seul moyen d'utiliser une clé
acquise est le décrypteur du CDM — et c'est là que tout s'arrête.

---

## 2. Ce qui ne marche pas : le déchiffrement, et la lecture native elle-même

Toutes ces mesures ont été refaites ou confirmées le 2026-11-03 sur cette machine.

### 2.1 L'ITA PlayReady est un objet **distant**, pas appelable depuis notre process

`CreateTrustedInput` et `GetInputTrustAuthority` répondent `S_OK`, mais le CDM construit ses objets
via un `PmpHostApp` qui active `Windows.Media.Protection.PlayReady.PRRemoteObjectFactory`. L'ITA
rendue est donc un **proxy COM vers un autre espace d'adressage**. Conséquence mesurée deux fois, à
l'identique :

```
ita=… vtbl=00007ff93bb8bdf0 q=… add=… rel=… getdec=00007ff93bb873e0
VEH code=0xc0000005 addr=… module=C:\WINDOWS\System32\combase.dll offset=0x2367fc
```

L'accès violant se produit **dans `BindAccess`** (le `printf` suivant n'arrive jamais), et
`GetDecrypter` casse de la même façon. Ce n'est pas un bug du pont : c'est l'absence d'un hôte PMP
capable de marshaller ces appels.

**Deux expériences ont été ajoutées pour en avoir le cœur net** (`build/ita_probe.cpp`, licence
fraîche à chaque essai — `Update: 0x00000000`, clé `USABLE`, donc le CDM est bien dans l'état
attendu) :

| Configuration | Résultat |
|---|---|
| aucune session PMP, `GetDecrypter(IID_IMFTransform)` **avant** tout `BindAccess` | AV dans `combase.dll` (`+0x23670c`) — donc la crash ne vient pas d'un `MFINPUTTRUSTAUTHORITY_ACCESS_PARAMS` mal rempli, et `BindAccess` n'est pas la cause |
| `MFCreatePMPMediaSession(0x2)` = `MFPMPSESSION_IN_PROCESS` (drapeau non documenté) | `S_OK`, session créée… mais l'ITA a **exactement la même vtable** (`0x…bb8bdf0`) et le même AV |
| `MFGetService(pmpSession, MF_PMP_SERVICE, IMFPMPHost)` puis `SetPMPHostApp(…)` avec **cet** hôte au lieu de celui du CDM | même vtable, même AV |

Autrement dit : quel que soit le mode de session PMP et quel que soit l'hôte qu'on fournit, le CDM
PlayReady instancie ses objets de confiance **de son côté** (`PRRemoteObjectFactory` → processus
sécurisé PlayReady), et l'ITA rendue reste un proxy non marshallable. Il n'y a pas de configuration
de l'API qui la rende locale.


### 2.2 Une session PMP « non protégée » ne déchiffre rien — c'est documenté

`MFCreatePMPMediaSession(MFPMPSESSION_UNPROTECTED_PROCESS, …)` existe, mais la documentation
Microsoft est explicite : *« You can use the unprotected process to play clear content but not
protected content »*
([MFPMPSESSION_CREATION_FLAGS](https://learn.microsoft.com/windows/win32/api/mfidl/ne-mfidl-mfpmpsession_creation_flags)).
Autrement dit : pas de déchiffrement, et donc pas de PCM dans notre process. Le seul mode qui
déchiffre (`flags = 0`) crée la session **dans un processus protégé** (PE), dont les composants
doivent être signés par Microsoft ; une app tierce n'y place pas son propre sink audio
([PMP Media Session](https://learn.microsoft.com/windows/win32/medfound/pmp-media-session),
[`IMFInputTrustAuthority`](https://learn.microsoft.com/windows/win32/api/mfidl/nn-mfidl-imfinputtrustauthority)).

Mesures qui ferment la voie dans ce sens :

| Tentative | Résultat |
|---|---|
| `pmp_probe.exe` (PMP + `MF_SESSION_CONTENT_PROTECTION_MANAGER`) sur le MP4 CENC, `file://` | topology posée, puis `MESessionTopologySet` = `MF_E_UNSUPPORTED_BYTESTREAM_TYPE` (`0xC00D36C4`) — **aucun content enabler n'est jamais demandé** |
| `mf_reader_probe.exe` sur le même fichier | `MF_E_DRM_UNSUPPORTED` (`0xC00D3700`) |
| `IMFMediaEngineClassFactoryEx::CreateMediaKeys` (PlayReady, Widevine, ClearKey) | `MF_NOT_SUPPORTED_ERR` (`0x80700009`) pour **tous** les key systems |

### 2.3 La voie WinRT échoue encore plus tôt : `PlayReadyLicenseSession` refuse de se construire

C'est le point qui a coûté le plus de temps, et il est maintenant tranché. `MediaProtectionPMPServer`
se construit (`S_OK`), mais `new PlayReadyLicenseSession(PropertySet)` lève
`E_NOINTERFACE` (« l'objet ne prend pas en charge le service spécifié ») dans un processus desktop
non empaqueté. Reproduit avec les propriétés **correctement typées** (`Guid` pour
`MediaProtectionSystemId`, `PropertySet` pour `MediaProtectionSystemIdMapping`,
`{9A04F079-9840-4286-AB92-E65BE0885F95}` pour le conteneur) — donc ce n'est pas une erreur de
typage, c'est une exigence d'identité de paquet :

```
url=http://127.0.0.1:8765/playlist.m3u8 systemId=Guid
MediaProtectionPMPServer ok
license session failed: Exception: L'objet ne prend pas en charge le service spécifié.
MediaFailed:  / 0xC00D715B          ← MF_E_TOPOLOGY_VERIFICATION_FAILED
state=None pos=00:00:00 dur=00:00:10.0078003
```

`MediaPlayer` lit bien la playlist (durée 10,0078 s = l'`EXTINF`), mais la topologie protégée ne
passe pas la vérification, et `ServiceRequested` n'est jamais déclenché. En complément :
`PlayReadyContentHeader(byte[])` rejette même le `pssh` minimal (`0x8004B8CE`).

**Conclusion de §2 : même la lecture native (sans PCM, sans égaliseur, sans téléchargement) n'est
pas accessible à l'app telle qu'elle est construite.** Il faudrait un paquet MSIX avec la capacité
PMP — et cela ne donnerait toujours pas d'échantillons au moteur audio.

---

## 3. Widevine sous Windows : le serveur refuse, et ce n'est pas une question de pont

Re-mesuré (`KITTYTUNE_DRM_LIVE=1 ./gradlew test --tests "*NativeWidevineLiveTest*"`) : les tests
`NativeWidevineLiveTest` échouent en `DrmLicenseException`, avec une requête de licence de **1 761 o**
— l'identité en clair d'un CDM non vérifié.

Le pont fait ce qu'il doit : `CreateCdmInstance`, `Initialize`, session, `GenerateRequest`,
`SetServerCertificate` (les 705 o du `SignedServerCertificate`), POST. C'est le serveur qui répond
`403`.

### 3.1 Ce que la capture proxy dit vraiment (et ce qu'elle a fait dire à tort)

Le journal du proxy TLS de la session précédente (`build/mitm/capture.log.old`) contient dix
échanges complets avec le serveur, et il est sans ambiguïté :

```
POST /playback/widevine?license_token=…   Content-Length: 2     -> 200 OK (1 095 o)   ← demande de certificat
POST /playback/widevine?license_token=…   Content-Length: 4297  -> 200 OK (1 004 o)   ← requête de licence
```

…répété **dix fois**, chaque fois `200`. Une vraie requête de navigateur fait donc **4 297 o**, et
elle est acceptée.

**Le « corps de 5 833 o » qui a servi de référence dans la session précédente n'était pas une requête
normale** : c'est un message du *shim* de débogage, qui ajoutait son propre journal à la fin de la
requête (`Host::OnSessionMessage`) pour l'exfiltrer. Comparer nos 4 409 o à ce 5 833 o a fait
chercher 1,4 ko manquants qui n'ont jamais existé. À jeter.

### 3.2 Ce qui a été essayé, et pourquoi ce n'est pas la taille

| État du CDM dans notre pont | Taille | Réponse |
|---|---|---|
| aucune vérification (identité en clair) | 1 761 | `403` |
| `VerifyCdmHost_0` + nos fichiers (sans `.sig`) | 2 281 | `403` |
| `VerifyCdmHost_0` + `chrome.dll`, `chrome_elf.dll` | 4 105 | `403` |
| `VerifyCdmHost_0` + les binaires signés de Firefox | 4 409 | `403` |
| **Firefox réel, même piste, même jeton** | **4 297** | **`200`** |

Nos 4 409 o sont *plus gros* que les 4 297 o acceptés : la taille n'est pas le critère. C'est le
**contenu** de `EncryptedClientIdentification` qui diffère, et il est chiffré — donc invisible.

### 3.3 Ce que l'identité dit d'elle-même

En clair, la requête non chiffrée expose tout `ClientIdentification` (décodé avec `build/wire.py`) :

```
architecture_name = x86-64
company_name      = Google
model_name        = ChromeCDM
platform_name     = Windows          ← la plateforme est déclarée
widevine_cdm_version = 4.10.3112.02
```

et elle **porte un vrai certificat d'appareil** : un jeton de 1 310 o contenant un `DrmCertificate` de
348 o et une signature de 256 o. Donc « CDM non provisionné » — l'explication de la session
précédente — est faux aussi : le certificat est là.

Ce qui a été éliminé par la mesure, sans ambiguïté :

- **la persistance n'est pas la différence** : le CDM de Firefox n'écrit rien d'utile
  (`build/ffprofile/gmp/…/storage/<id>/2459821194` fait **0 octet**) ; tout son état se réduit à un
  sel de 32 o pour `RequestStorageId` ;
- **le CDM n'est pas différent** : le même binaire, chargé par notre hôte sans vérification, produit
  exactement notre requête de 1 761 o (journal `build/tmp-wv9/shim.log`) ;
- **stockage, `CreateFileIO`, `RequestStorageId`** : sans effet sur le corps ;
- **drapeaux d'`Initialize`** et nombre de fichiers vérifiés : changent la taille, jamais la réponse ;
- sur Linux, la **même** identité en clair reçoit `200`.

### 3.4 La conclusion

Il ne reste qu'un levier : donner à `VerifyCdmHost_0` les **binaires signés d'un navigateur** et
reproduire son comportement d'hôte à l'octet. C'est-à-dire se faire passer pour Chrome ou Firefox
auprès du CDM. Techniquement c'est une piste ; ce projet ne la prend pas : c'est du contournement de
mesure technique, et un client open source qui l'embarquerait exposerait ses mainteneurs et
permettrait de récupérer des pistes monétisées. Le `403` reste donc la réponse attendue sur Windows.

Vérifié aussi, pour ne pas le retenter : l'API mobile de SoundCloud
(`api-mobile.soundcloud.com/tracks/soundcloud:tracks:<id>/streams/https`), que l'app synthétise,
répond `401` sans jeton OAuth ; et la piste `bladee1000/it-girl` (`policy=MONETIZE`,
`monetization_model=AD_SUPPORTED`, `full_duration=135 549 ms`) n'expose **que** des transcodings
chiffrés — aucun clair, aucun snippé. Chaque playlist a été tirée preset par preset :
`aac_160k` et `aac_96k` en `ctr-encrypted-hls` déclarent **deux** `#EXT-X-KEY` (Widevine +
PlayReady), les variantes `cbc-encrypted-hls` en déclarent une (FairPlay), et `abr_sq` répond `404`
à la résolution de flux. Il n'y a donc pas de variante claire à récupérer.


---

## 4. Les voies qui restent, et ce qu'elles coûtent

1. **Environnement Linux (WSL2) — la seule qui rende des échantillons clairs.** Le pont compile en
   `.so` et fonctionne : c'est la voie qui préserve lecture, égaliseur, visualiseur et
   téléchargement. Coût : WSL2 + un `libwidevinecdm.so` Linux à fournir, un démon côté Linux et un
   client JNA/IPC côté Kotlin (envoyer les fragments entiers, pas les échantillons un par un). Ce
   n'est pas « léger » : c'est une dépendance d'installation.
   Sur cette machine, `wsl --status` répond que le sous-système n'est pas installé.
2. **Lecture native PlayReady (sans PCM).** Rendue possible seulement par un paquet MSIX avec la
   capacité PMP, et elle ne donnerait ni égaliseur, ni visualiseur, ni cache, ni téléchargement : le
   son sortirait par Windows, pas par le moteur de KittyTune. Le produit y perdrait sa raison d'être
   sur ces pistes.
3. **Ne pas déchiffrer sur Windows, mais ne pas échouer pour autant** — c'est ce qui est livré
   (§6) : après le premier `403`, le CDM reste détecté mais n'est plus proposé, et la résolution
   retombe sur les fournisseurs configurés (Deezer/Tidal/Qobuz/YouTube) puis sur l'extrait de 30 s.
   La piste joue ce qui est disponible au lieu de mourir au premier fragment.

La question contractuelle reste ouverte et n'est pas technique : SoundCloud peut encadrer l'usage
d'un client tiers dans ses CGU, et l'accès partenaire est la seule voie propre pour distribuer cette
fonctionnalité.

---

## 5. Ce qu'il ne faut pas refaire

Toutes ces pistes ont été essayées et mesurées ; les rouvrir sans nouvel élément est une perte de
temps.

| Piste | Verdict mesuré |
|---|---|
| Appeler `IMFInputTrustAuthority::GetDecrypter` / `BindAccess` depuis l'app | `0xC0000005` dans `combase.dll` — l'ITA est un proxy distant (§2.1) |
| `MFCreatePMPMediaSession(0x2)` (`MFPMPSESSION_IN_PROCESS`) pour rendre l'ITA locale | `S_OK`, mais vtable identique et même AV : les objets PlayReady restent distants |
| `SetPMPHostApp` avec l'`IMFPMPHost` de notre propre session PMP | même vtable, même AV — l'hôte fourni ne change rien |
| `GetDecrypter(IID_IMFTransform)` **avant** `BindAccess` | AV quand même : ce n'est pas le struct d'accès qui casse |
| `MFCreatePMPMediaSession(0)` + MP4/fMP4 CENC local + renderer audio | `MF_E_UNSUPPORTED_BYTESTREAM_TYPE` ; aucun enabler demandé (§2.2) |
| `MFCreatePMPMediaSession(MFPMPSESSION_UNPROTECTED_PROCESS)` | exclu par la doc : ne joue pas de contenu protégé |
| `IMFSourceReader` sur une source protégée | `MF_E_DRM_UNSUPPORTED` |
| `IMFMediaEngine` + `MF_MEDIA_ENGINE_CONTENT_PROTECTION_MANAGER`, ou `CreateMediaKeys` | `MF_NOT_SUPPORTED_ERR` pour tous les key systems |
| WinRT `MediaPlayer` + `MediaProtectionPMPServer` + `PlayReadyLicenseSession` | `E_NOINTERFACE` au constructeur hors paquet MSIX ; `MF_E_TOPOLOGY_VERIFICATION_FAILED` ensuite |
| `PlayReadyContentHeader(byte[])` (WinRT) | `0x8004B8CE`, même sur le `pssh` minimal |
| WebView2 comme client PlayReady | arrête au mur d'authentification SoundCloud ; et ne rend jamais de PCM |
| `IFileIo`/`RequestStorageId`/`CreateFileIO` Widevine côté Windows | sans effet sur le corps de la requête |
| Drapeaux d'`Initialize` (distinctive, persistent, hw_secure) et `VerifyCdmHost_0` | changent la taille du corps, jamais la réponse du serveur |
| `gdb` sur le CDM Widevine | tue le processus (refus de débogage) |
| Remplacer un `call` par un `jmp` dans `widevinecdm.dll` | plante plus loin : le CDM vérifie son propre code |

Le détail des harnais et des artefacts reste dans `build/` (non versionné) ; les noms, tailles et
rôles sont listés dans l'annexe de la session précédente, et les fichiers toujours présents :

| Chemin | Rôle |
|---|---|
| `build/playready_mf_probe2.cpp` | licence PlayReady + clé `USABLE` + tentative ITA (§1, §2.1) |
| `build/ProbePlayReady2.cs` | WinRT : PMPServer, `PlayReadyLicenseSession`, `MediaPlayer` (§2.3) |
| `build/pmp_probe.cpp` | session PMP + topologie + renderer audio (§2.2) |
| `build/playready_decode.cpp` | chaîne complète CDM → POST → source protégée → WAV (s'arrête à l'ITA) |
| `build/mfcdm-store/mspr.hds` | magasin de licences PlayReady du système (opaque, 528 Ko) |
| `build/tmp-wv8/`, `build/tmp-wv9/`, `build/mitm/` | captures des requêtes Widevine par hôte / navigateur, et le proxy TLS qui les a produites |

---

## 6. Ce que voit l'utilisateur aujourd'hui sous Windows

**Le premier refus est mis à profit.** `SoundCloudWidevineLicense` retient un `403`
(`licenceRefused`), `SoundCloudDrm.canLicense` tombe à `false`, et `StreamResolver` cesse d'offrir
les candidats `ctr-encrypted-hls` (`allowDrm = SoundCloudDrm.canLicense`). La chaîne de repli qui
existait déjà prend alors le relais : fournisseurs configurés (Deezer/Tidal/Qobuz) → YouTube
(NewPipe) → extrait de 30 s. Sans cette bascule, la piste était résolue vers le flux chiffré et
échouait au premier fragment — c'est la différence entre « rien » et « ce qui est disponible ».

Rien n'est retenu d'une exécution à l'autre : un CDM différent, une autre plateforme ou une politique
serveur changée sont réessayés à neuf au prochain lancement.

- Avec un CDM détecté mais refusé : le journal dit `license server answered HTTP 403 — SoundCloud
  refuses the client identity presented by a CDM driven outside a browser on Windows…`, et la piste
  joue via le repli.
- Sans CDM (machine sans navigateur) : `DrmUnavailableException`, et `StreamResolver` ne propose même
  pas les transcodings chiffrés (`allowDrm = false`) : le repli est immédiat.
- La page « Analyser automatiquement » (`DrmSelfTest`) rapporte l'échange pas à pas, refus compris.


---

## Annexe A — reconstruire la requête PlayReady (si quelqu'un rouvre la voie)

Ce qui a demandé du travail et qu'il ne faut pas redécouvrir.

**La playlist porte le PlayReady Object** dans la seconde ligne `#EXT-X-KEY`
(`KEYFORMAT="com.microsoft.playready"`, `METHOD=SAMPLE-AES-CTR`). Décodé, c'est :

```xml
<WRMHEADER xmlns="http://schemas.microsoft.com/DRM/2007/03/PlayReadyHeader" version="4.0.0.0"><DATA>
  <PROTECTINFO><KEYLEN>16</KEYLEN><ALGID>AESCTR</ALGID></PROTECTINFO>
  <KID>iDT7FvqLBFmmcW4yyqMprg==</KID>
  <LA_URL>https://license.media-streaming.soundcloud.cloud/playback/playready</LA_URL>
  <DS_ID>VlR7IdsIJEuRd06Laqs2jw==</DS_ID>
  <CUSTOMATTRIBUTES><CID>MDRiYzI5MWMtYzNhNC00MzEwLWFjYzctMDA1NzBmMjU5OTg2</CID>
  <DRMTYPE>smooth</DRMTYPE></CUSTOMATTRIBUTES>
  <CHECKSUM>d4KkZdf7dgs=</CHECKSUM>
</DATA></WRMHEADER>
```

Trois pièges, tous reproductibles :

- le WRMHEADER **complet** (avec `<CHECKSUM>` et `<CUSTOMATTRIBUTES>`) est refusé par
  `GenerateRequest` (`0x80041301`) ; un WRMHEADER minimal (`KID` + `LA_URL` + `DS_ID` + `ALGID`)
  suffit ;
- il doit être enveloppé dans une **`pssh` box version 0** de system id
  `9A04F079-9840-4286-AB92-E65BE0885F95`, sinon le CDM refuse aussi l'init data ; 730 octets
  suffisent (`build/pr-min-box0.bin`) ;
- le message rendu par le CDM n'est pas le corps à poster : c'est un `PlayReadyKeyMessage`
  **UTF-16LE** qui contient un `<Challenge encoding="base64encoded">`. C'est ce challenge décodé
  (SOAP `AcquireLicense`, 9 720 o) qui part en `POST` avec
  `Content-Type: text/xml; charset=utf-8`, et sans `SOAPAction`.

Le `PlayReadyContentHeader` WinRT, lui, échoue sur certaines machines avec `0x8004B8CE`
(« HWDRM present but not supported ») : le chemin Media Foundation `IMFContentDecryptionModule` ne
passe pas par ce constructeur et fonctionne, c'est pour ça que les harnais C++ existent.

Recette minimale pour rejouer la mesure (WinLibs g++ 16.2 ; un autre g++ produit une DLL UCRT
dynamique qui échoue au chargement avec `0xC0000139`) :

```powershell
g++ -O2 -std=c++17 -D_WIN32_WINNT=0x0A00 build/playready_mf_probe2.cpp `
    -o build/playready_mf_probe2.exe -lmfplat -lmf -lole32 -loleaut32 -lpropsys -luuid -lshlwapi
# écrire le pssh PlayReady minimal, lancer le probe, poster le challenge interne, déposer la réponse
build/playready_mf_probe2.exe build/pr-min-box0.bin
```

Sortie attendue jusqu'au mur :

```
CreateSession: 0x00000000
KeyMessage type=0 size=26750 url=https://license.media-streaming.soundcloud.cloud/playback/playready
inner challenge size=9720
license size=1146
Update: 0x00000000
GetKeyStatuses: 0x00000000
key statuses: 1
  key 0: size=16 status=0        ← USABLE
CreateTrustedInput: 0x00000000
GetInputTrustAuthority: 0x00000000
VEH code=0xc0000005 … combase.dll offset=0x2367fc   ← l'ITA distante
```

Mesures écartées au passage, pour ne pas les refaire : `providers`/`verify`/`host files` du CDM
PlayReady ne changent rien ; `PlayReadyContentHeader`, `CreateMediaKeys`, `IMFMediaEngine` et
`IMFSourceReader` échouent tous comme indiqué au §2.2 ; le magasin `build/mfcdm-store/mspr.hds`
(528 Ko, format binaire avec `configuration`, `domainstore`, `securestore`, `LicStorexmr`,
`BLOBCACH`) est opaque et lié à la machine — l'attaquer est un projet de rétro-ingénierie à part
entière, et le but serait d'extraire une clé, ce qui n'est pas le sujet de ce projet.

## Annexe B — les harnais de la session précédente (non versionnés, dans `build/`)

| Chemin | Rôle |
|---|---|
| `build/playready_mf_probe2.cpp` | licence PlayReady + clé `USABLE`, puis tentative ITA (§1, §2.1) |
| `build/probe.out` | transcript de la seule exécution où la clé est devenue `USABLE` |
| `build/ProbePlayReady2.cs` | WinRT : `MediaProtectionPMPServer` + `PlayReadyLicenseSession` + `MediaPlayer` (§2.3) |
| `build/pmp_probe.cpp` | session PMP + topologie + renderer audio, `MF_SESSION_CONTENT_PROTECTION_MANAGER` (§2.2) |
| `build/playready_decode.cpp` | chaîne complète CDM → POST WinHTTP → source protégée maison → WAV |
| `build/media_keys_probe.cpp`, `build/media_engine_probe.cpp`, `build/mf_reader_probe.cpp` | les trois autres clients Media Foundation testés |
| `build/make_cenc_mp4.py`, `build/make_hls.py`, `build/rewrite_pssh.py`, `build/extract_samples.py` | fabriquent les fixtures (MP4 CENC non fragmenté, playlist HLS à `data:` URI, bundle d'échantillons `KTPR`) |
| `build/pr-min-box0.bin`, `build/playready-wrmheader.xml` | pssh minimal et WRMHEADER de référence |
| `build/mfcdm-store/mspr.hds` | magasin de licences PlayReady du système |
| `build/tmp-wv5`…`tmp-wv9` | harnais Widevine : `ktwv_*`, captures de requêtes par hôte/navigateur, `plainid`, `iface`, shim CDM |
| `build/mitm/proxy.py` | proxy TLS qui a capturé la requête de licence de Firefox (5 833 o) + la réponse `200` |
| `build/ffprofile/`, `build/chromeprofile/` | profils navigateur de laboratoire utilisés pour ces captures |

Rien de tout cela n'est nécessaire au fonctionnement de l'app : ce sont des instruments de mesure.


## Complément expérimental — 2026-11-03, chaîne MediaEngine explicite

Un nouvel essai isolé a reproduit HTTP 200, licence PlayReady de 1 146 octets et clé USABLE. Recevoir l’ITA en IUnknown puis QueryInterface(IMFInputTrustAuthority) ne supprime pas le plantage GetDecrypter (combase.dll +0x23670c).

En revanche, hors paquet MSIX, IMFGetService du CDM fournit bien IMediaProtectionPMPServer. Un gestionnaire implémentant IMFContentProtectionManager et IMediaProtectionManager peut le publier sous Windows.Media.Protection.MediaProtectionPMPServer ; IMFMediaEngineProtectedContent::SetContentProtectionManager réussit. Les conclusions précédentes sur l’impossibilité générale de raccorder ce serveur PMP hors MSIX sont donc trop larges.

La lecture n’est toujours pas acquise : fichier CENC puis source AAC protégée personnalisée chargée par IMFMediaEngineExtension aboutissent à MF_E_DRM_UNSUPPORTED (0xC00D3700), position zéro. Ce résultat n’établit ni une impossibilité générale ni une lecture fonctionnelle. La suite utile est de vérifier le contrat de source protégée et de confiance utilisé par MediaEngine, en s’appuyant sur media_foundation_source_wrapper.cc et media_foundation_protection_manager.cc de Chromium. Aucun chemin expérimental n’a été activé dans l’application.

---

## Complément mesuré — 2026-11-04 : où passe exactement le `403`, et ce qui ne le fait pas tomber

Cette session a rouvert la question du `403` avec une méthode différente : au lieu de comparer des
tailles, elle a **rejoué le corps exact d'une requête acceptée** et fait varier un paramètre à la fois.
Tout est reproductible avec `build/cdmtest/` (voir l'annexe C).

### Ce qui est éliminé

| Variable | Mesure | Verdict |
|---|---|---|
| En-têtes HTTP (`User-Agent` Firefox/Chrome, `Origin`, `Referer`, `Sec-Fetch-*`) | six combinaisons, corps identique | **sans effet** : `403` dans tous les cas |
| Corps d'une requête de navigateur **acceptée** (5 833 o), rejoué depuis Python avec un JWT neuf | `200`, 619 o | le jeton n'est pas lié au client ; c'est **le corps** qui décide |
| Même corps, **un octet inversé** au milieu | `403` | la signature couvre le message : une requête ne se retouche pas |
| Même corps, un octet inversé **en fin de message** | `200` | les octets hors message signé ne comptent pas |
| Chrome 156 (même machine, même JWT, même PSSH) | `200`, 1 004 o de licence | **Chrome passe** |
| Firefox 159 | `200`, 4 297 o | passe aussi |
| **Edge 156** (même CDM Chromium, profil neuf) | `403` | même un navigateur **est refusé** s'il n'est pas dans la liste |
| Pont KittyTune, identité en clair | `403` (1 761 o) | inchangé |
| Pont KittyTune, identité **chiffrée** après `VerifyCdmHost_0` réussi | `403` (2 249 → 4 377 o selon les fichiers) | voir ci-dessous |

### Ce que cette session a trouvé de neuf

1. **`VerifyCdmHost_0` doit être appelée avant `CreateCdmInstance`.** Appelée après (comme le faisait
   `build/tmp-wv9/verifyhost2.cpp`), elle réussit mais le CDM produit quand même l'identité en clair
   (1 761 o). Appelée avant, avec des binaires dont la signature Google est valide, le CDM **chiffre
   son identité** (`EncryptedClientIdentification`, champ 8) : 2 249 o pour un fichier vérifié,
   4 377 o pour les trois fichiers de Chrome. Le §2.2 de la session précédente disait que la
   vérification d'hôte « ne change jamais la réponse » : c'est vrai pour la réponse du serveur, faux
   pour la requête — elle change complètement sa forme. C'était le chaînon manquant.
2. **Le refus ne vient pas de la plateforme déclarée.** Edge, qui déclare `platform_name = Windows`
   comme Chrome et vérifie son hôte aussi bien, est refusé. Firefox, qui déclare la même chose, passe.
   Le serveur distingue donc les **identités de navigateur**, pas les systèmes.
3. **La différence restante est de 80 octets, à l'intérieur du bloc chiffré.** Avec exactement les
   trois mêmes fichiers signés de Chrome (`chrome.exe`, `chrome.dll`, `widevinecdm.dll` et leurs
   `.sig`), le pont produit une identité de 3 776 octets là où Chrome en produit 3 696. Tout le reste
   est identique (même certificat d'appareil, même numéro de série de certificat de service, même
   version de CDM 4.10.3112.0). Ces 80 octets sont le seul écart mesurable, et ils sont chiffrés côté
   client : on ne peut pas les lire sans la clé privée du service de licence.
4. **Ce n'est pas la peine de se faire passer pour le processus de Chrome.** `build/cdmtest/payload.cpp`
   s'injecte dans un `chrome.exe` lancé pour l'occasion et y exécute la même chaîne : `VerifyCdmHost_0`
   réussit, mais la requête fait toujours 4 377 o. Le CDM n'attache donc pas l'identité au chemin du
   processus hôte ; faire tourner le pont « dans le navigateur » ne suffit pas.
5. **Le certificat de service ne s'installe pas à la main.** Le vrai enchaînement, celui de Chrome, est
   une session en deux temps : la première réponse du CDM est une demande de certificat de service
   (`08 04`, deux octets) — elle n'apparaît **que si** `VerifyCdmHost_0` a réussi —, on la poste au
   même endpoint, le serveur rend 710 octets, on les rend au CDM par `UpdateSession`, et c'est
   seulement là qu'il émet la vraie demande de licence, l'identité chiffrée contre *ce* certificat.
   Passer un certificat capturé ailleurs (`SetServerCertificate`) produit une identité différente.
   Le flux complet est implémenté dans `build/cdmtest/probe6.cpp` ; il aboutit au même `403`, avec
   4 377 o contre 4 297 o pour Chrome. Donc l'écart de 80 octets ne vient pas du certificat non plus.
6. **Les pistes PlayReady restent fermées** pour les raisons déjà mesurées : la licence s'obtient, la
   clé est `USABLE`, mais le décrypteur est derrière l'ITA distante et le processus protégé.

### Conclusion, corrigée

Le serveur de licences SoundCloud n'accepte que des identités produites par **sa propre pile EME dans
un navigateur qu'il reconnaît** (Chrome, Firefox). Ni la vérification d'hôte, ni l'injection dans le
processus du navigateur, ni un autre CDM ne suffisent : il reste 80 octets d'identité que seul
l'adaptateur CDM du navigateur remplit. Ce n'est pas une limite du pont, c'est une politique côté
serveur, et elle est appliquée au corps signé de la requête.

**Ce que cela implique pour une suite** — deux chemins seulement, tous deux hors du « tout natif » :

- **CDM hébergé par le navigateur** : un petit shim qui s'insère dans la pile CDM d'un Firefox dédié
  (profil jetable, GMP réécrit) et expose `Decrypt` par IPC. Le déchiffrement et le PCM restent dans
  notre processus applicatif, mais il faut Firefox installé et un profil instrumenté. C'est le seul
  chemin qui rende des échantillons clairs sur Windows aujourd'hui.
- **Sidecar Linux (WSL2)** : le pont compile en `.so` et, sur Linux, la même identité en clair reçoit
  `200`. Coût : WSL2 + un `libwidevinecdm.so`, et un démon IPC. C'est la voie déjà décrite au §4.

Aucune des deux n'est « légère » au sens d'une simple bibliothèque ; c'est un choix de produit, pas
un bug à corriger.

### Annexe C — harnais de cette session (`build/cdmtest/`, non versionné)

| Chemin | Rôle |
|---|---|
| `fetch_live.py` | résout la piste, récupère playlist, PSSH, init et premier fragment avec un JWT neuf |
| `probe.exe`, `probe2.exe` | chaîne CDM → requête → POST → `UpdateSession`, avec ou sans certificat de service |
| `probe6.cpp` | le flux exact du navigateur : vérification d'hôte, demande de certificat `08 04`, certificat, demande de licence, licence |
| `verifyhost3.exe`, `verifyhost4.exe` | `VerifyCdmHost_0` **avant** la création de l'instance, avec des paires `fichier|.sig` explicites |
| `collect.py` | page EME servie à un vrai navigateur ; enregistre la requête et la licence qu'il produit |
| `payload.cpp`, `injector.exe` | exécutent la même chaîne **dans** un `chrome.exe` réel |
| `header_matrix.py` | fait varier les en-têtes HTTP sur un corps constant |
| `cert-*.bin`, `req-*.bin`, `browser-capture/` | certificat de service, requêtes par variante, échanges d'un vrai Chrome |

### Complément mesuré — 2026-11-04 (suite) : le service CDM résiste à l'injection

Le crochet sur `NtCreateUserProcess` fonctionne et a permis de fermer la dernière hypothèse : on
atteint bien la création du service CDM, mais **on ne peut pas y installer de code**.

**Ce qui marche.** `ntdll!NtCreateUserProcess` est le seul entonnoir de création de processus : son
entrée d'export est réécrite (stub `mov rax, imm64; jmp rax` alloué dans les 4 Go du module) puis les
slots d'import de tous les modules déjà chargés, `kernelbase` compris, sont repointés. La ligne de
commande de l'enfant est lue avant sa création dans `RTL_USER_PROCESS_PARAMETERS::CommandLine`
(offset `0x70`, x64) ; `CdmServiceBroker` dans cette chaîne identifie le service. Mesure :
`NtCreateUserProcess: CDM service spawn (we froze it: 0)` — Chrome l'a déjà créé suspendu, le crochet
le voit avant tout code.

**Ce que le service refuse, mesuré un par un** (harnais `build/chromehost/`, commutateurs `KTCDM_MODE`,
`KTCDM_ENTRY_MODE`, `KTCDM_STUB`, `KTCDM_HIJACK_DELAY_MS`).

| Primitive | Résultat |
|---|---|
| `VirtualAllocEx` + `WriteProcessMemory` | accepté, l'enfant vit ; la licence aboutit toujours |
| `CreateRemoteThread` (même sur `Sleep`) | l'enfant meurt **en 0 ms**, code `0xC0000022` |
| `QueueUserAPC` sur le thread initial suspendu | jamais délivré (pas de point d'attente alertable) |
| `SetThreadContext` vers un stub RWX (`Sleep`) | l'enfant vit ; mais **`LoadLibraryA` pendant l'init du loader = `0xC0000005`** |
| `SetThreadContext` après init, stub qui restaure `RSP` | `LoadLibraryA` refuse, l'enfant meurt en `0xC0000409` |
| patch du **point d'entrée** de `chrome.exe` (`E9 rel32` sur le `call` du prologue) + stub | `0xC0000409` (fast-fail) |
| même patch, mode témoin sans chargement de DLL | `0xC0000409` aussi — donc le patch lui-même est refusé |

**Politiques lues sur le processus** (`GetProcessMitigationPolicy`, juste après la création) :
`dynamic-code 0x0`, `cfg 0x1`, `signature 0x0`, `image-load 0x3`, `child-process 0x1`,
`shadow-stack 0x105` (pile fantôme CET active **et stricte**). Autrement dit : code dynamique
interdit à l'exécution, CFG actif, et un `call`/`ret` qui ne correspond pas à la pile fantôme termine
le processus en fast-fail. **Aucune primitive d'injection classique ne survit ici** — ce n'est pas une
erreur de mise en œuvre, c'est le durcissement de `--service-sandbox-type=cdm`.

**Preuve de contrôle.** Avec `KTCDM_SKIP_CDM=1` (on ne touche à rien), le même Chrome termine le flux
entier : `access ok` → `mediakeys ok` → demande de certificat `08 04` → `license 200 710` →
demande de licence 4 297 o → **`license 200 619`** → `update ok`. Le service CDM fonctionne ; c'est
notre intrusion qu'il refuse.

**Conséquence produit.** Le chemin « CDM hébergé par le navigateur » ne peut pas se contenter
d'injecter dans le service CDM : il faut soit rester **dans le navigateur** et lui faire faire le
déchiffrement (donc capter l'audio ailleurs), soit **éviter le service** et retravailler l'identité du
pont — le seul écart mesuré restant est de 80 octets, et `Initialize(true, true, false)` (identifiant
distinctif + état persistant) n'avait jamais été essayé jusqu'ici.

`libkittytune_widevine.dll` a été reconstruit depuis `src/main/cpp/widevine/WidevineBridge.cpp`
(API actuelle `ktwv_create` / `ktwv_license_request` / `ktwv_set_server_certificate` /
`ktwv_provide_license` / `ktwv_reset_session` / `ktwv_decrypt`). `probe6.exe` était lié à l'ancienne
API (`ktwv_open_session`, `ktwv_update`, `ktwv_keys_usable`) et ne démarre plus : il faut le relinker
contre l'API actuelle avant de refaire la mesure de licence.

### Mesure `Initialize` + `VerifyCdmHost` : tout ce que l'hôte peut régler est réglé

Harnais `build/cdmtest/idtest.cpp` (lié statiquement au pont, API actuelle), flux exact du navigateur :
`VerifyCdmHost_0` → `08 04` → POST → 710 o → `ktwv_update` (le certificat repasse **par la session**,
comme `session.update()` dans la page) → demande de licence → POST.

| Réglage de l'hôte | Demande de licence | Serveur |
|---|---|---|
| `Initialize(false,false,false)`, 1 fichier vérifié (le CDM) | 3 641 o | **403** |
| `Initialize(true,true,false)` (identifiant distinctif + état persistant) | 3 641 o, identique | **403** |
| + `chrome.dll` vérifié (2 fichiers) | 3 993 o | **403** |
| + `chrome.exe` vérifié (3 fichiers, chemins réels) | 4 345 o | **403** |
| Chrome, même page, même CDM | **4 297 o** | **200** |

**Ce que la comparaison structurée montre.** Les deux requêtes ont exactement la même forme :
champ 1 = 1, champ 2 = la demande signée, champ 3 = 128 o de signature, champ 9 = 20 o. Dans le champ 2,
tout est identique (contenu/KID 99 o, type, horodatage, politique, version CDM `4.10.3112.0`) **sauf le
sous-message 8**, l'identité chiffrée :

- `f1` = `buydrm.com` (identique), `f2` = 16 o d'IV (aléatoire), `f4` = 16 o, `f5` = 256 o de signature ;
- `f3`, le client id chiffré : **3 040 o** pour nous, **3 696 o** pour Chrome — l'écart de 656 o est là,
  et il est intégralement à l'intérieur du chiffré.

Les drapeaux d'identité n'y changent **rien** (3 641 o dans les deux cas), donc l'hypothèse des 80 octets
est morte : ce n'est pas ce que le CDM consent à dire de lui-même, c'est ce qu'il met dans ce blob. Et
puisque l'écart de taille se comble en vérifiant les bons fichiers (3 641 → 3 993 → 4 345) mais que **le
403 ne bouge pas**, la différence restante n'est pas une taille : c'est du contenu que seul le CDM décide,
hors de portée de l'API hôte (`Host_11` n'expose ni origine, ni version d'hôte, ni identité).

Autrement dit : le pont produit maintenant une requête de **la même forme et de la même taille** que
Chrome (4 345 contre 4 297, soit 48 octets), et le serveur la refuse quand même. Il ne reste plus de
levier côté hôte à essayer.

---

## Complément mesuré — 2026-11-07 : l'application SoundCloud du Microsoft Store, et pourquoi on ne peut pas la réutiliser

Cette session a rouvert la question par l'application officielle installée sur cette machine, en se
demandant si son moteur pouvait être appelé. Réponse mesurée : non, et la mesure explique enfin
*pourquoi* — ce n'est ni le CDM, ni la licence, ni le manifeste HLS, c'est le **type de processus**.

### Où elle est, et ce qu'elle est

```
C:\Program Files\WindowsApps\SoundcloudLtd.SoundCloud-MusicAudio_2.12.4.0_x64__2xc63xn306dnw
```

`SoundcloudLtd.SoundCloud-MusicAudio`, version 2.12.4.0, éditeur `CN=C1719774-…`. Ce n'est pas
Electron : c'est **React Native for Windows** (`soundcouch.exe`, `Microsoft.ReactNative.dll`,
`hermes.dll`, `Microsoft.Web.WebView2.Core.dll`). Le lecteur est `RNTrackPlayer.dll`, un serveur
WinRT in-process (`RNTrackPlayer.ReactPackageProvider`) qui contient une classe `PlayReadyHelper`.

Le manifeste donne la clé :

```xml
<EntryPoint>soundcouch.App</EntryPoint>          <!-- application XAML, pas un exe desktop -->
<Capability Name="internetClient" />
<uap3:Capability Name="backgroundMediaPlayback" />
<DeviceCapability Name="protectedMediaPlayback" />
<!-- pas de rescap:runFullTrust -->
```

Autrement dit : une application **UWP, en AppContainer**, avec la capacité `protectedMediaPlayback`.

### Ce qu'elle fait exactement (chaînes extraites de `RNTrackPlayer.dll`)

```text
Windows.Media.Protection.MediaProtectionSystemId            -> {F4637010-03C3-42CD-B932-B48ADF3A6A54}
Windows.Media.Protection.MediaProtectionSystemIdMapping     -> …PlayReady.PlayReadyWinRTTrustedInput
Windows.Media.Protection.MediaProtectionContainerGuid       -> {9A04F079-…}
Windows.Media.Protection.UseSoftwareProtectionLayer
Windows.Media.Protection.UseHardwareProtectionLayer
https://license.media-streaming.soundcloud.cloud/playback/playready
?license_token=
drm-playready / drm_individualization / drm_component_load / licenseServiceForbidden
```

C'est **exactement** le chemin du §1 : `MediaPlayer` + `MediaProtectionManager` + PlayReady, le
challenge posté à `/playback/playready?license_token=…` par WinHTTP, la réponse rendue au CDM par
`ServiceRequested`. Le pont de `src/main/cpp/widevine` fait déjà la même acquisition de licence avec
succès ; ce qui échoue n'est pas la licence.

### Le test qui tranche : identité de paquet, puis AppContainer

Un paquet MSIX de test a été construit autour d'un exécutable qui rejoue exactement ces propriétés
(inscription en mode développeur, puis lancement par `shell:AppsFolder`), avec `runFullTrust` **et**
`protectedMediaPlayback`, `uap10:TrustLevel="mediumIL"`, `uap10:RuntimeBehavior="win32App"`.

| Configuration | Résultat |
|---|---|
| aucun paquet (exe nu), mêmes propriétés | `MediaFailed 0xC00D715B` — `MF_E_TOPOLOGY_VERIFICATION_FAILED` |
| paquet + `protectedMediaPlayback` + `runFullTrust`, mediumIL | `MediaProtectionPMPServer` : OK |
| … puis `PlayReadyLicenseSession` | **`E_NOINTERFACE`** (« l'objet ne prend pas en charge le service spécifié ») — inchangé |
| … puis `MediaPlayer` sur la playlist réelle | playlist lue (durée exacte 135,547 s), puis **`0xC00D715B`** |
| … playlist servie en local, clé PlayReady **seule**, `METHOD=SAMPLE-AES`, `LA_URL` réécrite | **`0xC00D715B`**, et **aucune requête de licence** n'est émise |
| … playlist distante d'origine | `0xC00D2EE7` — `NS_E_REFUSED_BY_SERVER` |

Trois conclusions, chacune élimine une hypothèse :

1. **Ce n'est pas le manifeste HLS** : `MediaPlayer` lit la playlist et sa durée ; retirer la ligne
   Widevine et passer `SAMPLE-AES-CTR` en `SAMPLE-AES` ne change rien.
2. **Ce n'est pas l'identité de paquet** : avec `protectedMediaPlayback`, le PMP se construit, mais
   `PlayReadyLicenseSession` refuse toujours et la topologie protégée n'est pas validée.
3. **C'est le processus** : la capacité `protectedMediaPlayback` et le PMP PlayReady ne sont
   réellement accordés qu'à un processus **AppContainer** (UWP) ; un exe desktop, même packagé en
   mediumIL, reste un processus « full trust » qui n'a pas le droit de monter cette topologie. C'est
   pour cela que l'application du Store fonctionne et qu'un `.exe` ne peut pas.

### Et même dans l'application, il n'y aurait pas d'échantillons clairs

L'en-tête du SDK Windows (`mfcontentdecryptionmodule.h`, 10.0.28000) ne déclare **aucun**
`CreateDecryptor` sur `IMFContentDecryptionModule` : seulement `CreateTrustedInput` →
`IMFInputTrustAuthority`, c'est-à-dire l'ITA distante qui plante dans `combase.dll` (§2.1). Le PCM
déchiffré ne sort pas de la voie protégée ; une application tierce n'obtiendrait donc, au mieux,
qu'une **lecture** sans égaliseur, sans visualiseur, sans cache ni téléchargement.

### Conséquence

La piste « réutiliser l'application SoundCloud » est fermée : son moteur n'est pas appelable (pas de
protocole, pas d'AppService, pas de deep link dans son manifeste), et son privilège DRM tient au fait
qu'elle est une UWP AppContainer. Il ne reste donc que les voies du §4, inchangées : le sidecar Linux
(seul chemin qui rend des échantillons clairs), ou le repli déjà livré au §6.

### À ne pas refaire

| Piste | Verdict mesuré |
|---|---|
| Réutiliser `RNTrackPlayer.dll` / l'application du Store | moteur non exposé ; aucun point d'entrée (ni protocole, ni AppService) |
| Rejouer ses propriétés `MediaProtectionManager` hors paquet | `0xC00D715B` |
| Paquet MSIX `runFullTrust` + `protectedMediaPlayback` (mediumIL) | `0xC00D715B`, `PlayReadyLicenseSession` en `E_NOINTERFACE` |
| Réécrire la playlist (clé PlayReady seule, `SAMPLE-AES`, `LA_URL` locale) | `0xC00D715B`, aucune requête de licence |
| Chercher un déchiffreur in-process dans le SDK (`CreateDecryptor`) | n'existe pas ; seul `CreateTrustedInput`/ITA est exposé |

### L'application connectée ne change rien — mesuré sur `it-girl`

Le bundle Hermes a été décompilé (`hbc-decompiler`, HBC v96, 2,96 Mo → 645 186 lignes) pour lire ce
que l'app fait vraiment. Elle garde sa session dans
`%LOCALAPPDATA%\Packages\SoundcloudLtd.SoundCloud-MusicAudio_2xc63xn306dnw\LocalState\AsyncStorage.db`
(clé `auth-storage`, JWT OAuth de portée `*`). Ce jeton a été rejoué, avec le `client_id` de l'app
(`e97aTFdQ…`, extrait de la config `api-v2` du bundle) et `Authorization: OAuth …`, sur la piste
`it-girl` :

| Requête | Résultat |
|---|---|
| `GET /tracks/854420383` avec la session de l'app | `200`, `policy=MONETIZE`, **6 transcodings** : `cbc-encrypted-hls` et `ctr-encrypted-hls`, rien d'autre |
| `GET …/stream/ct` **avec** le jeton OAuth | `200`, `{url, licenseAuthToken}` — jeton de **524 o** |
| `GET …/stream/ct` **sans** jeton | `200`, `{url, licenseAuthToken}` — jeton de **524 o**, identique |

Autrement dit : **être connecté ne débloque aucun flux clair ni aucun transcoding privilégié**. L'app
préfère explicitement `ctr-encrypted-hls` (`selectBestTranscoding` : `ctr-encrypted-hls` `aac_160k`,
puis `aac_96k`, puis repli sur `hls`) et n'a aucun chemin non chiffré. Son « privilège » de lecture est
le **processus AppContainer**, pas un droit de compte : il n'y a donc aucune identité à copier qui
ouvre le déchiffrement. Copier le jeton OAuth de l'app n'apporterait rien de plus que la connexion
SoundCloud que KittyTune propose déjà.
