package mc.snakenest.launcher.android.multirt;

import java.util.Objects;

public class Runtime {
    public final String name;
    public final String versionString;
    public final String arch;
    public final int javaVersion;
    public Runtime(String name) {
        this.name = name;
        this.versionString = null;
        this.arch = null;
        this.javaVersion = 0;
    }
    Runtime(String name, String versionString, String arch, int javaVersion) {
        this.name = name;
        this.versionString = versionString;
        this.arch = arch;
        this.javaVersion = javaVersion;
    }
    


    public String displayName(android.content.Context context) {
        if ("Internal".equals(name)) return context.getString(mc.snakenest.launcher.android.R.string.sn3_legacy_runtime);
        if (mc.snakenest.launcher.android.NewJREUtil.BUNDLED_RUNTIME_NAME.equals(name))
            return context.getString(mc.snakenest.launcher.android.R.string.sn3_bundled_runtime);
        return name.replace(".tar.xz", "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Runtime runtime = (Runtime) o;
        return name.equals(runtime.name);
    }
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}