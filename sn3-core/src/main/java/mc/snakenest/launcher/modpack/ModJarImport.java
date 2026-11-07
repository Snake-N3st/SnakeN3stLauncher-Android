package mc.snakenest.launcher.modpack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

/** Bounded staging for a user-selected mod, without touching the previous installed file. */
public final class ModJarImport {
    public static final long MAX_BYTES=512L*1024*1024;
    private ModJarImport() { }
    public static String filename(String displayName)throws IOException {
        try {String name=ModOverrides.canonicalFilename(displayName);if(!name.equals(displayName))throw new IllegalArgumentException();return name;}
        catch(IllegalArgumentException failure){throw new IOException("Sélectionne un fichier .jar avec un nom valide.",failure);}
    }
    public static void stage(InputStream input,Path temporary,long limit)throws IOException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import annulé");
        if(limit<=0||limit>MAX_BYTES)throw new IOException("Limite d’import invalide.");
        long size=0;
        try(OutputStream output=Files.newOutputStream(temporary)) {
            byte[] buffer=new byte[65536];int count;
            while((count=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import annulé");size+=count;if(size>limit)throw new IOException("Le fichier dépasse la limite de l’import.");output.write(buffer,0,count);}
        }
        try(ZipFile zip=new ZipFile(temporary.toFile())) {
            ZipEntry metadata=zip.getEntry("fabric.mod.json");
            if(metadata==null)metadata=zip.getEntry("quilt.mod.json");
            if(metadata==null)metadata=zip.getEntry("META-INF/neoforge.mods.toml");
            if(metadata==null)metadata=zip.getEntry("META-INF/mods.toml");
            if(metadata==null)metadata=zip.getEntry("mcmod.info");
            if(metadata==null)throw new IOException("Ce fichier ne contient pas les métadonnées d’un mod Fabric, Quilt, Forge ou NeoForge.");
            byte[] bytes;
            try(InputStream source=zip.getInputStream(metadata);ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int count;
                while((count=source.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import annulé");if(output.size()+count>256*1024)throw new IOException("Métadonnées du mod trop volumineuses.");output.write(buffer,0,count);}
                bytes=output.toByteArray();
            }
            if(bytes.length>256*1024)throw new IOException("Métadonnées du mod trop volumineuses.");
            String content=new String(bytes,StandardCharsets.UTF_8);
            String name=metadata.getName();
            if(name.equals("fabric.mod.json")||name.equals("quilt.mod.json")) {
                try {
                    JsonObject json=JsonParser.parseString(content).getAsJsonObject();
                    if(name.equals("quilt.mod.json"))json=json.getAsJsonObject("quilt_loader");
                    requireString(json,"id");requireString(json,"version");
                }catch(RuntimeException invalid){throw new IOException("Métadonnées "+(name.equals("quilt.mod.json")?"Quilt":"Fabric")+" invalides.",invalid);}
            }else if(name.equals("mcmod.info")) {
                try {
                    var json=JsonParser.parseString(content);
                    var mods=json.isJsonArray()?json.getAsJsonArray():json.getAsJsonObject().getAsJsonArray("modList");
                    if(mods==null||mods.isEmpty())throw new IllegalArgumentException();
                    for(var mod:mods){requireString(mod.getAsJsonObject(),"modid");requireString(mod.getAsJsonObject(),"version");}
                }catch(RuntimeException invalid){throw new IOException("Métadonnées Forge historiques invalides.",invalid);}
            }else if(!content.contains("[[mods]]")||!content.matches("(?s).*\\bmodId\\s*=.*"))throw new IOException("Métadonnées Forge ou NeoForge invalides.");
        }catch(ZipException invalid){throw new IOException("Le fichier sélectionné n’est pas une archive JAR valide.",invalid);}
    }
    private static void requireString(JsonObject json,String name) {
        if(json==null||!json.has(name)||!json.get(name).isJsonPrimitive()||!json.get(name).getAsJsonPrimitive().isString()||json.get(name).getAsString().isBlank())throw new IllegalArgumentException();
    }
}
