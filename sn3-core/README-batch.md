# Snake N3st core

## Téléchargements groupés

La synchronisation utilise `GET /api/modpacks/{slug}/blobs?hashes=...`, jusqu’à 64 SHA-256 internes distincts par requête, uniquement pour les fichiers manquants ou modifiés. Chaque lot est transmis en flux `SN3B1`, sans archive ni recompression, puis vérifié (ordre, taille, SHA-256, fin du flux) dans un répertoire temporaire supprimé après synchronisation ou échec. Les doublons de contenu ne sont téléchargés qu’une fois. La fusion `.filesmetas` et les URL externes conservent leur comportement ; le fichier de règles peut nécessiter sa requête préalable.

Le site doit être mis à jour avant de distribuer ces clients : pas de repli automatique vers les téléchargements unitaires si l’endpoint manque ou refuse le lot, pour éviter une rafale après un 429. L’ancien endpoint blob reste disponible pour les anciens clients. Les appels successifs au nouvel endpoint sont espacés pour éviter les signatures identiques dans la même seconde. L’espace temporaire peut atteindre la taille des blobs internes à mettre à jour ; le transport reste borné en mémoire.

La requête annonce désormais `encoding=zstd`. Un site compatible renvoie `SN3B2`, avec pour chaque fichier `sha256 raw_size wire_size identity|zstd` suivi de ses octets. `SN3B1` reste accepté pour les anciens sites. Le décodage zstd est en flux, avec une fenêtre maximale de 1 Mio et une sortie limitée à la taille du manifeste ; le SHA-256 vérifie toujours le fichier original avant écriture définitive. La progression compte les octets décompressés.
Si la représentation zstd est invalide, le téléchargement reprend sur les fichiers originaux pour cette entrée et la suite du lot ; les octets déjà comptés ne sont pas ajoutés une seconde fois à la progression.
