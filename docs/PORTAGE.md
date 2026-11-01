# Portage PC → Android

Le portage part de l’état local du launcher PC au 1er novembre 2026, y compris ses modifications non commitées. Les fichiers métier sont copiés dans ce dépôt ; aucun chemin vers le checkout PC n’est nécessaire pour construire Android.

| Domaine PC | Intégration Android |
| --- | --- |
| Swing et pages PC | `SnakeActivity`, interface Android native |
| `java.net.http.HttpClient` | `HttpURLConnection`, délais bornés, flux fermés, aucune redirection de requête signée |
| Clé de session sur disque | Android Keystore + AES-GCM, stockage interne `noBackupFilesDir` |
| PIN / `.cryptlist` | PBKDF2 et chiffrement du cœur ; restauration à la sortie normale et au retour dans le launcher après arrêt du jeu |
| Device flow / signatures | Même contrat challenge/poll/Ed25519 que le PC |
| Modpacks et canaux | Même API, règles de fusion, signatures et blacklist |
| FlowUpdater / OpenLauncherLib | Moteur de téléchargement et JVM Android d’Amethyst |
| Fichiers partagés | Bibliothèques, assets et versions gérés par Amethyst ; répertoires de jeu propres à chaque pack |
| Paramètres Java | Mémoire/arguments Snake N3st ; runtime, renderer et contrôles du profil Amethyst préservés |
| Mods | Modrinth avec dépendances compatibles ; activation/désactivation des ajouts personnels |
| Bootstrap et installateurs Windows/Linux | APK Android ; page Releases accessible depuis les paramètres. Pas d’installation d’un JAR PC ni de mise à jour APK silencieuse |
| Présence Discord par IPC desktop | Pas d’équivalent Android intégré |
| Rapports de bugs | Envoi volontaire du formulaire. Pas de télémétrie ni d’envoi automatique de logs |
| Watchdogs Swing | Sans objet sur le thread d’interface Android |

## Organisation

- `sn3-core` : logique Java 17 indépendante d’Android, tests JUnit 5.
- `app_pojavlauncher/.../snaken3st` : écrans, coffre, opérations en arrière-plan et raccordement au jeu.
- `Sn3GameBridge` : profil stable par slug, identité du joueur issue du site, arguments JVM transmis seulement au processus de jeu.
- Les opérations de mutation sont bloquées pendant une partie. Android utilise un seul processus de jeu Amethyst ; le multi-instance simultané du PC n’est pas reproduit.

## Validation fonctionnelle à effectuer avec le serveur

La compilation et les tests unitaires ne garantissent pas la compatibilité des mods d’un pack avec les pilotes Android. À vérifier sur appareil avec un client Azuriom compilé dans l’APK :

1. Connexion dans le navigateur, retour dans le launcher, restauration de session et révocation.
2. Installation d’un pack réel, démarrage avec le mod d’authentification Snake N3st, nom de joueur et token reconnus par le serveur.
3. Canaux publics/restreints, mise à jour, version épinglée, réparation, annulation puis reprise.
4. PIN incorrect/correct, redémarrage, fichiers `.cryptlist`, arrêt normal et arrêt forcé.
5. Dépendances Modrinth, mise à jour d’un ajout, mod blacklisté et fichier officiel non remplaçable.
6. Fabric, Forge et NeoForge sur les versions réellement utilisées par le serveur.

Les clés racines publiques doivent être fournies à la compilation pour activer la vérification des signatures. Ne jamais récupérer automatiquement une racine depuis le serveur que l’on cherche à vérifier.
