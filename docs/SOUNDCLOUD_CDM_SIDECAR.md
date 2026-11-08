# Option : décoder les pistes protégées dans l'app, via le CDM en sidecar

> **Mise à jour (8 novembre 2026) — sous Windows, le sidecar tourne désormais dans une mini-distro
> WSL dédiée (`kittytune-cdm`), pas dans une Ubuntu complète.** L'app télécharge un rootfs d'environ
> 14 Mo (`scripts/build-wsl1-rootfs.sh`, construit en CI), l'importe en **WSL1 par défaut** (pas de VM,
> `vmmemWSL` à 0 ; WSL2 en secours), puis récupère le CDM depuis le `.deb` de Chrome avec
> `busybox ar` + `xz` + `busybox tar` (GNU `tar` et `dpkg-deb` échouent sous WSL1). L'activation du
> composant WSL1 passe par l'UAC et un redémarrage, après quoi l'assistant reprend tout seul. Les
> sections ci-dessous décrivant la compilation dans une Ubuntu (apt, `g++`, `wsl --install -d Ubuntu`)
> sont historiques. Voir `data/drm/WslSidecarSetup.kt`.

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
(`ui/profile/DrmSetupWizard.kt`, moteur dans `data/drm/WslSidecarSetup.kt`) :

1. détecte WSL et une distribution (`wsl -l -q`, `wsl --version`) ; s'il en manque, ouvre
   `wsl --install -d Ubuntu` dans sa propre fenêtre (élévation, redémarrage éventuel, compte Ubuntu)
   et revérifie toutes les 5 s ;
2. installe les outils (`build-essential`, `curl`, `dpkg`, `ca-certificates`) en root si absents ;
3. écrit les sources C++ embarquées dans l'app (`sidecar-src/`, builds Windows) dans
   `/opt/kittytune/src`, par stdin : pas besoin du dépôt ni d'un disque monté ;
4. prend le CDM d'un Chrome installé, d'une installation faite par le script ci-dessous, sinon du
   paquet Chrome Linux ; compile `/opt/kittytune/ktcdm` ; le sonde en tant qu'utilisateur par défaut ;
5. écrit `cdm-sidecar.json`, choisit la méthode WSL et vérifie l'aller-retour.

Sous Windows, le sidecar ne tourne que si la méthode choisie est WSL (`protected_backend`) ou si
`KITTYTUNE_CDM_SIDECAR` est défini. Tant que WSL ne répond pas, la lecture repasse par PlayReady.

### À la main

```powershell
wsl --install -d Ubuntu        # une fois, si WSL n'est pas déjà là
.\scripts\setup-cdm-sidecar.ps1
```

Le script, dans cet ordre :

1. vérifie qu'une distribution WSL existe ;
2. installe `build-essential`, `curl`, `dpkg` dedans (en root, non interactif) ;
3. exécute `scripts/setup-cdm-sidecar.sh` : récupère le `libwidevinecdm.so` du paquet Chrome Linux
   s'il n'y en a pas déjà un, compile le démon `ktcdm`, le sonde ;
4. écrit `%LOCALAPPDATA%\KittyTune\cdm-sidecar.json`.

Relancer KittyTune suffit ensuite. L'écran DRM (groupe Lecture) affiche l'état du sidecar.

**Pour désinstaller :** supprimer ce fichier de configuration. Rien d'autre n'est modifié.

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
- les deux scripts passent leur analyse syntaxique (`Parser::ParseFile`, `bash -n`).

Ce qui n'est **pas** mesuré ici :

- la lecture réelle d'une piste protégée par ce chemin — WSL n'est pas installé sur cette machine, et
  le `200` de la route Linux vient des mesures de `SOUNDCLOUD_DRM_WINDOWS.md` §3.3 ;
- le débit du tuyau : `DECRYPT` traite un échantillon à la fois, soit un aller-retour par échantillon
  (quelques centaines par fragment de dix secondes). Sur un hôte local ou en LAN, c'est négligeable
  devant la durée du fragment ; sur un lien lent, ce serait le premier point à regrouper.

## Fichiers

| Chemin | Rôle |
|---|---|
| `src/main/cpp/cdmsidecar/CdmSidecar.cpp` | le démon : le pont, exposé sur un tuyau |
| `src/main/kotlin/.../data/drm/CdmSidecar.kt` | transport JVM, `WidevineCdm` enregistré quand le probe passe |
| `scripts/setup-cdm-sidecar.ps1` | entrée Windows : WSL, outils, exécution, configuration |
| `scripts/setup-cdm-sidecar.sh` | le travail côté Linux : CDM, compilation, probe |
| `src/test/kotlin/.../data/drm/CdmSidecarTest.kt` | le transport contre un faux démon |
