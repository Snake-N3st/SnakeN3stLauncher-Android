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

- Connexion par validation dans le navigateur, requêtes Ed25519, avatar carré du joueur et révocation à la déconnexion.
- Session chiffrée avec une clé non exportable Android Keystore ; sauvegarde Android désactivée. PIN optionnel et fichiers `.cryptlist` protégés.
- Sélecteur dérivé du menu Amethyst avec les icônes réelles des modpacks. Catalogue, canaux, version épinglée, installation, mise à jour et réparation. Synchronisation par SHA-256, écritures atomiques, `.filesmetas` et fusion des réglages.
- Chaîne de confiance des manifestes, mémorisation des certificats et protection contre les retours à une signature absente ou un certificat antérieur.
- Bouton **Installer** pour un modpack absent ou incomplet, puis **Jouer** après installation complète. Désinstallation confirmée de l’instance et de son profil, y compris ses mondes et réglages locaux ; les autres instances et les ressources partagées sont conservées.
- Instances indépendantes ; mémoire et arguments JVM par modpack. Le jeton est transmis à la JVM en mémoire et n’est pas enregistré dans les profils ni affiché par les journaux du launcher.
- Installation Vanilla/Fabric/Quilt et raccordement aux installateurs Forge/NeoForge d’Amethyst. Pour Forge/NeoForge, terminer l’installateur puis toucher Jouer à nouveau.
- Écran de gestion intégré à Amethyst : onglets Instance, Mods et Modrinth, progression des téléchargements, logos embarqués des mods, activation/désactivation et retrait des mods du pack ou personnels, recherche et choix de version compatible, dépendances obligatoires et empreintes vérifiées. Les choix locaux survivent au lancement ; Réparer restaure les mods officiels et conserve les ajouts personnels. Vérification de la blacklist serveur avant lancement et installation d’un ajout. Les dépendances déjà installées sont reconnues par leur empreinte, même si leur nom diffère (`%2B` / `+`).
- Actualités Azuriom, consultation des articles et formulaire de rapport de bug volontaire.
- Contrôles tactiles, paramètres graphiques et gestion des runtimes d’Amethyst accessibles depuis les paramètres.

Les adaptations propres à Android et les vérifications restant à effectuer figurent dans [docs/PORTAGE.md](docs/PORTAGE.md).

## Tests

```bash
./scripts/build-sn3.sh -p sn3-core test
./scripts/build-sn3.sh :app_pojavlauncher:assembleDebug -Psn3.clientId=VOTRE_CLIENT_ID
```

Le cœur est autonome et peut être testé sans SDK Android. Il conserve les tests du launcher PC sur les signatures, la synchronisation, les règles de fusion et le stockage.

## Licences

Les sources Amethyst conservent leur licence et leurs en-têtes. `sn3-core` et l’intégration Snake N3st sont sous GPL-3.0 ; le binaire combiné est distribué sous GPL-3.0. Voir [LICENSE](LICENSE), [sn3-core/LICENSE](sn3-core/LICENSE) et [THIRD-PARTY-SNAKE-N3ST.md](THIRD-PARTY-SNAKE-N3ST.md).
