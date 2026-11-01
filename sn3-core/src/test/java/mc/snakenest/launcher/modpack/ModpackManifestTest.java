package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModpackManifestTest {

    private static ModpackManifest manifestWithRawMemory(Integer rawDefaultMemoryMb) {
        return new ModpackManifest("1.0.0", null, null, 0, "1.20.4", "vanilla", null, List.of(), null, rawDefaultMemoryMb);
    }

    @Test
    void defaultMemoryMbFallsBackToTheGlobalDefaultWhenNeverSet() {
        assertEquals(ModpackSettings.DEFAULT_MEMORY_MB, manifestWithRawMemory(null).defaultMemoryMb());
    }

    @Test
    void defaultMemoryMbFallsBackToTheGlobalDefaultWhenNotPositive() {
        assertEquals(ModpackSettings.DEFAULT_MEMORY_MB, manifestWithRawMemory(0).defaultMemoryMb());
        assertEquals(ModpackSettings.DEFAULT_MEMORY_MB, manifestWithRawMemory(-1).defaultMemoryMb());
    }

    @Test
    void defaultMemoryMbUsesTheCuratorsValueWhenPositive() {
        assertEquals(4096, manifestWithRawMemory(4096).defaultMemoryMb());
    }
}
