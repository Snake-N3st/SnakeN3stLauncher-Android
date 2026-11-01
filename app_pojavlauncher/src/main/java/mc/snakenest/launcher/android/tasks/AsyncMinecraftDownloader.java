package mc.snakenest.launcher.android.tasks;

import mc.snakenest.launcher.android.JMinecraftVersionList;
import mc.snakenest.launcher.android.extra.ExtraConstants;
import mc.snakenest.launcher.android.extra.ExtraCore;
import mc.snakenest.launcher.android.value.launcherprofiles.MinecraftProfile;

public class AsyncMinecraftDownloader {
    public static String normalizeVersionId(String versionString) {
        JMinecraftVersionList versionList = (JMinecraftVersionList) ExtraCore.getValue(ExtraConstants.RELEASE_TABLE);
        if(versionList == null || versionList.versions == null) return versionString;
        if(MinecraftProfile.LATEST_RELEASE.equals(versionString)) versionString = versionList.latest.get("release");
        if(MinecraftProfile.LATEST_SNAPSHOT.equals(versionString)) versionString = versionList.latest.get("snapshot");
        return versionString;
    }

    public static JMinecraftVersionList.Version getListedVersion(String normalizedVersionString) {
        JMinecraftVersionList versionList = (JMinecraftVersionList) ExtraCore.getValue(ExtraConstants.RELEASE_TABLE);
        if(versionList == null || versionList.versions == null) return null; // can't have listed versions if there's no list
        for(JMinecraftVersionList.Version version : versionList.versions) {
            if(version.id.equals(normalizedVersionString)) return version;
        }
        return null;
    }

    public interface DoneListener{
        void onDownloadDone();
        void onDownloadFailed(Throwable throwable);
    }
}
