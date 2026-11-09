# Charger le CDM Widevine Linux nativement sous Windows

Ce dossier fait tourner `libwidevinecdm.so` — le module Linux, celui dont l'identité est acceptée par
le serveur de licences de SoundCloud — dans un processus Windows ordinaire, sans WSL, sans machine
virtuelle, sans couche de compatibilité.

## Pourquoi

`docs/SOUNDCLOUD_DRM_WINDOWS.md` §3 : sous Windows, le CDM présente une identité que le serveur
refuse (`403`), et le chemin PlayReady qui joue quand même ne rend aucun échantillon à l'application
— donc pas d'égaliseur, pas de visualiseur, pas de téléchargement. Sous Linux, la **même** identité
en clair reçoit `200`. Le module Linux est le seul qui rende des échantillons clairs.

## Ce qui est mesuré comme fonctionnel

| Étape | État |
|---|---|
| Mapper l'ELF (80 Mio, 4 `PT_LOAD`, 4324 `R_X86_64_RELATIVE`, 191 relocations de symboles) | ✅ |
| Exécuter `DT_INIT` puis les 9 entrées de `init_array` | ✅ |
| `InitializeCdmModule_4()` retourne | ✅ |
| `CreateCdmInstance(11, "com.widevine.alpha", …)` retourne une instance | ✅ |
| `cdm->Initialize()` → `Host::OnInitialized(true)` | ✅ |
| `ktcdm.exe <so> --probe` → `ok`, code de sortie 0 | ✅ |
| Le protocole sidecar répond (`RESET_SESSION`, le probe de l'app) | ✅ |
| `CreateSessionAndGenerateRequest` avec le PSSH SoundCloud | ✅ 1702 octets |
| `SetServerCertificate` / `UpdateSession` (licence) | ✅ clés utilisables |
| `Decrypt` sur un fragment CENC SoundCloud réel | ✅ 431/431 échantillons |

**Résultat de bout en bout, `bladee1000/it-girl`, 10 novembre 2026 :**

```
resolve: HTTP 200, 'It Girl'
stream: HTTP 200, token 524 chars
fetched: init 2008 B, fragment 209260 B, PSSH 107 B
licence request: 1702 bytes
licence server: HTTP 200, 615 bytes      ← l'identité Linux est acceptée
provide_licence: status=0
  samples: 431, 195.7 KiB
decrypted 431/431 samples, 200403 bytes
```

Les 200 403 octets sont **identiques octet pour octet** à ceux que la configuration WSL1 produisait
le 8 novembre (`sha256 f38f26235202bb558367b75e23a8226f…`), qui étaient déjà documentés comme se
décodant en PCM dans l'app. Trois sessions successives sur le même démon (création → `RESET_SESSION`
→ création) passent aussi, ce qui est le rythme d'usage réel de l'app.


## Les trois pièces

**`winelf.c`** — le chargeur. Il mappe l'image, applique les relocations, installe les TCB Linux par
thread et le gestionnaire d'exceptions qui répare la base `fs`.

Deux faits Windows décident de la conception :

1. **Windows remet `fs` à 0 à chaque réordonnancement** (il garde le TEB dans `gs`). Le binaire Linux
   lit son canari à `fs:[0x28]` à chaque prologue : la première instruction après une préemption
   fauterait. Un *vectored exception handler* restaure la base et reprend l'instruction. Mesuré :
   ~1 faute par quantum (15 ms), quelques µs chacune.

2. **Appeler du code Linux par un pointeur non typé corrompt l'appelant.** Les deux conventions ne
   protègent pas les mêmes registres : `rdi` et `rsi` sont volatils en System V mais doivent survivre
   à un appel Microsoft. Un pointeur de fonction non annoté laisse donc le CDM écraser les locales de
   l'appelant (mesuré : `count` et `entries` de la boucle d'initialisation réécrits). Tout appel vers
   l'image passe par `__attribute__((sysv_abi))`.

**`glibc_shim.c` + `shim_printf.c`** — les 184 symboles que le binaire importe, en System V.

- `MinGW` ne sait pas compiler une fonction **variadique** System V : son `va_list` est celui de
  Windows, et `va_arg` lit alors n'importe quoi (mesuré : violation d'accès dans le shim lui-même).
  Les points d'entrée `printf`/`snprintf`/`sscanf` prennent donc leurs arguments comme paramètres
  fixes — les six premiers arrivent dans les mêmes registres quelle que soit la convention — et
  reconstruisent le bloc contigu que ucrt attend. Les variantes `v*` reçoivent un vrai `va_list`
  System V et le traduisent en parcourant la chaîne de format.
