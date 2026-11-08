package mc.snakenest.launcher.modrinth;

import java.util.*;

/** Shared ordering and compatibility rules for the Android catalogue and version picker. */
public final class ModBrowserPolicy {
    private ModBrowserPolicy() { }
    public static boolean compatible(ModrinthSearchHit hit,String minecraft,String loader) {
        return (minecraft==null||hit.versions()!=null&&hit.versions().contains(minecraft))
            &&(loader==null||hit.categories()!=null&&hit.categories().contains(loader));
    }
    public static boolean compatible(ModrinthVersion version,String minecraft,String loader) {
        return (minecraft==null||version.gameVersions()!=null&&version.gameVersions().contains(minecraft))
            &&(loader==null||version.loaders()!=null&&version.loaders().contains(loader));
    }
    public static List<ModrinthSearchHit> projects(List<ModrinthSearchHit> hits,String minecraft,String loader) {
        return hits.stream().sorted(Comparator.comparing(hit->!compatible(hit,minecraft,loader))).toList();
    }
    public static List<ModrinthVersion> versions(List<ModrinthVersion> versions,String minecraft,String loader) {
        return versions.stream().sorted(Comparator.comparing(version->!compatible(version,minecraft,loader))).toList();
    }
    public static Optional<ModrinthVersion> latest(List<ModrinthVersion> versions,String minecraft,String loader) {
        return versions.stream().filter(version->compatible(version,minecraft,loader)).findFirst();
    }
}
