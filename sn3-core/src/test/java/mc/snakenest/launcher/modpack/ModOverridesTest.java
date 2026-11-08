package mc.snakenest.launcher.modpack;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModOverridesTest {
    @TempDir Path root;
    private Path instance() throws IOException { return Files.createDirectories(root.resolve("instance/mods")).getParent(); }
    private ModOverrides choices() throws IOException { return new ModOverrides(root.resolve("preferences/pack")); }
    @Test void managedDisableSurvivesRestartAndSyncAndCanBeEnabledAgain() throws Exception {
        Path instance=instance(); Path jar=Files.writeString(instance.resolve("mods/official.jar"),"v1");
        choices().change(instance,"official.jar",false,true);
        assertFalse(Files.exists(jar)); assertEquals("v1",Files.readString(jar.resolveSibling("official.jar.disabled")));
        Files.writeString(jar,"v2"); choices().apply(instance);
        assertFalse(Files.exists(jar)); assertEquals("v2",Files.readString(jar.resolveSibling("official.jar.disabled")));
        choices().change(instance,"official.jar.disabled",false,true);
        assertEquals("v2",Files.readString(jar)); assertFalse(choices().ignoredModPaths().contains("mods/official.jar"));
    }
    @Test void managedRemovalStaysRemovedAfterSyncAndRepairCanResetIt() throws Exception {
        Path instance=instance(); Path jar=Files.writeString(instance.resolve("mods/official.jar"),"v1");
        choices().change(instance,"official.jar",true,true);
        Files.writeString(jar,"upstream"); choices().apply(instance); assertFalse(Files.exists(jar));
        choices().resetManaged(instance); Files.writeString(jar,"repaired"); choices().apply(instance);
        assertEquals("repaired",Files.readString(jar)); assertTrue(choices().choices().isEmpty());
    }
    @Test void customOfficialVersionIsRestoredAfterSyncAndRepairRemovesItButPreservesPersonalDisabledMods() throws Exception {
        Path instance=instance(); Path mods=instance.resolve("mods"); Files.writeString(mods.resolve("official.jar"),"official");
        Files.writeString(mods.resolve("custom.jar"),"chosen-version");
        choices().replacement(instance,"official.jar","custom.jar",true);
        Files.delete(mods.resolve("custom.jar")); Files.writeString(mods.resolve("official.jar"),"upstream-new"); choices().apply(instance);
        assertFalse(Files.exists(mods.resolve("official.jar"))); assertEquals("chosen-version",Files.readString(mods.resolve("custom.jar")));
        Files.writeString(mods.resolve("personal.jar"),"personal"); choices().change(instance,"personal.jar",false,false);
        choices().resetManaged(instance); Files.writeString(mods.resolve("official.jar"),"repaired"); choices().apply(instance);
        assertFalse(Files.exists(mods.resolve("custom.jar"))); assertEquals("repaired",Files.readString(mods.resolve("official.jar")));
        assertEquals("personal",Files.readString(mods.resolve("personal.jar.disabled")));
        assertFalse(choices().choices().values().stream().anyMatch(ModOverrides.Choice::managed));
    }
    @Test void updatingDisabledManagedModKeepsItDisabledAndVerifiesItsBackup() throws Exception {
        Path instance=instance(); Path mods=instance.resolve("mods"); Files.writeString(mods.resolve("official.jar"),"v1");
        choices().change(instance,"official.jar",false,true); Files.writeString(mods.resolve("next.jar"),"v2");
        choices().replacement(instance,"official.jar.disabled","next.jar",true);
        assertFalse(Files.exists(mods.resolve("next.jar"))); assertEquals("v2",Files.readString(mods.resolve("next.jar.disabled")));
        Path backup; try(var files=Files.list(root.resolve("preferences/pack/files"))) { backup=files.findFirst().orElseThrow(); }
        Files.writeString(backup,"tampered"); assertThrows(IOException.class,()->choices().apply(instance));
        assertEquals("v2",Files.readString(mods.resolve("next.jar.disabled")));
    }
    @Test void unsafeFilenamesAndSymlinkedModsDirectoriesCannotTouchOutsideFiles() throws Exception {
        Path instance=instance(); Path outside=Files.writeString(root.resolve("outside.jar"),"safe");
        for(String name:new String[]{"../outside.jar","a/b.jar","a\\b.jar","not-a-jar","x.jar\n"})
            assertThrows(IllegalArgumentException.class,()->choices().change(instance,name,true,true));
        Files.delete(instance.resolve("mods")); Files.createSymbolicLink(instance.resolve("mods"),root);
        assertThrows(IOException.class,()->choices().change(instance,"outside.jar",true,true));
        assertEquals("safe",Files.readString(outside));
    }
}
