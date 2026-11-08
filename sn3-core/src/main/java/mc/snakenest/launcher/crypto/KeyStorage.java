package mc.snakenest.launcher.crypto;

import java.util.Optional;

/** Where the player's Ed25519 key pair lives between launcher runs. */
public interface KeyStorage {

    void save(Ed25519KeyPair keyPair) throws KeyStorageException;

    Optional<Ed25519KeyPair> load() throws KeyStorageException;

    /** No-op if nothing is stored. Used on logout. */
    void delete();
}
