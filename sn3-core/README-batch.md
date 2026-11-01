# Snake N3st core

## Téléchargements groupés

La synchronisation utilise `GET /api/modpacks/{slug}/blobs?hashes=...`, jusqu’à 64 SHA-256 internes distincts par requête, uniquement pour les fichiers manquants ou modifiés. Chaque lot est transmis en flux `SN3B1`, sans archive ni recompression, puis vérifié (ordre, taille, SHA-256, fin du flux) dans un répertoire temporaire supprimé après synchronisation ou échec. Les doublons de contenu ne sont téléchargés qu’une fois. La fusion `.filesmetas` et les URL externes conservent leur comportement ; le fichier de règles peut nécessiter sa requête préalable.

Le site doit être mis à jour avant de distribuer ces clients : pas de repli automatique vers les téléchargements unitaires si l’endpoint manque ou refuse le lot, pour éviter une rafale après un 429. L’ancien endpoint blob reste disponible pour les anciens clients. Les appels successifs au nouvel endpoint sont espacés pour éviter les signatures identiques dans la même seconde. L’espace temporaire peut atteindre la taille des blobs internes à mettre à jour ; le transport reste borné en mémoire.
