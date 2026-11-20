# SoundCloud Android 2026.11.02 — ce qu'il y a dedans, et ce qui vaut le coup pour KittyTune

> Analyse statique du bundle `com.soundcloud.android_2026.11.02-release-379060_1arch` (APKMirror),
> faite le 12 novembre 2026. Objectif : repérer les fonctions de l'app Android qui manquent à
> KittyTune Desktop, et trier ce qui est transposable de ce qui ne l'est pas.
>
> Verdict court : **trois choses valent vraiment le coup** (badges de milestone, gigs/concerts,
> top fans), **trois autres sont des « peut-être »** (Spotlight, buy module, accueil SDUI), et le
> reste est du paiement, de la pub ou du mobile-only qu'il ne faut pas reprendre.

---

## 0. Ce qui a été analysé, et comment

| | |
|---|---|
| Fichier | `...apkm` (bundle APKMirror) → `base.apk` 92,4 Mo + `split_config.arm64_v8a.apk` 8,3 Mo |
| Version | `2026.11.02-release`, versionCode `379060`, minSdk 29, targetSdk 37 |
| Code | 8 fichiers DEX (~80 Mo), **R8 full mode** : toutes les classes sont obfusquées (`a0/a`, `a1/b`…) |
| Chaînes | 332 924 chaînes uniques extraites des *string pools* DEX |
| Ressources | `AndroidManifest.xml` + `res/values/*` décodés avec apktool 3.0.3 |
| Fichiers embarqués | 4 schémas GraphQL (`Sdui*.graphql`) + ~10 JSON de mocks SDUI dans `assets/` |

L'obfuscation R8 fait que les **noms de classes ne sont pas exploitables** ; en revanche les
signatures Kotlin, les clés de ressources, les corps de requêtes GraphQL et les libellés d'UI sont
restés en clair. C'est là que se trouve toute l'information utile : **~90 opérations GraphQL** et
**16 types de sections SDUI** ont pu être reconstitués. Ce sont eux qui font l'inventaire
fonctionnel ci-dessous.

Fichiers extraits (git-ignorés) : `scratch/sc_apk/`.

---

## 1. Verdict en une page

| Fonction | Preuve dans l'APK | Effort | Intérêt desktop | Verdict |
|---|---|---|---|---|
| Badges de milestone sur les pistes | `TrackBadges` + 14 libellés `track_page_badge_*` | S | ★★★ | **À faire** |
| Gigs / concerts personnalisés | `ConcertsQuery`, `features/library/gigs`, `gig_recommendations_*` | S–M | ★★★ | **À faire** |
| Top Fans / First Fans | `TopFans` (allTime + firstPeriod, `totalPlays`) | S–M | ★★☆ | **À faire** |
| Spotlight (épingles de profil) | `Me { spotlightLimit }`, `/you/spotlight`, `pin_items_to_your_spotlight` | M | ★★☆ | Peut-être |
| Buy module + Artist Storefront | `BuyModule`, `CreateBuyModule`, `artist_storefront_*` | S (affichage) | ★★☆ | Peut-être |
| Accueil piloté par le serveur (SDUI) | `HomeSduiQuery`, 16 types de sections, JSON de mocks | L | ★★☆ | Peut-être |
| Vibes (filtres de Likes) | `VIBES_QUERY`, 10 `vibe_*`, `likes_vibes_upsell_hint` | M | ★★☆ | Peut-être |
| Gated tracks (follow-to-unlock) | `gatedtracks/api`, `FollowArtistGatedTrack` | S–M | ★☆☆ | Peut-être |
| Tipping / Fan Support | `tipping/*`, `creatorcommerce/*`, `AndroidTopSupporters` | XL | ★☆☆ | **Non** |
| Paid downloads (achat/vente) | `paiddownloads/*`, 4 mutations de checkout | XL | ★☆☆ | **Non** |
| Artist Stories (vidéo verticale) | `/v1/stories/app/view`, `artistshortcut` | L | ★☆☆ | **Non** |
| Amplify / Get Heard (crédits promo) | `get_heard_*`, `CreatorCredits` | — | ★☆☆ | **Non** (payant) |
| Widgets, Android Auto, Cast, pairing | `widget/*`, `automotive/*`, `cast/*`, `data/pairingcodes` | — | ☆☆☆ | **Non** |
| Recap annuel | `yearlyplayback/*` (`yearlyFanPlayback2025`) | — | — | Déjà là (`RecapsScreen`) |
| « Liked by your crew » | `RelatedLikersForTracks`, `home-liked-by-your-crew` | — | — | Déjà là (`SocialProofRepository`) |
| Repost avec légende | `postwithcaptions` | — | — | Déjà là |
| « Behind This Track » | `trackpage/renderers/BehindThisTrackRenderer` | — | — | Déjà là (onglet playlists) |