- pthread sur SRWLOCK/CONDITION_VARIABLE, `mmap` sur VirtualAlloc avec la sémantique de **pool
  d'adresses** de PartitionAlloc (réservation `PROT_NONE`, puis `mprotect` pour committer une
  tranche, puis `mmap` par-dessus une tranche déjà réservée), `/dev/urandom` synthétique (sans lui le
  CDM s'arrête sur un `CHECK`), `ctype`, locales, `setjmp`/`longjmp` et `__udivti3` en assembleur.

**`cdm_bridge.c`** — l'hôte CDM (`Host_11`) et les appels vers le CDM, exposés avec la **même ABI C**
que `widevine/WidevineBridge.cpp`. `cdmsidecar/CdmSidecar.cpp` se compile donc contre l'un ou
l'autre sans modification : `ktcdm.exe` est un démon sidecar identique, mais qui charge le `.so`
Linux.

Les vtables sont construites à la main. Point d'ABI qui a coûté cher : le pointeur de vtable rangé
dans un objet Itanium désigne la **première fonction virtuelle** ; l'offset-to-top et le RTTI sont
*avant*. Mettre deux mots de préfixe décale tous les slots de deux — `Initialize` devient
`SetServerCertificate`, et le CDM rejette une promesse au lieu de s'initialiser.

## Construire et essayer

Le démon est construit et livré par le build normal de l'app :

```powershell
./gradlew.bat processResources      # construit ktcdm.exe dans build/generated/.../native/widevinelinux
```

`NativeCdmSetup` (côté Kotlin) l'extrait dans `%LOCALAPPDATA%\KittyTune\cdm` au moment de
l'installation, avec le module téléchargé. Rien à configurer à la main : l'assistant des titres
protégés fait tout, puis l'app parle au démon par le protocole sidecar.

Pour travailler sur le chargeur seul, `scripts/build-native-cdm.ps1` construit aussi un harnais :

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-native-cdm.ps1
build\widevinelinux\ktcdm.exe <chemin>\libwidevinecdm.so --probe   # -> ok, code 0
```

Variables utiles : `KITTYTUNE_WIDEVINE_DEBUG=1` (trace des rappels de l'hôte), `KT_LOG=1` (trace de
chaque appel du shim), `KT_TRACE=0` (coupe la trace), `KT_NOLOG=1` (le CDM n'écrit plus ses journaux),
`KT_SKIP_INIT=<masque hexa>` (saute des entrées de `init_array`), `KT_NOMSG=1` (l'hôte ne copie pas
la requête de licence).

`KITTYTUNE_CDM_SIDECAR='<ktcdm.exe> <libwidevinecdm.so>'` reste disponible et a priorité sur le
fichier de configuration, pour pointer un autre démon (une machine Linux en SSH, par exemple).

## Le piège qui a coûté le plus de temps

Le PSSH de SoundCloud ne contient pas seulement l'identifiant de clé : il porte aussi un
`provider_id` (`buydrmkeyos`) et un `content_id`. Dès que Widevine voit un `provider_id`, il prend un
chemin spécifique au fournisseur, et ce chemin meurt ici sur un pointeur non initialisé :

```
exception 0xc0000005 at cdm+0x101b686, fault address 0x13
code: 49 3b 71 10     ; cmp 0x10(%r9), %rsi   avec r9 = 3
rbx = 0xaaaaaaaa      ; valeur de poison « non initialisé »
```

Le serveur de licences identifie la requête par identifiant de clé, pas par ces métadonnées : le
pont réduit donc le PSSH à ses `key_id` (`sanitize_pssh()` dans `cdm_bridge.c`) avant de le donner au
CDM. C'est transparent pour l'app, qui continue d'envoyer le PSSH de la playlist.

## Deux autres pièges, tous deux dus à Linux≠Windows

1. **Fautes `fs` prises pour des accès TLS.** Le gestionnaire restaurait la base `fs` dès que
   l'adresse fautive « ressemblait » à un décalage TLS. Une écriture dans la pile non commitée était
   donc réessayée indéfiniment, et le processus mourait sans diagnostic. Le test est maintenant :
   l'instruction porte-t-elle le préfixe `fs:` (`0x64`) ?

2. **Piles non commitées.** Le code du CDM soustrait de grands cadres (16 Kio mesurés) de `rsp` puis
   écrit loin en dessous : Linux agrandit la pile pour cela, Windows non. Les threads du CDM ont
   maintenant 4 Mio de réserve, les 256 premiers Kio sont pré-touchés au démarrage du thread, et le
   binaire principal demande 32 Mio.

3. **`futex` par `syscall` direct.** Le CDM atteint le noyau par `syscall(SYS_futex, …)` pour ses
   verrous. Répondre `ENOSYS` transforme une attente réelle en « on t'a réveillé », et le code qui
   possède le verrou travaille alors sur une structure que personne n'a fini d'initialiser. C'est
   `WaitOnAddress`/`WakeByAddress` (résolus à l'exécution, tous les MinGW ne les exportent pas).


