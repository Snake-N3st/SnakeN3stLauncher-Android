package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.TextProgressBar;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.progresskeeper.ProgressKeeper;
import mc.snakenest.launcher.android.progresskeeper.ProgressListener;
import java.util.concurrent.atomic.AtomicReference;

/** Interleave real pack callbacks with Minecraft updates; no network, files or game are touched. */
final class ProgressChecks {
    static void run(Instrumentation test) {
        Bundle result=new Bundle();int checks=0;ModpackProgress pack=null;
        String[] keys={ProgressLayout.DOWNLOAD_SNAKE_MODPACK,ProgressLayout.APPLY_SNAKE_MODPACK,ProgressLayout.DOWNLOAD_MINECRAFT};
        Recorder[] records={new Recorder(),new Recorder(),new Recorder()};
        int baseline=ProgressKeeper.getTaskCount();
        try {
            for(int i=0;i<keys.length;i++)ProgressKeeper.addListener(keys[i],records[i]);
            var file=new AtomicReference<String>();pack=new ModpackProgress(file::set);
            pack.onDownloadProgress(25,100);
            check(records[0].starts==1&&records[0].progress==25&&records[0].resource==R.string.sn3_download_progress,"initial update must carry actual data");checks++;
            pack.onFileStarted("mods/example.jar",100);
            check(records[1].progress==-1&&records[1].resource==R.string.sn3_apply_file&&file.get().equals("mods/example.jar"),"application has its own indeterminate row");checks++;
            check(records[0].progress==25&&records[0].resource==R.string.sn3_download_progress,"file update must not overwrite download");checks++;
            ProgressKeeper.submitProgress(keys[2],60,R.string.newdl_starting);
            pack.onDownloadProgress(50,100);
            check(records[0].progress==50&&records[2].progress==60&&records[1].progress==-1,"three independent records");checks++;
            pack.onDeleted("obsolete.txt");
            check(records[1].resource==R.string.sn3_apply_delete&&records[0].progress==50&&records[2].progress==60,"delete must not replace either transfer");checks++;
            check(ProgressKeeper.getTaskCount()==baseline+3,"all active records count independently");checks++;
            var late=new Recorder();ProgressKeeper.addListener(keys[0],late);
            try {check(late.starts==1&&late.progress==50,"observer reconnect gets current data");checks++;}
            finally {ProgressKeeper.removeListener(keys[0],late);}
            pack.close();check(ProgressKeeper.getTaskCount()==baseline+1,"pack completion leaves Minecraft active");checks++;
            int starts=records[0].starts;pack.onDownloadProgress(75,100);pack.onFileStarted("late.jar",1);
            check(records[0].starts==starts&&ProgressKeeper.getTaskCount()==baseline+1,"late worker updates cannot resurrect ended rows");checks++;
            ProgressLayout.clearProgress(keys[2]);check(ProgressKeeper.getTaskCount()==baseline,"complete cleanup");checks++;
            try(ModpackProgress failing=new ModpackProgress(path->{})) {failing.onDownloadProgress(1,100);throw new java.io.IOException("fixture");}
            catch(java.io.IOException expected) { }
            check(ProgressKeeper.getTaskCount()==baseline,"failure clears pack records");checks++;
            test.runOnMainSync(()->check(new TextProgressBar(test.getTargetContext()).getProgress()==0,"no fabricated initial 35 percent"));checks++;
            DownloadProgressRows[] ui={null};
            test.runOnMainSync(()->{ui[0]=new DownloadProgressRows(test.getTargetContext(),null);ui[0].start();});
            try {
                try(ModpackProgress visible=new ModpackProgress(path->{})) {
                    visible.onDownloadProgress(20,100);visible.onFileStarted("example.jar",100);
                    ProgressKeeper.submitProgress(keys[2],70,R.string.newdl_starting);
                    test.waitForIdleSync();
                    test.runOnMainSync(()->{
                        var packBar=(android.widget.ProgressBar)ui[0].findViewWithTag(keys[0]+":bar");
                        var gameBar=(android.widget.ProgressBar)ui[0].findViewWithTag(keys[2]+":bar");
                        check(packBar!=gameBar&&packBar.getProgress()==20&&gameBar.getProgress()==70,"manager must show distinct bars with distinct percentages");
                    });checks++;
                    visible.onDownloadProgress(40,100);test.waitForIdleSync();
                    test.runOnMainSync(()->check(((android.widget.ProgressBar)ui[0].findViewWithTag(keys[2]+":bar")).getProgress()==70,"pack callbacks cannot change game UI"));checks++;
                }
                test.waitForIdleSync();
                test.runOnMainSync(()->check(ui[0].findViewWithTag(keys[0]).getVisibility()==android.view.View.GONE&&ui[0].findViewWithTag(keys[2]).getVisibility()==android.view.View.VISIBLE,"ending pack must keep Minecraft row visible"));checks++;
            } finally {test.runOnMainSync(ui[0]::stop);ProgressLayout.clearProgress(keys[2]);}
            result.putBoolean("success",true);
        }catch(Throwable failure){result.putBoolean("success",false);result.putString("message",failure.toString());}
        finally {
            if(pack!=null)pack.close();
            for(int i=0;i<keys.length;i++){ProgressLayout.clearProgress(keys[i]);ProgressKeeper.removeListener(keys[i],records[i]);}
        }
        result.putInt("checks",checks);test.finish(result.getBoolean("success")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Recorder implements ProgressListener {
        int starts,progress,resource;
        public void onProgressStarted(){starts++;}
        public void onProgressUpdated(int percent,int resid,Object... values){progress=percent;resource=resid;}
        public void onProgressEnded(){}
    }
}
