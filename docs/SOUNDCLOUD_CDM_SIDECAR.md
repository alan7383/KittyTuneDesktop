# Option : décoder les pistes protégées dans l'app, via le CDM en sidecar

## Le point de départ

Le chemin livré sous Windows est PlayReady (`SOUNDCLOUD_PLAYREADY_WINDOWS.md`) : il joue les pistes
protégées, mais dans le chemin protégé de Windows, qui ne rend **aucun échantillon** à l'application.
Conséquence mesurée et documentée : égaliseur, filtres, réverbération, pitch et visualiseur ne
s'appliquent pas à ces pistes. Ce n'est pas un bug, c'est le modèle DRM.

Ce document décrit une **option**, désactivée par défaut, qui rend ces effets : le même pont Widevine
que KittyTune utilise déjà, mais conduit depuis un processus Linux, où le serveur de licences
l'accepte. Les échantillons reviennent **en clair** au lecteur, qui les traite comme n'importe quel
flux : FFmpeg décode, la chaîne DSP s'applique, la vitesse, le pitch et l'égaliseur fonctionnent.

Sans cette option, rien ne change : PlayReady reste le chemin des pistes protégées.

## Installation

### Depuis l'app (le chemin normal)

Réglages › Divers › Titres protégés › Méthode **WSL**, puis « Configurer WSL ». L'assistant
(`ui/profile/DrmSetupWizard.kt`, moteur dans `data/drm/WslSidecarSetup.kt`) n'installe **pas** de
distribution complète : il importe une mini-distribution dédiée, `kittytune-cdm`, d'environ 38 Mo.

1. **Version** : WSL1 par défaut (pas de machine virtuelle, Windows exécute le démon comme un processus
   ordinaire : `vmmemWSL` reste à 0). WSL2 est proposé sous « Options avancées » pour les PC où WSL1 ne
   peut pas être activé ; il passe par une petite VM et consomme donc plus de mémoire.
2. **Composant Windows** : vérifie que le composant de la version choisie est actif
   (`Microsoft-Windows-Subsystem-Linux` pour WSL1, `VirtualMachinePlatform` pour WSL2). Sinon, l'active via
   l'invite UAC de Windows (aucune commande à taper), demande un redémarrage et mémorise qu'une
   configuration est en attente. Au relancement, un toast le signale et la page des réglages rouvre
   l'assistant directement à l'étape d'installation, qui démarre seule.
3. **Téléchargement** du rootfs `cdm-rootfs-v<N>` (≈ 11 Mo), vérifié par son sha256
   (`ROOTFS_VERSION` / `ROOTFS_SHA256`).
4. **Import** : `wsl --import kittytune-cdm %LOCALAPPDATA%\KittyTune\wsl\kittytune-cdm <tgz> --version 1|2`.
5. **Module Widevine** : dans la distribution, `curl` télécharge le `.deb` de Chrome pour Linux (≈ 140 Mo,
   supprimé ensuite), `busybox ar` + `xz` + `busybox tar` en extraient `libwidevinecdm.so`. GNU `tar` et
   `dpkg-deb` échouent sous WSL1 (appels système non implémentés, `ENOSYS`) : c'est pour ça que le rootfs
   embarque busybox.
6. **Sonde** : `ktcdm libwidevinecdm.so --probe`.
7. **Connexion** : écrit `cdm-sidecar.json`, choisit la méthode WSL et vérifie l'aller-retour.

Au total, environ 60 Mo restent sur le disque. Sous Windows, le sidecar ne tourne que si la méthode
choisie est WSL (`protected_backend`) ou si `KITTYTUNE_CDM_SIDECAR` est défini. Tant que WSL ne répond
pas, la lecture repasse par PlayReady.

**Désinstaller** : Réglages › Divers › Titres protégés › « Supprimer le module WSL » désenregistre
`kittytune-cdm` et oublie la configuration. Désactiver le composant WSL1 est proposé en option, et
seulement si aucune autre distribution ne l'utilise.

### Le rootfs

