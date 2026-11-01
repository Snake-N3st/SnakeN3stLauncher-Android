package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.crypto.SecretBox;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Encrypts a modpack's own sensitive files at rest under the player's PIN-derived key, for
 * whatever an optional <b>{@code .cryptlist}</b> at the instance root asks for.
 *
 * <h2>{@code .cryptlist} format</h2>
 * One glob pattern per line, relative to the instance root; blank lines and {@code #} comments
 * ignored. Globs (rather than plain paths) so a pack can say {@code config/*.secret} without
 * listing every file, and matching is done against files actually found under the instance - which
 * is also why a pattern can't reach outside it, however it's written.
 *
 * <pre>
 * # Everything here is unreadable unless the PIN is entered
 * config/private-api-keys.json
 * config/*.secret
 * </pre>
 *
 * <h2>Lifecycle</h2>
 * An encrypted file becomes {@code <original>.sn3enc} and the plaintext is removed, so a grabber
 * copying the instance directory gets only ciphertext. Since the game itself can't read that,
 * {@link #decryptAll} runs just before launch and {@link #encryptAll} right after the game exits -
 * the plaintext window is exactly the play session. {@link #encryptAll} is also called at startup
 * (after the PIN unlock) to re-seal anything a crash or a kill left decrypted.
 *
 * <p>Decryption deliberately works off the {@code .sn3enc} files present on disk rather than
 * re-reading {@code .cryptlist}: whatever was encrypted gets decrypted, even if the pack has since
 * changed or removed the list, so an edit can't strand a file as permanently unreadable.
 *
 * <p>Only meaningful while a PIN is enabled - without one there'd be no key worth encrypting
 * under (it would sit in {@code wrap.key} next to the data, see {@code crypto.PinProtection}), so
 * callers skip this entirely in that case.
 */
public final class SensitiveFileVault {

    static final String CRYPTLIST_FILE_NAME = ".cryptlist";
    static final String ENCRYPTED_SUFFIX = ".sn3enc";

    /**
     * These are meant to be configs/small secrets, and both directions read the whole file into
     * memory. Refusing to touch anything bigger keeps a pack that lists a multi-GB jar (by mistake
     * or otherwise) from taking the launcher's heap down with it.
     */
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

    private SensitiveFileVault() {
    }

    /**
     * Encrypts every not-yet-encrypted file matching the instance's {@code .cryptlist}. No-op when
     * there's no such file. Best-effort per file: one failure is logged and skipped rather than
     * aborting the rest, since leaving the others sealed is strictly better than leaving all of
     * them plaintext.
     *
     * @return how many files were newly encrypted
     */
    public static int encryptAll(Path instanceDir, SecretKey key) {
        List<PathMatcher> patterns = readCryptList(instanceDir);
        if (patterns.isEmpty()) {
            return 0;
        }

        int encrypted = 0;
        for (Path file : listFiles(instanceDir)) {
            Path relative = instanceDir.relativize(file);
            if (file.getFileName().toString().endsWith(ENCRYPTED_SUFFIX) || !matchesAny(patterns, relative)) {
                continue;
            }
            if (!isWithinSizeLimit(file)) {
                continue;
            }
            try {
                Path target = file.resolveSibling(file.getFileName() + ENCRYPTED_SUFFIX);
                AtomicFiles.write(target, SecretBox.encrypt(key, Files.readAllBytes(file)));
                Files.delete(file);
                encrypted++;
            } catch (IOException | GeneralSecurityException e) {
                Log.warn(SensitiveFileVault.class, "Could not encrypt " + relative + ": " + e.getMessage());
            }
        }
        if (encrypted > 0) {
            Log.info(SensitiveFileVault.class, "Encrypted " + encrypted + " sensitive file(s) in " + instanceDir.getFileName());
        }
        return encrypted;
    }

    /**
     * Restores every {@code .sn3enc} file in the instance back to plaintext. Best-effort per file
     * for the same reason as {@link #encryptAll} - except that a failure here does mean the game
     * may not find a file it expects, which is why each one is logged rather than silently skipped.
     *
     * @return how many files were decrypted
     */
    public static int decryptAll(Path instanceDir, SecretKey key) {
        int decrypted = 0;
        for (Path file : listFiles(instanceDir)) {
            String name = file.getFileName().toString();
            if (!name.endsWith(ENCRYPTED_SUFFIX)) {
                continue;
            }
            try {
                Path target = file.resolveSibling(name.substring(0, name.length() - ENCRYPTED_SUFFIX.length()));
                AtomicFiles.write(target, SecretBox.decrypt(key, Files.readAllBytes(file)));
                Files.delete(file);
                decrypted++;
            } catch (IOException | GeneralSecurityException e) {
                Log.warn(SensitiveFileVault.class, "Could not decrypt " + instanceDir.relativize(file) + ": " + e.getMessage());
            }
        }
        if (decrypted > 0) {
            Log.info(SensitiveFileVault.class, "Decrypted " + decrypted + " sensitive file(s) in " + instanceDir.getFileName());
        }
        return decrypted;
    }

    /**
     * Deletes every {@code .sn3enc} file in the instance without decrypting it - the "code oublié"
     * path, where the key to read them is gone for good, so keeping them would only leave
     * permanently unreadable files behind. The modpack re-downloads/regenerates them like any
     * other missing file.
     *
     * @return how many files were deleted
     */
    public static int wipeEncrypted(Path instanceDir) {
        int deleted = 0;
        for (Path file : listFiles(instanceDir)) {
            if (!file.getFileName().toString().endsWith(ENCRYPTED_SUFFIX)) {
                continue;
            }
            try {
                Files.delete(file);
                deleted++;
            } catch (IOException e) {
                Log.warn(SensitiveFileVault.class, "Could not delete " + instanceDir.relativize(file) + ": " + e.getMessage());
            }
        }
        return deleted;
    }

    /** @return one matcher per usable line, or empty when there's no {@code .cryptlist} at all (the common case) */
    private static List<PathMatcher> readCryptList(Path instanceDir) {
        Path cryptList = instanceDir.resolve(CRYPTLIST_FILE_NAME);
        if (!Files.isRegularFile(cryptList)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(cryptList, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.warn(SensitiveFileVault.class, "Could not read " + CRYPTLIST_FILE_NAME + " in " + instanceDir.getFileName() + ": " + e.getMessage());
            return List.of();
        }

        List<PathMatcher> patterns = new ArrayList<>();
        for (String line : lines) {
            String pattern = line.trim();
            if (pattern.isEmpty() || pattern.startsWith("#")) {
                continue;
            }
            try {
                patterns.add(FileSystems.getDefault().getPathMatcher("glob:" + pattern));
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                Log.warn(SensitiveFileVault.class, "Ignoring malformed " + CRYPTLIST_FILE_NAME + " pattern \"" + pattern + "\": " + e.getMessage());
            }
        }
        return patterns;
    }

    /**
     * Materialized into a list rather than streamed, because both callers create and delete files
     * as they go and {@code Files.walk}'s behavior over a tree being modified underneath it is
     * unspecified.
     */
    private static List<Path> listFiles(Path instanceDir) {
        if (!Files.isDirectory(instanceDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(instanceDir)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            Log.warn(SensitiveFileVault.class, "Could not list " + instanceDir + ": " + e.getMessage());
            return List.of();
        }
    }

    private static boolean matchesAny(List<PathMatcher> patterns, Path relative) {
        for (PathMatcher pattern : patterns) {
            if (pattern.matches(relative)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWithinSizeLimit(Path file) {
        try {
            if (Files.size(file) <= MAX_FILE_BYTES) {
                return true;
            }
            Log.warn(SensitiveFileVault.class, "Skipping " + file.getFileName() + ": larger than the "
                    + (MAX_FILE_BYTES / (1024 * 1024)) + " Mo limit for sensitive files");
            return false;
        } catch (IOException e) {
            Log.warn(SensitiveFileVault.class, "Could not stat " + file.getFileName() + ": " + e.getMessage());
            return false;
        }
    }
}
