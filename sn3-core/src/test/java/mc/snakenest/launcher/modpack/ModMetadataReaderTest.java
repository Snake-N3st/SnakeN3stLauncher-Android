package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Field values below mirror the real {@code fabric.mod.json}/{@code mods.toml} content confirmed
 * (via a throwaway scratch run, not just assumed) against real jars downloaded from Modrinth
 * (Fabric API and JEI-for-Forge) - a synthetic minimal jar here instead of bundling a multi-MB
 * real one into the repo as a test fixture.
 */
class ModMetadataReaderTest {

    @Test
    void readsFabricModJson(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "fabric.mod.json", """
                {
                  "schemaVersion": 1,
                  "id": "fabric-api",
                  "name": "Fabric API",
                  "version": "0.92.11+1.20.1",
                  "authors": ["FabricMC"],
                  "description": "Core API module providing key hooks and intercompatibility features."
                }
                """);

        InstalledModInfo info = ModMetadataReader.read(jar);

        assertEquals("Fabric API", info.name());
        assertEquals("0.92.11+1.20.1", info.version());
        assertEquals("FabricMC", info.author());
        assertEquals("Core API module providing key hooks and intercompatibility features.", info.description());
    }

    @Test
    void readsFabricModJsonWithObjectAuthors(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "fabric.mod.json", """
                {
                  "id": "example",
                  "name": "Example Mod",
                  "version": "1.0.0",
                  "authors": [{"name": "Someone"}]
                }
                """);

        assertEquals("Someone", ModMetadataReader.read(jar).author());
    }

    @Test
    void readsForgeModsToml(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "META-INF/mods.toml", """
                modLoader="javafml"
                loaderVersion="[47,)"
                license="The MIT License (MIT)"

                [[mods]]
                modId="jei"
                version="15.49.0.188"
                displayName="Just Enough Items"
                authors="mezz"
                description='''
                JEI is an item and recipe viewing mod for Minecraft, built from the ground up for stability and performance.
                '''
                """);

        InstalledModInfo info = ModMetadataReader.read(jar);

        assertEquals("Just Enough Items", info.name());
        assertEquals("15.49.0.188", info.version());
        assertEquals("mezz", info.author());
        assertEquals("JEI is an item and recipe viewing mod for Minecraft, built from the ground up for stability and performance.",
                info.description());
    }

    @Test
    void readsNeoForgeModsToml(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "META-INF/neoforge.mods.toml", """
                [[mods]]
                modId="examplemod"
                version="1.2.3"
                displayName="Example NeoForge Mod"
                """);

        InstalledModInfo info = ModMetadataReader.read(jar);

        assertEquals("Example NeoForge Mod", info.name());
        assertEquals("1.2.3", info.version());
    }

    @Test
    void ignoresTheUnsubstitutedJarVersionPlaceholder(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "META-INF/mods.toml", """
                [[mods]]
                modId="dev-build"
                version="${file.jarVersion}"
                displayName="Dev Build"
                """);

        assertNull(ModMetadataReader.read(jar).version());
    }

    @Test
    void readsFabricIconPathAsPlainString(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "fabric.mod.json", """
                {"id": "example", "name": "Example Mod", "version": "1.0.0", "icon": "assets/example/icon.png"}
                """);

        assertEquals("assets/example/icon.png", ModMetadataReader.read(jar).iconPath());
    }

    @Test
    void readsFabricIconPathPickingTheLargestSize(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "fabric.mod.json", """
                {"id": "example", "name": "Example Mod", "version": "1.0.0",
                 "icon": {"16": "icon-16.png", "128": "icon-128.png", "32": "icon-32.png"}}
                """);

        assertEquals("icon-128.png", ModMetadataReader.read(jar).iconPath());
    }

    @Test
    void readsForgeLogoFileAsTopLevelKey(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "META-INF/mods.toml", """
                modLoader="javafml"
                logoFile="jei-icon.png"

                [[mods]]
                modId="jei"
                version="1.0.0"
                displayName="Just Enough Items"
                """);

        assertEquals("jei-icon.png", ModMetadataReader.read(jar).iconPath());
    }

    @Test
    void readsIconBytesFromTheJar(@TempDir Path dir) throws IOException {
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        Path jar = writeJarWithFiles(dir, Map.of("fabric.mod.json",
                "{\"id\": \"example\", \"name\": \"Example\", \"version\": \"1.0.0\", \"icon\": \"icon.png\"}"),
                Map.of("icon.png", source));

        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(ModMetadataReader.readIcon(jar, "icon.png")));

        assertNotNull(decoded);
        assertEquals(4, decoded.getWidth());
        assertEquals(4, decoded.getHeight());
    }

    @Test
    void readIconReturnsNullWhenThePathDoesNotExistInTheJar(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "fabric.mod.json", "{\"id\": \"example\"}");

        assertNull(ModMetadataReader.readIcon(jar, "missing-icon.png"));
    }

    @Test
    void fallsBackToFilenameWhenNoKnownMetadataFileExists(@TempDir Path dir) throws IOException {
        Path jar = writeJar(dir, "some-resource.txt", "not metadata");

        InstalledModInfo info = ModMetadataReader.read(jar);

        assertEquals(jar.getFileName().toString(), info.name());
        assertNull(info.version());
    }

    private static Path writeJar(Path dir, String entryName, String content) throws IOException {
        return writeJarWithFiles(dir, Map.of(entryName, content), Map.of());
    }

    private static Path writeJarWithFiles(Path dir, Map<String, String> textEntries, Map<String, BufferedImage> imageEntries) throws IOException {
        Path jar = dir.resolve("mod.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (var entry : textEntries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                // Not try-with-resources: closing an OutputStreamWriter closes the underlying
                // stream too, which would break the zip.closeEntry()/further entries right after.
                Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
                writer.write(entry.getValue());
                writer.flush();
                zip.closeEntry();
            }
            for (var entry : imageEntries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                ImageIO.write(entry.getValue(), "png", zip);
                zip.closeEntry();
            }
        }
        return jar;
    }
}
