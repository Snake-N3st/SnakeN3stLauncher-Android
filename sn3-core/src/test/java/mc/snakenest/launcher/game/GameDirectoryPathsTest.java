package mc.snakenest.launcher.game;

import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameDirectoryPathsTest {
    @Test void privateAndroidInstanceRemainsTheGameDirectory() {
        String instance="/data/user/0/mc.snakenest.launcher.android.debug/no_backup/snake-n3st/instances/fb-1-21-11";
        assertEquals(new File(instance),GameDirectoryPaths.resolve("/storage/emulated/0/Android/data/launcher/files",instance));
    }
    @Test void relativeAmethystProfilesStillResolveUnderStorage() {
        assertEquals(new File("/storage/games/instances/custom"),GameDirectoryPaths.resolve("/storage/games","instances/custom"));
    }
}