Construit par `scripts/build-wsl1-rootfs.sh` (Linux) : `ktcdm` compilé, la fermeture glibc/libstdc++,
busybox, `curl`, `xz`, `/bin/sh`, le squelette dont WSL a besoin pour démarrer (`/etc/passwd`, `/proc`,
`/dev`, …), un `ld.so.cache` (sans lui, `ktcdm` ne trouve pas `libdl.so.2`) et un `wsl.conf` sans systemd,
sans interop du `PATH` Windows ni montage automatique. Le module Widevine n'y est jamais : il n'est pas
redistribuable.

Les versions publiées sont **immuables** : l'app épingle l'URL et le sha256 d'une version précise. Le
workflow « Build CDM rootfs » se contente de construire et vérifier sur chaque push ; pour publier, le
lancer à la main avec un **nouveau** numéro de version (il refuse d'écraser une version existante), puis
mettre à jour `ROOTFS_VERSION` et `ROOTFS_SHA256` ensemble.

### Sans le script

Le transport ne sait pas ce qu'il y a de l'autre côté du tuyau. `KITTYTUNE_CDM_SIDECAR` a priorité sur
le fichier de configuration et accepte n'importe quelle ligne de commande :

```powershell
# une machine Linux du réseau, avec le démon compilé
$env:KITTYTUNE_CDM_SIDECAR = 'ssh -o BatchMode=yes -T user@host ~/.local/share/kittytune/ktcdm ~/.local/share/kittytune/libwidevinecdm.so'

# un démon local
$env:KITTYTUNE_CDM_SIDECAR = 'C:\tools\ktcdm.exe C:\tools\libwidevinecdm.so'
```

Sans variable, sans fichier, et sans distribution WSL détectable, l'option est simplement absente.

## Variante mesurée : le module Linux chargé directement sous Windows

Depuis le 10 novembre 2026, le `.so` Linux tourne **nativement sous Windows**, sans WSL, sans machine
virtuelle et sans processus Linux : `src/main/cpp/widevinelinux/` mappe le binaire, applique ses
relocations, installe un TCB Linux par thread et fournit les 184 symboles qu'il importe de la glibc.

`ktcdm.exe` (construit par `scripts/build-native-cdm.ps1`) est le **même démon** : il compile
`cdmsidecar/CdmSidecar.cpp` et `WidevineBridge.cpp` est remplacé par `cdm_bridge.c`, qui expose
exactement la même ABI C. Le protocole, les opcodes et le probe `RESET_SESSION` sont identiques, donc
l'app n'a rien à changer : c'est encore un sidecar, simplement local et natif.

```powershell
$env:KITTYTUNE_CDM_SIDECAR = 'C:\...\build\widevinelinux\ktcdm.exe C:\...\libwidevinecdm.so'
```

Ou, sans variable d'environnement, le fichier habituel `%LOCALAPPDATA%\KittyTune\cdm-sidecar.json` :

```json
{ "command": ["C:\\...\\ktcdm.exe", "C:\\...\\libwidevinecdm.so"] }
```

Mesuré le 10 novembre 2026 sur `bladee1000/it-girl` : requête de licence de 1 702 octets, `HTTP 200`
avec 615 octets de licence, 431 échantillons déchiffrés sur 431 (200 403 octets), **identiques octet
pour octet** à ce que la configuration WSL1 produisait le 8 novembre. Trois sessions successives sur
le même démon passent, ce qui est le rythme d'usage réel de l'app.

Le module reste non redistribuable : `ktcdm.exe` ne l'embarque pas, il reçoit son chemin en argument.

## Ce que le protocole transporte, et ce qu'il ne transporte pas

`src/main/cpp/cdmsidecar/CdmSidecar.cpp` expose six appels sur `stdin`/`stdout`, en trames
`u8 opcode, u32 longueur, charge utile` (petit-boutiste), réponse `u8 statut, u32 longueur, charge` :

| Opcode | Rôle |
|---|---|
| 1 `LICENSE_REQUEST` | ouvre la session sur le PSSH, rend la requête signée |
| 2 `PROVIDE_LICENSE` | rend la licence au CDM, attend des clés utilisables |
| 4 `DECRYPT` | un échantillon CENC : chiffré en entrée, clair en sortie |
| 5 `RESET_SESSION` | libère la session précédente sur la même instance |
| 6 `SET_SERVER_CERTIFICATE` | certificat de service, quand un serveur l'exige |
| 0 `QUIT` | arrêt propre |

**La clé de contenu ne traverse pas le tuyau.** `DECRYPT` prend du chiffré et rend du clair, comme le
pont en cours de processus : ni le JVM ni le protocole ne voient jamais une clé.

## Repli, et comment il est garanti

`CdmSidecar.installIfConfigured()` ne s'inscrit **que si le démon répond à un vrai aller-retour**
(`RESET_SESSION`), pas sur un code de sortie : `wsl.exe` est le transport, et un probe qui se
contenterait de « le processus a démarré » enregistrerait un démon dont les octets n'arrivent jamais.
En cas d'échec, la raison est retenue cinq minutes, puis l'essai suivant retente — la machine Linux
peut très bien être éteinte à un moment et allumée plus tard.

Quand l'option est active, `AudioEngine` ne passe **pas** par le lecteur PlayReady : l'URL protégée
part dans la chaîne FFmpeg habituelle, `HlsStreamAdapter` déchiffre chaque fragment par le CDM, et
le DSP reçoit du PCM. Quand elle ne l'est pas, tout se comporte exactement comme avant.

## Vérifications

Mesuré ici, sur la machine de développement :

- `CdmSidecar.cpp` compile et se lie avec `WidevineBridge.cpp` (`g++ -std=c++17 -O2 -Wall -Wextra`,
  MinGW) ; les deux chemins d'erreur du démon répondent comme prévu (`usage` → 64, CDM
  introuvable → 70) ;
