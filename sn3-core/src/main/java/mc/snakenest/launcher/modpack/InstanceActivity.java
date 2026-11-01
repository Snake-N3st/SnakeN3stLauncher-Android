package mc.snakenest.launcher.modpack;

/** The one thing a single modpack instance can be doing at a time - see {@link InstanceStateRegistry}. */
public enum InstanceActivity {
    INSTALLING, RUNNING, STOPPING
}
