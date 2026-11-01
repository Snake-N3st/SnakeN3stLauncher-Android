package mc.snakenest.launcher.modpack;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Deletes exactly one validated instance; symbolic links are unlinked, never traversed. */
public final class InstanceUninstaller {
    private InstanceUninstaller() { }

    public static void delete(Path instancesRoot, String slug) throws IOException {
        if (slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("Invalid modpack slug");
        Path root = instancesRoot.toAbsolutePath().normalize();
        if (root.getParent() == null) throw new IOException("Filesystem root cannot be an instances directory");
        if (Files.isSymbolicLink(root)) throw new IOException("Instances directory must not be a symbolic link");
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Instances root is not a directory");
        Path target = root.resolve(slug).normalize();
        if (!root.equals(target.getParent())) throw new IOException("Instance must be a direct child of its root");
        checkInterrupted();
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        // walkFileTree's default options do not follow links, including a link at target.
        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
            private void guard(Path path) throws IOException {
                checkInterrupted();
                if (!path.toAbsolutePath().normalize().startsWith(target)) throw new IOException("Path outside the instance");
            }
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                guard(directory);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                guard(file); Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                guard(directory); Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Uninstall cancelled");
    }
}
