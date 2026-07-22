# TODO — Lecture des pistes SoundCloud protégées par Widevine (CENC/DRM)

## Statut : EN STAND-BY (à faire plus tard)

Décision utilisateur : reporter. Le reste du port avance avec le moteur FFmpeg pour tout le
contenu clair (progressive + HLS non chiffré), qui couvre la grande majorité du catalogue.

## Contexte technique (capture réseau réelle fournie par l'utilisateur)

Les pistes CENC-only se lisent sur soundcloud.com SANS COMPTE, mais c'est le **CDM Widevine
du navigateur** qui déchiffre — pas le JS du site.

Flux observé :
1. `POST https://license.media-streaming.soundcloud.cloud/playback/widevine?license_token=<JWT>`
   - Body = challenge Widevine (~5833 octets, application/octet-stream) généré PAR le CDM
   - Headers : Origin/Referer https://soundcloud.com/
   - Le `license_token` (JWT) autorise la demande (pas besoin de compte)
   - Réponse = licence Widevine (clé de contenu chiffrée pour le CDM demandeur)
2. `GET https://playback.media-streaming.soundcloud.cloud/cenc/<id>/aac_160k/<uuid>/dataNNN.m4s`
   - Segments audio CENC (AES-128-CTR), signés CloudFront (expires/Policy/Signature/Key-Pair-Id)

Côté Android : `SoundCloudDrmCallback` (POST le challenge à /playback/widevine ou
/persistable/widevine) + `FrameworkMediaDrm` (CDM Widevine de l'OS Android). Le déchiffrement
est fait par le CDM, l'app ne transporte que la licence.

## Solution retenue pour plus tard : CEF + Widevine embarqué

Seule voie LÉGALE et fidèle (n'extrait aucun CDM piraté) : embarquer Chromium (JCEF/CEF avec
composant Widevine activé) et lire ces pistes via une page HTML5 EME — exactement comme le fait
le site. ~150 Mo de natives en plus. À câbler UNIQUEMENT pour les transcodings
`ctr/cbc-encrypted-hls` ; tout le reste passe par le moteur FFmpeg.

Étapes quand on reprendra :
1. Vérifier un binding JCEF avec Widevine dispo pour le JDK cible.
2. Mini-page EME (MediaSource + navigator.requestMediaKeySystemAccess('com.widevine.alpha'))
   qui pointe sur le manifest HLS CENC et le license server SoundCloud.
3. Router dans StreamResolver : si seul candidat = encrypted-hls → lecteur CEF, sinon FFmpeg.
4. Option offline (/persistable/widevine + keySetId) = hors scope desktop pour l'instant.

## En attendant (comportement actuel du port)

`StreamResolver.buildTranscodingCandidates(..., allowDrm = false)` écarte les candidats DRM.
Pour une piste DRM-only → fallback YouTube (NewPipe), comme le filet Android. Voir
[[kittytune-windows-port]].
