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
    private Thread worker;
    private TextView result;
    private Button start;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(getResources().getColor(R.color.background_app));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);scroll.addView(body);setContentView(scroll);
        Button back=new Button(this);back.setText("Retour aux paramètres");back.setOnClickListener(v->finish());body.addView(back);
        TextView title=new TextView(this);title.setText("Benchmark CPU + GPU");title.setTextSize(24);title.setTextColor(getResources().getColor(R.color.primary_text));body.addView(title);
        TextView explanation=new TextView(this);explanation.setText("Environ 10 secondes. Ferme le jeu et les applications gourmandes avant le test. CPU sur un cœur, puis rendu GPU natif dans une surface 512 × 512. Le test utilise temporairement le processeur et la puce graphique ; il s’arrête en quittant cette page.");explanation.setTextColor(getResources().getColor(R.color.primary_text));body.addView(explanation);
        start=new Button(this);start.setText("Lancer le test");start.setOnClickListener(v->begin());body.addView(start);
        Button cancel=new Button(this);cancel.setText("Annuler");cancel.setOnClickListener(v->cancel());body.addView(cancel);
        result=new TextView(this);result.setTextSize(15);result.setTextColor(getResources().getColor(R.color.primary_text));result.setTextIsSelectable(true);result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(result);
        if(state!=null)result.setText(state.getString("result",""));
    }
    private void begin() {
        if(worker!=null)return;
        start.setEnabled(false);result.setText("Mesure du CPU…");getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Thread thread=new Thread(()->{
            try {
                double cpu=DeviceBenchmark.cpu();phase("Mesure du GPU…");DeviceBenchmark.Gpu gpu=null;String failure=null;
                try {gpu=DeviceBenchmark.gpu();} catch(RuntimeException error){failure=error.getMessage();}
                DeviceBenchmark.checkCancelled();String report=DeviceBenchmark.report(cpu,gpu,failure);complete(report);
            } catch(InterruptedException ignored) { /* cancellation owns UI state */ }
            catch(RuntimeException failure){complete("Mesure impossible : "+failure.getMessage());}
        },"sn3-benchmark");
        worker=thread;thread.start();
    }
    private void phase(String text) {
        Thread source=Thread.currentThread();runOnUiThread(()->{if(worker==source&&!isDestroyed())result.setText(text);});
    }
    private void complete(String text) {
        Thread source=Thread.currentThread();runOnUiThread(()->{if(worker==source&&!isDestroyed()){worker=null;start.setEnabled(true);result.setText(text);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}});
    }
    private void cancel() {
        if(worker!=null){worker.interrupt();worker=null;result.setText("Test annulé.");}
        start.setEnabled(true);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onStop(){cancel();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("result",result.getText().toString());super.onSaveInstanceState(out);}
}
