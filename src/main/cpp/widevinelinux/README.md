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
| Le protocole sidecar (`RESET_SESSION`) répond | ✅ |
| `CreateSessionAndGenerateRequest` avec un PSSH SoundCloud | ❌ plante (voir plus bas) |

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

```powershell
# MinGW-w64 (g++ 16), depuis n'importe quel shell
powershell -ExecutionPolicy Bypass -File scripts\build-native-cdm.ps1
```

Le script construit `build/widevinelinux/probe.exe` (harnais du chargeur) et
`build/widevinelinux/ktcdm.exe` (le démon).

```powershell
build\widevinelinux\ktcdm.exe <chemin>\libwidevinecdm.so --probe   # -> ok, code 0
```

Puis, côté application, le démon se branche comme n'importe quel sidecar :

```powershell
$env:KITTYTUNE_CDM_SIDECAR = 'C:\...\build\widevinelinux\ktcdm.exe C:\...\libwidevinecdm.so'
```

Variables utiles : `KITTYTUNE_WIDEVINE_DEBUG=1` (trace des rappels de l'hôte), `KT_LOG=1` (trace de
chaque appel du shim), `KT_TRACE=0` (coupe la trace), `KT_NOLOG=1` (le CDM n'écrit plus ses journaux),
`KT_SKIP_INIT=<masque hexa>` (saute des entrées de `init_array`).

## Le blocage actuel

`CreateSessionAndGenerateRequest` (101 octets de PSSH SoundCloud, session `kTemporary`, init `kCenc`)
va loin — le CDM alloue ses pools, tire de l'aléa, construit sa requête — puis meurt sur :

```
exception 0xc0000005 at cdm+0x101b686, fault address 0x13
code: 49 3b 71 10     ; cmp 0x10(%r9), %rsi   avec r9 = 3
rbx = 0xaaaaaaaa      ; valeur de remplissage « non initialisé »
```

Un pointeur non initialisé (`0xaaaaaaaa`, la valeur de poison que le CDM pose sur ses locales) est
déréférencé. Ce n'est donc plus une question de chargement : quelque chose, en amont, rend une
valeur que le CDM n'attendait pas, et il l'utilise comme adresse.

Deux corrections ont déjà été nécessaires dans cette étape, et sont en place :

1. **Fautes `fs` prises pour des accès TLS.** Le gestionnaire restaurait la base `fs` dès que
   l'adresse fautive « ressemblait » à un décalage TLS. Une écriture dans la pile non commitée
   (adresse `0xffff…` ou petite) était donc réessayée indéfiniment, et le processus mourait sans
   qu'aucun diagnostic ne s'affiche. Le test est maintenant : l'instruction porte-t-elle le préfixe
   `fs:` (`0x64`) ? Rien d'autre n'est une lecture TLS.

2. **Piles non commitées.** Le code du CDM soustrait de grands cadres (16 Kio mesurés) de `rsp` puis
   écrit loin en dessous : Linux agrandit la pile pour cela, Windows non — une écriture sous la page
   de garde est une faute. Les threads du CDM ont maintenant 4 Mio de réserve et les 256 premiers Kio
   sont pré-touchés au démarrage du thread, ce qui les rend commités.

Pistes pour la suite, dans l'ordre :

1. Trouver quel import rend une valeur inattendue : `clock_gettime` (`urandom`/seed), `uname`,
   `getpid`, `sysconf`, ou le bloc de `getrandom`. Comparer avec ce que le CDM fait d'un PSSH
   synthétique minimal (une clé de 16 octets, système Widevine) pour distinguer « parseur » de
   « construction de requête ».
2. Vérifier `Host::Allocate` : le CDM demande parfois des tailles surprenantes ; un tampon trop petit
   ou non remis à zéro produirait exactement ce genre de poison.
3. Instrumenter la valeur `0xaaaaaaaa` : `KT_LOG=1` trace chaque appel du shim, et le ring de trace
   est vidé au moment de la faute.

