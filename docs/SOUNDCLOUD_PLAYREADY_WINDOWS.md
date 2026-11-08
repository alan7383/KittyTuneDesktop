# Lecture SoundCloud protégée sous Windows : lecteur desktop PlayReady

## Résultat vérifié le 7 novembre 2026

KittyTune lance désormais son propre exécutable **Win32 non empaqueté**. Il utilise
le CDM PlayReady et Media Foundation de Windows, sans navigateur, application
SoundCloud installée, MSIX, publication Store, import de certificat ni inscription
`Add-AppxPackage`. Le Mode développeur ne fait pas partie de ce chemin.

Le prototype a lu un fragment protégé, puis la piste complète
`bladee1000/it-girl` (854420383, MONETIZE, AAC 160 kbit/s), avec une recherche à
60 secondes. Les premiers tests réels passent aussi sur une deuxième piste protégée
(1385927722), avec un autre KID et une autre licence. L'intégration Kotlin est testée avec le vrai lecteur : lecture,
pause stable, recherche pendant la pause, reprise, et passage par
`AudioEngine`. Le statut natif vérifie `appContainer=false` et
`hasPackageIdentity=false`. La suite ciblée compte 37 tests réussis, dont quatre
tests réels ; deux sessions natives simultanées restent indépendantes. Un essai
séparé dans un chemin avec accents a vérifié la lecture, l’arrêt sur heartbeat
expiré, l’effacement du jeton et la suppression du média chiffré.

Ces résultats portent sur cette machine. Ils ne constituent pas une validation
sur toutes les versions, éditions, configurations matérielles et politiques de
licence Windows. Les interfaces CDM utilisées demandent Windows 10 version 2004
(build 19041) ou plus récent ; le build livré vise x64.

## Pourquoi le premier prototype demandait le Mode développeur

Il utilisait `MediaPlayer` WinRT dans une application UWP AppContainer inscrite
sans signature. Cette inscription imposait le Mode développeur. Le nouveau
lecteur utilise directement le CDM avec `IMFMediaEngine`, et lui fournit le même
serveur PMP et une source `IMFTrustedInput`. Il n'inscrit aucun paquet.

Les essais desktop précédents avaient plusieurs défauts concrets : l'extension
acceptait une demande de flux d'octets comme une demande de source média ; la
source personnalisée ne définissait pas son type courant, annonçait ses événements
de démarrage dans le mauvais ordre et utilisait les unités de temps du conteneur
au lieu de 100 ns. Surtout, l'autorité de confiance était recréée à chaque demande.
Elle est maintenant conservée pour la session, comme dans l'exemple Microsoft.
Le lecteur final utilise le démultiplexeur MP4 de Windows.

## Ce que SoundCloud for Xbox a permis de comprendre

L'application inspectée est
`SoundcloudLtd.SoundCloud-MusicAudio_2.12.4.0_x64__2xc63xn306dnw`.
Le bundle Hermes préfère `ctr-encrypted-hls` AAC 160, puis 96 kbit/s.
`RNTrackPlayer.dll` utilise le pipeline PlayReady de Windows. Son manifeste
n'expose pas de protocole ou AppService pour piloter sa lecture depuis KittyTune.
Aucun binaire, compte, jeton de session ou identité de cette application n'est
réutilisé par le lecteur livré.

## Fonctionnement et limites actuelles

`WindowsProtectedMedia` télécharge les fragments **encore chiffrés**, en parallèle,
et les rassemble dans un fMP4 temporaire. Il prépare un en-tête EME standard avec
le KID et le domaine indiqués par la playlist. Le serveur de licence décide des
droits et Windows les applique dans le processus protégé. KittyTune ne reçoit
ni clé de contenu ni échantillons audio clairs.

