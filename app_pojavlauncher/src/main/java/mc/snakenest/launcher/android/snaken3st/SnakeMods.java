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
import mc.snakenest.launcher.util.Sha1;
import mc.snakenest.launcher.util.Sha256;

/** Compatible verified installs, including explicit replacements of server-provided mods. */
final class SnakeMods {
    private final SnakeRepository r;
    private final ModrinthApiClient api;
    private List<ModBlacklistEntry> blacklist;
    private final Map<String, String> installedProjects = new HashMap<>();
    private boolean projectsIdentified;
    SnakeMods(SnakeRepository r) { this.r=r; api=new ModrinthApiClient(r.http); }
    void install(String slug, ModpackManifest manifest, ModrinthVersion version) throws Exception {
        install(slug,manifest,version,(Path)null);
    }
    void install(String slug, ModpackManifest manifest, ModrinthVersion version, Path replacedFile) throws Exception {
        if (Sn3GameBridge.isRunning(r.context)) throw new IOException("Ferme Minecraft avant de modifier les mods.");
        SnakeRepository.requireSlug(slug);
        blacklist=r.api().getBlacklist(slug,r.requireSession());
        install(slug,manifest,version,new HashSet<>(),replacedFile,true);
    }
    private void downloadProgress(String name, long received, long total, boolean verified) {
        int percent = verified ? 100 : total > 0 ? (int)Math.min(99, received * 99.0 / total) : 0;
        mc.snakenest.launcher.android.progresskeeper.ProgressKeeper.submitProgress("sn3", percent,
                mc.snakenest.launcher.android.R.string.sn3_download_mod_progress,
                name, received / 1048576.0, total / 1048576.0);
    }
    private void install(String slug, ModpackManifest manifest, ModrinthVersion version, Set<String> seen,
                         Path replacedFile, boolean requested) throws Exception {
        if (!seen.add(version.id())) return;
        if (!version.isCompatible(manifest.mcVersion(),manifest.loader())) throw new IOException("Mod incompatible : "+version.name());
        if (version.dependencies()!=null) for (var dependency:version.dependencies()) if (dependency.isRequired()) {
            if (dependency.projectId()==null) throw new IOException("Dépendance sans projet : installation manuelle requise");
            ModrinthVersion chosen=api.versions(dependency.projectId()).stream()
                    .filter(candidate -> candidate.isCompatible(manifest.mcVersion(),manifest.loader()))
                    .filter(candidate -> dependency.versionId()==null || dependency.versionId().equals(candidate.id())).findFirst()
                    .orElseThrow(()->new IOException("Dépendance compatible introuvable : "+dependency.projectId()));
            install(slug,manifest,chosen,seen,null,false);
        }
        ModrinthVersionFile file=version.primaryFile().orElseThrow(()->new IOException("Fichier du mod absent"));
        String name=file.filename(); ModOverrides.canonicalFilename(name);
        String expected=file.hashes().get("sha512"); String algorithm="SHA-512";
        if (expected==null) { expected=file.sha1(); algorithm="SHA-1"; }
        if (expected==null) throw new IOException("Empreinte du mod absente");
        if (!"https".equals(URI.create(file.url()).getScheme())) throw new IOException("Téléchargement HTTPS requis");
        Path instance=r.instance(slug); Path mods=instance.resolve("mods").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(instance) || Files.isSymbolicLink(mods)) throw new IOException("Dossier de mods non sûr");
        Files.createDirectories(mods);
        if (replacedFile!=null && !mods.equals(replacedFile.toAbsolutePath().normalize().getParent())) throw new IOException("Le mod à remplacer n’appartient pas à cette instance");

