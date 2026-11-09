package mc.snakenest.launcher.android.snaken3st;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import mc.snakenest.launcher.android.R;

/** Explicit, cancellable test; no benchmark runs simply by visiting settings. */
public final class BenchmarkActivity extends AppCompatActivity {
    private boolean inFlight;
    private String runId;
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> benchmark=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), response->{
        if(runId==null)return;
        java.io.File file=new java.io.File(getCacheDir(),"benchmark-"+runId+".properties");
        try {completeMeasurements(DeviceBenchmark.readResult(file),DeviceBenchmark.read(file));}
        catch(java.io.IOException error){complete(response.getData()!=null&&response.getData().hasExtra("error")?"Mesure impossible : "+response.getData().getStringExtra("error"):error.getMessage());}
        finally {file.delete();runId=null;}
    });
    private BenchmarkResultsView dashboard;
    private String lastDetails,lastRenderer;
    private double lastCpu,lastFrame;
    private double[] lastCpuSamples,lastGpuSamples;
    private TextView result;
    private Button start;
    private Button cancelButton;
    private long startedAt;
    private String initialContext = "";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(getResources().getColor(R.color.background_app));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);scroll.addView(body);setContentView(scroll);
        Button back=new Button(this);back.setText("Retour aux paramètres");back.setAllCaps(false);back.setOnClickListener(v->finish());body.addView(back);
        TextView title=new TextView(this);title.setText("Benchmark CPU + GPU");title.setTextSize(24);title.setTextColor(getResources().getColor(R.color.primary_text));body.addView(title);
        TextView explanation=new TextView(this);explanation.setText("Mesures dans un JAR exécuté par la JVM Amethyst et le moteur de rendu sélectionnés (~10 s). Ce diagnostic ne lance pas Minecraft et ne valide ni ses FPS ni ses shaderpacks.");explanation.setTextColor(getResources().getColor(R.color.primary_text));body.addView(explanation);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);body.addView(actions);
        start=new Button(this);start.setAllCaps(false);start.setText("Lancer le test");start.setOnClickListener(v->begin());actions.addView(start,new LinearLayout.LayoutParams(0,-2,1));
        cancelButton=new Button(this);cancelButton.setAllCaps(false);cancelButton.setText("Annuler");cancelButton.setEnabled(false);cancelButton.setOnClickListener(v->cancel());actions.addView(cancelButton,new LinearLayout.LayoutParams(0,-2,1));
        result=new TextView(this);result.setTextSize(15);result.setLineSpacing(0,1.16f);result.setTextColor(getResources().getColor(R.color.primary_text));result.setTextIsSelectable(true);result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(result);
        dashboard=new BenchmarkResultsView(this);body.addView(dashboard);
        if(state!=null){result.setText(state.getString("result",""));runId=state.getString("run");initialContext=state.getString("initialContext","");startedAt=state.getLong("startedAt");inFlight=runId!=null;start.setEnabled(!inFlight);
            lastDetails=state.getString("details");lastRenderer=state.getString("renderer");lastCpu=state.getDouble("cpu");lastFrame=state.getDouble("frame");lastCpuSamples=state.getDoubleArray("cpuSamples");lastGpuSamples=state.getDoubleArray("gpuSamples");
            if(lastDetails!=null)dashboard.render(lastCpu,lastFrame,lastRenderer,lastCpuSamples,lastGpuSamples,lastDetails);
        }
    }
    private void begin() {
        if(inFlight)return;
        try {
            DeviceBenchmark.Configuration config=DeviceBenchmark.configuration(this);
            runId=java.util.UUID.randomUUID().toString().replace("-", "");inFlight=true;
            startedAt=System.nanoTime();initialContext=contextInfo()+"\nRuntime sélectionné : "+config.runtime()+"\nMoteur sélectionné : "+config.renderer()+"\nTas JVM du test : 64–256 Mio";
            start.setEnabled(false);cancelButton.setEnabled(false);result.setText("Démarrage de la JVM Amethyst…");dashboard.removeAllViews();lastDetails=null;
            benchmark.launch(new android.content.Intent(this,BenchmarkJvmActivity.class).putExtra("run",runId).putExtra("runtime",config.runtime()).putExtra("renderer",config.renderer()));
        } catch(RuntimeException error){inFlight=false;start.setEnabled(true);result.setText("Mesure impossible : "+error.getMessage());}
    }
    private void complete(String error) {
        inFlight=false;start.setEnabled(true);start.setText("Relancer le test");result.setText(error);
        dashboard.pending("Aucune conclusion possible pour cet essai.");
    }
    private void completeMeasurements(DeviceBenchmark.Measurement measured,String details) {
        inFlight=false;start.setEnabled(true);start.setText("Relancer le test");result.setText("");
        lastCpu=measured.cpu();
        boolean validGpu=!measured.software()&&measured.error()==null&&Double.isFinite(measured.frameMillis())&&measured.frameMillis()>0&&Double.isFinite(measured.gpu())&&measured.gpu()>0;
        lastFrame=validGpu?measured.frameMillis():Double.NaN;lastRenderer=measured.renderer();
        lastCpuSamples=measured.cpuSamples().stream().mapToDouble(Double::doubleValue).toArray();
        lastGpuSamples=validGpu?measured.gpuSamples().stream().mapToDouble(Double::doubleValue).toArray():new double[0];
        lastDetails=details+"\n\nCONTEXTE DE L’ESSAI\n"+initialContext+"\nÉtat thermique après : "+thermalState()
                +String.format(java.util.Locale.FRANCE,"\nDurée totale : %.1f s",(System.nanoTime()-startedAt)/1_000_000_000.0);
        dashboard.render(lastCpu,lastFrame,lastRenderer,lastCpuSamples,lastGpuSamples,lastDetails);
    }
    private String contextInfo() {
        android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();
        ((android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        android.view.Display display=getWindowManager().getDefaultDisplay();
        android.view.Display.Mode mode=display.getMode();
        android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        return "Relevé : "+new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss",java.util.Locale.FRANCE).format(new java.util.Date())+"\n"+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+" · Android "+android.os.Build.VERSION.RELEASE
                +String.format(java.util.Locale.FRANCE,"\nÉcran : %d × %d · %.1f Hz au relevé",mode.getPhysicalWidth(),mode.getPhysicalHeight(),display.getRefreshRate())
                +String.format(java.util.Locale.FRANCE,"\nÉchelle globale du jeu : %.0f %% (non appliquée à la scène de test)",mc.snakenest.launcher.android.prefs.LauncherPreferences.PREF_SCALE_FACTOR*100)
                +String.format(java.util.Locale.FRANCE,"\nRAM totale : %.1f Gio · disponible : %.1f Gio (instantané, pas un test mémoire)",memory.totalMem/1073741824.0,memory.availMem/1073741824.0)
                +"\nÉconomie d’énergie : "+(power.isPowerSaveMode()?"activée":"désactivée")
                +"\nÉtat thermique avant : "+thermalState();
    }
    private String thermalState() {
        if(android.os.Build.VERSION.SDK_INT<29)return "indisponible";
        int status=((android.os.PowerManager)getSystemService(POWER_SERVICE)).getCurrentThermalStatus();
        String[] names={"normal","léger","modéré","sévère","critique","urgence","arrêt"};
        return status>=0&&status<names.length?names[status]:"inconnu";
    }
    private void cancel() {result.setText("Le test s’annule depuis sa fenêtre JVM.");}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("result",result.getText().toString());out.putString("run",runId);out.putString("initialContext",initialContext);out.putLong("startedAt",startedAt);out.putString("details",lastDetails);out.putString("renderer",lastRenderer);out.putDouble("cpu",lastCpu);out.putDouble("frame",lastFrame);out.putDoubleArray("cpuSamples",lastCpuSamples);out.putDoubleArray("gpuSamples",lastGpuSamples);super.onSaveInstanceState(out);}
}