Le fichier chiffré est entièrement préparé avant le démarrage. Cela ajoute un
délai dépendant du réseau et de la durée de la piste. La préparation est limitée
à 512 Mio, 10 000 fragments et 16 Mio par réponse ; les playlists avec plages
d'octets ou discontinuités ne sont pas prises en charge par ce nouveau chemin.
La piste mesurée faisait environ 2,9 Mo. Le cache temporaire est supprimé à la
fermeture du lecteur ; les jetons sont effacés à la révocation de session.

Chaque moteur audio possède un répertoire UUID et un processus natif distinct.
Un heartbeat perdu pendant 10 secondes ferme le lecteur. La licence, le décodage
et le rendu restent dans PlayReady/PMP. Les effets PCM, le visualiseur fondé sur
les échantillons et l'analyse locale ne s'appliquent donc pas à ce pipeline.

## Vitesse et effets : correction complémentaire

Le premier test de vitesse vérifiait seulement que la position avançait après une
commande à 1,25 ; il ne prouvait pas la vitesse réelle. La mesure suivante a montré
que `SetPlaybackRate(2)` retournait S_OK et `GetPlaybackRate()` retournait 2, tandis
que la position avançait à 1x. La source enveloppée doit aussi exposer
`IMFGetService` et transmettre le service de contrôle de vitesse de sa source MP4.
Cette transmission est maintenant implémentée. Le taux par défaut est défini
avant le chargement pour respecter une vitesse déjà sélectionnée.

Les tests mesurent désormais le rapport temps média/temps réel à 2x et 0,5x,
ainsi qu'une vitesse 2x choisie avant le chargement. Les contrôles Windows de
balance et volume réalisent le 8D et le Trance Gate sans sortir les échantillons
du pipeline protégé. Un test réel vérifie leur variation et le retour à une
balance/volume neutres après désactivation. Les ambiances restent un lecteur
indépendant. L'interface indique quels effets sont disponibles sur une piste
protégée ; égaliseur, réverbération, filtres et hauteur indépendante ne sont pas
implémentés dans ce chemin. Leurs préférences sont conservées pour les autres
pistes.

### Essais supplémentaires : conserver exactement le flux SoundCloud

Contrainte confirmée : aucune substitution par une autre source ou un autre
enregistrement pour rendre les effets disponibles.

L'insertion d'un objet `IMFTransform` Resampler créé directement échoue à la
lecture (`0x80004002`). En revanche, l'objet `IMFActivate` renvoyé par `MFTEnumEx`
pour le même Resampler, passé à `InsertAudioEffect(..., FALSE)`, permet la lecture
protégée. Le prototype a mesuré environ 0,994x, 1,935x et 0,486x aux commandes
1x, 2x et 0,5x. Cela valide l'insertion obligatoire de ce composant système ;
cela ne valide pas un pitch shifter ni la configuration d'un effet personnalisé.

Une activation de la réverbération DirectSound Windows échoue à la lecture avec
`0x80004002`, même si `InsertAudioEffect` retourne S_OK avant le chargement.
Ces objets DirectSound n'exposent pas `IMFTransform`. Les effets AudioGraph
inspectés ne sont pas directement activables comme MFT de ce lecteur.

Aucun de ces prototypes n'est intégré au lecteur livré. Aucun nouveau support
pitch, égaliseur, réverbération ou ensemble complet d'effets n'est validé.
Les effets personnalisés dans le PMP exigent des composants signés et de confiance,
avec leurs interfaces de contrôle adaptées au passage entre processus. Le chemin
PMP non protégé est documenté pour le contenu clair et ne constitue pas une
solution pour ce flux DRM.

