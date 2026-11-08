package mc.snakenest.launcher.modpack;

/**
 * One file entry of a version's manifest: a path, its SHA-256 content hash, and its size.
 *
 * @param url when set, this file's content must be fetched directly from this (third-party)
 *            origin URL instead of this site's own blob storage - e.g. a mod whose license
 *            forbids redistribution. The site computes {@code hash}/{@code size} itself at
 *            import time by fetching the URL once server-side, so this is otherwise a completely
 *            normal, hash-diffed manifest entry - see {@code ModpackFileDownloader}. {@code null}
 *            for the overwhelming majority of files, which really are this site's own blobs.
 */
public record ManifestFile(String path, String hash, long size, String url) {
}
