package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class LocalInstallationFilesTest {
    @TempDir Path root;
    @Test void missingFileFailsWithoutCreatingAnything() {
        assertThrows(IOException.class,()->LocalInstallationFiles.requireFile(root,"mods/missing.jar",false));
        assertFalse(Files.exists(root.resolve("mods")));
    }
    @Test void rejectsTraversalAndSymlinkParent() throws Exception {
        assertThrows(IOException.class,()->LocalInstallationFiles.requireFile(root,"../outside.jar",false));
        Path outside=Files.createTempDirectory("sn3-offline-outside");
        try { Files.writeString(outside.resolve("file"),"x"); Files.createSymbolicLink(root.resolve("linked"),outside);
            assertThrows(IOException.class,()->LocalInstallationFiles.requireFile(root,"linked/file",false));
        } finally { Files.deleteIfExists(outside.resolve("file")); Files.deleteIfExists(outside); }
    }
    @Test void encryptedFilesRemainValidWithoutDecryption() throws Exception {
        Files.writeString(root.resolve("private.sn3enc"),"ciphertext");
        LocalInstallationFiles.requireFile(root,"private",true);
        assertFalse(Files.exists(root.resolve("private")));
    }
    @Test void honorsDisabledRemovedAndReplacementChoices() throws Exception {
        Files.createDirectories(root.resolve("mods")); Files.writeString(root.resolve("mods/new.jar.disabled"),"jar");
        var manifest=new StoredManifest("1",Map.of("mods/old.jar","a","mods/removed.jar","b"));
        var choices=Map.of("old.jar",new ModOverrides.Choice("old.jar","new.jar",true,false,true),
                "removed.jar",new ModOverrides.Choice("removed.jar","removed.jar",false,true,true));
        LocalInstallationFiles.requireInstance(root,manifest,choices);
        Files.delete(root.resolve("mods/new.jar.disabled"));
        assertThrows(IOException.class,()->LocalInstallationFiles.requireInstance(root,manifest,choices));
    }
}
