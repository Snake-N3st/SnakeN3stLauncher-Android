package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstancePathsTest {

    @Test
    void resolvesAnOrdinaryRelativePath(@TempDir Path instanceDir) throws PathTraversalException {
        Path resolved = InstancePaths.resolveSafely(instanceDir, "mods/foo.jar");

        assertEquals(instanceDir.resolve("mods/foo.jar"), resolved);
    }

    @Test
    void rejectsParentDirectoryTraversal(@TempDir Path instanceDir) {
        assertThrows(PathTraversalException.class, () -> InstancePaths.resolveSafely(instanceDir, "../../etc/passwd"));
    }

    @Test
    void rejectsAnAbsolutePathElsewhere(@TempDir Path instanceDir) {
        assertThrows(PathTraversalException.class, () -> InstancePaths.resolveSafely(instanceDir, "/etc/passwd"));
    }
}
