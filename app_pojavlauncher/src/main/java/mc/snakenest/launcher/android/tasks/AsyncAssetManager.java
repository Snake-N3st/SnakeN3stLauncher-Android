package mc.snakenest.launcher.android.tasks;


import static mc.snakenest.launcher.android.Architecture.archAsString;
import static mc.snakenest.launcher.android.Architecture.archAsStringAndroid;
import static mc.snakenest.launcher.android.Architecture.getDeviceArchitecture;
import static mc.snakenest.launcher.android.PojavApplication.sExecutorService;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.multirt.MultiRTUtils;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class AsyncAssetManager {

    private AsyncAssetManager(){}

    /** Install the bundled Java 21 without changing any existing runtime or profile selection. */
    public static void unpackRuntime(AssetManager am) {
        sExecutorService.execute(() -> {
            if (!mc.snakenest.launcher.android.NewJREUtil.ensureBundledRuntime(am)) {
                Log.e("JREAuto", "Bundled Java 21 could not be prepared");
            }
        });
    }

    /** Unpack single files, with no regard to version tracking */
    public static void unpackSingleFiles(Context ctx){
        ProgressLayout.setProgress(ProgressLayout.EXTRACT_SINGLE_FILES, 0);
        sExecutorService.execute(() -> {
            try {
                Tools.copyAssetFile(ctx, "options.txt", Tools.DIR_GAME_NEW, false);

                // This is disgusting, but am lazy. We probably wont be getting any updates to
                // controlmap till rewrite anyway so this is fiiine.
                try (InputStream is = ctx.getAssets().open("default.json")) {
                    String assetSha1 = new String(org.apache.commons.codec.binary.Hex.encodeHex(org.apache.commons.codec.digest.DigestUtils.sha1(is)));
                    if (!Tools.compareSHA1(new File(Tools.CTRLDEF_FILE), assetSha1)) {
                        Tools.copyAssetFile(ctx, "default.json", Tools.CTRLMAP_PATH, "new_default.json" , false);
                    } else if (!new File(Tools.CTRLMAP_PATH+"/new_default.json").exists())
                    Tools.copyAssetFile(ctx, "default.json", Tools.CTRLMAP_PATH, false);
                }

                Tools.copyAssetFile(ctx, "launcher_profiles.json", Tools.DIR_GAME_NEW, false);
                Tools.copyAssetFile(ctx,"resolv.conf",Tools.DIR_DATA, false);
            } catch (IOException e) {
                Log.e("AsyncAssetManager", "Failed to unpack critical components !");
            }
            ProgressLayout.clearProgress(ProgressLayout.EXTRACT_SINGLE_FILES);
        });
    }

    public static void unpackComponents(Context ctx){
        ProgressLayout.setProgress(ProgressLayout.EXTRACT_COMPONENTS, 0);
        sExecutorService.execute(() -> {
            try {
                unpackComponent(ctx, "caciocavallo", false);
                unpackComponent(ctx, "caciocavallo17", false);
                // Since the Java module system doesn't allow multiple JARs to declare the same module,
                // we repack them to a single file here
                unpackLwjglNatives(ctx);
                unpackComponent(ctx, "lwjgl3/3.3.3", false);
                unpackComponent(ctx, "lwjgl3/3.4.1", false);
                unpackComponent(ctx, "security", true);
                unpackComponent(ctx, "arc_dns_injector", true);
                unpackComponent(ctx, "MioLibPatcher", true);
                unpackComponent(ctx, "forge_installer", true);
            } catch (IOException e) {
                Log.e("AsyncAssetManager", "Failed to unpack components !",e );
            }
            ProgressLayout.clearProgress(ProgressLayout.EXTRACT_COMPONENTS);
        });
    }
    // Piggybacks off of the java modules extracting later to use their version files for update checks
    // This is indeed prone to breaking.
    private static void unpackLwjglNatives(Context ctx) throws IOException {
        AssetManager am = ctx.getAssets();
        String rootDir = Tools.DIR_DATA;
        String sArch = archAsStringAndroid(getDeviceArchitecture());

        String[] lwjglVersions = {"3.3.3", "3.4.1"};
        for (String lwjglVer : lwjglVersions) {
            File versionFile = new File(Tools.DIR_GAME_HOME + String.format("/lwjgl3/%s/version", lwjglVer));
            InputStream is = am.open("components/lwjgl3/" + lwjglVer + "/version");
            String pathToLwjglNatives = String.format("lwjgl-%s-natives/", lwjglVer) + sArch;

            boolean shouldUpdate = true;
            if (versionFile.exists()) {
                FileInputStream fis = new FileInputStream(versionFile);
                String release1 = Tools.read(is);
                String release2 = Tools.read(fis);
                if (release1.equals(release2))
                    shouldUpdate = false;
            }

            if (shouldUpdate) {
                Log.i("UnpackLwjgl", lwjglVer + " was installed manually, or does not exist, unpacking new...");
                String[] fileList = am.list("components/" + pathToLwjglNatives);
                for (String fileName : fileList) {
                    Tools.copyAssetFile(ctx, "components/" + pathToLwjglNatives + "/" + fileName, rootDir + "/" + pathToLwjglNatives, true);
                }
            } else {
                Log.i("UnpackLwjgl", lwjglVer + " is up-to-date with the launcher, continuing...");
            }
        }
    }

    private static void unpackComponent(Context ctx, String component, boolean privateDirectory) throws IOException {
        AssetManager am = ctx.getAssets();
        String rootDir = privateDirectory ? Tools.DIR_DATA : Tools.DIR_GAME_HOME;

        File versionFile = new File(rootDir + "/" + component + "/version");
        InputStream is = am.open("components/" + component + "/version");
        if(!versionFile.exists()) {
            if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                FileUtils.deleteDirectory(versionFile.getParentFile());
            }
            versionFile.getParentFile().mkdir();

            Log.i("UnpackPrep", component + ": Pack was installed manually, or does not exist, unpacking new...");
            String[] fileList = am.list("components/" + component);
            for(String s : fileList) {
                Tools.copyAssetFile(ctx, "components/" + component + "/" + s, rootDir + "/" + component, true);
            }
        } else {
            FileInputStream fis = new FileInputStream(versionFile);
            String release1 = Tools.read(is);
            String release2 = Tools.read(fis);
            if (!release1.equals(release2)) {
                if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                    FileUtils.deleteDirectory(versionFile.getParentFile());
                }
                versionFile.getParentFile().mkdir();

                String[] fileList = am.list("components/" + component);
                for (String fileName : fileList) {
                    Tools.copyAssetFile(ctx, "components/" + component + "/" + fileName, rootDir + "/" + component, true);
                }
            } else {
                Log.i("UnpackPrep", component + ": Pack is up-to-date with the launcher, continuing...");
            }
        }
    }
}
