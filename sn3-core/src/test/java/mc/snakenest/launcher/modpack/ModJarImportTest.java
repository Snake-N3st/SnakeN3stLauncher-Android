package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ModJarImportTest {
    @TempDir Path root;
    private static byte[] jar(String entry,String content)throws IOException {var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry(entry));zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}return bytes.toByteArray();}
    @Test void acceptsValidFabricMod()throws Exception {byte[] bytes=jar("fabric.mod.json","{\"id\":\"example\",\"version\":\"1.0\"}");Path stage=root.resolve("staged.tmp");ModJarImport.stage(new ByteArrayInputStream(bytes),stage,bytes.length);assertArrayEquals(bytes,Files.readAllBytes(stage));}
    @Test void acceptsForgeMetadata()throws Exception {byte[] bytes=jar("META-INF/mods.toml","[[mods]]\nmodId=\"example\"\nversion=\"1.0\"");ModJarImport.stage(new ByteArrayInputStream(bytes),root.resolve("staged.tmp"),bytes.length);}
    @Test void acceptsQuiltMetadata()throws Exception {byte[] bytes=jar("quilt.mod.json","{\"schema_version\":1,\"quilt_loader\":{\"id\":\"example\",\"version\":\"1.0\"}}");ModJarImport.stage(new ByteArrayInputStream(bytes),root.resolve("quilt.tmp"),bytes.length);}
    @Test void rejectsMalformedQuiltMetadata()throws Exception {for(String content:new String[]{"{}","{\"quilt_loader\":{\"id\":\"example\"}}","{\"quilt_loader\":{\"id\":\"\",\"version\":\"1.0\"}}","{\"quilt_loader\":[]}"}){byte[] bytes=jar("quilt.mod.json",content);assertThrows(IOException.class,()->ModJarImport.stage(new ByteArrayInputStream(bytes),root.resolve("quilt.tmp"),bytes.length));}}
    @Test void acceptsLegacyForgeArrayMetadata()throws Exception {byte[] bytes=jar("mcmod.info","[{\"modid\":\"example\",\"version\":\"1.0\"},{\"modid\":\"second\",\"version\":\"2.0\"}]");ModJarImport.stage(new ByteArrayInputStream(bytes),root.resolve("legacy.tmp"),bytes.length);}
    @Test void acceptsLegacyForgeModListMetadata()throws Exception {byte[] bytes=jar("mcmod.info","{\"modList\":[{\"modid\":\"example\",\"version\":\"1.0\"}]}");ModJarImport.stage(new ByteArrayInputStream(bytes),root.resolve("legacy.tmp"),bytes.length);}
    @Test void rejectsMalformedLegacyForgeMetadata()throws Exception {for(String content:new String[]{"[]","{}","{\"modList\":[]}","[{\"modid\":\"example\"}]","[{\"modid\":\"example\",\"version\":\"\"}]","[{\"modid\":\"example\",\"version\":\"1.0\"},null]","not json"}){byte[] bytes=jar("mcmod.info",content);assertThrows(IOException.class,()->ModJarImport.stage(new ByteArrayInputStream(bytes),root.resolve("legacy.tmp"),bytes.length));}}
    @Test void rejectsPathTraversalAndDisabledNames(){assertThrows(IOException.class,()->ModJarImport.filename("../other.jar"));assertThrows(IOException.class,()->ModJarImport.filename("example.jar.disabled"));assertThrows(IOException.class,()->ModJarImport.filename("not-a-mod.zip"));}
    @Test void rejectsTruncatedAndNonModArchives()throws Exception {assertThrows(IOException.class,()->ModJarImport.stage(new ByteArrayInputStream(new byte[]{1,2,3}),root.resolve("stage.tmp"),1024));assertThrows(IOException.class,()->ModJarImport.stage(new ByteArrayInputStream(jar("text.txt","text")),root.resolve("stage.tmp"),1024));}
    @Test void rejectsMalformedMetadata()throws Exception {assertThrows(IOException.class,()->ModJarImport.stage(new ByteArrayInputStream(jar("fabric.mod.json","{}")),root.resolve("stage.tmp"),1024));}
    @Test void oversizedInputNeverReplacesTheInstalledFile()throws Exception {Path installed=root.resolve("installed.jar");Files.writeString(installed,"old mod");assertThrows(IOException.class,()->ModJarImport.stage(new ByteArrayInputStream(new byte[20]),root.resolve("stage.tmp"),10));assertEquals("old mod",Files.readString(installed));}
    @Test void cancellationDoesNotReplaceTheInstalledFile()throws Exception {Path installed=root.resolve("installed.jar");Files.writeString(installed,"old mod");try{Thread.currentThread().interrupt();assertThrows(InterruptedIOException.class,()->ModJarImport.stage(new ByteArrayInputStream(new byte[20]),root.resolve("stage.tmp"),30));}finally{Thread.interrupted();}assertEquals("old mod",Files.readString(installed));}
    @Test void rejectsOversizedUncompressedMetadata()throws Exception {byte[] data=jar("fabric.mod.json","x".repeat(256*1024+1));assertThrows(IOException.class,()->ModJarImport.stage(new ByteArrayInputStream(data),root.resolve("stage.tmp"),data.length));}
}
