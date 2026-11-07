package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import mc.snakenest.launcher.android.BuildConfig;
import mc.snakenest.launcher.update.android.AndroidUpdateCatalog.Candidate;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

/** Exercises archive inspection only: never starts an installer or installs a fixture APK. */
public final class UpdateVerifierInstrumentation extends Instrumentation {
    private static final String URL = "https://github.com/Snake-N3st/SnakeN3stLauncher-Android/releases/download/instrumentation/fixture.apk";
    private static final String PLACEHOLDER_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private boolean offlineChecks;
    private boolean iconCacheChecks;
    private boolean jvmSettingsChecks;
    private boolean loginChecks;
    private boolean crashReportChecks;
    private boolean modBrowserChecks;
    private boolean newsUiChecks;
    @Override public void onCreate(Bundle arguments) {
        offlineChecks=arguments!=null && "true".equals(arguments.getString("offline"));
        iconCacheChecks=arguments!=null && "true".equals(arguments.getString("iconCache"));
        jvmSettingsChecks=arguments!=null && "true".equals(arguments.getString("jvmSettings"));
        loginChecks=arguments!=null && "true".equals(arguments.getString("login"));
        newsUiChecks=arguments!=null && "true".equals(arguments.getString("newsUi"));
        crashReportChecks=arguments!=null && "true".equals(arguments.getString("crashReport"));
        modBrowserChecks=arguments!=null && "true".equals(arguments.getString("modBrowser"));
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        super.onStart();
        if(offlineChecks) { new OfflineLaunchChecks(this).run(); return; }
        if(iconCacheChecks) { IconCacheChecks.run(this); return; }
        if(jvmSettingsChecks) { JvmSettingsChecks.run(this); return; }
        if(loginChecks) { LoginScreenChecks.run(this); return; }
        if(newsUiChecks) { NewsUiChecks.run(this); return; }
        if(modBrowserChecks) { ModBrowserChecks.run(this); return; }
        if(crashReportChecks) { CrashReportChecks.run(this); return; }
        Bundle result = new Bundle();
        int passed = 0;
        StringBuilder failures = new StringBuilder();
        try {
            Context target = getTargetContext();
            Object updater = SnakeUpdater.get(target);
            Method verify = SnakeUpdater.class.getDeclaredMethod("verifyArchive", File.class, Candidate.class);
            verify.setAccessible(true);
            File directory = new File(target.getCacheDir(), "sn3-update-verifier");
            String[] fixtures = {"future.apk", "same-version.apk", "wrong-package.apk", "wrong-signer.apk"};
            String[] rejectionReasons = {null, "Version", "édition", "signature"};
            for (int index = 0; index < fixtures.length; index++) {
                String fixture = fixtures[index];
                Bundle status = new Bundle();
                status.putString("case", fixture);
                status.putString("expected", index == 0 ? "accepted" : "rejected");
                try {
                    File apk = new File(directory, fixture);
                    if (!apk.isFile() || apk.length() == 0) throw new AssertionError("Missing or empty fixture: " + apk);
                    long code = (long) BuildConfig.VERSION_CODE + (index == 1 ? 0 : 1);
                    // Hash/size validation belongs to the download path. This test targets verifyArchive only.
                    Candidate candidate = new Candidate(target.getPackageName(), code, "0.2.1", URI.create(URL),
                            PLACEHOLDER_HASH, apk.length(), 26, "Instrumentation archive-verifier fixture");
                    Throwable rejected = null;
                    try { verify.invoke(updater, apk, candidate); }
                    catch (InvocationTargetException exception) { rejected = exception.getCause(); }
                    if (index == 0) {
                        if (rejected != null) throw new AssertionError("Future same-signer APK was refused", rejected);
                        status.putString("actual", "accepted");
                    } else {
                        if (!(rejected instanceof IOException))
                            throw new AssertionError("Expected archive validation IOException, got " + describe(rejected), rejected);
                        String message = rejected.getMessage();
                        if (message == null || !message.contains(rejectionReasons[index]))
                            throw new AssertionError("Fixture refused for the wrong reason: " + describe(rejected), rejected);
                        status.putString("actual", "rejected");
                        status.putString("rejection", message);
                    }
                    passed++;
                    status.putBoolean("passed", true);
                    sendStatus(0, status);
                } catch (Throwable failure) {
                    status.putBoolean("passed", false);
                    status.putString("message", describe(failure));
                    sendStatus(1, status);
                    if (failures.length() > 0) failures.append('\n');
                    failures.append(fixture).append(": ").append(describe(failure));
                }
            }
        } catch (Throwable failure) {
            failures.append("Instrumentation setup: ").append(describe(failure));
        }
        result.putInt("checks", passed);
        result.putBoolean("success", passed == 4 && failures.length() == 0);
        result.putString("message", failures.length() == 0 ? "All 4 archive-verifier checks passed" : failures.toString());
        result.putString("stream", "\n" + result.getString("message") + "\n");
        finish(passed == 4 && failures.length() == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private static String describe(Throwable failure) {
        if (failure == null) return "no exception (archive accepted)";
        StringBuilder message = new StringBuilder();
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 6; depth++, cause = cause.getCause()) {
            if (depth > 0) message.append("; caused by ");
            message.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
        }
        return message.toString();
    }
}
