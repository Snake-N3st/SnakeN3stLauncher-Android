package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import mc.snakenest.launcher.android.R;
import java.util.Locale;

/** Compact result dashboard; protocol/context remain available on demand. */
final class BenchmarkResultsView extends LinearLayout {
    private final int foreground;
    BenchmarkResultsView(Context context){
        super(context);setOrientation(VERTICAL);foreground=getResources().getColor(R.color.primary_text);
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean bold){
        TextView label=new TextView(getContext());label.setText(value);label.setTextSize(size);label.setTextColor(foreground);
        label.setLineSpacing(0,1.12f);if(bold)label.setTypeface(null,Typeface.BOLD);return label;
    }
    private LinearLayout card(){
        LinearLayout box=new LinearLayout(getContext());box.setOrientation(VERTICAL);box.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable background=new GradientDrawable();background.setColor(Color.argb(12,Color.red(foreground),Color.green(foreground),Color.blue(foreground)));
        background.setCornerRadius(dp(8));background.setStroke(dp(1),Color.argb(45,Color.red(foreground),Color.green(foreground),Color.blue(foreground)));box.setBackground(background);
        return box;
    }
    private void block(View child){LayoutParams params=new LayoutParams(-1,-2);params.topMargin=dp(12);addView(child,params);}
    void pending(String message){removeAllViews();block(text(message,16,false));}
    void render(double cpu,double frameMs,String renderer,double[] cpuSamples,double[] gpuSamples,String details){
        removeAllViews();block(text("Résultats JVM / Amethyst",21,true));
        if(!Double.isFinite(cpu)||cpu<=0||!Double.isFinite(frameMs)||frameMs<=0)block(text("Mesure incomplète : consulte les détails avant toute conclusion.",14,false));
        LinearLayout row=new LinearLayout(getContext());row.setOrientation(HORIZONTAL);
        LinearLayout cpuCard=card();cpuCard.addView(text("CPU · un cœur",14,true));
        cpuCard.addView(text(Double.isFinite(cpu)&&cpu>0?String.format(Locale.FRANCE,"%.1f",cpu):"—",25,true));cpuCard.addView(text("millions d’itérations/s",12,false));
        LinearLayout gpuCard=card();gpuCard.addView(text("GPU · scène de test",14,true));
        gpuCard.addView(text(Double.isFinite(frameMs)&&frameMs>0?String.format(Locale.FRANCE,"%.2f ms",frameMs):"—",25,true));gpuCard.addView(text("par image synthétique",12,false));
        LayoutParams half=new LayoutParams(0,-2,1);half.rightMargin=dp(6);row.addView(cpuCard,half);row.addView(gpuCard,new LayoutParams(0,-2,1));block(row);
        block(text("Rendu : "+(renderer==null||renderer.isBlank()?"non identifié":renderer),13,false));
        LinearLayout cpuPlot=card();cpuPlot.addView(text("CPU pendant le test · M itérations/s",15,true));cpuPlot.addView(new TimeSeriesChartView(getContext(),cpuSamples,"Millions d’itérations/s"),new LayoutParams(-1,dp(170)));block(cpuPlot);
        LinearLayout gpuPlot=card();gpuPlot.addView(text("GPU pendant le test · ms/image de test",15,true));gpuPlot.addView(new TimeSeriesChartView(getContext(),gpuSamples,"Millisecondes par image synthétique"),new LayoutParams(-1,dp(170)));block(gpuPlot);
        LinearLayout uses=card();uses.addView(text("Ce que cela valide",17,true));
        uses.addView(text("• Calcul JVM et rendu Amethyst : mesurés si le test a réussi.\n• Vanilla / petits packs : essai en jeu nécessaire.\n• Gros modpacks : non validés.\n• Shaders : pas de recommandation sans essai en jeu.\n• 60 / 120 FPS Minecraft : non mesurés.",14,false));block(uses);
        TextView full=text(details==null?"Détails indisponibles":details,13,false);full.setTextIsSelectable(true);full.setVisibility(GONE);
        Button toggle=new Button(getContext());toggle.setAllCaps(false);toggle.setText("Afficher les détails du test");
        toggle.setOnClickListener(v->{boolean open=full.getVisibility()!=VISIBLE;full.setVisibility(open?VISIBLE:GONE);toggle.setText(open?"Masquer les détails":"Afficher les détails du test");});
        block(toggle);block(full);
    }
}