        // Required dependencies are resolved first, but equal bytes never get downloaded again
        // just because the manifest encoded '+' as '%2B' or used another filename.
        Optional<Path> identical=InstalledModMatcher.findIdentical(mods,algorithm,expected,file.size());
        if (identical.isPresent()) {
            Path existing=identical.get(); checkBlacklist(existing);
            String existingName=existing.getFileName().toString();
            var choice=r.modOverrides(slug).choiceFor(existingName);
            if (replacedFile!=null || requested && choice.isPresent() && (choice.get().disabled() || choice.get().removed())) {
                String previous=replacedFile==null ? existingName : replacedFile.getFileName().toString();
                rememberReplacement(slug,manifest,version.projectId(),previous,existingName);
            } else r.prefs.edit().putString("mod."+slug+"."+version.projectId(),existingName).apply();
            installedProjects.put(version.projectId(),existingName);
            r.status="Déjà présent : "+existingName; r.changed(); downloadProgress(existingName,file.size(),file.size(),true);
            return;
        }
        String previous=replacedFile==null ? previousFile(slug,mods,version.projectId()) : replacedFile.getFileName().toString();
        if (previous==null) previous=name;
        Path temporary=Files.createTempFile(mods,"sn3-mod-",".tmp");
        try {
            MessageDigest digest=MessageDigest.getInstance(algorithm); long size=0; long lastProgress=0;
            r.status="Téléchargement : "+name; r.changed(); downloadProgress(name,0,file.size(),false);
            try (RawResponse response=r.http.getRaw(URI.create(file.url()))) {
                if (!response.isSuccess()) throw new IOException("Téléchargement du mod refusé");
                try (InputStream input=response.body(); OutputStream output=Files.newOutputStream(temporary)) {
                    byte[] buffer=new byte[65536]; int count;
                    while ((count=input.read(buffer))!=-1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        size+=count; if (size>file.size()) throw new IOException("Taille du mod inattendue");
                        digest.update(buffer,0,count); output.write(buffer,0,count);
                        long now=System.nanoTime();
                        if (now-lastProgress>=100_000_000L) { downloadProgress(name,size,file.size(),false); lastProgress=now; }
                    }
                }
            }
            if (size!=file.size() || !expected.equalsIgnoreCase(Hex.encode(digest.digest()))) throw new IOException("Empreinte du mod invalide");
            checkBlacklist(temporary);
            Files.move(temporary,mods.resolve(name),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            rememberReplacement(slug,manifest,version.projectId(),previous,name);
            installedProjects.put(version.projectId(),name); downloadProgress(name,size,file.size(),true);
        } finally { Files.deleteIfExists(temporary); }
    }
    private void checkBlacklist(Path file) throws IOException {
        String hash; try (InputStream input=Files.newInputStream(file)) { hash=Sha256.hex(input); }
        for (ModBlacklistEntry blocked:blacklist) if (hash.equalsIgnoreCase(blocked.hash())) throw new IOException("Mod interdit : "+blocked.reason());
    }
    private void rememberReplacement(String slug, ModpackManifest manifest, String project, String previous, String next) throws Exception {
        String base=ModOverrides.canonicalFilename(previous);
        boolean managed=manifest.files().stream().anyMatch(file->file.path().equals("mods/"+base) || file.path().equals("mods/"+next));
        r.modOverrides(slug).replacement(r.instance(slug),previous,next,managed);
        r.prefs.edit().putString("mod."+slug+"."+project,next).apply();
    }
    private String previousFile(String slug, Path mods, String project) throws Exception {
        String stored=r.prefs.getString("mod."+slug+"."+project,null);
        if (stored!=null) {
            String base=ModOverrides.canonicalFilename(stored);
            if (Files.isRegularFile(mods.resolve(base+".disabled"),LinkOption.NOFOLLOW_LINKS)) return base+".disabled";
            if (Files.isRegularFile(mods.resolve(base),LinkOption.NOFOLLOW_LINKS)) return base;
        }
        if (!projectsIdentified) {
            projectsIdentified=true; Map<String,String> paths=new LinkedHashMap<>();
            try (var files=Files.list(mods)) {
                for (Path file:files.filter(path->Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)).toList()) {
                    String name=file.getFileName().toString();
                    if (!name.endsWith(".jar") && !name.endsWith(".jar.disabled")) continue;
                    try (InputStream input=Files.newInputStream(file)) { paths.put(Sha1.hex(input),name); }
                }
            }
            if (!paths.isEmpty()) for (var identified:api.lookupByHashes(new ArrayList<>(paths.keySet())).entrySet()) {
                if (identified.getValue()!=null && identified.getValue().projectId()!=null)
                    installedProjects.put(identified.getValue().projectId(),paths.get(identified.getKey()));
            }
        }
        return installedProjects.get(project);
    }
}
