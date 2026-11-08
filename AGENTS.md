# Instructions

Toutes les compilations doivent tourner avec la priorité CPU minimale : `nice -n 19`, Gradle `org.gradle.priority=low`. Utiliser `scripts/build-sn3.sh`.
Le moteur natif et les contrôles viennent d’Amethyst. Les services Snake N3st sont dans `sn3-core` et l’intégration Android dans `snaken3st`.
Ne jamais journaliser la graine Ed25519, les requêtes signées complètes ou `sn3.token`.
