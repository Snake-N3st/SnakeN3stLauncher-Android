# Développement Android

Ce guide complète le [README](../README.md) avec la configuration des builds, la publication et les vérifications sur appareil.

## Configuration de compilation

Toutes les compilations passent par `./scripts/build-sn3.sh`, y compris les tests et les builds release. Le script impose `nice -n 19`, `org.gradle.priority=low`, une priorité disque inactive si disponible, deux workers et un daemon non réutilisé.

| Propriété Gradle | Valeur par défaut | Usage |
| --- | --- | --- |
| `sn3.clientId` | vide | Client Azuriom enregistré pour Android ; nécessaire à la connexion. |
| `sn3.baseUrl` | `https://snake-n3st.fr` | Origine HTTPS, sans chemin, paramètres ni identifiants. |
| `sn3.modpackRootKeys` | vide | Clés publiques racines Ed25519 : 64 caractères hexadécimaux chacune, séparées par des virgules. |
| `sn3.versionCode` | `10000001` | Code numérique Android ; doit augmenter à chaque publication. |
| `sn3.versionName` | `0.2.0` | Nom de version affiché. |

Le `client_id` est défini à la compilation et ne peut pas être changé depuis l’application. Aucun identifiant de test n’est enregistré dans les sources.

Sans racines configurées, la vérification des signatures des manifestes est désactivée. Les empreintes des fichiers téléchargés restent vérifiées. Les builds distribués doivent fournir les racines publiques du site ; ne pas les récupérer automatiquement depuis le serveur que l’on cherche à vérifier.

## Runtimes Java à la demande

Aucun runtime Java n’est embarqué dans l’APK. Il n’y a pas d’étape de préparation de JRE avant la compilation ; les anciens assets de runtimes sont exclus du packaging même lorsqu’ils subsistent dans un checkout local.

Le démarrage du launcher et la connexion au compte ne nécessitent pas de JRE Minecraft installé. Lors de l’installation d’un pack, le launcher réutilise un runtime compatible ou télécharge celui nécessaire au jeu. La version demandée par les métadonnées du jeu, y compris celles héritées par un profil Fabric, détermine le runtime nécessaire. Les installateurs de loaders disposent également de ce chemin d’installation.

Les runtimes et les choix explicites existants sont conservés. Une installation complète reste lançable hors connexion ; un runtime absent doit être installé en ligne. Les données de l’application contiennent les runtimes extraits utilisés par le jeu, sans copie d’archive intégrée dans le nouvel APK.

## Priorité des réglages JVM

Les préférences Java Amethyst fournissent les valeurs globales par défaut. Des exceptions indépendantes, activées explicitement par modpack, peuvent remplacer la mémoire et les arguments. Les anciens fichiers de préférences conservent leur allocation ; leurs arguments non vides sont conservés comme exceptions. Les nouveaux packs suivent les réglages globaux.

La résolution utilise les valeurs globales actuelles à chaque lancement, en ligne comme hors connexion. Des arguments propres vides remplacent réellement les arguments globaux non vides. Les exceptions d’un pack ne modifient pas les préférences globales et préservent son canal et son choix de runtime. Le champ JVM natif reste éditable pour les profils Amethyst ordinaires ; pour les profils Snake N3st, la gestion du modpack constitue le point d’édition.

## Session et données locales

La clé de session est chiffrée avec Android Keystore, complétée par le PIN lorsqu’il est activé. La sauvegarde Android est désactivée. Le jeton du jeu est transmis en mémoire ; il ne doit jamais être enregistré dans un profil ni apparaître dans les journaux du launcher.

Le catalogue et les métadonnées de lancement sont conservés séparément de la clé de session. Les images mises en cache ne contiennent aucun jeton : les avatars sont isolés par site et par compte, et les logos des modpacks utilisent le format natif Amethyst. Une erreur réseau ne doit ni effacer la session ni faire passer artificiellement tout le catalogue en « Retiré du site ».

## Publication des versions et mises à jour

Le build accepte `-Psn3.versionCode=10000001` et `-Psn3.versionName=0.2.0`
(valeurs par défaut). `versionCode` doit être un entier strict entre `1` et
`2100000000`, sans espace ni signe, et **augmenter à chaque nouvelle version
publiée** ; Android utilise ce code numérique pour autoriser la mise à jour.
Le nom est une étiquette ASCII de 1 à 80 caractères, par exemple `0.2.0-rc1`.

Le workflow `.github/workflows/android-release.yml` s’exécute sur les tags
`v*`. Il construit les variantes debug et production, puis publie leurs APK et
`android-update.json` comme assets GitHub Release. Il faut configurer avant
publication :

- Variable de dépôt `SN3_CLIENT_ID` et, selon le site, `SN3_BASE_URL` /
  `SN3_MODPACK_ROOT_KEYS`.
- Secrets `SN3_RELEASE_KEYSTORE_BASE64`, `SN3_RELEASE_KEYSTORE_PASSWORD` et
  `SN3_RELEASE_KEY_ALIAS`. Le keystore est décodé dans un fichier temporaire
  privé et supprimé en fin de workflow ; aucun secret n’est affiché.
