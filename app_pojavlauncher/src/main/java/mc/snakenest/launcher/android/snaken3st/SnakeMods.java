package mc.snakenest.launcher.android.snaken3st;

import java.nio.file.*;
import java.net.URI;
import java.io.*;
import java.util.*;
import java.security.*;
import mc.snakenest.launcher.modrinth.*;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.util.Hex;

/** Install only compatible, verified files; server-managed files cannot be replaced. */
final class SnakeMods {
    private final SnakeRepository r;
    private final ModrinthApiClient api;
    private List<ModBlacklistEntry> blacklist;
    SnakeMods(SnakeRepository r) { this.r=r; api=new ModrinthApiClient(r.http); }
    void install(String slug, ModpackManifest m, ModrinthVersion v) throws Exception { blacklist=r.api().getBlacklist(slug,r.requireSession()); install(slug,m,v,new HashSet<>()); }
    private void downloadProgress(String name, long received, long total, boolean verified) {
        int percent = verified ? 100 : total > 0 ? (int)Math.min(99, received * 99.0 / total) : 0;
        mc.snakenest.launcher.android.progresskeeper.ProgressKeeper.submitProgress("sn3", percent,
                mc.snakenest.launcher.android.R.string.sn3_download_mod_progress,
                name, received / 1048576.0, total / 1048576.0);
    }
    private void install(String slug,ModpackManifest m,ModrinthVersion v,Set<String> seen) throws Exception {
        if(!seen.add(v.id())) return;
        if(!v.isCompatible(m.mcVersion(),m.loader())) throw new IOException("Mod incompatible : "+v.name());
        if(v.dependencies()!=null) for(var dependency:v.dependencies()) if(dependency.isRequired()) {
            if(dependency.projectId()==null) throw new IOException("Dépendance sans projet : installation manuelle requise");
            ModrinthVersion chosen=api.versions(dependency.projectId()).stream()
                .filter(candidate -> candidate.isCompatible(m.mcVersion(),m.loader()))
                .filter(candidate -> dependency.versionId()==null || dependency.versionId().equals(candidate.id())).findFirst()
                .orElseThrow(()->new IOException("Dépendance compatible introuvable : "+dependency.projectId()));
            install(slug,m,chosen,seen);
        }
        ModrinthVersionFile file=v.primaryFile().orElseThrow(()->new IOException("Fichier du mod absent"));
        String name=file.filename();
        if(name==null || !name.matches("[A-Za-z0-9_.+() -]+\\.jar") || name.contains("..")) throw new IOException("Nom de fichier non sûr");
        for(ManifestFile managed:m.files()) if(managed.path().equals("mods/"+name)) throw new IOException("Ce fichier est géré par le modpack : "+name);
        String expected=file.hashes().get("sha512"); String algorithm="SHA-512";
        if(expected==null) { expected=file.sha1(); algorithm="SHA-1"; }
        if(expected==null) throw new IOException("Empreinte du mod absente");
        if(!"https".equals(URI.create(file.url()).getScheme())) throw new IOException("Téléchargement HTTPS requis");
        Path mods=r.instance(slug).resolve("mods"); Files.createDirectories(mods);
        Path temporary=Files.createTempFile(mods,"sn3-mod-",".tmp");
        try {
            MessageDigest digest=MessageDigest.getInstance(algorithm); long size=0;
            long lastProgress = 0;
            r.status = "Téléchargement : " + name; r.changed();
            downloadProgress(name, 0, file.size(), false);
            try(RawResponse response=r.http.getRaw(URI.create(file.url()))) {
                if(!response.isSuccess()) throw new IOException("Téléchargement du mod refusé");
                try(InputStream in=response.body(); OutputStream out=Files.newOutputStream(temporary)) {
                    byte[] buffer=new byte[65536]; int count;
                    while((count=in.read(buffer))!=-1) {
                        if(Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        size+=count; if(size>file.size()) throw new IOException("Taille du mod inattendue");
                        digest.update(buffer,0,count); out.write(buffer,0,count);
                        long now = System.nanoTime();
                        if (now - lastProgress >= 100_000_000L) {
                            downloadProgress(name, size, file.size(), false); lastProgress = now;
                        }
                    }
                }
            }
            if(size!=file.size() || !expected.equalsIgnoreCase(Hex.encode(digest.digest()))) throw new IOException("Empreinte du mod invalide");
            try(InputStream in=Files.newInputStream(temporary)) {
                String hash=mc.snakenest.launcher.util.Sha256.hex(in);
                for(ModBlacklistEntry blocked:blacklist) if(hash.equalsIgnoreCase(blocked.hash())) throw new IOException("Mod interdit : "+blocked.reason());
            }
            String old=r.prefs.getString("mod."+slug+"."+v.projectId(),null);
            Files.move(temporary,mods.resolve(name),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            if(old!=null && !old.equals(name) && old.matches("[A-Za-z0-9_.+() -]+\\.jar")) {
                boolean managed=m.files().stream().anyMatch(f->f.path().equals("mods/"+old));
                if(!managed) Files.deleteIfExists(mods.resolve(old));
            }
            r.prefs.edit().putString("mod."+slug+"."+v.projectId(),name).apply();
            downloadProgress(name, size, file.size(), true);
        } finally { Files.deleteIfExists(temporary); }
    }
}
