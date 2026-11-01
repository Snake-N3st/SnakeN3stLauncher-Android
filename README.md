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

Pour embarquer Java 8, télécharger l’artifact public `jre8-pojav` du workflow `build.yml`, branche `buildjre8`, du dépôt `AngelAuraMC/angelauramc-openjdk-build`, dans `app_pojavlauncher/src/main/assets/components/jre/`. Les instructions Amethyst originales sont conservées dans [README.amethyst.md](README.amethyst.md). Les runtimes modernes peuvent être téléchargés depuis les paramètres Amethyst.

Le script de compilation impose `nice -n 19`, la priorité disque inactive si disponible, deux workers et un daemon non réutilisé. Toujours l’utiliser pour les compilations.

## Fonctionnalités intégrées

- Connexion par validation dans le navigateur, requêtes Ed25519, avatar carré du joueur et révocation à la déconnexion.
- Session chiffrée avec une clé non exportable Android Keystore ; sauvegarde Android désactivée. PIN optionnel et fichiers `.cryptlist` protégés.
- Sélecteur dérivé du menu Amethyst avec les icônes réelles des modpacks. Catalogue, canaux, version épinglée, installation, mise à jour et réparation. Synchronisation par SHA-256, écritures atomiques, `.filesmetas` et fusion des réglages.
- Chaîne de confiance des manifestes, mémorisation des certificats et protection contre les retours à une signature absente ou un certificat antérieur.
- Instances indépendantes ; mémoire et arguments JVM par modpack. Le jeton est transmis à la JVM en mémoire et n’est pas enregistré dans les profils ni affiché par les journaux du launcher.
- Installation Vanilla/Fabric/Quilt et raccordement aux installateurs Forge/NeoForge d’Amethyst. Pour Forge/NeoForge, terminer l’installateur puis toucher Jouer à nouveau.
- Liste des mods, activation/désactivation des ajouts personnels, recherche Modrinth, choix de version compatible, dépendances obligatoires et empreintes vérifiées. Vérification de la blacklist serveur avant lancement et installation d’un ajout.
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