- Variable facultative `SN3_VERSION_CODE` pour imposer le code de la version.
  Sinon le workflow prend `10000000 + GITHUB_RUN_NUMBER`. Vérifier que cette
  valeur dépasse les codes de toutes les versions déjà distribuées. Le nom
  de version vient du tag sans son préfixe `v`.

La production refuse de se construire sans sa configuration de signature.
Pour une compilation locale `assembleRelease`, fournir les variables
`SN3_RELEASE_KEYSTORE_PATH`, `SN3_RELEASE_KEYSTORE_PASSWORD` et
`SN3_RELEASE_KEY_ALIAS` ; aucun chemin de clé ni mot de passe n’est inscrit dans
les sources. La configuration Google Play amont reste séparée.

`scripts/generate-android-update.py` lit `aapt dump badging` sur les APK réels,
puis calcule leur SHA-256 et leur taille. Il ne suppose pas que les versions
passées à Gradle sont celles des binaires produits. Il émet le schéma `1`, avec
une entrée par `applicationId` contenant `versionCode`, `versionName`, `apkUrl`,
`sha256`, `size` et `minSdk` ; `--notes-file` permet d’ajouter des notes UTF-8
facultatives. Les liens APK restent confinés aux assets de release du dépôt
`Snake-N3st/SnakeN3stLauncher-Android`.

La variante `.debug` et la production ont des identifiants distincts. Une mise à
jour doit conserver l’identifiant **et la signature de l’application installée** :
un APK signé avec une autre clé ne remplace pas une installation existante.
Le manifeste ne rend disponibles que les versions effectivement publiées dans
GitHub Releases ; un build local ou un artifact Actions seul n’est pas une
release de mise à jour. L’installation d’un APK reste soumise à la confirmation
Android et, si nécessaire, à l’autorisation d’installer des applications depuis
cette source. Il n’y a pas d’installation silencieuse.

## Tests sur appareil

Fermer toute partie avant d’installer un APK de test ou de lancer l’instrumentation : celle-ci interrompt le processus du launcher.

### Compilation et installation du runner

```bash
./scripts/build-sn3.sh :sn3-core:test \
  :app_pojavlauncher:assembleDebug \
  :app_pojavlauncher:assembleDebugAndroidTest \
  -Psn3.clientId=VOTRE_CLIENT_ID

adb install -r app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk
adb install -r app_pojavlauncher/build/outputs/apk/androidTest/debug/app_pojavlauncher-debug-androidTest.apk
```

Si plusieurs appareils sont connectés, utilisez `adb -s ADRESSE_OU_SERIE` à la place de `adb`.

### Cache des icônes

```bash
adb shell am instrument -w -e iconCache true \
  mc.snakenest.launcher.android.debug.test/mc.snakenest.launcher.android.snaken3st.UpdateVerifierInstrumentation
```

Le test utilise une identité factice pour vérifier l’écriture et la lecture sur disque, l’isolation par compte et site et le repli en cas d’image corrompue. Il lit aussi les icônes natives déjà disponibles. Résultat attendu : `success=true`, `checks=4`, `httpRequests=0`. `nativePackIcons` dépend des images présentes sur l’appareil.

### Préparation hors connexion

```bash
adb shell am instrument -w -e offline true \
  mc.snakenest.launcher.android.debug.test/mc.snakenest.launcher.android.snaken3st.UpdateVerifierInstrumentation
```

Ce contrôle utilise une session et un modpack déjà installés. Il vérifie la préparation locale, la conservation de la clé et la présence d’un pack retiré du catalogue, sans lancer Minecraft ni télécharger ses fichiers. Le réseau est refusé pour le processus de test ; les métadonnées et préférences temporairement modifiées sont restaurées. Si un PIN est activé ou si les prérequis locaux manquent, le runner indique `blocked=true` au lieu de les contourner.

### Vérification des APK de mise à jour

Le runner `UpdateVerifierInstrumentation` teste le vérificateur réel du système sur quatre petits APK : version supérieure avec la bonne signature, version identique, autre package et autre signature. Il n’installe aucun APK factice.

Compiler l’application et le runner à priorité minimale avec `./scripts/build-sn3.sh :sn3-core:test :app_pojavlauncher:assembleDebug :app_pojavlauncher:assembleDebugAndroidTest -Psn3.clientId=…`. Installer les deux APK debug sur l’appareil de test. Préparer les fixtures avec `scripts/prepare-update-verifier-fixtures.py --apk CHEMIN_APK_DEBUG --aapt CHEMIN_AAPT --apksigner CHEMIN_APKSIGNER --android-jar CHEMIN_ANDROID_JAR --output /tmp/sn3-update-verifier`. Copier ses quatre `.apk` dans le répertoire privé `cache/sn3-update-verifier` du launcher, puis lancer `adb shell am instrument -w mc.snakenest.launcher.android.debug.test/mc.snakenest.launcher.android.snaken3st.UpdateVerifierInstrumentation`. Le résultat attendu est quatre contrôles réussis. Ces essais interrompent le processus du launcher : fermer toute partie avant de les lancer.

