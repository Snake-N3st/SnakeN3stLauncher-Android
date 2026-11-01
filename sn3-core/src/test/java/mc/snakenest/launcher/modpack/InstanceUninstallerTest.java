package mc.snakenest.launcher.modpack;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class InstanceUninstallerTest {
    @TempDir Path temporary;

    @Test void removesOnlySelectedInstanceIncludingWorldsAndPreservesOtherInstancesAndSharedAssets() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("instances"));
        Path target = Files.createDirectories(root.resolve("pack-one/saves/world"));
        Files.writeString(target.resolve("level.dat"), "world");
        Files.writeString(root.resolve("pack-one/options.txt"), "options");
        Path other = Files.createDirectories(root.resolve("pack-two/mods"));
        Files.writeString(other.resolve("keep.jar"), "other pack");
        Path shared = Files.createDirectories(temporary.resolve("shared/assets"));
        Files.writeString(shared.resolve("keep"), "shared");
        InstanceUninstaller.delete(root, "pack-one");
        assertFalse(Files.exists(root.resolve("pack-one"), LinkOption.NOFOLLOW_LINKS));
        assertEquals("other pack", Files.readString(other.resolve("keep.jar")));
        assertEquals("shared", Files.readString(shared.resolve("keep")));
        assertTrue(Files.isDirectory(root));
    }

    @Test void rejectsEveryPathLikeSlugBeforeDeletion() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("instances"));
        Path sentinel = Files.writeString(temporary.resolve("keep"), "safe");
        for (String slug : new String[]{null, "", ".", "..", "../keep", "/tmp", "pack/child", "pack\\child", "PACK", "pack--one", "pack-one/"})
            assertThrows(IllegalArgumentException.class, () -> InstanceUninstaller.delete(root, slug));
        assertEquals("safe", Files.readString(sentinel));
    }

    @Test void nestedDirectoryAndFileLinksAreUnlinkedWithoutTouchingTheirTargets() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("instances"));
        Path instance = Files.createDirectory(root.resolve("pack-one"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("keep"), "safe");
        Files.createSymbolicLink(instance.resolve("linked-directory"), outside);
        Files.createSymbolicLink(instance.resolve("linked-file"), sentinel);
        Files.createSymbolicLink(instance.resolve("dangling"), outside.resolve("missing"));
        InstanceUninstaller.delete(root, "pack-one");
        assertEquals("safe", Files.readString(sentinel));
        assertFalse(Files.exists(instance, LinkOption.NOFOLLOW_LINKS));
    }

    @Test void aSymlinkUsedAsTheInstanceIsRemovedWithoutFollowingIt() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("instances"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("keep"), "safe");
        Files.createSymbolicLink(root.resolve("pack-one"), outside);
        InstanceUninstaller.delete(root, "pack-one");
        assertEquals("safe", Files.readString(outside.resolve("keep")));
        assertFalse(Files.exists(root.resolve("pack-one"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test void refusesAnInstancesRootThatIsItselfALinkOrAFile() throws Exception {
        Path outside = Files.createDirectories(temporary.resolve("outside/pack-one"));
        Files.writeString(outside.resolve("keep"), "safe");
        Path linked = Files.createSymbolicLink(temporary.resolve("instances"), outside.getParent());
        assertThrows(IOException.class, () -> InstanceUninstaller.delete(linked, "pack-one"));
        assertEquals("safe", Files.readString(outside.resolve("keep")));
        Path file = Files.writeString(temporary.resolve("not-a-directory"), "safe");
        assertThrows(IOException.class, () -> InstanceUninstaller.delete(file, "pack-one"));
    }

    @Test void missingInstancesAreHarmlessAndAnInterruptedUninstallDoesNotStartDeleting() throws Exception {
        InstanceUninstaller.delete(temporary.resolve("missing"), "pack-one");
        Path root = Files.createDirectory(temporary.resolve("instances"));
        InstanceUninstaller.delete(root, "missing-pack");
        Path instance = Files.createDirectory(root.resolve("pack-one"));
        Path sentinel = Files.writeString(instance.resolve("keep"), "safe");
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class, () -> InstanceUninstaller.delete(root, "pack-one")); }
        finally { Thread.interrupted(); }
        assertEquals("safe", Files.readString(sentinel));
    }
}
