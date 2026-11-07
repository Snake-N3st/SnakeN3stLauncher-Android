package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.news.Post;

/** Isolated account/news UI only: no real session read, network call, setting change or article opened. */
final class NewsUiChecks {
    static void run(Instrumentation test) {
        Bundle result=new Bundle();int passed=0;Path root=null;Field repositoryInstance=null,updaterInstance=null;
        SnakeRepository original=null,fixture=null;Object previousUpdater=null;SnakeUpdater fixtureUpdater=null;LauncherActivity activity=null;
        ProxySelector previousProxy=ProxySelector.getDefault();AtomicInteger requests=new AtomicInteger();Set<String> preferences=new HashSet<>();
        Context target=test.getTargetContext();
        try {
            Tools.initStorageConstants(target);LauncherPreferences.loadPreferences(target);
            root=Files.createTempDirectory(target.getCacheDir().toPath(),"sn3-news-ui-fixture-");Path fixtureRoot=root;String prefix=root.getFileName().toString();
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
            fixture.session=new PlayerSession(Ed25519KeyPair.fromSeed(new byte[32]),999999,fixture.client());fixture.catalog.cacheUsername(999999,"FixturePlayer");
            fixture.offlineMode=true;fixture.posts=List.of(
                new Post(1,"Nouvelles du serveur","Une actualité de démonstration pour vérifier la présentation.","fixture","https://example.invalid/news/fixture",null,new Post.Author(1,"Snake N3st"),"2026-11-08T00:00:00Z",null),
                new Post(2,"Rendez-vous communautaire","Un deuxième article de test.","fixture-2","https://example.invalid/news/fixture-2",null,new Post.Author(1,"Snake N3st"),"2026-11-07T00:00:00Z",null));
            repositoryInstance.set(null,fixture);RemoteIconLoader.setOffline(true);
            context.getSharedPreferences("sn3-updates",Context.MODE_PRIVATE).edit().putBoolean("automatic",false).commit();
            Constructor<SnakeUpdater> updaterConstructor=SnakeUpdater.class.getDeclaredConstructor(Context.class);updaterConstructor.setAccessible(true);fixtureUpdater=updaterConstructor.newInstance(context);updaterInstance.set(null,fixtureUpdater);
            ProxySelector.setDefault(new ProxySelector(){
                @Override public List<java.net.Proxy> select(URI uri){requests.incrementAndGet();throw new IllegalStateException("Network disabled for UI fixture");}
                @Override public void connectFailed(URI uri,SocketAddress address,IOException failure){}
            });
            activity=(LauncherActivity)test.startActivitySync(new Intent(target,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LauncherActivity host=activity;
            test.waitForIdleSync();
            int[] headerIds={R.id.account_spinner,R.id.sn3_account_button,R.id.setting_button};int[] originalVisibility=new int[headerIds.length];
            test.runOnMainSync(()->{for(int i=0;i<headerIds.length;i++)originalVisibility[i]=host.findViewById(headerIds[i]).getVisibility();});
            SnakeRepository newsRepo=fixture;
            test.runOnMainSync(()->{newsRepo.offlineMode=false;Sn3NewsFragment.open(host);host.getSupportFragmentManager().executePendingTransactions();});test.waitForIdleSync();
            test.runOnMainSync(()->check(host.getSupportFragmentManager().findFragmentById(R.id.container_fragment) instanceof Sn3NewsFragment));passed++;
            test.runOnMainSync(()->{for(int id:headerIds)check(host.findViewById(id).getVisibility()==View.GONE);});passed++;
            test.runOnMainSync(()->{
                View rootView=host.getSupportFragmentManager().findFragmentById(R.id.container_fragment).getView();
                check(findText(rootView,"Actualités")!=null);check(findDescription(rootView,"Retour") instanceof ImageButton);check(findDescription(rootView,"Actualiser les actualités") instanceof ImageButton);
            });passed++;
            test.runOnMainSync(()->check(findText(host.getWindow().getDecorView(),"Nouvelles du serveur")!=null));passed++;
            test.runOnMainSync(()->{
                View view=host.getWindow().getDecorView();Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
                try(OutputStream output=new FileOutputStream(new File(target.getCacheDir(),"sn3-news-toolbar-check.png"))){image.compress(Bitmap.CompressFormat.PNG,100,output);}catch(IOException failure){throw new RuntimeException(failure);}finally{image.recycle();}
            });passed++;
            test.runOnMainSync(()->findDescription(host.getSupportFragmentManager().findFragmentById(R.id.container_fragment).getView(),"Retour").performClick());
            test.waitForIdleSync();test.runOnMainSync(()->host.getSupportFragmentManager().executePendingTransactions());
            test.runOnMainSync(()->{check(!(host.getSupportFragmentManager().findFragmentById(R.id.container_fragment) instanceof Sn3NewsFragment));for(int i=0;i<headerIds.length;i++)check(host.findViewById(headerIds[i]).getVisibility()==originalVisibility[i]);});passed++;
            test.runOnMainSync(()->{
                try {
                    Object home=host.getSupportFragmentManager().findFragmentById(R.id.container_fragment);Field field=home.getClass().getDeclaredField("snakeUi");field.setAccessible(true);Object ui=field.get(home);
                    host.findViewById(R.id.sn3_account_button).performClick();Field dialogField=SnakeUi.class.getDeclaredField("accountDialog");dialogField.setAccessible(true);android.app.AlertDialog dialog=(android.app.AlertDialog)dialogField.get(ui);
                    check(dialog!=null&&dialog.isShowing());View dialogView=dialog.getWindow().getDecorView();check(findText(dialogView,"SÉCURITÉ")!=null);check(findText(dialogView,"Commandes tactiles")==null);dialog.dismiss();
                }catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}
            });passed++;
            check(requests.get()==0);passed++;result.putBoolean("success",true);
        }catch(Throwable failure){result.putBoolean("success",false);result.putString("message",failure.getClass().getSimpleName()+": "+failure.getMessage());}
        finally {
            try {
                if(activity!=null){LauncherActivity finish=activity;test.runOnMainSync(finish::finish);test.waitForIdleSync();}
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
    private static View findDescription(View view,String expected){if(expected.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),expected);if(found!=null)return found;}return null;}
    private static void check(boolean value){if(!value)throw new AssertionError("News/profile UI check failed");}
}
