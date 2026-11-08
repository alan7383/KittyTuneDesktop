# Tester les annonces de titres protégés

Depuis la racine du projet, sous Windows :

```powershell
.\gradlew.bat runProtectedTracksDemo
```

L’application réelle démarre en invité, avec un profil distinct dans
`build/protected-tracks-demo`. Les réglages, le compte et le cache du profil
habituel sont conservés. La carte d’annonce est réinitialisée à chaque lancement.

Les boutons de démonstration au-dessus du lecteur permettent de :

- **Démo : carte** : réafficher la carte sur l’accueil après l’avoir fermée ou activée.
- **Proposer l’activation** : afficher la notice proposant les titres protégés.
- **Indisponible** : afficher la notice d’échec de lecture.
- **Notification** : envoyer un vrai message `Toaster.show(...)` à la notice flottante.

Les boutons « Activer » et « En savoir plus » utilisent le comportement réel de
l’application. L’activation s’applique au profil de démonstration. Les contrôles
de démo n’apparaissent pas lors d’un lancement normal.

Pour régénérer les aperçus et vérifier les règles d’affichage :

```powershell
.\gradlew.bat headlessTest --tests '*ProtectedTracksNoticeTest' --tests '*ProtectedTracksCardsRenderTest'
```

Les images sont écrites dans `build/previews/protected-announcement.png` et
`build/previews/protected-notice.png`.
