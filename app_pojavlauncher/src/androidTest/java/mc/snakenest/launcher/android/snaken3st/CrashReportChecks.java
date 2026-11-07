package mc.snakenest.launcher.android.snaken3st;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic reports only: never launches a JVM, submits a report or reads the user's journals. */
final class CrashReportChecks {
    static void run(Instrumentation test) {
        Context target=test.getTargetContext();Bundle result=new Bundle();int passed=0;Path root=null,uiFile=null,shared=null;
        Activity activity=null;String previousHome=Tools.DIR_GAME_HOME;ProxySelector previousProxy=ProxySelector.getDefault();AtomicInteger requests=new AtomicInteger();
        try {
            Tools.initStorageConstants(target);LauncherPreferences.loadPreferences(target);
            root=Files.createTempDirectory(target.getCacheDir().toPath(),"sn3-report-fixture-");Path fixtureRoot=root;
            Context isolated=new ContextWrapper(target) {
                @Override public File getNoBackupFilesDir(){File f=fixtureRoot.resolve("no_backup").toFile();f.mkdirs();return f;}
                @Override public File getFilesDir(){File f=fixtureRoot.resolve("files").toFile();f.mkdirs();return f;}
                @Override public File getExternalFilesDir(String type){File f=fixtureRoot.resolve("external").toFile();f.mkdirs();return f;}
            };
            Tools.DIR_GAME_HOME=isolated.getExternalFilesDir(null).toString();
            Path game=isolated.getNoBackupFilesDir().toPath().resolve("snake-n3st/instances/test-pack");
            Files.createDirectories(game.resolve("crash-reports"));Files.createDirectories(game.resolve("logs"));
            String fakeSecret="cd".repeat(32);
            long started=System.currentTimeMillis()-1000;
            var run=new GameCrashReports.Run(started,game.toString(),"test-pack","Modpack de test","1.21.11","External-21","Rendu de test",2048,true,true);
            Path runFile=isolated.getNoBackupFilesDir().toPath().resolve("snake-n3st/last-game-run.json");
            Files.writeString(runFile,Tools.GLOBAL_GSON.toJson(run));
            Files.writeString(game.resolve("crash-reports/crash-current.txt"),"CURRENT CRASH\n-Dsn3.token="+fakeSecret);
            Path old=game.resolve("crash-reports/crash-old.txt");Files.writeString(old,"OLD CRASH MUST NOT BE USED");Files.setLastModifiedTime(old,FileTime.fromMillis(started-60000));
            Files.writeString(game.resolve("logs/latest.log"),"GAME LOG\nStack trace");
            Files.writeString(Path.of(Tools.DIR_GAME_HOME,"latestlog.txt"),"LAUNCHER LOG\nJava exit code: 42\nseed="+fakeSecret);
            ProxySelector.setDefault(new ProxySelector(){
                @Override public List<Proxy> select(URI uri){requests.incrementAndGet();throw new IllegalStateException("No network in report test");}
                @Override public void connectFailed(URI uri,SocketAddress address,IOException failure){}
            });
            var report=GameCrashReports.collect(isolated,42,false);
            check(report.details().contains("CURRENT CRASH")&&report.details().contains("GAME LOG")&&report.details().contains("LAUNCHER LOG"));passed++;
            check(!report.details().contains(fakeSecret)&&!report.details().contains("OLD CRASH"));passed++;
            Path file=GameCrashReports.save(isolated,report);check(!Files.readString(file).contains(fakeSecret));passed++;
            var restored=GameCrashReports.read(isolated,file.getFileName().toString());check(restored.code()==42&&restored.details().equals(report.details()));passed++;
            boolean rejected=false;try{GameCrashReports.read(isolated,"../sn3-session.bin");}catch(IOException expected){rejected=true;}check(rejected);passed++;
            Path realFolder=target.getFilesDir().toPath().resolve("sn3-crash-reports");Files.createDirectories(realFolder);
            uiFile=realFolder.resolve("game-crash-fixture-"+UUID.randomUUID()+".json");Files.writeString(uiFile,Tools.GLOBAL_GSON.toJson(report));
            Tools.DIR_GAME_HOME=previousHome;
            activity=test.startActivitySync(new Intent(target,ExitActivity.class).putExtra("report",uiFile.getFileName().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity screen=activity;boolean[] visible={false};
            for(int i=0;i<60&&!visible[0];i++){test.waitForIdleSync();test.runOnMainSync(()->visible[0]=contains(screen.getWindow().getDecorView(),"Code de sortie : 42"));if(!visible[0])Thread.sleep(50);}
            check(visible[0]);passed++;
            test.runOnMainSync(()->{
                View view=screen.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
                try(OutputStream output=new FileOutputStream(new File(target.getCacheDir(),"sn3-report-check.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}catch(IOException failure){throw new RuntimeException(failure);}finally{bitmap.recycle();}
            });passed++;
            Path shareRoot=target.getCacheDir().toPath().resolve("sn3-crash-reports");Files.createDirectories(shareRoot);shared=shareRoot.resolve("fixture-"+UUID.randomUUID()+".txt");Files.writeString(shared,report.details());
            var uri=FileProvider.getUriForFile(target,target.getPackageName()+".updates",shared.toFile());
            try(InputStream input=target.getContentResolver().openInputStream(uri)){check(input!=null&&new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("CURRENT CRASH"));}passed++;
            check(requests.get()==0);passed++;result.putBoolean("success",true);
        }catch(Throwable failure){result.putBoolean("success",false);result.putString("message",failure.getClass().getSimpleName()+": "+failure.getMessage());}
        finally {
            try {
                if(activity!=null){Activity finish=activity;test.runOnMainSync(finish::finish);test.waitForIdleSync();}
                if(uiFile!=null)Files.deleteIfExists(uiFile);if(shared!=null)Files.deleteIfExists(shared);
                if(root!=null)try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
            }catch(Exception cleanup){result.putBoolean("success",false);result.putString("cleanup",cleanup.getClass().getSimpleName());}
            Tools.DIR_GAME_HOME=previousHome;ProxySelector.setDefault(previousProxy);result.putInt("checks",passed);result.putInt("httpRequests",requests.get());
            test.finish(result.getBoolean("success")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
    private static boolean contains(View view,String text){if(view instanceof TextView label&&label.getText().toString().contains(text))return true;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),text))return true;return false;}
    private static void check(boolean condition){if(!condition)throw new AssertionError("Crash report check failed");}
}