---

## 2. Les trois qui valent le coup

### 2.1 Badges de milestone sur la page piste

SoundCloud a gamifié la page piste : chaque piste affiche des badges débloqués (ou verrouillés,
avec le critère) selon ses statistiques réelles. **14 badges** sont définis dans les ressources :

| Badge | Critère |
|---|---|
| First Fans 10 / 100 / 500 / 1000 | X auditeurs uniques ont écouté la piste |
| On Repeat 10 / 100 / 500 / 1000 | X auditeurs uniques l'ont écoutée en boucle |
| Talked about 10 / 100 | X fans uniques ont commenté |
| Buzzing | ajoutée à la playlist *Buzzing* |
| Charted | entrée dans les charts |
| Taking Off | recommandée par SoundCloud |
| GOATED | n°1 des charts |

La requête est minuscule et renvoie à la fois l'icône débloquée et l'icône verrouillée :

```graphql
query TrackBadges($trackUrn: ID!) {
  trackBadges(trackUrn: $trackUrn) {
    earnedAt
    template { icon lockedIcon templateName }
    trackPlaylist { urn title permalinkUrl }
  }
}
```

**Pourquoi c'est le meilleur candidat :** une seule requête, un rendu purement visuel (une rangée
de pastilles sous l'en-tête de piste), zéro logique métier, et ça colle exactement à l'ADN
« collectibles » de KittyTune (Music Passport, Ivy). C'est aussi le genre de détail qui donne
l'impression que le client desktop est *à jour* avec l'app officielle.

**Où l'accrocher :** `ui/track/TrackDetailScreen.kt`, sous `TrackDetailHeader` (ligne ~114) ;
requête dans `data/network/` à côté de `FollowingFeedGraphQl.kt`, endpoint
`https://graph.soundcloud.com/graphql` déjà utilisé par `SoundCloudApi`.

**Effort : S** (une demi-journée avec le cache et les états vides).

---

### 2.2 Gigs / concerts personnalisés

L'app a un module `features/library/gigs` complet : une rangée « **Gigs near you (🌥️ Clouders
only)** » dans la Library, alimentée par des concerts triés par `similarityScore`, avec bouton
« Start station » pour lancer une station de l'artiste du concert.

```graphql
query ConcertsQuery {
  concerts {
    urn
    url
    time
    similarityScore
    venue { name city }
    artist { urn username avatarUrl stationUrns }
  }
}
```

**Pourquoi pour un client desktop :** c'est le genre de fonction « sortir de l'app » que le desktop
fait *mieux* que le téléphone (ouvrir le lien du billet dans le navigateur, copier l'adresse, poser
un rappel). Aucun équivalent chez KittyTune aujourd'hui, et le coût est faible : une requête + une
rangée + un `openUrl`.

**Effort : S–M**, selon qu'on ajoute ou non le « Start station » (KittyTune sait déjà reconstruire
des stations à partir d'URN d'artiste — `DownloadManager` gère `artist-stations`).

