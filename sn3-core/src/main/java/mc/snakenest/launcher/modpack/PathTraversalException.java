package mc.snakenest.launcher.modpack;

import java.io.IOException;

/** A manifest path resolved outside its instance directory - refused, never written/deleted. */
public final class PathTraversalException extends IOException {

    public PathTraversalException(String path) {
        super("Refusing to use path outside the instance directory: " + path);
    }
}
