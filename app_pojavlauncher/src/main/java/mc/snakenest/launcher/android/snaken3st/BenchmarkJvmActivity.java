package mc.snakenest.launcher.android.snaken3st;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.android.multirt.MultiRTUtils;
import mc.snakenest.launcher.android.utils.JREUtils;
import java.io.*;
import java.util.*;

/** Separate process because an Amethyst JVM cannot be safely restarted inside the launcher. */
public final class BenchmarkJvmActivity extends AppCompatActivity {
    private boolean started,finished;
    private File result;
    private final Handler timer=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);Tools.initStorageConstants(this);LauncherPreferences.loadPreferences(this);Tools.updateWindowSize(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String id=getIntent().getStringExtra("run");
        if(id==null || !id.matches("[a-f0-9]{32}")){finish();return;}
        result=new File(getCacheDir(),"benchmark-"+id+".properties");
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        TextView label=new TextView(this);label.setText("Benchmark dans la JVM Amethyst\nCPU puis GPU avec le moteur sélectionné (~10 s)");layout.addView(label);
        Button cancel=new Button(this);cancel.setText("Annuler");cancel.setOnClickListener(v->stop(null));layout.addView(cancel);
        SurfaceView surface=new SurfaceView(this);layout.addView(surface,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);
        surface.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder holder){if(!started){started=true;try{JREUtils.setupBridgeWindow(holder.getSurface());new Thread(()->launch(),"sn3-benchmark-jvm").start();timer.postDelayed(()->stop("Le benchmark a dépassé 45 secondes."),45000);}catch(Throwable error){stop("Surface de rendu indisponible : "+error.getMessage());}}}
            public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
            public void surfaceDestroyed(SurfaceHolder holder){}
        });
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){public void handleOnBackPressed(){stop(null);}});
    }
    private void launch() {
        try {
            String runtime=getIntent().getStringExtra("runtime"),renderer=getIntent().getStringExtra("renderer");
            if(runtime==null || renderer==null)throw new IOException("Configuration manquante");
            Tools.LOCAL_RENDERER=renderer;Tools.iLwjglVersion=333;Tools.sLwjglVersion="3.3.3";
            Tools.lwjglNativesDir=String.format(Locale.ROOT,"%s/lwjgl-3.3.3-natives/%s",Tools.DIR_DATA,mc.snakenest.launcher.android.Architecture.archAsStringAndroid(mc.snakenest.launcher.android.Architecture.getDeviceArchitecture()));
            File lwjgl=new File(Tools.DIR_GAME_HOME,"lwjgl3/3.3.3");
            File jar=new File(getCacheDir(),"sn3-benchmark.jar");
            try(InputStream in=getAssets().open("sn3-benchmark/sn3-benchmark.jar");OutputStream out=new FileOutputStream(jar)){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
            File[] modules=lwjgl.listFiles(f->f.getName().endsWith(".jar")&&!f.getName().contains("lwjglx"));
            if(modules==null || modules.length==0 || !new File(Tools.lwjglNativesDir,"liblwjgl.so").isFile())throw new IOException("Composants LWJGL absents : ouvre le launcher et termine son initialisation avant le test.");
            StringBuilder cp=new StringBuilder(jar.getAbsolutePath());
            // The unpatched core must precede merged module classes, matching Amethyst's game classpath.
            cp.append(':').append(new File(lwjgl,"lwjgl.jar"));cp.append(':').append(new File(lwjgl,"lwjgl-3.3.3-merged-modules.jar"));
            for(File module:modules)if(!module.getName().equals("lwjgl.jar")&&!module.getName().contains("merged-modules"))cp.append(':').append(module.getAbsolutePath());
            List<String> args=new ArrayList<>();args.add("-Dorg.lwjgl.librarypath="+Tools.lwjglNativesDir);args.add("-Djava.library.path="+Tools.lwjglNativesDir+":"+Tools.NATIVE_LIB_DIR);args.add("-Dglfwstub.windowWidth=512");args.add("-Dglfwstub.windowHeight=512");args.add("-cp");args.add(cp.toString());args.add("mc.snakenest.benchmark.BenchmarkMain");args.add(result.getAbsolutePath());
            int code=JREUtils.launchBenchmarkVM(this,MultiRTUtils.forceReread(runtime),getCacheDir(),args);
            runOnUiThread(()->stop(result.isFile()?null:"La JVM s’est arrêtée (code "+code+") sans résultat."));
        } catch(Throwable error){runOnUiThread(()->stop(error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage())));}
    }
    private void stop(String error) {
        if(finished)return;finished=true;timer.removeCallbacksAndMessages(null);
        Intent data=new Intent();if(error!=null)data.putExtra("error",error);setResult(result!=null&&result.isFile()?RESULT_OK:RESULT_CANCELED,data);finish();
        timer.postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),250);
    }
    @Override protected void onStop(){if(!finished)stop(null);super.onStop();}
}
