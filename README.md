<p align="center">
  <img src="app_pojavlauncher/src/main/res/drawable-nodpi/sn3_logo.png" width="104" height="104" alt="Logo Snake N3st">
</p>

<h1 align="center">Snake N3st Launcher · Android</h1>

<p align="center">
  Les modpacks Snake N3st sur Android, avec l’interface et le moteur d’Amethyst.
</p>

<p align="center">
  <a href="https://snake-n3st.fr">Site</a> ·
  <a href="https://discord.gg/wCJZpreVDj">Discord</a> ·
  <a href="https://github.com/Snake-N3st/SnakeN3stLauncher-Android/releases">Versions</a> ·
  <a href="https://github.com/Snake-N3st/SnakeN3stLauncher">Launcher PC</a>
</p>

Connectez votre compte Snake N3st, choisissez un modpack et retrouvez ses mods, ses réglages et ses fichiers sur votre téléphone. Le launcher s’appuie sur [Amethyst](https://github.com/AngelAuraMC/Amethyst-Android) pour exécuter Minecraft Java Edition et gérer les commandes tactiles, les moteurs de rendu et les runtimes Java.

**Android 8.0 minimum · Java à la demande · Interface en français · Mode hors connexion**

## Ce que propose le launcher

| | Fonctionnalités |
| --- | --- |
| **Compte** | Écran de connexion dédié, validation dans le navigateur, avatar carré, panneau de profil et protection par PIN optionnelle. Un même compte peut être utilisé sur plusieurs appareils. |
| **Modpacks** | Catalogue Snake N3st, sélecteur Amethyst avec les vraies icônes, choix du canal et de la version, installation, réparation et désinstallation. |
| **Mods** | Onglets Instance, Mods et Modrinth ; icônes des mods, activation, désactivation, retrait et installation de versions compatibles avec leurs dépendances. |
| **Téléchargements** | Téléchargement des fichiers du pack par lots ; fichiers Minecraft et Java téléchargés en parallèle, vérification des empreintes Minecraft et progression du transfert. |
| **Hors connexion** | Lancement des packs déjà installés avec la session sauvegardée ; avatars et icônes restent disponibles depuis leur cache sur disque. |
| **Actualités** | Articles illustrés, date et auteur, avec ouverture dans le navigateur. |
| **Mises à jour** | Recherche automatique ou manuelle des versions publiées ; vérification de l’APK avant confirmation de l’installation par Android. |
| **Jeu** | Réglages par modpack, mémoire et arguments JVM, contrôles tactiles et paramètres Amethyst. |

## Installer et jouer

1. Consultez les [versions publiées](https://github.com/Snake-N3st/SnakeN3stLauncher-Android/releases) pour récupérer un APK. Les builds de développement sont accessibles dans les artifacts du workflow [Android APK](https://github.com/Snake-N3st/SnakeN3stLauncher-Android/actions/workflows/android.yml).
2. Installez l’APK, puis ouvrez **Snake N3st Launcher**. Android peut demander d’autoriser l’installation depuis la source utilisée.
3. Connectez votre compte : validez la demande dans le navigateur, puis revenez dans l’application.
4. Choisissez un modpack et touchez **Installer**. Le runtime Java nécessaire est téléchargé si aucun runtime installé ne convient. Une fois l’installation terminée, le bouton devient **Jouer**.
5. Utilisez l’écran de gestion du pack pour régler la mémoire, gérer ses mods ou réparer son installation.

La version de production et la version de test utilisent des identifiants distincts : `mc.snakenest.launcher.android` et `mc.snakenest.launcher.android.debug`. Elles peuvent cohabiter avec Amethyst, et chacune conserve ses propres données.

### Vos mods et vos fichiers

Chaque modpack dispose de son propre répertoire de jeu. Les changements effectués dans l’onglet **Mods** sont conservés lors des lancements suivants. **Réparer** restaure les mods officiels du pack et conserve les ajouts personnels.

**Désinstaller supprime l’instance, y compris ses mondes solo et ses réglages locaux.** Une confirmation est demandée avant cette opération ; sauvegardez les fichiers que vous souhaitez garder.

### Réglages Java : global ou par modpack

Dans **Paramètres → Java**, l’allocation de mémoire et les arguments JVM sont les valeurs globales par défaut. Dans **Gestion du modpack → Réglages Java de ce modpack**, chaque option peut suivre la valeur globale ou utiliser une exception propre au pack ; la source active est affichée.

Les arguments propres à un modpack **remplacent** les arguments globaux. Une exception vide permet de ne fournir aucun argument personnalisé. La mémoire se règle avec son allocation, sans ajouter de `-Xmx` ou `-Xms`. Pour les profils Snake N3st, ces réglages sont modifiés depuis la gestion du modpack ; le champ JVM de l’éditeur Amethyst est en lecture seule.

Les anciens réglages sont conservés. Cochez **Utiliser l’allocation globale** ou **Utiliser les arguments JVM globaux** pour faire suivre les valeurs Amethyst à un pack déjà configuré.

### Jouer hors connexion

Après une erreur de requête, choisissez **Passer hors connexion**. Cette action est également accessible depuis le panneau du compte.

- Les modpacks entièrement installés peuvent être lancés sans synchronisation ni téléchargement.
- La session sauvegardée est transmise au jeu comme d’habitude. Si sa clé a expiré et qu’un serveur la refuse, reconnectez-vous dans le launcher puis relancez le jeu.
- Le PIN reste nécessaire lorsqu’il est activé. Les avatars et les icônes déjà chargés restent disponibles après redémarrage.
- Un pack installé mais absent du dernier catalogue reçu reste visible avec la mention **Retiré du site**. Il peut encore être lancé pour retrouver, par exemple, un monde solo ; sa réparation et ses téléchargements sont désactivés.
- Une installation incomplète reste visible et doit être terminée en ligne avant de jouer. Les actualités, téléchargements et recherches de mises à jour nécessitent une connexion.

Pour reprendre la synchronisation, choisissez **Revenir en ligne**.

### Java et compatibilité

L’APK **n’embarque aucun runtime Java**. Le launcher réutilise un runtime compatible déjà installé ou télécharge celui nécessaire au modpack lors de son installation. Les runtimes restent ensuite sur l’appareil et sont réutilisables hors connexion. Leur gestion manuelle reste accessible depuis les paramètres Amethyst.

Les runtimes déjà installés sont conservés lors de la mise à jour. Ils occupent de la place dans les données de l’application, mais leur archive compressée n’est plus incluse dans l’APK.

Vanilla, Fabric et Quilt sont intégrés ; Forge et NeoForge utilisent les installateurs Amethyst. Pour ces derniers, terminez l’installateur puis touchez **Jouer** à nouveau. La compatibilité d’un modpack dépend aussi de ses mods, du runtime choisi et du moteur de rendu Android.

## Compiler une version de développement

Prérequis : **JDK 21**, **SDK Android 37**, **NDK 27.3.13750724**, Git et les outils Bash usuels (`curl`, `tar`, `xz`, `sha256sum`). Configurez `JAVA_HOME` pour le JDK et `ANDROID_HOME` pour le SDK. Les licences SDK doivent être acceptées ; les sous-modules peuvent demander des composants NDK supplémentaires.

```bash
git clone --recurse-submodules https://github.com/Snake-N3st/SnakeN3stLauncher-Android.git
cd SnakeN3stLauncher-Android

export ANDROID_HOME="$HOME/Android/Sdk"

# Générer les ressources de langues.
bash scripts/languagelist_updater.sh

# Remplacer la valeur par le client Azuriom prévu pour Android.
./scripts/build-sn3.sh :app_pojavlauncher:assembleDebug \
  -Psn3.clientId=VOTRE_CLIENT_ID
```

APK généré : `app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk`.

Le **client_id est fixé à la compilation**, pas dans l’application. Les builds distribués doivent également fournir les clés publiques racines du site pour vérifier les signatures des manifestes. Les propriétés disponibles et la configuration des versions signées sont détaillées dans le [guide de développement](docs/DEVELOPMENT.md).

Utilisez toujours `scripts/build-sn3.sh` : il impose la priorité CPU minimale (`nice -n 19`), une priorité disque inactive si disponible, deux workers et un daemon Gradle non réutilisé.

Pour exécuter uniquement les tests du cœur, sans SDK Android :

```bash
./scripts/build-sn3.sh -p sn3-core test
```

### Téléchargements simultanés

Dans **Paramètres → Divers**, le réglage **Téléchargements simultanés du jeu** permet de choisir entre 2 et 16 transferts Minecraft, avec **8 par défaut**. Les bibliothèques, ressources, fichiers du jeu et le runtime nécessaire peuvent être téléchargés en parallèle une fois leurs métadonnées connues. Les fichiers identiques sont dédupliqués ; les téléchargements des modpacks conservent leur protocole par lots.

## Organisation du projet

- [`sn3-core/`](sn3-core/) : authentification, catalogues, synchronisation, signatures et gestion des fichiers, indépendants d’Android.
- [`app_pojavlauncher/`](app_pojavlauncher/) : application Amethyst et intégration Android Snake N3st dans le package `snaken3st`.
- [`scripts/`](scripts/) : compilation et outils de publication.
- [Guide de développement](docs/DEVELOPMENT.md) : configuration, sécurité, publication et vérifications sur appareil.
- [Notes de portage](docs/PORTAGE.md) : correspondance avec le launcher PC et points de validation.
- [README Amethyst d’origine](README.amethyst.md) : documentation conservée du projet amont.

## Aide et contributions

Pour une question ou un problème en jeu, rejoignez le [Discord Snake N3st](https://discord.gg/wCJZpreVDj). L’application propose aussi **Signaler un bug** depuis le panneau du compte.

Pour contribuer, ouvrez une issue ou une pull request en précisant le modèle de l’appareil, la version d’Android, le modpack et les étapes de reproduction. Ne publiez jamais votre PIN, une clé de session ou un journal contenant un jeton d’authentification.

## Crédits et licences

Merci aux équipes d’[Amethyst](https://github.com/AngelAuraMC/Amethyst-Android) et de [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher), dont ce fork reprend le moteur Android et les contrôles.

Les sources amont conservent leurs licences et leurs en-têtes. Le cœur et l’intégration Snake N3st sont sous **GPL-3.0** ; le binaire combiné est distribué sous GPL-3.0. Voir [LICENSE](LICENSE), [sn3-core/LICENSE](sn3-core/LICENSE) et [THIRD-PARTY-SNAKE-N3ST.md](THIRD-PARTY-SNAKE-N3ST.md).