- `CdmSidecarTest` fait tourner le transport JVM contre un faux démon dans un vrai processus :
  requête de licence, trame de déchiffrement complète (données, IV, sous-échantillons, KID), refus
  remonté en `DrmLicenseException`, démon absent remonté en `DrmUnavailableException`, découpage
  d'une ligne de commande avec guillemets. Cinq tests ;
- la suite DRM existante reste verte avec l'option branchée (41 tests : `WindowsProtected*`,
  `CencDecrypter`, `SoundCloudDrmPipeline`, `HlsStreamAdapterRefresh`, `CdmDiscovery`,
  `CdmSidecar`) ;
- le script de récupération du CDM passe `bash -n` et n'utilise ni `dpkg-deb` ni GNU `tar`
  (`WslSidecarSetupTest`) ;
- **de bout en bout, sous WSL1** (`WslProvisionTest`, 8 novembre 2026) : `provision(1)` télécharge le
  rootfs publié, importe `kittytune-cdm`, récupère le module, le sonde, écrit la configuration, puis
  `bladee1000/it-girl` est déchiffrée entièrement par cette configuration (2 829 389 octets), avec
  `vmmemWSL` à 0 pendant le déchiffrement, contre ≈ 1,5 Go pour l'ancienne Ubuntu en WSL2.

Ce qui n'est **pas** mesuré ici :

- le débit du tuyau : `DECRYPT` traite un échantillon à la fois, soit un aller-retour par échantillon
  (quelques centaines par fragment de dix secondes). Sur un hôte local ou en LAN, c'est négligeable
  devant la durée du fragment ; sur un lien lent, ce serait le premier point à regrouper.

## Fichiers

| Chemin | Rôle |
|---|---|
| `src/main/cpp/cdmsidecar/CdmSidecar.cpp` | le démon : le pont, exposé sur un tuyau |
| `src/main/kotlin/.../data/drm/CdmSidecar.kt` | transport JVM, `WidevineCdm` enregistré quand le probe passe |
| `src/main/kotlin/.../data/drm/WslSidecarSetup.kt` | Windows : composant WSL (UAC), import du rootfs, CDM, probe, configuration, désinstallation |
| `src/main/kotlin/.../ui/profile/DrmSetupWizard.kt` | l'assistant (choix WSL1/WSL2, activation, reprise après redémarrage) |
| `scripts/build-wsl1-rootfs.sh` | construit le rootfs minimal (Linux / CI) |
| `.github/workflows/build-cdm-rootfs.yml` | construit et vérifie le rootfs ; publie une version immuable à la main |
| `src/test/kotlin/.../data/drm/WslProvisionTest.kt` | de bout en bout : provision depuis la release puis déchiffrement réel |
| `src/test/kotlin/.../data/drm/CdmSidecarTest.kt` | le transport contre un faux démon |