**Réserve :** la fonction est marquée « Clouders only » côté app, donc probablement réservée aux
abonnés Go+. À vérifier en live ; si l'API renvoie une liste vide pour un compte gratuit, il suffit
d'afficher la rangée quand elle est non vide.

---

### 2.3 Top Fans / First Fans

Deux notions distinctes, une seule requête :

- **Top Fans** — classement des auditeurs d'une piste (`allTime`), avec le nombre de lectures de
  chacun (`totalPlays`) et le rang.
- **First Fans** — les auditeurs de la **première semaine** (`firstPeriod`, `firstPeriodDays`),
  que l'artiste peut épingler et partager. C'est le cœur du produit « donne ta piste à tes 100
  premiers fans ».

```graphql
query TopFans($input: TopFansInput!, $trackUrn: ID!) {
  topFans(input: $input) {
    sessionUserIsArtist
    isWithinFirstPeriod
    hasArtistOptedOut
    firstPeriodDays
    allTime {
      showFanPlaceholder
      showArtistPlaceholder
      fans {
        __typename
        totalPlays
        fan {
          allowedInteractions(trackUrn: $trackUrn) { canRemove }
          avatarUrlTemplate
          username
          verified
          urn
        }
      }
    }
    firstPeriod { /* même forme */ }
  }
}
```

**Pourquoi :** c'est de la donnée sociale et du « qui écoute vraiment », pas juste un compteur de
likes. Sur desktop, ça se place très bien en onglet supplémentaire de `TrackDetailScreen` (à côté
de likers/reposters/playlists), et pour un compte artiste ça devient un petit tableau de bord.

**Effort : S–M.** Les champs `showFanPlaceholder` / `showArtistPlaceholder` / `hasArtistOptedOut`
sont déjà prévus pour les états vides et le opt-out : le rendu est guidé par l'API.

**Réserve :** `TopFansInput` n'apparaît pas en clair dans l'APK (probablement construit côté code).
À confirmer en live ; au pire on retombe sur les champs les plus simples (`trackUrn` seul).

---

## 3. Les « peut-être »

### 3.1 Spotlight

