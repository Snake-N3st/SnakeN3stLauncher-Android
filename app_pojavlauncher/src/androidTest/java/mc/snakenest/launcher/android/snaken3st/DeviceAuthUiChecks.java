package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;

/** Isolated locked-account UI and native cancellation. Never prepares, enables or unlocks a vault. */
final class DeviceAuthUiChecks {
    static void run(Instrumentation test) {
        Bundle result=new Bundle();int passed=0;Path root=null;Field repositoryInstance=null,updaterInstance=null;
        SnakeRepository original=null,fixture=null;Object previousUpdater=null;SnakeUpdater fixtureUpdater=null;LauncherActivity activity=null;
        ProxySelector previousProxy=ProxySelector.getDefault();AtomicInteger requests=new AtomicInteger(),successes=new AtomicInteger(),errors=new AtomicInteger();Set<String> preferences=new HashSet<>();
        Context target=test.getTargetContext();
        try {
            Tools.initStorageConstants(target);LauncherPreferences.loadPreferences(target);
            KeyStore keyStore=KeyStore.getInstance("AndroidKeyStore");keyStore.load(null);boolean hadAlias=keyStore.containsAlias(AndroidDeviceProtection.ALIAS);
            root=Files.createTempDirectory(target.getCacheDir().toPath(),"sn3-device-ui-fixture-");Path fixtureRoot=root;String prefix=root.getFileName().toString();
            Context context=new ContextWrapper(target) {
                @Override public Context getApplicationContext(){return this;}
                @Override public File getNoBackupFilesDir(){File f=fixtureRoot.resolve("no_backup").toFile();f.mkdirs();return f;}
                @Override public File getCacheDir(){File f=fixtureRoot.resolve("cache").toFile();f.mkdirs();return f;}
                @Override public SharedPreferences getSharedPreferences(String name,int mode){String isolated=prefix+"-"+name;preferences.add(isolated);return target.getSharedPreferences(isolated,mode);}
            };
            repositoryInstance=SnakeRepository.class.getDeclaredField("instance");repositoryInstance.setAccessible(true);original=(SnakeRepository)repositoryInstance.get(null);
            if(original!=null&&original.busy)throw new IllegalStateException("Operation in progress");
            updaterInstance=SnakeUpdater.class.getDeclaredField("instance");updaterInstance.setAccessible(true);previousUpdater=updaterInstance.get(null);
            if(previousUpdater!=null)for(String name:List.of("checking","downloading")){Field f=SnakeUpdater.class.getDeclaredField(name);f.setAccessible(true);if(f.getBoolean(previousUpdater))throw new IllegalStateException("Update in progress");}
            Constructor<SnakeRepository> constructor=SnakeRepository.class.getDeclaredConstructor(Context.class);constructor.setAccessible(true);fixture=constructor.newInstance(context);
            Path session=context.getNoBackupFilesDir().toPath().resolve("sn3-session.bin");byte[] sessionBytes={1,2,3,4};Files.write(session,sessionBytes);
            Path protection=fixture.dirs.secure().resolve("device-lock.bin");Files.createDirectories(protection.getParent());byte[] protectionBytes={9,8,7,6};Files.write(protection,protectionBytes);
            fixture.offlineMode=true;repositoryInstance.set(null,fixture);RemoteIconLoader.setOffline(true);
            context.getSharedPreferences("sn3-updates",Context.MODE_PRIVATE).edit().putBoolean("automatic",false).commit();
            Constructor<SnakeUpdater> updaterConstructor=SnakeUpdater.class.getDeclaredConstructor(Context.class);updaterConstructor.setAccessible(true);fixtureUpdater=updaterConstructor.newInstance(context);updaterInstance.set(null,fixtureUpdater);
            ProxySelector.setDefault(new ProxySelector(){
                @Override public List<java.net.Proxy> select(URI uri){requests.incrementAndGet();throw new IllegalStateException("Network disabled for device auth fixture");}
                @Override public void connectFailed(URI uri,SocketAddress address,IOException failure){}
            });
            activity=(LauncherActivity)test.startActivitySync(new Intent(target,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LauncherActivity host=activity;
            boolean[] login={false};for(int i=0;i<60&&!login[0];i++){test.waitForIdleSync();test.runOnMainSync(()->login[0]=host.getSupportFragmentManager().findFragmentById(R.id.container_fragment) instanceof Sn3LoginFragment);if(!login[0])Thread.sleep(50);}
            check(login[0]);passed++;
            test.runOnMainSync(()->check(findText(host.getWindow().getDecorView(),"Déverrouiller avec Android")!=null));passed++;
            test.runOnMainSync(()->check(!visiblePin(host.getWindow().getDecorView())));passed++;
            test.runOnMainSync(()->check(findText(host.getWindow().getDecorView(),"Créer un PIN")==null));passed++;
            test.waitForIdleSync();Thread.sleep(180);test.waitForIdleSync();
            test.runOnMainSync(()->{
                View view=host.getWindow().getDecorView();Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
                try(OutputStream output=new FileOutputStream(new File(target.getCacheDir(),"sn3-device-auth-ui-check.png"))){image.compress(Bitmap.CompressFormat.PNG,100,output);}catch(IOException failure){throw new RuntimeException(failure);}finally{image.recycle();}
            });passed++;
            // No protected operation is attached: success only increments a fixture counter.
            // The existing user's key alias is never prepared or read by this test.
            test.runOnMainSync(()->DeviceAuthFragment.authenticate(host,successes::incrementAndGet,message->errors.incrementAndGet()));
            boolean systemPrompt=false;
            for(int i=0;i<30&&!systemPrompt;i++) {
                AccessibilityNodeInfo node=test.getUiAutomation().getRootInActiveWindow();
                if(node!=null){CharSequence pkg=node.getPackageName();systemPrompt=pkg!=null&&(pkg.toString().equals("com.android.systemui")||pkg.toString().equals("com.android.settings")||pkg.toString().equals("com.samsung.android.biometrics.app.setting"));node.recycle();}
                if(!systemPrompt){test.waitForIdleSync();Thread.sleep(50);}
            }
            result.putBoolean("systemPromptShown",systemPrompt);
            if(systemPrompt)try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand("input keyevent KEYCODE_BACK");InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd)){while(input.read()!=-1){}}
            boolean[] removed={false};
            for(int i=0;i<40&&!removed[0];i++){test.waitForIdleSync();test.runOnMainSync(()->removed[0]=host.getSupportFragmentManager().findFragmentByTag("Sn3DeviceAuthentication")==null);if(!removed[0])Thread.sleep(50);}
            check(removed[0]&&errors.get()==1);passed++;
            check(successes.get()==0&&fixture.session==null&&fixture.pinKey==null);passed++;
            check(Arrays.equals(protectionBytes,Files.readAllBytes(protection))&&Arrays.equals(sessionBytes,Files.readAllBytes(session)));passed++;
            keyStore.load(null);check(keyStore.containsAlias(AndroidDeviceProtection.ALIAS)==hadAlias);passed++;
            check(requests.get()==0);passed++;result.putBoolean("success",true);
        }catch(Throwable failure){result.putBoolean("success",false);result.putString("message",failure.getClass().getSimpleName()+": "+failure.getMessage());}
        finally {
            try {
                if(activity!=null){LauncherActivity finish=activity;test.runOnMainSync(()->{var fragment=finish.getSupportFragmentManager().findFragmentByTag("Sn3DeviceAuthentication");if(fragment!=null)finish.getSupportFragmentManager().beginTransaction().remove(fragment).commitNowAllowingStateLoss();finish.finish();});test.waitForIdleSync();}
                if(fixture!=null)fixture.executor.shutdownNow();
                if(fixtureUpdater!=null){Field executor=SnakeUpdater.class.getDeclaredField("executor");executor.setAccessible(true);((ExecutorService)executor.get(fixtureUpdater)).shutdownNow();}
                if(repositoryInstance!=null)repositoryInstance.set(null,original);if(updaterInstance!=null)updaterInstance.set(null,previousUpdater);
                RemoteIconLoader.setOffline(original!=null&&original.offlineMode);
                for(String name:preferences)target.deleteSharedPreferences(name);
                if(root!=null)try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
            }catch(Exception cleanup){result.putBoolean("success",false);result.putString("cleanup",cleanup.getClass().getSimpleName());}
            ProxySelector.setDefault(previousProxy);result.putInt("checks",passed);result.putInt("httpRequests",requests.get());test.finish(result.getBoolean("success")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
    private static View findText(View view,String expected){if(view instanceof TextView label&&expected.equals(label.getText().toString()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),expected);if(found!=null)return found;}return null;}
    private static boolean visiblePin(View view){if(view instanceof EditText field&&field.isShown())return true;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(visiblePin(group.getChildAt(i)))return true;return false;}
    private static void check(boolean value){if(!value)throw new AssertionError("Device auth UI check failed");}
}
