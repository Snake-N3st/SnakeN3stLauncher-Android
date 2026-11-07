# Snake N3st Launcher — Android

Fork Android d’[Amethyst](https://github.com/AngelAuraMC/Amethyst-Android), avec les services du launcher Snake N3st et l’interface Amethyst adaptée à Snake N3st. Le moteur Minecraft, les rendus graphiques, les runtimes et les commandes tactiles restent ceux d’Amethyst.

Android 8.0 / API 26 minimum. L’application utilise `mc.snakenest.launcher.android` (`.debug` pour les builds de test), ce qui permet de conserver Amethyst installé à côté.

## Compiler

Prérequis : JDK 21, SDK Android 37, NDK 27.3.13750724 et les sous-modules Git. Gradle installe les composants SDK manquants si leurs licences sont déjà acceptées. Le sous-module androidnsbypass peut aussi demander son propre NDK.

```bash
git clone --recursive git@github.com:Snake-N3st/SnakeN3stLauncher-Android.git
cd SnakeN3stLauncher-Android
export ANDROID_HOME="$HOME/Android/Sdk"
./scripts/build-sn3.sh :app_pojavlauncher:assembleDebug -Psn3.clientId=VOTRE_CLIENT_ID
```

Le `client_id` est **défini à la compilation** et ne peut pas être changé dans l’application. Aucun identifiant de test n’est enregistré dans les sources.

Paramètres de compilation :

| Propriété | Valeur par défaut | Usage |
| --- | --- | --- |
| `sn3.clientId` | vide | Client Azuriom enregistré pour Android ; nécessaire pour se connecter |
| `sn3.baseUrl` | `https://snake-n3st.fr` | Origine HTTPS du site |
| `sn3.modpackRootKeys` | vide | Clés publiques racines Ed25519, 64 caractères hexadécimaux, séparées par des virgules |

Comme sur PC, sans racines configurées la vérification des signatures des manifestes est désactivée. Les téléchargements restent vérifiés par leurs empreintes. Configurer les racines du site dans les builds distribués.

L’APK se trouve dans `app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk`.

L’APK embarque **Java 21 uniquement**, sous le nom `Internal-21`, pour les quatre ABI Android (`arm`, `arm64`, `x86`, `x86_64`). Préparer les assets avant une compilation locale :

```sh
./scripts/prepare-jre21.sh
```

Le script récupère les archives de la [release officielle Amethyst Java 21](https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/tag/download_jre21), vérifie leurs SHA-256, puis utilise le repacker officiel épinglé au commit `4527b5a73dcf3f890b45eab4c6a91651ea28a5ea` à priorité CPU minimale (`nice -n 19`). Prérequis de préparation : Bash, curl, GNU tar, xz, sha256sum et une connexion GitHub. La CI exécute cette même préparation. L’artifact amont s’appelle bien `jre21-pojav` (workflow `build.yml`, branche `buildjre17-21`), mais ses anciens exemplaires ont expiré : les archives de release assurent une source durable et vérifiable. Les assets générés `components/jre-21/` sont ignorés par Git ; les anciens assets Java 8/17/25 sont retirés de l’APK lors de la préparation.

Une nouvelle installation utilise Java 21 par défaut. À la première mise à jour, l’ancien choix global `Internal` passe à `Internal-21`. Les sélections suivantes restent libres et les profils qui ciblent explicitement un Java 8 installé sont conservés (`Internal` ou `External-8`) ; aucun runtime installé n’est renommé ou remplacé par Java 21. Java 8 reste téléchargeable depuis les paramètres des runtimes. Certains anciens modpacks, notamment ceux dont Forge impose Java 8, doivent sélectionner ce runtime dans leur profil. Java 17 et Java 25 restent également téléchargeables si un pack les exige. Cette modification évite d’embarquer plusieurs JRE ; elle ne garantit pas que Java 21 seul soit plus petit que Java 8 seul. Les instructions Amethyst originales sont conservées dans [README.amethyst.md](README.amethyst.md).

Le script de compilation impose `nice -n 19`, la priorité disque inactive si disponible, deux workers et un daemon non réutilisé. Toujours l’utiliser pour les compilations.

## Fonctionnalités intégrées

- Connexion par validation dans le navigateur, requêtes Ed25519, avatar carré du joueur et révocation à la déconnexion. Le dernier avatar reçu est conservé sur disque par compte et par site, et reste affiché hors connexion après redémarrage ; les icônes des modpacks sont récupérées du cache natif Amethyst.
- Session chiffrée avec une clé non exportable Android Keystore ; sauvegarde Android désactivée. PIN optionnel et fichiers `.cryptlist` protégés.
- Sélecteur dérivé du menu Amethyst avec les icônes réelles des modpacks. Logos mis en cache durablement dans le format natif des profils Amethyst ; le site contrôle l’icône des profils Snake N3st et son édition manuelle y est désactivée. Catalogue, canaux, version épinglée, installation, mise à jour et réparation. Synchronisation par SHA-256, écritures atomiques, `.filesmetas` et fusion des réglages.
- Chaîne de confiance des manifestes, mémorisation des certificats et protection contre les retours à une signature absente ou un certificat antérieur.
- Bouton **Installer** pour un modpack absent ou incomplet, puis **Jouer** après installation complète. Désinstallation confirmée de l’instance et de son profil, y compris ses mondes et réglages locaux ; les autres instances et les ressources partagées sont conservées.
- Mode **hors connexion**, proposé après une erreur réseau et accessible depuis le compte : lancement des modpacks déjà installés avec la session sauvegardée, sans synchronisation ni téléchargement. Une clé expirée est conservée ; si le serveur de jeu la refuse, se reconnecter dans le launcher puis relancer le jeu. Le PIN reste requis lorsqu’il est activé. Les modpacks locaux absents du dernier catalogue reçu restent visibles avec **Retiré du site** ; réparation et téléchargement y sont désactivés. Les installations incomplètes restent listées mais nécessitent une réparation en ligne avant de jouer.
- Instances indépendantes ; mémoire et arguments JVM par modpack. Le jeton est transmis à la JVM en mémoire et n’est pas enregistré dans les profils ni affiché par les journaux du launcher.
- Installation Vanilla/Fabric/Quilt et raccordement aux installateurs Forge/NeoForge d’Amethyst. Pour Forge/NeoForge, terminer l’installateur puis toucher Jouer à nouveau.
- Écran de gestion intégré à Amethyst : onglets Instance, Mods et Modrinth, progression des téléchargements, logos embarqués des mods, activation/désactivation et retrait des mods du pack ou personnels, recherche et choix de version compatible, dépendances obligatoires et empreintes vérifiées. Les choix locaux survivent au lancement ; Réparer restaure les mods officiels et conserve les ajouts personnels. Vérification de la blacklist serveur avant lancement et installation d’un ajout. Les dépendances déjà installées sont reconnues par leur empreinte, même si leur nom diffère (`%2B` / `+`).
- Actualités Azuriom dans un écran Amethyst dédié : article à la une, couvertures, dates et auteurs, cartes accessibles et ouverture dans le navigateur. Panneau de profil avec avatar carré, état de session et sections Sécurité/Launcher. Formulaire de rapport de bug volontaire.
- Contrôles tactiles, paramètres graphiques et gestion des runtimes d’Amethyst accessibles depuis les paramètres.

Les adaptations propres à Android et les vérifications restant à effectuer figurent dans [docs/PORTAGE.md](docs/PORTAGE.md).

## Tests

```bash
./scripts/build-sn3.sh -p sn3-core test
./scripts/build-sn3.sh :app_pojavlauncher:assembleDebug -Psn3.clientId=VOTRE_CLIENT_ID
```

Le cœur est autonome et peut être testé sans SDK Android. Il conserve les tests du launcher PC sur les signatures, la synchronisation, les règles de fusion et le stockage.

## Mises à jour Android et versions publiées

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

## Licences

Les sources Amethyst conservent leur licence et leurs en-têtes. `sn3-core` et l’intégration Snake N3st sont sous GPL-3.0 ; le binaire combiné est distribué sous GPL-3.0. Voir [LICENSE](LICENSE), [sn3-core/LICENSE](sn3-core/LICENSE) et [THIRD-PARTY-SNAKE-N3ST.md](THIRD-PARTY-SNAKE-N3ST.md).

## Vérifier les APK de mise à jour sur Android

Le runner `UpdateVerifierInstrumentation` teste le vérificateur réel du système sur quatre petits APK : version supérieure avec la bonne signature, version identique, autre package et autre signature. Il n’installe aucun APK factice.

Compiler l’application et le runner à priorité minimale avec `./scripts/build-sn3.sh :sn3-core:test :app_pojavlauncher:assembleDebug :app_pojavlauncher:assembleDebugAndroidTest -Psn3.clientId=…`. Installer les deux APK debug sur l’appareil de test. Préparer les fixtures avec `scripts/prepare-update-verifier-fixtures.py --apk CHEMIN_APK_DEBUG --aapt CHEMIN_AAPT --apksigner CHEMIN_APKSIGNER --android-jar CHEMIN_ANDROID_JAR --output /tmp/sn3-update-verifier`. Copier ses quatre `.apk` dans le répertoire privé `cache/sn3-update-verifier` du launcher, puis lancer `adb shell am instrument -w mc.snakenest.launcher.android.debug.test/mc.snakenest.launcher.android.snaken3st.UpdateVerifierInstrumentation`. Le résultat attendu est quatre contrôles réussis. Ces essais interrompent le processus du launcher : fermer toute partie avant de les lancer.
