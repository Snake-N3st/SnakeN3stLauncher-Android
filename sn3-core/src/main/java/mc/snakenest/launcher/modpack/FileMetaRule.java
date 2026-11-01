package mc.snakenest.launcher.modpack;

import java.util.Locale;

/**
 * One {@code .filesmetas} rule - see {@link FileMetas} for the file format and why it exists.
 *
 * @param kind        what to do with the file
 * @param mergeFormat how to merge, set only when {@code kind} is {@link Kind#MERGE}
 */
public record FileMetaRule(Kind kind, MergeFormat mergeFormat) {

    public enum Kind {
        /**
         * Rewrite the file on every update, even when the pack's copy is byte-identical to what
         * was last applied. For a file the pack must own outright and a player must not be able to
         * drift away from - plain hash-diffing would skip it the moment nothing changed upstream,
         * leaving a locally edited copy in place forever.
         */
        OVERRIDE,

        /**
         * Seed it once, then never touch it again. For files the pack provides a starting point
         * for but the player owns afterwards - a default {@code servers.dat}, {@code options.txt}.
         */
        DEFAULT,

        /**
         * Apply only the settings the pack declares, leaving everything else in the player's file
         * alone. For a config where the pack needs to change one or two keys without flattening
         * the player's other choices.
         */
        MERGE
    }

    /** How a {@link Kind#MERGE} file is structured. */
    public enum MergeFormat {
        PROPERTIES,
        JSON
    }

    /**
     * Parses the right-hand side of a rule ({@code OVERRIDE}, {@code DEFAULT},
     * {@code MERGE JSON}...).
     *
     * @return {@code null} when the type is unrecognised - the caller logs and skips the line
     *         rather than failing the whole sync over one bad rule
     */
    static FileMetaRule parse(String rawType) {
        String[] parts = rawType.trim().toUpperCase(Locale.ROOT).split("\\s+");

        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }

        return switch (parts[0]) {
            case "OVERRIDE" -> new FileMetaRule(Kind.OVERRIDE, null);
            case "DEFAULT" -> new FileMetaRule(Kind.DEFAULT, null);
            case "MERGE" -> parseMerge(parts);
            default -> null;
        };
    }

    /** {@code MERGE} without a usable format is meaningless, so it is rejected rather than guessed. */
    private static FileMetaRule parseMerge(String[] parts) {
        if (parts.length < 2) {
            return null;
        }

        try {
            return new FileMetaRule(Kind.MERGE, MergeFormat.valueOf(parts[1]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Whether an existing copy of this file belongs to the player, so a sync must not overwrite or
     * delete it outright. True for both {@code DEFAULT} (never touched again) and {@code MERGE}
     * (rewritten, but only after folding the player's own content back in).
     */
    public boolean protectsExistingFile() {
        return kind == Kind.DEFAULT || kind == Kind.MERGE;
    }
}
