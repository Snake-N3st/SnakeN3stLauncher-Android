package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;

/** Renders a fresh-account fixture without deleting, decrypting or revoking the user's session. */
final class LoginScreenChecks {
    static void run(Instrumentation test) {
        Bundle result=new Bundle();int passed=0;Path root=null;Field instance=null;
        SnakeRepository original=null,fixture=null;LauncherActivity activity=null;
        ProxySelector previous=ProxySelector.getDefault();AtomicInteger requests=new AtomicInteger();Set<String> preferences=new HashSet<>();
        Context target=test.getTargetContext();
        try {
            check(SnakeUi.needsSignIn(false,false,false,false));passed++;
            check(!SnakeUi.needsSignIn(true,true,true,false));passed++;
            check(!SnakeUi.needsSignIn(false,true,false,false));passed++;
            check(!SnakeUi.needsSignIn(false,false,true,true));passed++;
            check(SnakeUi.needsSignIn(false,true,true,false));passed++;
            Tools.initStorageConstants(target);LauncherPreferences.loadPreferences(target);
            root=Files.createTempDirectory(target.getCacheDir().toPath(),"sn3-login-fixture-");Path fixtureRoot=root;
            String prefix=root.getFileName().toString();
            Context context=new ContextWrapper(target) {
                @Override public Context getApplicationContext(){return this;}
                @Override public File getNoBackupFilesDir(){File f=fixtureRoot.resolve("no_backup").toFile();f.mkdirs();return f;}
                @Override public File getCacheDir(){File f=fixtureRoot.resolve("cache").toFile();f.mkdirs();return f;}
                @Override public SharedPreferences getSharedPreferences(String name,int mode){String isolated=prefix+"-"+name;preferences.add(isolated);return target.getSharedPreferences(isolated,mode);}
            };
            instance=SnakeRepository.class.getDeclaredField("instance");instance.setAccessible(true);original=(SnakeRepository)instance.get(null);
            if(original!=null&&original.busy)throw new IllegalStateException("Operation in progress");
            Constructor<SnakeRepository> constructor=SnakeRepository.class.getDeclaredConstructor(Context.class);constructor.setAccessible(true);fixture=constructor.newInstance(context);
            fixture.offlineMode=true;RemoteIconLoader.setOffline(true);instance.set(null,fixture);
            ProxySelector.setDefault(new ProxySelector(){
                @Override public List<Proxy> select(URI uri){requests.incrementAndGet();throw new IllegalStateException("Network disabled for fixture");}
                @Override public void connectFailed(URI uri,SocketAddress address,IOException failure){}
            });
            activity=(LauncherActivity)test.startActivitySync(new Intent(target,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            LauncherActivity host=activity;boolean[] welcome={false};
            for(int i=0;i<40&&!welcome[0];i++) {
                test.waitForIdleSync();test.runOnMainSync(()->welcome[0]=host.getSupportFragmentManager().findFragmentById(R.id.container_fragment) instanceof Sn3LoginFragment);
                if(!welcome[0])Thread.sleep(50);
            }
            check(welcome[0]);passed++;
            test.runOnMainSync(()->{
                check(host.findViewById(R.id.sn3_account_button).getVisibility()==android.view.View.GONE);
                android.view.View view=host.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
                try(OutputStream output=new FileOutputStream(new File(target.getCacheDir(),"sn3-login-check.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}catch(IOException e){throw new RuntimeException(e);}finally{bitmap.recycle();}
            });passed++;
            SnakeRepository restored=fixture;
            restored.catalog.cacheUsername(999999,"FixturePlayer");
            restored.session=new mc.snakenest.launcher.auth.PlayerSession(mc.snakenest.launcher.crypto.Ed25519KeyPair.fromSeed(new byte[32]),999999,restored.client());
            test.runOnMainSync(restored::changed);
            boolean[] home={false};
            for(int i=0;i<40&&!home[0];i++) {
                test.waitForIdleSync();test.runOnMainSync(()->home[0]=!(host.getSupportFragmentManager().findFragmentById(R.id.container_fragment) instanceof Sn3LoginFragment));
                if(!home[0])Thread.sleep(50);
            }
            check(home[0]);passed++;
            check(requests.get()==0);passed++;result.putBoolean("success",true);
        } catch(Throwable failure){result.putBoolean("success",false);result.putString("message",failure.getClass().getSimpleName()+": "+failure.getMessage());}
        finally {
            try {
                if(activity!=null){LauncherActivity finish=activity;test.runOnMainSync(finish::finish);test.waitForIdleSync();}
                if(fixture!=null)fixture.executor.shutdownNow();
                if(instance!=null)instance.set(null,original);
                RemoteIconLoader.setOffline(original!=null&&original.offlineMode);
                for(String name:preferences)target.deleteSharedPreferences(name);
                if(root!=null)try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
            }catch(Exception cleanup){result.putBoolean("success",false);result.putString("cleanup",cleanup.getClass().getSimpleName());}
            ProxySelector.setDefault(previous);result.putInt("checks",passed);result.putInt("httpRequests",requests.get());
            test.finish(result.getBoolean("success")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
    private static void check(boolean value){if(!value)throw new AssertionError("Login screen check failed");}
}
