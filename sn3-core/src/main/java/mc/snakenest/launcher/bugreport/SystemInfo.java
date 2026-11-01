package mc.snakenest.launcher.bugreport;

/** One-line OS/JVM summary attached to every report - enough to spot "only happens on Windows" patterns without collecting anything more identifying. */
public final class SystemInfo {

    private SystemInfo() {
    }

    public static String describe() {
        return String.format("%s %s (%s), Java %s",
                System.getProperty("os.name", "?"),
                System.getProperty("os.version", "?"),
                System.getProperty("os.arch", "?"),
                System.getProperty("java.version", "?"));
    }
}
