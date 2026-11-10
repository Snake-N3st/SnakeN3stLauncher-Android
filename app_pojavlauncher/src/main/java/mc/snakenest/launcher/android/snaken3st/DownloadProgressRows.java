package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.kdt.mcgui.ProgressLayout;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.progresskeeper.ProgressKeeper;
import mc.snakenest.launcher.android.progresskeeper.ProgressListener;

/** One independently owned UI row per concurrent installer record. */
public final class DownloadProgressRows extends LinearLayout {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Row[] rows;
    private volatile boolean listening;
    private volatile int generation;
    public DownloadProgressRows(Context context,AttributeSet attributes) {
        super(context,attributes);setOrientation(VERTICAL);setVisibility(GONE);
        rows=new Row[]{new Row(ProgressLayout.DOWNLOAD_SNAKE_MODPACK,"Modpack"),
                new Row(ProgressLayout.DOWNLOAD_MINECRAFT,"Minecraft"),
                new Row(ProgressLayout.UNPACK_RUNTIME,"Java"),
                new Row(ProgressLayout.APPLY_SNAKE_MODPACK,"Fichiers du modpack")};
    }
    public void start() {
        if(listening)return;listening=true;generation++;
        for(Row row:rows)ProgressKeeper.addListener(row.key,row);
    }
    public void stop() {
        if(!listening)return;listening=false;generation++;
        for(Row row:rows){ProgressKeeper.removeListener(row.key,row);row.container.setVisibility(GONE);}
        setVisibility(GONE);
    }
    private void update(Runnable action) {
        int current=generation;
        main.post(()->{if(listening&&current==generation){action.run();boolean visible=false;
            for(Row row:rows)visible|=row.container.getVisibility()==VISIBLE;
            setVisibility(visible?VISIBLE:GONE);
        }});
    }
    private final class Row implements ProgressListener {
        final String key,title;
        final LinearLayout container;
        final TextView label;
        final ProgressBar bar;
        Row(String key,String title) {
            this.key=key;this.title=title;
            container=new LinearLayout(getContext());container.setOrientation(VERTICAL);container.setVisibility(GONE);container.setTag(key);
            int padding=Math.round(4*getResources().getDisplayMetrics().density);container.setPadding(0,padding,0,padding);
            label=new TextView(getContext());label.setTextSize(12);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setTextColor(getResources().getColor(R.color.primary_text));label.setTag(key+":label");
            bar=new ProgressBar(getContext(),null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setTag(key+":bar");
            container.addView(label,new LinearLayout.LayoutParams(-1,-2));container.addView(bar,new LinearLayout.LayoutParams(-1,padding));
            addView(container,new LinearLayout.LayoutParams(-1,-2));
        }
        public void onProgressStarted(){update(()->{container.setVisibility(VISIBLE);label.setText(title);bar.setIndeterminate(true);});}
        public void onProgressUpdated(int percent,int resource,Object... values){update(()->{
            container.setVisibility(VISIBLE);bar.setIndeterminate(percent<0);if(percent>=0)bar.setProgress(percent);
            String message=resource!=-1?getContext().getString(resource,values):values!=null&&values.length>0&&values[0] instanceof String?(String)values[0]:"";
            label.setText(message.startsWith(title)?message:title+" · "+message);
        });}
        public void onProgressEnded(){update(()->container.setVisibility(GONE));}
    }
}
