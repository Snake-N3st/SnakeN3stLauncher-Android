package mc.snakenest.launcher.crypto;

import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;

/**
 * Best-effort restriction of a file/directory to the owner only, tried on
 * whichever attribute view the current filesystem actually supports.
 *
 * <p><b>Honest limit</b>: this resists casual disk browsing, another local
 * OS account, and careless unencrypted backups. It does <b>not</b> resist a
 * privileged process running as the same OS account (malware or an admin
 * under this user), since that attacker already has the same filesystem
 * access this code relies on. Real resistance to that would need an OS
 * keychain (DPAPI / Keychain Services / Secret Service), which was
 * deliberately left out to avoid a native/JNI dependency on top of a
 * cross-platform, no-native-code launcher.
 */
final class SecureFilePermissions {

    private SecureFilePermissions() {
    }

    static void restrictToOwner(Path path) {
        try {
            if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
                return;
            }
            AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (aclView != null) {
                restrictAcl(path, aclView);
            }
        } catch (IOException | UnsupportedOperationException e) {
            Log.warn(SecureFilePermissions.class, "Could not restrict permissions on " + path.getFileName() + ": " + e.getMessage());
        }
    }

    private static void restrictAcl(Path path, AclFileAttributeView aclView) throws IOException {
        UserPrincipal owner = Files.getOwner(path);
        AclEntry ownerFullControl = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
        aclView.setAcl(List.of(ownerFullControl));
    }
}
