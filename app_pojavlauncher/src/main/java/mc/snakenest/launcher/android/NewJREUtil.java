package mc.snakenest.launcher.android;

import static mc.snakenest.launcher.android.Architecture.archAsString;
import static mc.snakenest.launcher.android.Architecture.getDeviceArchitecture;
import static mc.snakenest.launcher.android.Tools.NATIVE_LIB_DIR;
import static mc.snakenest.launcher.android.Tools.isOnline;

import android.app.Activity;
import android.content.Context;
import com.kdt.mcgui.ProgressLayout;
import mc.snakenest.launcher.android.multirt.MultiRTUtils;
import mc.snakenest.launcher.android.multirt.Runtime;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.android.progresskeeper.DownloaderProgressWrapper;
import mc.snakenest.launcher.android.utils.DownloadUtils;
import mc.snakenest.launcher.android.utils.MathUtils;
import mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles;
import mc.snakenest.launcher.android.value.launcherprofiles.MinecraftProfile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

/** Reuses an installed runtime, or downloads the required Java only when installing a game. */
public final class NewJREUtil {
    /** Name from previous APKs. Existing installations keep it; this APK contains no Java. */
    public static final String BUNDLED_RUNTIME_NAME = "Internal-21";
    private NewJREUtil() { }

    /** Platform-independent decision seam shared by real provisioning and isolated device tests. */
    public interface RuntimeAccess {
        int version(String name);
        String findInstalled(int minimumVersion);
        void download(int majorVersion);
    }

    public static String chooseRuntime(int required, String selected, RuntimeAccess access) {
        if(required<8) required=8;
        if(selected!=null && !selected.isBlank() && access.version(selected)>=required) return selected;
        String installed=access.findInstalled(required);
        if(installed!=null && access.version(installed)>=required) return installed;
        int downloadable=0;
        for(int version:new int[]{8,17,21,25}) if(version>=required) { downloadable=version; break; }
        if(downloadable==0) throw new IllegalStateException("No downloadable Java runtime for version "+required);
        access.download(downloadable);
        installed=access.findInstalled(required);
        if(installed==null || access.version(installed)<required) throw new IllegalStateException("Java "+required+" installation did not provide a usable runtime");
        return installed;
    }

    /** Does disk/network work; call from the installation worker, never from the UI thread. */
    public static Runtime ensureRuntime(Context context,int required,String preferred) {
        String selected=chooseRuntime(required,preferred,new RuntimeAccess() {
            @Override public int version(String name) { return usableVersion(name); }
            @Override public String findInstalled(int minimumVersion) {
                var candidates=MultiRTUtils.getInstalledRuntimes().stream().filter(runtime->usableVersion(runtime.name)>=minimumVersion).toList();
                var nearest=MathUtils.findNearestPositive(minimumVersion,candidates,runtime->runtime.javaVersion);
                return nearest==null?null:nearest.value.name;
            }
            @Override public void download(int majorVersion) { tryDownloadRuntime(context,majorVersion); }
        });
        Runtime runtime=MultiRTUtils.forceReread(selected);
        if(LauncherPreferences.PREF_DEFAULT_RUNTIME==null || LauncherPreferences.PREF_DEFAULT_RUNTIME.isBlank()) {
            LauncherPreferences.PREF_DEFAULT_RUNTIME=selected;
            LauncherPreferences.DEFAULT_PREF.edit().putString("defaultRuntime",selected).apply();
        }
        return runtime;
    }

    private static int usableVersion(String name) {
        if(name==null || name.isBlank()) return 0;
        Runtime runtime=MultiRTUtils.forceReread(name);
        if(runtime.versionString==null || runtime.javaVersion<8) return 0;
        try(var files=Files.walk(MultiRTUtils.getRuntimeHome(name).toPath(),5)) {
            return files.anyMatch(path->path.getFileName().toString().equals("libjvm.so") && Files.isRegularFile(path))?runtime.javaVersion:0;
        } catch(IOException ignored) { return 0; }
    }

    /** Called by the Minecraft installer after reading the required Java version from local metadata. */
    public static boolean installNewJreIfNeeded(Activity activity, JMinecraftVersionList.Version versionInfo) {
        int required=versionInfo.javaVersion==null?8:versionInfo.javaVersion.majorVersion;
        LauncherProfiles.load();
        MinecraftProfile profile=LauncherProfiles.getCurrentProfile();
        String selectedBefore=Tools.getSelectedRuntime(profile);
        Runtime runtime=ensureRuntime(activity,required,selectedBefore);
        if(!runtime.name.equals(selectedBefore)
                && (profile.javaDir!=null || !runtime.name.equals(LauncherPreferences.PREF_DEFAULT_RUNTIME))) {
            profile.javaDir=Tools.LAUNCHERPROFILES_RTPREFIX+runtime.name;
            LauncherProfiles.write();
        }
        return true;
    }

    public static boolean isJavaVersionAvailableForDownload(int version) { return version==8 || version==17 || version==21 || version==25; }

    private static String getJreSource(int javaVersion,String arch) {
        return String.format("https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre%1$s/jre%1$s-android-%2$s.tar.xz",javaVersion,arch);
    }

    private static synchronized void tryDownloadRuntime(Context context,int javaVersion) {
        if(!isOnline(context)) throw new IllegalStateException(context.getString(R.string.multirt_no_internet));
        String arch=archAsString(getDeviceArchitecture());
        if(!isJavaVersionAvailableForDownload(javaVersion)) throw new IllegalArgumentException("Unavailable Java version");
        if(getDeviceArchitecture()==Architecture.ARCH_X86 && javaVersion>=25) throw new IllegalStateException("x86 is not supported on Java "+javaVersion);
        String name="External-"+javaVersion;
        if(usableVersion(name)>=javaVersion) return;
        File archive=new File(Tools.DIR_CACHE,String.format("jre%s-android-%s.tar.xz",javaVersion,arch));
        try {
            DownloaderProgressWrapper monitor=new DownloaderProgressWrapper(R.string.newdl_downloading_jre_runtime,ProgressLayout.UNPACK_RUNTIME);
            monitor.extraString=Integer.toString(javaVersion);
            DownloadUtils.downloadFileMonitored(getJreSource(javaVersion,arch),archive,null,monitor);
            try(FileInputStream input=new FileInputStream(archive)) { MultiRTUtils.installRuntimeNamed(NATIVE_LIB_DIR,input,name); }
            MultiRTUtils.forceReread(name);
            MultiRTUtils.postPrepare(name);
            if(usableVersion(name)<javaVersion) throw new IOException("Downloaded runtime is incomplete");
        } catch(IOException failure) {
            throw new IllegalStateException("Failed to download Java "+javaVersion+" for "+arch,failure);
        } finally {
            // The extracted runtime is retained; its archive has no further use.
            archive.delete();
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
        }
    }

    public enum ExternalRuntime {
        JRE_8(8,"External-8"), JRE_17(17,"External-17"), JRE_21(21,"External-21"), JRE_25(25,"External-25");
        public final int majorVersion;
        public final String name;
        public final String downloadLink;
        public boolean isDownloading=false;
        ExternalRuntime(int version,String name) { this.majorVersion=version;this.name=name;this.downloadLink=getJreSource(version,archAsString(getDeviceArchitecture())); }
        public void downloadRuntime(Context context) { tryDownloadRuntime(context,majorVersion); }
    }
}