Références : [activation générique des MFT](https://learn.microsoft.com/en-us/windows/win32/api/mftransform/nf-mftransform-mfcreatetransformactivate),
[architecture et composants de confiance du PMP](https://learn.microsoft.com/en-us/windows/win32/medfound/protected-media-path),
[limites du PMP non protégé](https://learn.microsoft.com/en-us/windows/win32/api/mfmediaengine/ne-mfmediaengine-mf_media_engine_protection_flags).

## Téléchargement réellement hors ligne : non résolu

Aucun téléchargement Windows DRM hors ligne n'est livré par cette modification.
Un fichier chiffré qui exige une nouvelle licence en ligne n'est pas considéré
comme un téléchargement hors ligne. Le repli vers un extrait de 30 secondes est
désormais limité à la lecture ; une tentative de téléchargement sans source
utilisable affiche une erreur au lieu de sembler ne rien faire.

Essais sur la licence reçue pour la piste 854420383 :

- Le système historique `com.microsoft.playready` refuse la configuration de
  session persistante (`0x80700009`, MF_NOT_SUPPORTED_ERR).
- `com.microsoft.playready.recommendation` accepte la configuration persistante,
  la création du CDM et de la session. L'acquisition HTTP réussit, mais `Update`
  refuse la réponse avec `0x80704005` (MF_TYPE_ERR).
- La même configuration moderne avec session temporaire accepte la licence et
  joue la piste. Cela correspond à une licence de streaming non persistante.
- La réponse de licence conservée puis remise au CDM dans un nouveau processus,
  sans appel HTTP, est refusée (`0x8004c006`). Cet essai n'est pas une solution
  hors ligne et ne fait pas partie du lecteur livré.

Les deux pistes testées annoncent également `downloadable=false` dans les
métadonnées SoundCloud. Le téléchargement direct du fichier original n’est donc
pas proposé pour ces pistes. Le compte concerné est gratuit.

Ces essais utilisent les droits de streaming disponibles ; ils ne valident pas
une API d'autorisation de téléchargement hors ligne. L'application ne fabrique
pas de licence persistante et ne copie pas de clés de contenu.

Références : [persistance des licences PlayReady](https://learn.microsoft.com/en-us/playready/overview/license-persistence),
[contrôle de vitesse d'une source Media Foundation](https://learn.microsoft.com/en-us/windows/win32/medfound/implementing-rate-control),
[extensions dans le pipeline protégé](https://learn.microsoft.com/en-us/windows/win32/medfound/protected-media-path).

## Compilation

Prérequis de compilation : Windows x64, JDK 21, MinGW-w64 g++ C++20 et SDK Windows
avec en-têtes C++/WinRT. Le script accepte les en-têtes déjà présents dans
`build/sdk-cpp/c/Include`. Ces outils ne sont pas requis chez l'utilisateur.

```powershell
.\gradlew.bat compileWindowsPlayReady -PdesktopOnly=true
.\gradlew.bat zipWindowsPortable -PdesktopOnly=true
```

Le seul fichier natif embarqué est `native/playready/KittyTuneProtectedPlayer.exe`.
Il est extrait sous `%LOCALAPPDATA%\KittyTune\playready\desktop-<hash>`.
Les anciens fichiers UWP sont conservés comme historique, sans être empaquetés.

## Vérifications

```powershell
.\gradlew.bat :test --tests '*WindowsProtected*Test' `
  --tests '*SoundCloudDrmPipelineTest' --tests '*CencDecrypterTest' `
  --tests '*HlsStreamAdapterRefreshTest' --tests '*HlsVariantResolverTest' `
  -PdesktopOnly=true
```

Les tests réels sont explicites et jouent à faible volume. Fournir un JSON frais
contenant `url` et `licenseAuthToken`, sans le committer ni le joindre aux rapports :

```powershell
$env:KITTYTUNE_PLAYREADY_LIVE = '1'
$env:KITTYTUNE_PLAYREADY_REQUEST = 'C:\chemin\resolution-fraiche.json'
```

Références : [exemple Microsoft Media Foundation](https://github.com/microsoft/media-foundation/tree/master/samples/MediaEngineEMEUWPSample),
[IMFMediaEngineClassFactory4 et versions compatibles](https://learn.microsoft.com/en-us/windows/win32/api/mfmediaengine/nn-mfmediaengine-imfmediaengineclassfactory4).