Fonction **Artist Pro** : l'artiste épingle une sélection de pistes/playlists en haut de son profil
(section « Spotlight », sous-titre « Artist picks »), dans une limite donnée
(`Me { spotlightLimit }`, erreur `too_many_items_error_message` quand c'est plein). Édition via
`/you/spotlight` ; lecture publique dans le profil.

- **À faire :** afficher la section sur `ui/profile/ProfileScreen.kt` (lecture seule). Petit et
  joli, ça enrichit la page artiste.
- **À ne pas faire tout de suite :** l'éditeur (drag & drop de la sélection), qui n'a de sens que
  pour les comptes Artist Pro et demande la limite + la gestion d'erreur.

**Effort : S (lecture) / M (édition).**

### 3.2 Buy module + Artist Storefront

Deux objets distincts, tous deux « artiste vend quelque chose » :

- `BuyModule(urn)` sur une piste : `{ type, title, link, description, linkTitle, imageUrl, price }`,
  affiché en module sur la page piste et en bouton « Buy » dans le player.
- **Artist Storefront** : une bannière de boutique sur le profil (titre, description, image, lien,
  prix), éditable, réservée aux comptes payants.

**Verdict :** afficher le module et ouvrir le lien (checkout web) est légitime et peu coûteux.
Tout ce qui touche au **paiement in-app** (checkout, contributions, taxes, KYC marchand) est hors
scope — voir §4.

**Effort : S (affichage) / M (édition du storefront).**

### 3.3 Accueil piloté par le serveur (SDUI)

C'est la vraie nouveauté architecturale de cette version : l'accueil, le catalogue et la recherche
ne sont plus des écrans codés en dur mais des **sections rendues depuis le serveur**
(`HomeSduiQuery`, `CatalogSduiQuery`, `SearchSduiQuery`). 16 types de sections sont définis dans
`SduiFragments.graphql` : Carousel, UpdateCarousel, RepostCarousel, Banner, ContentWall, Shortcuts,
Pair, ImageBanner, PromoCarousel, Single, ListPreview, Placeholder, Suggestions, Gallery,
HorizontalMenu (+ Base).

Les identifiants de sections visibles dans les chaînes révèlent les rangées réellement servies :

```
soundcloud:sections:home-artist-uploads      nouveautés des artistes suivis
soundcloud:sections:home-get-back            « reprendre où tu t'es arrêté »
soundcloud:sections:home-hot-for-you         trending personnalisé
soundcloud:sections:home-liked-by-your-crew  liké par les gens que tu suis
soundcloud:sections:home-trending-by-genre   trending par genre
soundcloud:sections:offline_downloads        rangée offline
soundcloud:sections:ads_banner               pub (à ignorer)
```

**Pourquoi c'est intéressant :** ça explique *pourquoi* l'accueil officiel change tout le temps, et
ça donne une feuille de route toute faite pour des rangées que KittyTune n'a pas
(« Hot for you », « Trending by genre », « Get back »). `homeSdui` renvoie aussi
`trackingInfo { sessionUrn, impression }` et une pagination par curseur.

**Pourquoi je ne le mets pas en tier 1 :** adopter le format complet, c'est écrire un moteur de
rendu de sections polymorphes (16 types, actions polymorphes) et refaire l'accueil. La version
raisonnable est de **piocher 3–4 types** (Carousel, ListPreview, ContentWall, Shortcuts) et de les
mapper sur les composants existants de `ui/main/HomeContent.kt`.

**Effort : L (complet) / M (sélectif).**

### 3.4 Vibes — filtres de mood sur les Likes

L'écran Likes de l'app peut filtrer les titres likés par « vibe », calculée côté serveur :
`Chill, Dancy, Electronic, Euphoric, Happy, HipHop, Majestic, Pop, R&B, Rock`. C'est un argument
d'upsell (`likes_vibes_upsell_hint` = « Filters are only available to subscribers »), et la requête
`VIBES_QUERY` + `/search/vibes` alimentent les collections.

**Pourquoi c'est intéressant pour KittyTune :** ils n'ont pas besoin du paywall — ils ont déjà le
moteur audio, les features DSP et l'IA locale. Recalculer des « vibes » **localement** sur la
bibliothèque (tempo/énergie/valence + clustering) donnerait une fonction *supérieure* à celle de
SoundCloud, sans dépendance API.

**Effort : M** (feature extraction + clustering + UI de filtres). KittyTune a déjà des mixes
`mix_vibe_*` — il s'agirait d'ajouter la **filtrabilité** de la bibliothèque, pas un nouveau moteur.

### 3.5 Gated tracks (follow-to-unlock)

Une piste peut être « gatée » : il faut suivre l'artiste (et autoriser le partage) pour l'écouter.
L'app gère l'état (`FollowArtistGatedTrack`, `GatedTracksBottomSheet`, `TrackShareAccess`) au lieu
d'afficher une erreur.

**Intérêt :** si KittyTune tombe aujourd'hui sur un `401/403` de stream pour ces pistes, un message
« suis l'artiste pour débloquer » + un bouton Follow transformerait un bug en fonction.
**Effort : S–M**, à condition de reproduire le cas (compte de test + piste gatée).

---

## 4. Ce qu'il ne faut pas reprendre

**Tout le bloc monétisation fan → artiste.** Tipping/Fan Support (contribution directe, slider de
montant, leaderboard, « support story », thank-you messages), Paid Downloads (checkout, contribution,
TVA, formats original/standard, remboursements), Amplify/Get Heard (crédits de promotion), Merchant
(payouts, onboarding marchand). C'est un demi-produit à lui seul : KYC, fiscalité, conformité,
support. KittyTune est un client, pas un PSP. **Exception acceptable :** afficher en lecture seule
le module d'achat et le classement des top supporters (données publiques), et renvoyer vers le web.

**Artist Stories.** Stories verticales sur le profil artiste (`/v1/stories/app/view`,
`artist_shortcuts`), avec lecteur plein écran et progression. C'est du mobile-first (format 9:16,
snap/swipe) ; sur desktop, ça se réduirait à une rangée de vignettes cliquables pour ouvrir un
navigateur — beaucoup de travail pour peu d'usage.

