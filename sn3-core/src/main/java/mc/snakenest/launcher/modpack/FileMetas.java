package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code .filesmetas} convention: a curator-authored file at the root of a modpack saying how
 * each listed path should be treated when an instance is updated or repaired.
 *
 * <p>One rule per line, {@code path = TYPE}:
 * <pre>
 *   options.txt        = DEFAULT
 *   config/pack.cfg    = OVERRIDE
 *   config/client.json = MERGE JSON
 *   server.properties  = MERGE PROPERTIES
 * </pre>
 *
 * <p><b>Why it exists.</b> Hash-diffing alone cannot express intent. A curator's uploaded zip is
 * usually a snapshot of their own instance, so it routinely contains files the player owns rather
 * than the pack - and equally, some pack files must be forced back into shape on every update no
 * matter what the player did to them. Before this, the launcher guessed, with two hardcoded
 * filenames (see {@link ManifestDiffer}); now the curator says so explicitly.
 *
 * <p>Blank lines and {@code #} comments are ignored. A malformed or unknown rule is logged and
 * skipped rather than failing the sync: a typo in this file must never leave a player unable to
 * update, and the fallback (plain hash-diffing) is exactly the behaviour that predates it.
 */
public final class FileMetas {

    /** Read before anything else in a sync - see {@code ModpackSyncEngine}. */
    public static final String FILE_NAME = ".filesmetas";

    private static final FileMetas EMPTY = new FileMetas(Map.of());

    private final Map<String, FileMetaRule> rules;

    private FileMetas(Map<String, FileMetaRule> rules) {
        this.rules = rules;
    }

    /** No rules at all - a pack that ships no {@value #FILE_NAME}. */
    public static FileMetas empty() {
        return EMPTY;
    }

    public static FileMetas parse(String content) {
        if (content == null || content.isBlank()) {
            return EMPTY;
        }

        Map<String, FileMetaRule> parsed = new LinkedHashMap<>();

        for (String rawLine : content.split("\\R")) {
            String line = stripComment(rawLine).trim();

            if (line.isEmpty()) {
                continue;
            }

            int separator = line.indexOf('=');

            if (separator < 0) {
                Log.warn(FileMetas.class, "Ignoring malformed " + FILE_NAME + " line (no '='): " + line);
                continue;
            }

            String path = normalizePath(line.substring(0, separator));
            FileMetaRule rule = FileMetaRule.parse(line.substring(separator + 1));

            if (path.isEmpty() || rule == null) {
                Log.warn(FileMetas.class, "Ignoring unusable " + FILE_NAME + " line: " + line);
                continue;
            }

            parsed.put(path, rule);
        }

        return parsed.isEmpty() ? EMPTY : new FileMetas(parsed);
    }

    /** The rule declared for {@code path}, if the curator declared one. */
    public Optional<FileMetaRule> ruleFor(String path) {
        return Optional.ofNullable(rules.get(normalizePath(path)));
    }

    /** Every path the curator marked as player-owned - {@code DEFAULT} or {@code MERGE}. */
    public Set<String> playerOwnedPaths() {
        Set<String> owned = new java.util.LinkedHashSet<>();

        rules.forEach((path, rule) -> {
            if (rule.protectsExistingFile()) {
                owned.add(path);
            }
        });

        return owned;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /**
     * Trailing {@code #} comments, so a curator can annotate a rule inline. Not applied to the
     * value side only - a {@code #} anywhere starts a comment, which keeps the rule simple and
     * matches how the sibling {@code .autodownload} file is read on the site.
     */
    private static String stripComment(String line) {
        int hash = line.indexOf('#');

        return hash < 0 ? line : line.substring(0, hash);
    }

    /**
     * Manifest paths always use forward slashes, so a curator who wrote Windows separators (or a
     * stray leading {@code ./}) still gets a rule that matches.
     */
    private static String normalizePath(String path) {
        String normalized = path.trim().replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }
}
