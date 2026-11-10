package mc.snakenest.launcher.android;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static mc.snakenest.launcher.android.Tools.getMods;
import static mc.snakenest.launcher.android.Tools.hasMods;
import static mc.snakenest.launcher.android.Tools.hasNoOnlineProfileDialog;
import static mc.snakenest.launcher.android.Tools.isOnline;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcAccountSpinner;

import mc.snakenest.launcher.android.contracts.OpenDocumentWithExtension;
import mc.snakenest.launcher.android.extra.ExtraConstants;
import mc.snakenest.launcher.android.extra.ExtraCore;
import mc.snakenest.launcher.android.extra.ExtraListener;
import mc.snakenest.launcher.android.fragments.MainMenuFragment;
import mc.snakenest.launcher.android.fragments.MicrosoftLoginFragment;
import mc.snakenest.launcher.android.fragments.SelectAuthFragment;
import mc.snakenest.launcher.android.lifecycle.ContextAwareDoneListener;
import mc.snakenest.launcher.android.lifecycle.ContextExecutor;
import mc.snakenest.launcher.android.modloaders.LWJGL3ifyUtils;
import mc.snakenest.launcher.android.modloaders.modpacks.ModloaderInstallTracker;
import mc.snakenest.launcher.android.modloaders.modpacks.api.CommonApi;
import mc.snakenest.launcher.android.modloaders.modpacks.api.ModLoader;
import mc.snakenest.launcher.android.modloaders.modpacks.api.ModpackInstaller;
import mc.snakenest.launcher.android.modloaders.modpacks.api.NotificationDownloadListener;
import mc.snakenest.launcher.android.modloaders.modpacks.imagecache.IconCacheJanitor;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.android.prefs.screens.LauncherPreferenceFragment;
import mc.snakenest.launcher.android.progresskeeper.ProgressKeeper;
import mc.snakenest.launcher.android.progresskeeper.TaskCountListener;
import mc.snakenest.launcher.android.services.ProgressServiceKeeper;
import mc.snakenest.launcher.android.tasks.AsyncMinecraftDownloader;
import mc.snakenest.launcher.android.tasks.AsyncVersionList;
import mc.snakenest.launcher.android.tasks.MinecraftDownloader;
import mc.snakenest.launcher.android.utils.DateUtils;
import mc.snakenest.launcher.android.utils.NotificationUtils;
import mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles;
import mc.snakenest.launcher.android.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";

    public final ActivityResultLauncher<Object> modInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(this, data);
            });
    public final ActivityResultLauncher<Object> modpackImportLauncher =
            registerForActivityResult(new OpenDocumentWithExtension(new String[]{"zip", "mrpack"}), (data)->{
                if(data != null) {
                    PojavApplication.sExecutorService.execute(() -> {
                        try {
                            // Copy ZIP file to cache
                            long fileSize = -1;
                            try (Cursor returnCursor = getContentResolver().query(data, new String[]{OpenableColumns.SIZE}, null, null, null)) {
                                if (returnCursor != null && returnCursor.moveToFirst()) {
                                    fileSize = returnCursor.getLong(0);
                                }
                            }
                            File modpackFile = new File(Tools.DIR_CACHE, "import_modpack_placeholdername.cf");
                            try (InputStream inputStream = getContentResolver().openInputStream(data)){
                                FileOutputStream output = new FileOutputStream(modpackFile);
                                byte[] b = new byte[262144];
                                int read;
                                int readTotal = 0;
                                while ((read = inputStream.read(b)) != -1) {
                                    output.write(b, 0, read);
                                    readTotal += read;
                                    String readMB = fileSize > 0 ? String.format(Locale.US, "%.2f", readTotal / (1024.0 * 1024.0)) : "unknown";
                                    String totalMB = fileSize > 0 ? String.format(Locale.US, "%.2f", fileSize / (1024.0 * 1024.0)) : "unknown";
                                    int progress = fileSize > 0 ? (int) ((readTotal * 100L) / fileSize) : 0;
                                    ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, progress, R.string.import_modpack_copy, readMB, totalMB);
                                }
                                output.flush();
                                output.close();
                            }
                            ModLoader loaderInfo = new CommonApi(getString(R.string.curseforge_api_key)).importModpack(modpackFile);
                            modpackFile.delete();
                            if (loaderInfo == null) return;
                            loaderInfo.getDownloadTask(new NotificationDownloadListener(this, loaderInfo)).run();
                        } catch (IOException e) {
                            Tools.showErrorRemote(this, R.string.modpack_install_download_failed, e);
                            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                        } catch (IllegalArgumentException e) {
                            Tools.showError(this, R.string.not_modpack_file, e);
                            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                        } catch (NoSuchAlgorithmException e) {
                            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                            // Should literally never happen because SHA-1 is required Java spec
                            throw new RuntimeException(e);
                        }
                    });
                }
            });

    private Runnable snakeAccountAction;
    private Runnable snakeLaunchAction;

    public void bindSnakeActions(Runnable account, Runnable launch) {
        snakeAccountAction = account;
        snakeLaunchAction = launch;
    }
    private FragmentContainerView mFragmentView;
    private ImageButton mSettingsButton;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ModloaderInstallTracker mInstallTracker;
    private NotificationManager mNotificationManager;

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            mSettingsButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), f instanceof MainMenuFragment
                    ? R.drawable.ic_menu_settings : R.drawable.ic_menu_home));
        }
    };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if(value.equals("true")) onBackPressed();
        return false;
    };

    /* All authentication entry points belong to the Snake N3st device flow. */
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        if (snakeAccountAction != null) snakeAccountAction.run();
        else Tools.backToMainMenu(this);
        return false;
    };

    /* Listener for the settings fragment */
    private final View.OnClickListener mSettingButtonListener = v -> {
        Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        if(fragment instanceof MainMenuFragment){
            Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null);
        } else{
            // The setting button doubles as a home button now
            Tools.backToMainMenu(this);
        }
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if (snakeLaunchAction != null) snakeLaunchAction.run();
        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if(taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
    };

    private ActivityResultLauncher<String> mRequestNotificationPermissionLauncher;
    private ActivityResultLauncher<String> mRequestMicrophonePermissionLauncher;
    private WeakReference<Runnable> mRequestNotificationPermissionRunnable;
    private WeakReference<Runnable> mRequestMicrophonePermissionRunnable;

    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pojav_launcher);
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment restored = fragmentManager.findFragmentById(R.id.container_fragment);
        if (restored instanceof SelectAuthFragment || restored instanceof MicrosoftLoginFragment
                || restored instanceof mc.snakenest.launcher.android.fragments.LocalLoginFragment) {
            fragmentManager.popBackStackImmediate("ROOT", 0);
        }
        // If we don't have a back stack root yet...
        if(fragmentManager.getBackStackEntryCount() < 1) {
            // Manually add the first fragment to the backstack to get easily back to it
            // There must be a better way to handle the root though...
            // (artDev: No, there is not. I've spent days researching this for another unrelated project.)
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack("ROOT")
                    .add(R.id.container_fragment, MainMenuFragment.class, null, "ROOT").commit();
        }


        IconCacheJanitor.runJanitor();
        mRequestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestNotificationPermissionRunnable);
                        if(runnable != null) runnable.run();
                    }
                }
        );
        mRequestMicrophonePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestMicrophonePermissionRunnable);
                        if(runnable != null) runnable.run();
                    }
                }
        );
        getWindow().setBackgroundDrawable(null);
        bindViews();
        checkNotificationPermission();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        mSettingsButton.setOnClickListener(mSettingButtonListener);
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);

        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions), false);

        mInstallTracker = new ModloaderInstallTracker(this);

        mProgressLayout.observe("sn3");
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_SNAKE_MODPACK);
        mProgressLayout.observe(ProgressLayout.APPLY_SNAKE_MODPACK);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_MINECRAFT);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE_MICROSOFT);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        mInstallTracker.attach();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
        mInstallTracker.detach();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if(fragment != null){
            if(fragment.canGoBack()){
                fragment.goBack();
                return;
            }
        }

        // Check if we are at the root then
        if(getVisibleFragment("ROOT") != null){
            finish();
        }

        super.onBackPressed();
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag){
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id){
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    private void checkNotificationPermission() {
        if(LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
            checkForNotificationPermission()) {
            return;
        }

        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationPermissionReasoning();
            return;
        }
        askForNotificationPermission(null);
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) -> askForNotificationPermission(null))
                .setNegativeButton(android.R.string.cancel, (d, w)-> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
        Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show();
    }

    public boolean checkForNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_DENIED;
    }
    public boolean checkForMicrophonePermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_DENIED;
    }

    public void askForNotificationPermission(Runnable onSuccessRunnable) {
        if(Build.VERSION.SDK_INT < 33) return;
        if(onSuccessRunnable != null) {
            mRequestNotificationPermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    public void askForMicrophonePermission(Runnable onSuccessRunnable) {
        if(onSuccessRunnable != null) {
            mRequestMicrophonePermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    /** Stuff all the view boilerplate here */
    private void bindViews(){
        mFragmentView = findViewById(R.id.container_fragment);
        mSettingsButton = findViewById(R.id.setting_button);
        mProgressLayout = findViewById(R.id.progress_layout);
    }
}
