package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMetasTest {

    private static FileMetaRule.Kind kindOf(FileMetas metas, String path) {
        return metas.ruleFor(path).map(FileMetaRule::kind).orElse(null);
    }

    @Test
    void parsesEachType() {
        FileMetas metas = FileMetas.parse("""
                options.txt = DEFAULT
                config/pack.cfg = OVERRIDE
                config/client.json = MERGE JSON
                server.properties = MERGE PROPERTIES
                """);

        assertEquals(FileMetaRule.Kind.DEFAULT, kindOf(metas, "options.txt"));
        assertEquals(FileMetaRule.Kind.OVERRIDE, kindOf(metas, "config/pack.cfg"));
        assertEquals(FileMetaRule.Kind.MERGE, kindOf(metas, "config/client.json"));

        assertEquals(FileMetaRule.MergeFormat.JSON,
                metas.ruleFor("config/client.json").orElseThrow().mergeFormat());
        assertEquals(FileMetaRule.MergeFormat.PROPERTIES,
                metas.ruleFor("server.properties").orElseThrow().mergeFormat());
    }

    @Test
    void isCaseInsensitiveOnTypesAndToleratesLooseSpacing() {
        FileMetas metas = FileMetas.parse("  options.txt   =   default  \nfoo.json=merge   json\n");

        assertEquals(FileMetaRule.Kind.DEFAULT, kindOf(metas, "options.txt"));
        assertEquals(FileMetaRule.MergeFormat.JSON, metas.ruleFor("foo.json").orElseThrow().mergeFormat());
    }

    @Test
    void ignoresBlankLinesAndComments() {
        FileMetas metas = FileMetas.parse("""
                # les reglages du joueur
                options.txt = DEFAULT

                config/pack.cfg = OVERRIDE # impose par le pack
                """);

        assertEquals(FileMetaRule.Kind.DEFAULT, kindOf(metas, "options.txt"));
        assertEquals(FileMetaRule.Kind.OVERRIDE, kindOf(metas, "config/pack.cfg"));
    }

    /**
     * A typo in this file must never be the reason a player cannot update: the bad line is dropped
     * and everything else still applies.
     */
    @Test
    void skipsUnusableLinesWithoutLosingTheGoodOnes() {
        FileMetas metas = FileMetas.parse("""
                options.txt = DEFAULT
                broken line with no equals
                config/a.cfg = NOT_A_REAL_TYPE
                config/b.json = MERGE
                config/c.json = MERGE YAML
                config/pack.cfg = OVERRIDE
                """);

        assertEquals(FileMetaRule.Kind.DEFAULT, kindOf(metas, "options.txt"));
        assertEquals(FileMetaRule.Kind.OVERRIDE, kindOf(metas, "config/pack.cfg"));

        assertTrue(metas.ruleFor("config/a.cfg").isEmpty(), "unknown type must be dropped");
        assertTrue(metas.ruleFor("config/b.json").isEmpty(), "MERGE without a format is meaningless");
        assertTrue(metas.ruleFor("config/c.json").isEmpty(), "unknown merge format must be dropped");
    }

    @Test
    void normalisesPathSeparatorsAndPrefixes() {
        FileMetas metas = FileMetas.parse("""
                config\\sub\\a.cfg = OVERRIDE
                ./b.txt = DEFAULT
                /c.txt = DEFAULT
                """);

        assertEquals(FileMetaRule.Kind.OVERRIDE, kindOf(metas, "config/sub/a.cfg"));
        assertEquals(FileMetaRule.Kind.DEFAULT, kindOf(metas, "b.txt"));
        assertEquals(FileMetaRule.Kind.DEFAULT, kindOf(metas, "c.txt"));
    }

    @Test
    void reportsPlayerOwnedPaths() {
        FileMetas metas = FileMetas.parse("""
                options.txt = DEFAULT
                config/client.json = MERGE JSON
                config/pack.cfg = OVERRIDE
                """);

        assertEquals(java.util.Set.of("options.txt", "config/client.json"), metas.playerOwnedPaths());
    }

    @Test
    void emptyInputYieldsNoRules() {
        assertTrue(FileMetas.parse("").isEmpty());
        assertTrue(FileMetas.parse(null).isEmpty());
        assertTrue(FileMetas.parse("   \n\n# rien que des commentaires\n").isEmpty());
        assertTrue(FileMetas.empty().isEmpty());
    }

    @Test
    void overrideDoesNotProtectAnExistingFile() {
        FileMetas metas = FileMetas.parse("a = OVERRIDE\nb = DEFAULT\nc = MERGE JSON\n");

        assertFalse(metas.ruleFor("a").orElseThrow().protectsExistingFile());
        assertTrue(metas.ruleFor("b").orElseThrow().protectsExistingFile());
        assertTrue(metas.ruleFor("c").orElseThrow().protectsExistingFile());
    }
}
