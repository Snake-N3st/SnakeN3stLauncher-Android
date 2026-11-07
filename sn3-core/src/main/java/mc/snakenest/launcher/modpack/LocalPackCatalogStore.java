package mc.snakenest.launcher.modpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.snakenest.launcher.util.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local launch metadata only. Session keys/tokens must remain in the encrypted session store. */
public final class LocalPackCatalogStore {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;
    private final Path file;
    private final String origin;
    private static final class Data {
        int schema=1;
        String origin;
        boolean remoteKnown;
        List<String> remoteSlugs=new ArrayList<>();
        Map<String,ModpackSummary> summaries=new LinkedHashMap<>();
        Map<String,ModpackManifest> manifests=new LinkedHashMap<>();
        Map<String,String> usernames=new LinkedHashMap<>();
    }
    public LocalPackCatalogStore(Path file,String siteOrigin) {
        this.file=Objects.requireNonNull(file).toAbsolutePath().normalize();
        this.origin=Objects.requireNonNull(siteOrigin).replaceAll("/+$", "");
        if(origin.isBlank())throw new IllegalArgumentException("Missing site origin");
    }
    private Data empty() { Data data=new Data(); data.origin=origin; return data; }
    private Data read() throws IOException {
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return empty();
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>MAX_CACHE_BYTES)
            throw new IOException("Invalid local pack catalogue");
        try {
            Data data=JSON.fromJson(Files.readString(file),Data.class);
            if(data==null || data.schema!=1 || data.origin==null)throw new IOException("Invalid local pack catalogue format");
            if(!origin.equals(data.origin))return empty();
            if(data.remoteSlugs==null || data.summaries==null || data.manifests==null || data.usernames==null)
                throw new IOException("Incomplete local pack catalogue");
            for(String slug:data.remoteSlugs)requireSlug(slug);
            for(var entry:data.summaries.entrySet()) {
                requireSlug(entry.getKey()); requireSummary(entry.getValue());
                if(!entry.getKey().equals(entry.getValue().slug()))throw new IOException("Mismatched cached pack identity");
            }
            for(var entry:data.manifests.entrySet()) { requireSlug(entry.getKey()); requireManifest(entry.getValue()); }
            for(var entry:data.usernames.entrySet()) {
                if(!entry.getKey().matches("[1-9][0-9]*") || !validUsername(entry.getValue()))throw new IOException("Invalid cached player identity");
            }
            return data;
        } catch(RuntimeException exception) { throw new IOException("Cannot read local pack catalogue",exception); }
    }
    private void write(Data data) throws IOException {
        byte[] bytes=JSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_CACHE_BYTES)throw new IOException("Local pack catalogue is too large");
        if(Files.isSymbolicLink(file))throw new IOException("Local pack catalogue must not be a symbolic link");
        AtomicFiles.write(file,bytes);
    }
    private static void requireSlug(String slug) {
        if(slug==null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))throw new IllegalArgumentException("Invalid modpack slug");
    }
    private static void requireSummary(ModpackSummary pack) { if(pack==null)throw new IllegalArgumentException("Missing pack summary"); requireSlug(pack.slug()); }
    private static void requireManifest(ModpackManifest manifest) {
        if(manifest==null || manifest.mcVersion()==null || !manifest.mcVersion().matches("[A-Za-z0-9_.+-]+")
                || manifest.loader()==null || manifest.loader().isBlank() || manifest.files()==null)
            throw new IllegalArgumentException("Incomplete installed pack launch metadata");
    }
    private static boolean validUsername(String name) { return name!=null && name.matches("[A-Za-z0-9_]{1,16}"); }

    /** Call exclusively after a successful catalogue response, including a genuinely empty catalogue. */
    public synchronized void saveRemote(List<ModpackSummary> remote) throws IOException {
        Objects.requireNonNull(remote); Set<String> unique=new HashSet<>();
        for(ModpackSummary pack:remote) { requireSummary(pack); if(!unique.add(pack.slug()))throw new IllegalArgumentException("Duplicate remote modpack"); }
        Data data=read(); data.remoteSlugs=new ArrayList<>();
        for(ModpackSummary pack:remote) { data.remoteSlugs.add(pack.slug()); data.summaries.put(pack.slug(),pack); }
        data.remoteKnown=true; write(data);
    }
    public synchronized void rememberSummary(ModpackSummary summary) throws IOException {
        requireSummary(summary); Data data=read(); data.summaries.put(summary.slug(),summary); write(data);
    }
    /** The manifest must describe a COMPLETED local installation, never an update merely fetched from the site. */
    public synchronized void saveManifest(String slug,ModpackManifest manifest) throws IOException {
        requireSlug(slug); requireManifest(manifest); Data data=read(); data.manifests.put(slug,manifest); write(data);
    }
    public synchronized Optional<ModpackManifest> loadManifest(String slug) throws IOException {
        requireSlug(slug); return Optional.ofNullable(read().manifests.get(slug));
    }
    /** Cached metadata, including historical packs. Filter by actual local installation before displaying offline. */
    public synchronized List<ModpackSummary> loadSummaries() throws IOException {
        Data data=read(); LinkedHashMap<String,ModpackSummary> result=new LinkedHashMap<>();
        for(String slug:data.remoteSlugs)if(data.summaries.containsKey(slug))result.put(slug,data.summaries.get(slug));
        data.summaries.forEach(result::putIfAbsent); return List.copyOf(result.values());
    }
    public synchronized List<ModpackSummary> localSummaries(Set<String> installedSlugs) throws IOException {
        Data data=read(); LinkedHashMap<String,ModpackSummary> result=new LinkedHashMap<>();
        for(String slug:installedSlugs)requireSlug(slug);
        for(ModpackSummary pack:loadSummaries())if(installedSlugs.contains(pack.slug()))result.put(pack.slug(),pack);
        for(String slug:new TreeSet<>(installedSlugs))if(!result.containsKey(slug)) {
            ModpackManifest manifest=data.manifests.get(slug);
            result.put(slug,new ModpackSummary(slug,slug,"Modpack installé localement",null,false,
                    manifest==null?null:manifest.version(),0));
        }
        return List.copyOf(result.values());
    }
    /** Merge live results with installed historical packs. An exception must never be passed as an empty remote result. */
    public synchronized List<ModpackSummary> mergeInstalled(List<ModpackSummary> remote,Set<String> installedSlugs) throws IOException {
        for(String slug:installedSlugs)requireSlug(slug);
        saveRemote(remote); LinkedHashMap<String,ModpackSummary> merged=new LinkedHashMap<>();
        for(ModpackSummary pack:remote)merged.put(pack.slug(),pack);
        for(ModpackSummary pack:localSummaries(installedSlugs))merged.putIfAbsent(pack.slug(),pack);
        return List.copyOf(merged.values());
    }
    /** Uses the last SUCCESSFUL remote snapshot, so a network error cannot mark every local pack as removed. */
    public synchronized Set<String> removedSlugs(Set<String> installedSlugs) throws IOException {
        for(String slug:installedSlugs)requireSlug(slug);
        Data data=read(); if(!data.remoteKnown)return Set.of();
        Set<String> removed=new HashSet<>(installedSlugs); removed.removeAll(data.remoteSlugs); return Set.copyOf(removed);
    }
    public synchronized void cacheUsername(long playerId,String username) throws IOException {
        if(playerId<=0 || !validUsername(username))throw new IllegalArgumentException("Invalid cached player identity");
        Data data=read(); data.usernames.put(Long.toString(playerId),username); write(data);
    }
    public synchronized Optional<String> loadUsername(long playerId) throws IOException {
        if(playerId<=0)return Optional.empty(); return Optional.ofNullable(read().usernames.get(Long.toString(playerId)));
    }
    public synchronized void removeInstalled(String slug) throws IOException {
        requireSlug(slug); Data data=read(); data.manifests.remove(slug);
        if(!data.remoteSlugs.contains(slug))data.summaries.remove(slug); write(data);
    }
}