**Tout le reste du mobile-only.** Widgets Glance, Android Auto + pairing code, Chromecast, MoEngage/
Firebase, Statsig, dev drawer, voice search, ads (bannières, pre-roll, `ads/play`, `ads/display`),
consentement CCPA/DSA. Rien de tout ça n'a de sens sur un client JVM.

**Ce que KittyTune fait déjà (ne pas « rajouter »).** Recap annuel (`RecapsScreen`,
`RecapStoryScreen`), « liké par les gens que tu suis » (`SocialProofRepository` +
`RelatedLikersForTracks`), repost avec légende, « Behind This Track » (onglet playlists),
sélection du meilleur transcoding AAC 256 kbps (`StreamResolver`).

---

## 5. Détails qui peuvent servir tout de suite

**HD audio.** L'app affiche un badge « HD audio » quand le flux actif est **AAC HQ 256 kbps**
(`player_stream_hd_audio_badge`, `aac_256k`). KittyTune choisit déjà `aac_256k` en premier
(`StreamResolver.kt:993`) mais n'affiche rien. Un badge « HQ/HD » à côté du codec dans le player et
la page piste, c'est ~30 lignes et ça rend la qualité visible. C'est le meilleur ratio
effort/effet de toute l'analyse.

**Limite d'appareils Go+.** L'app applique désormais **3 appareils connectés** et **10 appareils
avec téléchargements** (`device_management_limit_active`, `device_management_limit_registered`).
Comme KittyTune se connecte au même compte, un utilisateur Go+ peut se retrouver déconnecté d'un
autre appareil en se connectant sur le desktop. À documenter (et éventuellement à détecter dans le
message d'erreur OAuth) — sinon on aura des rapports de bug incompréhensibles.

**Provenance dans la file d'attente.** L'app étiquette chaque entrée de la file (`From your feed`,
`From comments`, `From artist station based on %s`, `Added by you`, `Casting`). KittyTune a une file
riche mais muette sur l'origine. Petit plus de finition.

**Suiveurs mutuels.** `fetchMutuals` existe côté GraphQL ; un badge « vous vous suivez » sur les
listes de followers/following est peu coûteux.

**Insights de piste pour artistes.** `TrackPageTrackInsights` renvoie `repeatListeners` et
`topAlgorithmsByWindowV2 { algorithmType count }` — donc « d'où viennent les écoutes » (algorithmes
SoundCloud). Pour les comptes artistes, c'est un vrai argument desktop (dashboard), mais ça suppose
un écran dédié ; à garder pour plus tard.

---

## 6. Annexe — opérations GraphQL embarquées (~90)

