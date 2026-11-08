package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.modpack.ModpackSettings;
import mc.snakenest.launcher.modpack.ModpackSettingsStore;
import mc.snakenest.launcher.util.AppDirs;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Objects;

/** Independent per-pack opt-ins over Amethyst's current global JVM preferences. */
public final class SnakeJvmSettings {
    private static final Object LOCK=new Object();
    private final SharedPreferences flags;
    private final SharedPreferences globals;
    private final AppDirs dirs;
    private final ModpackSettingsStore store;
    private java.nio.file.Path profilesFile;
    public SnakeJvmSettings(Context context) {
        this(context.getApplicationContext().getSharedPreferences("sn3",Context.MODE_PRIVATE),
                PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()),
                new AppDirs(context.getApplicationContext().getNoBackupFilesDir().toPath().resolve("snake-n3st")));
        String file=mc.snakenest.launcher.android.Tools.GAME_PROFILES_FILE;
        if(file!=null)profilesFile=java.nio.file.Path.of(file);
    }
    /** Isolated storage seam for instrumentation; never consults another preference store. */
    SnakeJvmSettings(SharedPreferences flags,SharedPreferences globals,AppDirs dirs) {
        this.flags=Objects.requireNonNull(flags);this.globals=Objects.requireNonNull(globals);
        this.dirs=Objects.requireNonNull(dirs);this.store=new ModpackSettingsStore(dirs);
    }
    private static String memoryKey(String slug){return "jvm.memoryOverride."+slug;}
    private static String argumentsKey(String slug){return "jvm.argumentsOverride."+slug;}
    /** Call before creating a first settings file: legacy files opt in, new packs inherit globals. */
    public void initialFlags(String slug) {
        SnakeRepository.requireSlug(slug);
        synchronized(LOCK) {
            boolean memoryKnown=flags.contains(memoryKey(slug)),argumentsKnown=flags.contains(argumentsKey(slug));
            if(memoryKnown&&argumentsKnown)return;
            boolean legacy=Files.isRegularFile(dirs.modpackSettingsFile(slug),LinkOption.NOFOLLOW_LINKS);
            ModpackSettings settings=store.load(slug,ModpackSettings.defaults());
            if(legacy&&!argumentsKnown)settings=preserveNativeArguments(slug,settings);
            SharedPreferences.Editor edit=flags.edit();
            if(!memoryKnown)edit.putBoolean(memoryKey(slug),legacy);
            if(!argumentsKnown)edit.putBoolean(argumentsKey(slug),legacy&&settings.extraJvmArgs()!=null&&!settings.extraJvmArgs().isBlank());
            if(!edit.commit())throw new IllegalStateException("Impossible de mémoriser les réglages JVM de ce modpack.");
        }
    }
    private ModpackSettings preserveNativeArguments(String slug,ModpackSettings saved) {
        if(profilesFile==null)return saved;
        try {
            if(!Files.isRegularFile(profilesFile,LinkOption.NOFOLLOW_LINKS)||Files.size(profilesFile)>4*1024*1024)return saved;
            var profiles=mc.snakenest.launcher.android.Tools.GLOBAL_GSON.fromJson(Files.readString(profilesFile),mc.snakenest.launcher.android.value.launcherprofiles.MinecraftLauncherProfiles.class);
            String id=java.util.UUID.nameUUIDFromBytes(("sn3:"+slug).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            var profile=profiles==null||profiles.profiles==null?null:profiles.profiles.get(id);
            if(profile==null||profile.gameDir==null||!java.nio.file.Path.of(profile.gameDir).toAbsolutePath().normalize().equals(dirs.instance(slug).toAbsolutePath().normalize()))return saved;
            String args=profile.javaArgs;
            if(args==null||args.isBlank()||args.contains("sn3.token")||args.equals(saved.extraJvmArgs()))return saved;
            ModpackSettings migrated=new ModpackSettings(saved.memoryMb(),args,saved.channel(),saved.javaPath());
            store.save(slug,migrated);
            return migrated.equals(store.load(slug,null))?migrated:saved;
        }catch(Exception ignored){return saved;}
    }
    public boolean memoryOverride(String slug){initialFlags(slug);return flags.getBoolean(memoryKey(slug),false);}
    public boolean argumentsOverride(String slug){initialFlags(slug);return flags.getBoolean(argumentsKey(slug),false);}
    public int globalMemory() {
        int fallback=LauncherPreferences.PREF_RAM_ALLOCATION>0?LauncherPreferences.PREF_RAM_ALLOCATION:ModpackSettings.DEFAULT_MEMORY_MB;
        return Math.max(256,globals.getInt("allocation",fallback));
    }
    public String globalArguments(){return Objects.toString(globals.getString("javaArgs",""),"");}
    public ModpackSettings resolve(String slug) {
        synchronized(LOCK) {
            initialFlags(slug);
            ModpackSettings saved=store.load(slug,ModpackSettings.defaults());
            int memory=flags.getBoolean(memoryKey(slug),false)?Math.max(256,saved.memoryMb()):globalMemory();
            String args=flags.getBoolean(argumentsKey(slug),false)?Objects.toString(saved.extraJvmArgs(),""):globalArguments();
            validateArguments(args);
            return new ModpackSettings(memory,args,saved.channel(),saved.javaPath());
        }
    }
    public void save(String slug,int memory,String args,boolean overrideMemory,boolean overrideArgs) {
        SnakeRepository.requireSlug(slug);
        if(memory<256)throw new IllegalArgumentException("La mémoire doit être d’au moins 256 Mo.");
        String arguments=Objects.toString(args,"");validateArguments(arguments);
        synchronized(LOCK) {
            initialFlags(slug);
            ModpackSettings previous=store.load(slug,ModpackSettings.defaults());
            ModpackSettings saved=new ModpackSettings(memory,arguments,previous.channel(),previous.javaPath());
            store.save(slug,saved);
            // ModpackSettingsStore's legacy API logs I/O failures; the UI must not report a false success.
            if(!saved.equals(store.load(slug,null)))throw new IllegalStateException("Impossible d’enregistrer les réglages JVM du modpack.");
            if(!flags.edit().putBoolean(memoryKey(slug),overrideMemory).putBoolean(argumentsKey(slug),overrideArgs).commit())
                throw new IllegalStateException("Impossible de mémoriser les options JVM de ce modpack.");
        }
    }
    static String validateArguments(String arguments) {
        if(arguments.contains("sn3.token"))throw new IllegalArgumentException("sn3.token est géré par le launcher.");
        return arguments;
    }
}
