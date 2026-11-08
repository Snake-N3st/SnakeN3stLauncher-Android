package mc.snakenest.launcher.bugreport;

/**
 * Mirrors the {@code BugReport::TYPE_*} constants on the site plugin
 * (`launcher-toolkit-plugin/src/LauncherAuth/Models/BugReport.php`) - the
 * wire value has to stay byte-identical since the server validates it
 * against exactly these three strings.
 */
public enum BugReportType {

    /** An uncaught exception in the launcher itself (background thread or EDT). */
    LAUNCHER_CRASH("launcher_crash"),
    /** The Minecraft process exited unexpectedly (non-zero, not a player-requested stop). */
    GAME_CRASH("game_crash"),
    /** Sent by hand from Settings > "Signaler un bug". */
    MANUAL("manual");

    private final String wireValue;

    BugReportType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