`activeSubscription`, `AllTracks`, `AndroidDownloadPurchase`,
`AndroidFanMonetizationEligibilityStatus`, `AndroidFanSupportPreferences`,
`AndroidPaidDownloadCollection`, `AndroidPaidDownloadListing`,
`AndroidPaidDownloadListingsForCreator`, `AndroidPaidDownloadPurchase`,
`AndroidPaidDownloadRecommendations`, `AndroidStartPaidDownloadCheckout`,
`AndroidStartWebHostedCheckout`, `AndroidSyncPaidDownloadCheckout`,
`AndroidSyncWebHostedCheckoutStatus`, `AndroidTippingConfig`, `AndroidTopSupporters`,
`AndroidUpdateFanSupportPreferences`, `Artists`, `BuyModule`, `CommentsForWaveform`,
`CommentsWithReactions`, `ConcertsQuery`, `CreateBuyModule`, `CreateTrack`, `CreateTranscoding`,
`CreatorCredits`, `DeleteBuyModule`, `deleteTrack`, `DiscoverFeed`, `EditTrack`, `EnableFirstFans`,
`ExternalLikedTracks`, `ExternalPlaylists`, `FeaturedFansOptOutStatus`, `FeaturedPlaylists`,
`fetchMutuals`, `FetchMyTracks`, `FollowAndAuthorize`, `FollowingFeed`, `GenresQuery`,
`GooglePlayProductGraph`, `InteractionCountsByParent`, `LikesSyncStatus`, `Me`, `Merchant`,
`MerchantOnboardingLink`, `myActivities`, `MyFollowersQuery`, `MyFollowingsQuery`,
`MyLikesCollectionsQuery`, `MyNotificationSetting`, `MyTracks`, `NotifyForTarget`,
`PaginatedReactionUsers`, `PaginatedTargetInteractions`, `PaginatedTracks`,
`PaidDownloadStatusForTracks`, `Payout`, `PayoutHistory`, `PlaylistSyncStatus`, `PopularComments`,
`ReactionsPerTarget`, `RelatedLikersForTracks`, `RemoveAsATopFan`, `RemoveInteraction`,
`RemoveRepostReaction`, `ResolvePermalink`, `StartGooglePlayCheckout`, `StartLikesSync`,
`StartPlaylistSync`, `TopFans`, `TrackBadges`, `TrackCommentReplies`, `TrackPageTrackInsights`,
`TrackPermalinkAvailability`, `Tracks`, `TrackShares`, `TranscodingStatus`,
`UpdateFeaturedFansArtistOptOutStatus`, `UpdateFeaturedFansFanOptOutStatus`,
`UpdateGooglePlayCheckout`, `UpdateSetting`, `UploadQuotaQuery`, `UpsellsQuery`, `UpsertInteraction`,
`UpsertRepostReaction`, `UserFollowersQuery`, `UserFollowingsQuery`, `UserTracks`,
`YearlyPlayback`, `YourSc2024`.

## 7. Annexe — types de sections SDUI

Carousel, UpdateCarousel, RepostCarousel, Banner, ContentWall, Shortcuts, Pair, ImageBanner,
PromoCarousel, Single, ListPreview, Placeholder, Suggestions, Gallery, HorizontalMenu.
Actions polymorphes : `HeaderAction`, `ShortcutAction`, `FilterAction`, `BannerAction`,
`ImageBannerAction`, `UpdateCarouselAction`. Opérations : `LinkOperation`, `SduiNavigateOperation`,
`ReplaceOperation`. CTA : Play, Like, Dislike, Follow, Share.

---

## 8. Limites de cette analyse

- **Pas d'appel réseau.** Toutes les requêtes citées ont été lues dans l'APK ; je n'ai pas vérifié
  qu'elles répondent avec le `client_id` / le token OAuth de KittyTune. `TrackBadges` et
  `ConcertsQuery` sont les plus sûrs (données publiques), `TopFans` et `Spotlight` demandent un
  test avec un vrai compte.
- **Pas de logique.** R8 oblige : on voit les requêtes, les libellés et les noms de modules, pas les
  algorithmes. Les « vibes » et le matching concerts sont donc des boîtes noires côté serveur.
- **Une seule version.** Ces fonctions peuvent être des A/B tests (Statsig) ; leur présence dans
  l'APK ne garantit pas qu'elles soient actives pour tous les comptes.
- **CGU.** Ces endpoints sont ceux de l'app officielle. KittyTune les utilise déjà
  (`graph.soundcloud.com/graphql`), donc le risque n'est pas nouveau, mais il augmente avec le
  nombre de requêtes « app-only ».
