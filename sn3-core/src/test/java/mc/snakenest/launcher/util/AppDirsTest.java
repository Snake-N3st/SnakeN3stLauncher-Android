package mc.snakenest.launcher.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppDirsTest {

    @Test
    void subdirectoriesNestUnderRoot(@TempDir Path root) {
        AppDirs dirs = new AppDirs(root);

        assertEquals(root, dirs.root());
        assertEquals(root.resolve("cache"), dirs.cache());
        assertEquals(root.resolve("cache").resolve("launcher"), dirs.cacheLauncher());
        assertEquals(root.resolve("instances"), dirs.instances());
        assertEquals(root.resolve("instances").resolve("aventure-ultime"), dirs.instance("aventure-ultime"));
        assertEquals(root.resolve("secure"), dirs.secure());
        assertEquals(root.resolve("logs"), dirs.logs());
        assertEquals(root.resolve("config.json"), dirs.configFile());
        assertEquals(root.resolve("shared").resolve("assets"), dirs.sharedAssetsPool());
        assertEquals(root.resolve("shared").resolve("libraries"), dirs.sharedLibrariesPool());
    }

    @Test
    void windowsRootUsesAppData() {
        Path resolved = AppDirs.resolveRootForTesting(null, "Windows 11", "/home/someone", "C:\\Users\\someone\\AppData\\Roaming");

        assertEquals(Path.of("C:\\Users\\someone\\AppData\\Roaming", "snake-n3st"), resolved);
    }

    @Test
    void macRootUsesApplicationSupport() {
        Path resolved = AppDirs.resolveRootForTesting(null, "Mac OS X", "/Users/someone", null);

        assertEquals(Path.of("/Users/someone", "Library", "Application Support", "snake-n3st"), resolved);
    }

    @Test
    void linuxRootUsesXdgStyleLocalShare() {
        Path resolved = AppDirs.resolveRootForTesting(null, "Linux", "/home/someone", null);

        assertEquals(Path.of("/home/someone", ".local", "share", "snake-n3st"), resolved);
    }

    @Test
    void dataDirOverrideWinsRegardlessOfOs() {
        Path resolved = AppDirs.resolveRootForTesting("/tmp/sn3-test-instance", "Linux", "/home/someone", null);

        assertEquals(Path.of("/tmp/sn3-test-instance"), resolved);
    }

    @Test
    void blankDataDirOverrideFallsBackToOsDefault() {
        Path resolved = AppDirs.resolveRootForTesting("   ", "Linux", "/home/someone", null);

        assertEquals(Path.of("/home/someone", ".local", "share", "snake-n3st"), resolved);
    }

    @Test
    void resolveRootReadsTheActualSystemProperty() {
        // Exercises resolveRoot() itself (not just the pure resolveRootForTesting it delegates
        // to) - a typo in the actual property name it reads wouldn't be caught otherwise.
        String previous = System.getProperty("sn3.dataDir");
        try {
            System.setProperty("sn3.dataDir", "/tmp/sn3-real-property-check");
            assertEquals(Path.of("/tmp/sn3-real-property-check"), AppDirs.resolveRoot());
        } finally {
            if (previous == null) {
                System.clearProperty("sn3.dataDir");
            } else {
                System.setProperty("sn3.dataDir", previous);
            }
        }
    }

    @Test
    void appIdReplacesOnlyTheFolderNameOnEveryOs() {
        assertEquals(Path.of("/home/u", ".local", "share", "snake-n3st-launcher-test"),
                AppDirs.resolveRootForTesting(null, "Linux", "/home/u", null, "snake-n3st-launcher-test"));
        assertEquals(Path.of("/Users/u", "Library", "Application Support", "snake-n3st-launcher-test"),
                AppDirs.resolveRootForTesting(null, "Mac OS X", "/Users/u", null, "snake-n3st-launcher-test"));
        assertEquals(Path.of("C:\\Roaming", "snake-n3st-launcher-test"),
                AppDirs.resolveRootForTesting(null, "Windows 11", "C:\\Users\\u", "C:\\Roaming", "snake-n3st-launcher-test"));
    }

    @Test
    void noAppIdMeansTheFolderEveryExistingInstallUses() {
        assertEquals(Path.of("/home/u", ".local", "share", "snake-n3st"),
                AppDirs.resolveRootForTesting(null, "Linux", "/home/u", null, null));
        assertEquals(Path.of("/home/u", ".local", "share", "snake-n3st"),
                AppDirs.resolveRootForTesting(null, "Linux", "/home/u", null, ""));
    }

    @Test
    void anAppIdThatCouldBeAPathIsIgnored() {
        for (String hostile : new String[] {"..", ".", "../x", "a/b", "a\\b", "/etc", "x/..", " ", "Upper", "a b", "a;b",
                "-lead", "\u00e9t\u00e9", "x\n", "x\u0000y"}) {
            assertEquals("snake-n3st", AppDirs.resolveAppId(hostile), "should be refused: [" + hostile + "]");
            assertEquals(Path.of("/home/u", ".local", "share", "snake-n3st"),
                    AppDirs.resolveRootForTesting(null, "Linux", "/home/u", null, hostile));
        }
    }

    @Test
    void anExplicitDataDirStillWinsOverTheAppId() {
        assertEquals(Path.of("/srv/data"),
                AppDirs.resolveRootForTesting("/srv/data", "Linux", "/home/u", null, "snake-n3st-launcher-test"));
    }

    @Test
    void resolveRootReadsTheAppIdSystemProperty() {
        String previous = System.getProperty("sn3.appId");
        String previousDir = System.getProperty("sn3.dataDir");
        try {
            System.clearProperty("sn3.dataDir");
            System.setProperty("sn3.appId", "sn3launcher-test");
            assertEquals("sn3launcher-test", AppDirs.resolveRoot().getFileName().toString());
        } finally {
            if (previous == null) {
                System.clearProperty("sn3.appId");
            } else {
                System.setProperty("sn3.appId", previous);
            }
            if (previousDir != null) {
                System.setProperty("sn3.dataDir", previousDir);
            }
        }
    }
}
