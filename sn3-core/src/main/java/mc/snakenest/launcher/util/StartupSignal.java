package mc.snakenest.launcher.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one-way handshake that lets the bootstrap know the launcher it spawned has actually put a
 * window on screen.
 *
 * <p><b>Why it exists.</b> The bootstrap used to close its splash the instant {@code
 * ProcessBuilder.start()} returned, but a freshly spawned JVM needs seconds to boot, load classes
 * and paint its first frame. That left a gap with nothing at all on screen - the player sees the
 * splash vanish and reasonably concludes the launcher failed, and clicks the shortcut again. Worse,
 * if the launcher died during that gap the bootstrap had already exited and could report nothing.
 *
 * <p><b>Why a file and not a socket.</b> A loopback {@code ServerSocket} would be the tidier
 * handshake, but a process that listens on a port can trip Windows Firewall into showing an
 * "allow this app?" prompt - an unacceptable thing to put in front of a player during the very
 * first launch, which is exactly the moment this feature is meant to make reassuring. A file has no
 * such failure mode. Reading the child's stdout instead would mean giving up {@code inheritIO()},
 * and a parent that stops draining a child's pipes deadlocks it once the buffer fills.
 *
 * <p>Both sides treat this as advisory: the launcher never fails to start because it could not
 * write the file, and the bootstrap gives up waiting after a timeout rather than hanging forever on
 * a launcher that (for whatever reason) never signals.
 */
public final class StartupSignal {

    /**
     * JVM property naming the file to touch. Set by the bootstrap on the launcher it spawns;
     * absent when the launcher is started directly (from an IDE, or a player running the jar
     * themselves), in which case signalling is simply a no-op.
     */
    public static final String PROPERTY = "sn3.readyFile";

    /** Written at most once per JVM - the launcher signals from more than one code path. */
    private static final AtomicBoolean SIGNALLED = new AtomicBoolean();

    private StartupSignal() {
    }

    /**
     * Launcher side: announces that a window is now visible.
     *
     * <p>Call this as the <em>first</em> window appears, whichever it is - the login window for a
     * player who has to sign in, or the main shell for one whose session was restored. What the
     * bootstrap is waiting for is "something is on screen for the player to look at", not any
     * particular window, so waiting for the shell specifically would leave the splash up for the
     * entire login flow.
     *
     * <p>Idempotent, and deliberately silent about failures: a launcher that is running fine must
     * never be derailed by a cosmetic handshake. If this does fail, the bootstrap simply falls back
     * to its timeout.
     */
    public static void signalReady() {
        String target = System.getProperty(PROPERTY);

        if (target == null || target.isBlank() || !SIGNALLED.compareAndSet(false, true)) {
            return;
        }

        try {
            Path path = Path.of(target);
            Path parent = path.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(path, "ready");
        } catch (Exception e) {
            // Advisory only - see this class's Javadoc.
            Log.warn(StartupSignal.class, "Could not write the startup signal file: " + e);
        }
    }
}
