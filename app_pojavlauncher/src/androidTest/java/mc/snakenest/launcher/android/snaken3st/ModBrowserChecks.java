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

/** Synthetic catalogue UI: no network, mod installation or real session changes. */
final class ModBrowserChecks {
    static void run(Instrumentation test) {
        Bundle result=new Bundle();int passed=0;Path root=null;Field instance=null;
        SnakeRepository original=null,fixture=null;LauncherActivity activity=null;
        ProxySelector previous=ProxySelector.getDefault();AtomicInteger requests=new AtomicInteger();Set<String> preferences=new HashSet<>();
        Context target=test.getTargetContext();
        Field updaterInstance=null;Object previousUpdater=null;SnakeUpdater fixtureUpdater=null;
        try {
            Tools.initStorageConstants(target);LauncherPreferences.loadPreferences(target);
            root=Files.createTempDirectory(target.getCacheDir().toPath(),"sn3-mod-browser-fixture-");Path fixtureRoot=root;
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
            updaterInstance=SnakeUpdater.class.getDeclaredField("instance");updaterInstance.setAccessible(true);previousUpdater=updaterInstance.get(null);
            context.getSharedPreferences("sn3-updates",Context.MODE_PRIVATE).edit().putBoolean("automatic",false).commit();
            Constructor<SnakeUpdater> updaterConstructor=SnakeUpdater.class.getDeclaredConstructor(Context.class);updaterConstructor.setAccessible(true);fixtureUpdater=updaterConstructor.newInstance(context);updaterInstance.set(null,fixtureUpdater);
            ProxySelector.setDefault(new ProxySelector(){
                @Override public List<Proxy> select(URI uri){requests.incrementAndGet();throw new IllegalStateException("Network disabled for fixture");}
                @Override public void connectFailed(URI uri,SocketAddress address,IOException failure){}
            });
            fixture.session=new mc.snakenest.launcher.auth.PlayerSession(mc.snakenest.launcher.crypto.Ed25519KeyPair.fromSeed(new byte[32]),999999,fixture.client());
            fixture.catalog.cacheUsername(999999,"FixturePlayer");
            activity=(LauncherActivity)test.startActivitySync(new Intent(target,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            LauncherActivity host=activity;test.waitForIdleSync();
            SnakeRepository repository=fixture;
            test.runOnMainSync(()->{repository.offlineMode=false;ModpackManagerFragment.open(host,"test-pack",false);host.getSupportFragmentManager().executePendingTransactions();});
            ModpackManagerFragment[] manager={null};
            for(int i=0;i<60&&manager[0]==null;i++){test.waitForIdleSync();test.runOnMainSync(()->{var current=host.getSupportFragmentManager().findFragmentById(R.id.container_fragment);if(current instanceof ModpackManagerFragment found)manager[0]=found;});if(manager[0]==null)Thread.sleep(50);}
            check(manager[0]!=null);passed++;
            var supported=new mc.snakenest.launcher.modrinth.ModrinthSearchHit("compatible","compatible","Mod compatible","Description du mod compatible","Auteur de test",null,java.util.List.of("fabric"),java.util.List.of("1.21"),null,null,100);
            var unsupported=new mc.snakenest.launcher.modrinth.ModrinthSearchHit("incompatible","incompatible","Mod incompatible","Description du mod incompatible","Autre auteur",null,java.util.List.of("forge"),java.util.List.of("1.20"),null,null,50);
            var good=new mc.snakenest.launcher.modrinth.ModrinthVersion("good","compatible","2.0","Version stable",java.util.List.of("1.21"),java.util.List.of("fabric"),"release",java.util.List.of(),java.util.List.of());
            var bad=new mc.snakenest.launcher.modrinth.ModrinthVersion("bad","compatible","1.0","Version Forge",java.util.List.of("1.20"),java.util.List.of("forge"),"beta",java.util.List.of(),java.util.List.of());
            Object browser=field(manager[0],"browser");
            set(browser,"loaded",true);set(browser,"needsIdentity",false);
            set(browser,"manifest",new mc.snakenest.launcher.modpack.ModpackManifest("test",null,null,0,"1.21","fabric","test",java.util.List.of(),null,null));
            set(browser,"hits",java.util.List.of(supported,unsupported));
            test.runOnMainSync(()->manager[0].openModCatalogue());test.waitForIdleSync();
            check(contains(manager[0].requireView(),"Mod compatible")&&contains(manager[0].requireView(),"Mod incompatible"));passed++;
            check(contains(manager[0].requireView(),"par Auteur de test")&&contains(manager[0].requireView(),"Importer un .jar"));passed++;
            screenshot(test,host,target,"sn3-mods-catalogue-check.png");
            set(browser,"selected",supported);set(browser,"releases",java.util.List.of(good,bad));
            test.runOnMainSync(manager[0]::redrawMods);test.waitForIdleSync();
            check(contains(manager[0].requireView(),"Voir sur Modrinth")&&contains(manager[0].requireView(),"Installation rapide"));passed++;
            screenshot(test,host,target,"sn3-mods-detail-check.png");
            test.runOnMainSync(()->{check(click(manager[0].requireView(),"Choisir une version"));});test.waitForIdleSync();
            check(contains(manager[0].requireView(),"Version stable")&&contains(manager[0].requireView(),"Version Forge"));passed++;
            boolean[] disabled={false};test.runOnMainSync(()->disabled[0]=disabled(manager[0].requireView(),"Installation incompatible"));check(disabled[0]);passed++;
            screenshot(test,host,target,"sn3-mods-versions-check.png");
            test.runOnMainSync(()->host.getOnBackPressedDispatcher().onBackPressed());test.waitForIdleSync();check(contains(manager[0].requireView(),"Voir sur Modrinth"));passed++;
            test.runOnMainSync(()->host.getOnBackPressedDispatcher().onBackPressed());test.waitForIdleSync();check(contains(manager[0].requireView(),"Découvrir des mods"));passed++;
            check(requests.get()==0);passed++;result.putBoolean("success",true);
        } catch(Throwable failure){result.putBoolean("success",false);result.putString("message",failure.getClass().getSimpleName()+": "+failure.getMessage());}
        finally {
            try {
                if(activity!=null){LauncherActivity finish=activity;test.runOnMainSync(finish::finish);test.waitForIdleSync();}
                if(fixture!=null)fixture.executor.shutdownNow();
                if(instance!=null)instance.set(null,original);
                if(fixtureUpdater!=null){Field executor=SnakeUpdater.class.getDeclaredField("executor");executor.setAccessible(true);((java.util.concurrent.ExecutorService)executor.get(fixtureUpdater)).shutdownNow();}
                if(updaterInstance!=null)updaterInstance.set(null,previousUpdater);
                RemoteIconLoader.setOffline(original!=null&&original.offlineMode);
                for(String name:preferences)target.deleteSharedPreferences(name);
                if(root!=null)try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
            }catch(Exception cleanup){result.putBoolean("success",false);result.putString("cleanup",cleanup.getClass().getSimpleName());}
            ProxySelector.setDefault(previous);result.putInt("checks",passed);result.putInt("httpRequests",requests.get());
            test.finish(result.getBoolean("success")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
    private static Object field(Object target,String name)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    private static boolean contains(android.view.View view,String text){if(view instanceof android.widget.TextView label&&label.getText().toString().contains(text))return true;if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),text))return true;return false;}
    private static boolean click(android.view.View view,String text){if(view instanceof android.widget.Button label&&label.getText().toString().equals(text)){label.performClick();return true;}if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(click(group.getChildAt(i),text))return true;return false;}
    private static boolean disabled(android.view.View view,String text){if(view instanceof android.widget.Button label&&label.getText().toString().equals(text))return !label.isEnabled();if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(disabled(group.getChildAt(i),text))return true;return false;}
    private static void screenshot(Instrumentation test,LauncherActivity host,Context target,String name){test.waitForIdleSync();try{Thread.sleep(180);}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new RuntimeException(failure);}test.waitForIdleSync();test.runOnMainSync(()->{android.view.View view=host.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(OutputStream out=new FileOutputStream(new File(target.getCacheDir(),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException failure){throw new RuntimeException(failure);}finally{bitmap.recycle();}});}
    private static void check(boolean value){if(!value)throw new AssertionError("Mod browser UI check failed");}
}
