package mc.snakenest.launcher.modpack;

import com.google.gson.Gson;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Sha256;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Player mod choices, stored outside the synced instance without changing its signed manifest. */
public final class ModOverrides {
    public record Choice(String originalFilename, String filename, boolean disabled, boolean removed, boolean managed) { }
    private record Entry(String originalFilename, String filename, String backupHash, boolean disabled, boolean removed, boolean managed) { }
    private static final class Data { Map<String, Entry> choices = new LinkedHashMap<>(); }
    private static final Gson GSON = new Gson();
    private final Path root;
    private final Path state;
    private final Data data;

    public ModOverrides(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.state = this.root.resolve("choices.json");
        if (Files.isSymbolicLink(this.root) || Files.isSymbolicLink(state)) throw new IOException("Unsafe mod override directory");
        Data loaded = new Data();
        if (Files.isRegularFile(state, LinkOption.NOFOLLOW_LINKS)) try {
            loaded = GSON.fromJson(Files.readString(state), Data.class);
            if (loaded == null || loaded.choices == null) throw new IOException("Invalid mod choices");
            for (var item : loaded.choices.entrySet()) {
                Entry entry = item.getValue();
                if (entry == null || !item.getKey().equals(entry.originalFilename())) throw new IOException("Invalid mod choice");
                requireFilename(entry.originalFilename()); requireFilename(entry.filename());
                if (entry.backupHash() != null && !entry.backupHash().matches("[0-9a-f]{64}")) throw new IOException("Invalid mod backup hash");
            }
        } catch (RuntimeException exception) { throw new IOException("Cannot read mod choices", exception); }
        data = loaded;
    }
    public Map<String, Choice> choices() {
        Map<String, Choice> result = new LinkedHashMap<>();
        for (Entry entry : data.choices.values()) result.put(entry.originalFilename(), choice(entry));
        return Collections.unmodifiableMap(result);
    }
    public Set<String> ignoredModPaths() {
        Set<String> result = new HashSet<>();
        for (Entry entry : data.choices.values()) if (entry.disabled() || entry.removed() || entry.backupHash() != null) {
            result.add("mods/"+entry.originalFilename()); result.add("mods/"+entry.filename());
        }
        return Collections.unmodifiableSet(result);
    }
    public Optional<Choice> choiceFor(String filename) {
        String base = canonicalFilename(filename); Entry entry = find(base);
        return entry == null ? Optional.empty() : Optional.of(choice(entry));
    }
    private static Choice choice(Entry entry) { return new Choice(entry.originalFilename(),entry.filename(),entry.disabled(),entry.removed(),entry.managed()); }
    private Entry find(String name) {
        Entry exact = data.choices.get(name);
        if (exact != null) return exact;
        return data.choices.values().stream().filter(entry -> entry.filename().equals(name)).findFirst().orElse(null);
    }
    public static String canonicalFilename(String filename) {
        String name = filename != null && filename.endsWith(".disabled") ? filename.substring(0,filename.length()-9) : filename;
        requireFilename(name); return name;
    }
    private static void requireFilename(String filename) {
        if (filename == null || !filename.endsWith(".jar") || filename.length() > 255 || filename.indexOf('/') >= 0
                || filename.indexOf('\\') >= 0 || filename.indexOf('\0') >= 0 || filename.indexOf('\n') >= 0 || filename.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Unsafe mod filename");
    }
    private static Path mods(Path instance) throws IOException {
        if (Files.isSymbolicLink(instance)) throw new IOException("Instance must not be a symbolic link");
        Path directory = instance.toAbsolutePath().normalize().resolve("mods");
        if (Files.isSymbolicLink(directory)) throw new IOException("Mods directory must not be a symbolic link");
        Files.createDirectories(directory); return directory;
    }
    private void save() throws IOException {
        if (Files.isSymbolicLink(root)) throw new IOException("Unsafe mod override directory");
        AtomicFiles.write(state, GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
    }
    /** Toggle/remove a real direct-child jar. The decision is durable before filesystem application. */
    public void change(Path instance, String filename, boolean remove, boolean managed) throws IOException {
        String base = canonicalFilename(filename); Path directory = mods(instance);
        Path supplied = directory.resolve(filename);
        if (!Files.isRegularFile(supplied, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Mod file is missing or not a regular file");
        Entry previous = find(base);
        String original = previous == null ? base : previous.originalFilename();
        boolean disabled = !filename.endsWith(".disabled");
        Entry next = new Entry(original,base,previous == null ? null : previous.backupHash(),remove ? false : disabled,remove,
                managed || previous != null && previous.managed());
        data.choices.put(original,next); save(); apply(instance);
    }
    /** Remember an already verified Modrinth replacement, including renamed managed jars. */
    public void replacement(Path instance, String previousFilename, String replacementFilename, boolean managed) throws IOException {
        String previousName = canonicalFilename(previousFilename); requireFilename(replacementFilename);
        Entry previous = find(previousName);
        String original = previous == null ? previousName : previous.originalFilename();
        boolean disabled = previous != null ? previous.disabled() : previousFilename.endsWith(".disabled");
        boolean official = managed || previous != null && previous.managed();
        Path directory = mods(instance); Path replacement = directory.resolve(replacementFilename);
        if (!Files.isRegularFile(replacement, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Replacement jar is missing");
        String hash = null;
        if (official) {
            try (var input = Files.newInputStream(replacement)) { hash = Sha256.hex(input); }
            Path files = root.resolve("files");
            if (Files.isSymbolicLink(files)) throw new IOException("Unsafe mod backup directory");
            Files.createDirectories(files);
            try (var input = Files.newInputStream(replacement)) { AtomicFiles.writeVerified(files.resolve(hash+".jar"),input,hash); }
        }
        data.choices.put(original,new Entry(original,replacementFilename,hash,disabled,false,official)); save();
        if (!previousName.equals(replacementFilename)) deleteVariants(directory,previousName);
        apply(instance);
    }
    /** Reset only choices for server-provided mods; personal additions and disabled state survive repair. */
    public void resetManaged(Path instance) throws IOException {
        if (data.choices.values().stream().noneMatch(Entry::managed)) return;
        Path directory = mods(instance);
        for (Entry entry : new ArrayList<>(data.choices.values())) if (entry.managed()) {
            deleteVariants(directory,entry.originalFilename()); deleteVariants(directory,entry.filename());
            data.choices.remove(entry.originalFilename());
        }
        save();
    }
    public void apply(Path instance) throws IOException {
        if (data.choices.isEmpty()) return;
        Path directory = mods(instance);
        for (Entry entry : data.choices.values()) {
            if (entry.removed()) {
                deleteVariants(directory,entry.originalFilename()); deleteVariants(directory,entry.filename()); continue;
            }
            Path active = directory.resolve(entry.filename()); Path disabled = directory.resolve(entry.filename()+".disabled");
            if (entry.backupHash() != null) {
                if (!entry.originalFilename().equals(entry.filename())) deleteVariants(directory,entry.originalFilename());
                Path backup = root.resolve("files").resolve(entry.backupHash()+".jar");
                if (Files.isSymbolicLink(root.resolve("files")) || !Files.isRegularFile(backup,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Custom mod backup is unavailable");
                Path destination = entry.disabled() ? disabled : active;
                try (var input = Files.newInputStream(backup)) { AtomicFiles.writeVerified(destination,input,entry.backupHash()); }
                Files.deleteIfExists(entry.disabled() ? active : disabled);
            } else if (entry.disabled()) {
                if (Files.isRegularFile(active,LinkOption.NOFOLLOW_LINKS)) Files.move(active,disabled,StandardCopyOption.REPLACE_EXISTING);
            } else if (Files.exists(disabled,LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.exists(active,LinkOption.NOFOLLOW_LINKS)) Files.move(disabled,active);
                else Files.delete(disabled);
            }
        }
    }
    private static void deleteVariants(Path directory, String filename) throws IOException {
        Files.deleteIfExists(directory.resolve(filename)); Files.deleteIfExists(directory.resolve(filename+".disabled"));
    }
}
