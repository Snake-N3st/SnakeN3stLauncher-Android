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

## Préparation de Java 21

```bash
./scripts/prepare-jre21.sh
```

Le script récupère les archives de la [release Amethyst Java 21](https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/tag/download_jre21), vérifie leurs SHA-256 et utilise le repacker officiel épinglé au commit `4527b5a73dcf3f890b45eab4c6a91651ea28a5ea`, lui-même vérifié par son empreinte. La recompression tourne à priorité minimale. La CI utilise la même préparation.

Les assets générés `app_pojavlauncher/src/main/assets/components/jre-21/` sont ignorés par Git. Le script remplace les assets embarqués Java 8/17/25, sans modifier les runtimes déjà installés sur les appareils. `SN3_JRE_DOWNLOAD_DIR` permet de réutiliser les quatre archives locales ; leur vérification reste obligatoire.

Une nouvelle installation utilise `Internal-21`. À la première migration, l’ancien choix global `Internal` passe à `Internal-21`. Les sélections suivantes restent libres et les profils qui ciblent explicitement un Java 8 installé sont conservés ; aucun runtime utilisateur n’est renommé en Java 21.

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
