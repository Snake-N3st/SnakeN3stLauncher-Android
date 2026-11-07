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
    record InstallPlan(ModrinthVersion requested,List<ModrinthVersion> dependencies,long totalBytes) {
        InstallPlan { dependencies=List.copyOf(dependencies); }
    }
    InstallPlan plan(String slug,ModpackManifest manifest,ModrinthVersion requested)throws Exception {
        r.requireOnline(slug);SnakeRepository.requireSlug(slug);
        List<ModrinthVersion> active=new ArrayList<>();Path directory=r.instance(slug).resolve("mods");
        Map<String,String> hashes=new LinkedHashMap<>();
        if(Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))try(var files=Files.list(directory)) {
            for(Path file:files.filter(f->Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)&&f.getFileName().toString().endsWith(".jar")).toList()) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Préparation annulée");
                try(InputStream input=Files.newInputStream(file)){hashes.put(Sha1.hex(input),file.getFileName().toString());}
            }
        }
        if(!hashes.isEmpty())for(var entry:api.lookupByHashes(new ArrayList<>(hashes.keySet())).entrySet()) {
            if(entry.getValue()!=null){active.add(entry.getValue());installedProjects.put(entry.getValue().projectId(),hashes.get(entry.getKey()));}
        }
        List<ModrinthVersion> dependencies=ModInstallPlanner.dependencies(requested,manifest.mcVersion(),manifest.loader(),active,new ModInstallPlanner.Resolver(){
            public List<ModrinthVersion> versions(String project)throws Exception{return api.versions(project);}
            public ModrinthVersion version(String id)throws Exception{return api.getVersion(id);}
        });
        long size=validatedFile(requested).size();
        for(var version:dependencies)size=Math.addExact(size,validatedFile(version).size());
        return new InstallPlan(requested,dependencies,size);
    }
    void install(String slug, ModpackManifest manifest, ModrinthVersion version) throws Exception {
        install(slug,manifest,version,(Path)null);
    }
    void install(String slug, ModpackManifest manifest, ModrinthVersion version, Path replacedFile) throws Exception {
        install(slug,manifest,plan(slug,manifest,version),replacedFile);
    }
    void install(String slug,ModpackManifest manifest,InstallPlan plan,Path replacedFile)throws Exception {
        r.requireOnline(slug);
        if (Sn3GameBridge.isRunning(r.context)) throw new IOException("Ferme Minecraft avant de modifier les mods.");
        SnakeRepository.requireSlug(slug);
        for(var version:plan.dependencies()){if(!version.isCompatible(manifest.mcVersion(),manifest.loader()))throw new IOException("Dépendance incompatible : "+version.name());validatedFile(version);}
        if(!plan.requested().isCompatible(manifest.mcVersion(),manifest.loader()))throw new IOException("Version incompatible : "+plan.requested().name());validatedFile(plan.requested());
        blacklist=r.api().getBlacklist(slug,r.requireSession());
        // Apply only the versions shown in the confirmation, without a second resolution.
        for(var dependency:plan.dependencies()){installFile(slug,manifest,dependency,null,false);enableInstalled(slug,manifest,dependency.projectId());}
        installFile(slug,manifest,plan.requested(),replacedFile,true);enableInstalled(slug,manifest,plan.requested().projectId());
    }
    void importJar(String slug,ModpackManifest manifest,InputStream input,String displayName)throws Exception {
        r.requireOnline(slug);SnakeRepository.requireSlug(slug);
        if(Sn3GameBridge.isRunning(r.context))throw new IOException("Ferme Minecraft avant de modifier les mods.");
        String filename=ModJarImport.filename(displayName);Path mods=modsDirectory(slug);
        blacklist=r.api().getBlacklist(slug,r.requireSession());
        Path temporary=Files.createTempFile(mods,"sn3-import-",".tmp");
        try {
            ModJarImport.stage(input,temporary,ModJarImport.MAX_BYTES);checkBlacklist(temporary);
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import annulé");
            Files.move(temporary,mods.resolve(filename),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            rememberReplacement(slug,manifest,null,filename,filename);
            r.status="Importé : "+filename;r.changed();
        }finally{Files.deleteIfExists(temporary);}
    }
    private Path modsDirectory(String slug)throws IOException {
        Path instance=r.instance(slug),mods=instance.resolve("mods").toAbsolutePath().normalize();
        if(Files.isSymbolicLink(instance)||Files.isSymbolicLink(mods))throw new IOException("Dossier de mods non sûr");
        Files.createDirectories(mods);return mods;
    }
    private void enableInstalled(String slug,ModpackManifest manifest,String project)throws Exception {
        String name=installedProjects.get(project);if(name==null)return;
        String base=ModOverrides.canonicalFilename(name);Path mods=modsDirectory(slug);
        if(Files.isRegularFile(mods.resolve(base+".disabled"),LinkOption.NOFOLLOW_LINKS)) {
            boolean managed=manifest.files().stream().anyMatch(f->f.path().equals("mods/"+base));
            r.modOverrides(slug).change(r.instance(slug),base+".disabled",false,managed);
        }
        r.prefs.edit().putString("mod."+slug+"."+project,base).apply();installedProjects.put(project,base);
    }
    private ModrinthVersionFile validatedFile(ModrinthVersion version)throws IOException {
        ModrinthVersionFile file=version.primaryFile().orElseThrow(()->new IOException("Fichier du mod absent"));
        String name=ModJarImport.filename(file.filename());
        if(file.size()<0||file.size()>ModJarImport.MAX_BYTES)throw new IOException("Taille du mod invalide : "+name);
        URI uri;try{uri=URI.create(file.url());}catch(RuntimeException failure){throw new IOException("Adresse du mod invalide",failure);}
        if(!"https".equals(uri.getScheme())||uri.getHost()==null)throw new IOException("Téléchargement HTTPS requis");
        if(file.hashes()==null)throw new IOException("Empreinte du mod absente");
        String sha512=file.hashes().get("sha512"),sha1=file.sha1();
        if(sha512!=null?!sha512.matches("[0-9a-fA-F]{128}"):sha1==null||!sha1.matches("[0-9a-fA-F]{40}"))throw new IOException("Empreinte du mod invalide");
        return file;
    }
    private void downloadProgress(String name, long received, long total, boolean verified) {
        int percent = verified ? 100 : total > 0 ? (int)Math.min(99, received * 99.0 / total) : 0;
        mc.snakenest.launcher.android.progresskeeper.ProgressKeeper.submitProgress("sn3", percent,
                mc.snakenest.launcher.android.R.string.sn3_download_mod_progress,
                name, received / 1048576.0, total / 1048576.0);
    }
    private void installFile(String slug, ModpackManifest manifest, ModrinthVersion version,
                         Path replacedFile, boolean requested) throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Installation annulée");
        if (!version.isCompatible(manifest.mcVersion(),manifest.loader())) throw new IOException("Mod incompatible : "+version.name());
        ModrinthVersionFile file=validatedFile(version);
        String name=file.filename(); ModOverrides.canonicalFilename(name);
        String expected=file.hashes().get("sha512"); String algorithm="SHA-512";
        if (expected==null) { expected=file.sha1(); algorithm="SHA-1"; }
        if (expected==null) throw new IOException("Empreinte du mod absente");
        if (!"https".equals(URI.create(file.url()).getScheme())) throw new IOException("Téléchargement HTTPS requis");
        Path mods=modsDirectory(slug);
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
        if(previous!=null&&previous.endsWith(".disabled")) {
            Path disabled=mods.resolve(previous);
            if(Files.isRegularFile(disabled,LinkOption.NOFOLLOW_LINKS)&&Files.size(disabled)==file.size()) {
                MessageDigest digest=MessageDigest.getInstance(algorithm);
                try(InputStream input=Files.newInputStream(disabled)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Installation annulée");digest.update(buffer,0,count);}}
                if(expected.equalsIgnoreCase(Hex.encode(digest.digest()))) {
                    checkBlacklist(disabled);installedProjects.put(version.projectId(),previous);enableInstalled(slug,manifest,version.projectId());
                    r.status="Réactivé : "+previous;r.changed();return;
                }
            }
        }
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
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Installation annulée");
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
        if(project!=null)r.prefs.edit().putString("mod."+slug+"."+project,next).apply();
        else {
            var edit=r.prefs.edit();for(var entry:r.prefs.getAll().entrySet())if(entry.getKey().startsWith("mod."+slug+".")&&next.equals(entry.getValue()))edit.remove(entry.getKey());edit.apply();
        }
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
