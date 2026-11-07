package mc.snakenest.launcher.android.snaken3st;

import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.bugreport.*;
import mc.snakenest.launcher.net.HttpJsonClient;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.*;

/** Crash report runs in the launcher process, independently of the failed game JVM. */
public class GameCrashActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private GameCrashReports.Report report;
    private String reportName;
    private boolean sent,sending;
    private LinearLayout body;
    private TextView state;
    private Button send;
    private final ActivityResultLauncher<String> export=registerForActivityResult(new ActivityResultContracts.CreateDocument(),uri->{if(uri!=null&&report!=null)writeExport(uri);});
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Tools.initStorageConstants(this);LauncherPreferences.loadPreferences(this);
        if(saved!=null){reportName=saved.getString("reportName");sent=saved.getBoolean("sent");}
        if(reportName==null)reportName=getIntent().getStringExtra("report");
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(color(R.color.background_app));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(18),dp(22),dp(24));scroll.addView(body);setContentView(scroll);
        label("Préparation du rapport…",23,true);state=label("Récupération des journaux du jeu.",14,false);
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){backToLauncher();}});
        worker.execute(()->{
            try {
                GameCrashReports.Report loaded;
                if(reportName!=null)loaded=GameCrashReports.read(this,reportName);
                else {
                    Thread.sleep(150);loaded=GameCrashReports.collect(this,getIntent().getIntExtra("code",-1),getIntent().getBooleanExtra("isSignal",false));
                    try{reportName=GameCrashReports.save(this,loaded).getFileName().toString();}catch(IOException ignored){}
                }
                report=loaded;runOnUiThread(()->{if(!isDestroyed())render();});
            }catch(Exception failure){runOnUiThread(()->{if(!isDestroyed()){body.removeAllViews();label("Rapport indisponible",23,true);label("Les détails du plantage n’ont pas pu être récupérés.",15,false);button("Retour au launcher",this::backToLauncher);}});}
        });
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("reportName",reportName);out.putBoolean("sent",sent);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private void render() {
        body.removeAllViews();
        button("Retour au launcher",this::backToLauncher);
        label(report.run().game()?"Le jeu s’est arrêté":"L’installateur Java s’est arrêté",27,true);
        label("Un arrêt inattendu a été détecté. Ce rapport rassemble les informations disponibles pour comprendre le problème.",15,false);
        TextView badge=label((report.signal()?"Signal reçu : ":"Code de sortie : ")+report.code(),16,true);badge.setTextColor(0xffffb3a8);
        label("Contexte du lancement",18,true);
        String date=report.timestamp();try{date=DateTimeFormatter.ofPattern("d MMMM yyyy à HH:mm:ss",Locale.FRENCH).withZone(ZoneId.systemDefault()).format(Instant.parse(date));}catch(Exception ignored){}
        label("Modpack : "+safe(report.run().name())+"\nMinecraft : "+safe(report.run().version())+"\nJava : "+safe(report.run().runtime())+"\nRendu : "+safe(report.run().renderer())+"\nMémoire : "+report.run().memory()+" Mo\nDate : "+date,14,false);
        state=label("Le rapport est conservé sur cet appareil. Rien n’est envoyé automatiquement.",13,false);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        send=button(sent?"Rapport envoyé":"Envoyer un rapport à l’équipe",this::confirmSend);send.setEnabled(!sent&&!offline());
        if(offline())label("L’envoi nécessite une connexion. Tu peux enregistrer ou partager le rapport hors connexion.",13,false);
        button("Copier le rapport",()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Rapport Snake N3st",report.details()));Toast.makeText(this,"Rapport copié",Toast.LENGTH_SHORT).show();});
        button("Enregistrer le rapport",()->export.launch("SnakeN3st-rapport-"+System.currentTimeMillis()+".txt"));
        button("Partager le rapport",this::share);
        LinearLayout technical=new LinearLayout(this);technical.setOrientation(LinearLayout.VERTICAL);technical.setVisibility(View.GONE);
        Button details=button("Afficher les détails techniques",()->{boolean show=technical.getVisibility()!=View.VISIBLE;technical.setVisibility(show?View.VISIBLE:View.GONE);});
        details.setOnClickListener(v->{boolean show=technical.getVisibility()!=View.VISIBLE;if(show&&technical.getChildCount()==0){TextView text=new TextView(this);text.setText(report.details());text.setTextSize(11);text.setTextColor(color(R.color.primary_text));text.setTypeface(Typeface.MONOSPACE);text.setTextIsSelectable(true);text.setPadding(dp(10),dp(12),dp(10),dp(12));technical.addView(text);}technical.setVisibility(show?View.VISIBLE:View.GONE);details.setText(show?"Masquer les détails techniques":"Afficher les détails techniques");});
        body.addView(technical);
    }
    private boolean offline(){SnakeRepository repo=SnakeRepository.get(this);return repo.offlineMode||repo.session==null&&report.run().offline();}
    private void confirmSend() {
        if(sent||sending||offline())return;
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),0,dp(20),0);
        TextView explanation=new TextView(this);explanation.setText("Le contexte et les journaux masqués seront transmis à l’équipe Snake N3st."+(report.details().length()>65000?" Pour les longs journaux, un extrait du début et de la fin sera envoyé ; le rapport complet reste exportable.":"")+" Que faisais-tu avant le plantage ?");form.addView(explanation);
        EditText description=new EditText(this);description.setHint("Description facultative");description.setMinLines(3);form.addView(description);
        new AlertDialog.Builder(this).setTitle("Envoyer ce rapport ?").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Envoyer",(dialog,which)->submit(description.getText().toString())).show();
    }
    private void submit(String description) {
        sending=true;send.setEnabled(false);state.setText("Envoi du rapport…");
        worker.execute(()->{
            boolean accepted=new BugReportApiClient(new HttpJsonClient(),URI.create(BuildConfig.SN3_BASE_URL),BuildConfig.SN3_CLIENT_ID)
                .submit(report.run().game()?BugReportType.GAME_CRASH:BugReportType.LAUNCHER_CRASH,
                    (report.run().game()?"Minecraft":"Java")+" — "+(report.signal()?"signal ":"code ")+report.code()+" — "+safe(report.run().name()),
                    CrashReportText.sanitize(description),CrashReportText.forSubmission(report.details()),report.run().pack());
            runOnUiThread(()->{if(isDestroyed())return;sending=false;sent=accepted;send.setEnabled(!accepted&&!offline());send.setText(accepted?"Rapport envoyé":"Réessayer l’envoi");state.setText(accepted?"Merci, le rapport a été reçu par l’équipe.":"L’envoi a échoué. Le rapport reste disponible sur cet appareil.");});
        });
    }
    private void share() {
        worker.execute(()->{
            try {
                File directory=new File(getCacheDir(),"sn3-crash-reports");directory.mkdirs();File file=new File(directory,"rapport.txt");
                Files.writeString(file.toPath(),report.details(),StandardCharsets.UTF_8);
                Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",file);
                Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("Rapport",uri));
                runOnUiThread(()->{if(!isDestroyed())startActivity(Intent.createChooser(intent,"Partager le rapport"));});
            }catch(Exception failure){runOnUiThread(()->{if(!isDestroyed())state.setText("Le partage est indisponible. Tu peux copier ou enregistrer le rapport.");});}
        });
    }
    private void writeExport(Uri uri) {
        worker.execute(()->{
            boolean saved=false;try(OutputStream output=getContentResolver().openOutputStream(uri)){if(output==null)throw new IOException();output.write(report.details().getBytes(StandardCharsets.UTF_8));saved=true;}catch(Exception ignored){}
            boolean success=saved;runOnUiThread(()->{if(!isDestroyed())state.setText(success?"Rapport enregistré.":"Impossible d’enregistrer le rapport.");});
        });
    }
    private void backToLauncher(){startActivity(new Intent(this,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}
    private static String safe(String value){return value==null||value.isBlank()?"Non disponible":CrashReportText.sanitize(value);}
    private TextView label(String value,int size,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color(bold?R.color.primary_text:R.color.secondary_text));if(bold)view.setTypeface(null,Typeface.BOLD);view.setPadding(0,dp(10),0,dp(8));body.addView(view);return view;}
    private Button button(String text,Runnable action){Button button=new Button(this);button.setAllCaps(false);button.setText(text);button.setOnClickListener(v->action.run());body.addView(button,new LinearLayout.LayoutParams(-1,-2));return button;}
    private int color(int id){return getResources().getColor(id);}private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