## Vérifications JVM, runtimes et téléchargements

Le runner peut vérifier les priorités des réglages JVM et la sélection de Java avec des préférences et des fichiers isolés, sans modifier les runtimes installés :

```bash
adb shell am instrument -w -e jvmSettings true \
  mc.snakenest.launcher.android.debug.test/mc.snakenest.launcher.android.snaken3st.UpdateVerifierInstrumentation
```

Il vérifie aussi la planification sans HEAD ni doublon et les transferts atomiques avec de fausses connexions HTTP : réponses 404/500, flux tronqué, annulation et conservation du fichier précédent. Les tests du cœur couvrent le nombre maximal de workers et leur arrêt complet sur erreur ou annulation.

Pour rendre et vérifier l’écran sans compte avec un dépôt de session fictif, puis contrôler son retour à l’accueil :

```bash
adb shell am instrument -w -e login true \
  mc.snakenest.launcher.android.debug.test/mc.snakenest.launcher.android.snaken3st.UpdateVerifierInstrumentation
```

Ce test ne déconnecte pas le compte réel et ne lance pas de connexion distante. La capture de son écran est conservée dans le cache privé sous `sn3-login-check.png`.

La limite de transferts Minecraft est stockée dans la préférence `sn3.downloadThreads` (8 par défaut, bornée entre 2 et 16). Les requêtes HEAD de planification sont évitées, les destinations sont dédupliquées, et Java participe au même lot borné que les fichiers. Les métadonnées héritées sont résolues avant ce lot. Les transferts échoués ou annulés ne remplacent pas leur fichier précédent et les workers s’arrêtent avant que l’opération rende la main.

## Rapports de plantage Android

`ExitActivity` conserve son point d’entrée JNI et ouvre un écran complet dans le processus du launcher, indépendant de la JVM du jeu. Le rapport rassemble le contexte du dernier lancement, les rapports Minecraft et Java récents, et les journaux disponibles. Les gros fichiers sont présentés sous forme d’extraits identifiés. Les valeurs d’authentification reconnues sont masquées avant affichage, stockage et export ; aucun rapport n’est envoyé automatiquement.

Les dix derniers rapports sont conservés dans le stockage privé. **Paramètres → Dernier rapport de jeu** permet de rouvrir le plus récent. L’envoi demande une confirmation et une description facultative ; au-delà de la limite du serveur, il conserve le début et la fin du rapport. Le fichier exporté contient tous les extraits collectés.

```bash
adb shell am instrument -w -e crashReport true \
  mc.snakenest.launcher.android.debug.test/mc.snakenest.launcher.android.snaken3st.UpdateVerifierInstrumentation
```

Ce contrôle utilise uniquement des journaux synthétiques isolés : collecte, exclusion des anciens rapports, masquage, sauvegarde/relecture, rejet des chemins invalides, rendu de l’écran et accès FileProvider. Il ne lance pas Minecraft, ne lit pas les journaux réels et n’envoie aucun rapport. Résultat attendu : `success=true`, `checks=9`, `httpRequests=0`. Capture privée : `cache/sn3-report-check.png`.

## Catalogue et installation des mods

Le gestionnaire Android reprend le parcours PC : catalogue populaire ou recherche, fiche de projet, installation rapide et sélection de version. Les projets et versions compatibles apparaissent en premier ; les autres restent consultables et leur installation est désactivée. Les versions stables, bêta et alpha peuvent être filtrées. Les fichiers installés sont identifiés par SHA-1 via une requête groupée Modrinth, et les vérifications de mises à jour concernent seulement les projets installés parmi les résultats affichés.

Avant le téléchargement, le plan de dépendances transitives est figé et confirmé avec les versions et la taille prévue. Les dépendances actives compatibles sont réutilisées ; les versions imposées, conflits et cycles sont contrôlés. Les écritures restent temporaires puis atomiques après vérification des empreintes et de la blacklist. L’import utilise le sélecteur de documents Android ; il est borné à 512 Mio et valide les métadonnées Fabric, Quilt, Forge (y compris `mcmod.info`) ou NeoForge. La compatibilité d’un fichier importé reste à vérifier par son utilisateur. Le mode en ligne est nécessaire pour consulter la blacklist.

```bash
adb shell am instrument -w -e modBrowser true \
  mc.snakenest.launcher.android.debug.test/mc.snakenest.launcher.android.snaken3st.UpdateVerifierInstrumentation
adb shell am instrument -w -e newsUi true \
  mc.snakenest.launcher.android.debug.test/mc.snakenest.launcher.android.snaken3st.UpdateVerifierInstrumentation
```

Ces deux contrôles utilisent des dépôts, préférences et comptes fictifs isolés. Ils vérifient le catalogue, les fiches, les versions incompatibles et la navigation arrière, puis la barre Actualités, la restauration de l’en-tête et l’absence du raccourci tactile dans le profil. Aucun mod n’est installé et aucune connexion distante n’est autorisée. Captures dans le cache privé : `sn3-mods-catalogue-check.png`, `sn3-mods-detail-check.png`, `sn3-mods-versions-check.png` et `sn3-news-toolbar-check.png`.
