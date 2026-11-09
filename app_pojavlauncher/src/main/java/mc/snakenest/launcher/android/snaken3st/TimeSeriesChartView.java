package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import mc.snakenest.launcher.android.R;
import java.util.Locale;

/** Measured samples only; no interpolation presented as an additional observation. */
final class TimeSeriesChartView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final double[] samples;
    private final int foreground;
    TimeSeriesChartView(Context context,double[] values,String unit) {
        super(context);samples=values==null?new double[0]:values.clone();
        foreground=getResources().getColor(R.color.primary_text);
        setMinimumHeight(dp(170));
        setContentDescription(unit+" : "+samples.length+" relevés mesurés. Axe horizontal : relevés successifs.");
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private float sp(float v){return v*getResources().getDisplayMetrics().scaledDensity;}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left=dp(50),right=getWidth()-dp(12),top=dp(12),bottom=getHeight()-dp(35);
        if(right<=left||bottom<=top)return;
        double maximum=0;int valid=0;
        for(double sample:samples)if(Double.isFinite(sample)&&sample>=0){maximum=Math.max(maximum,sample);valid++;}
        paint.setColor(foreground);paint.setTextSize(sp(11));paint.setStrokeWidth(dp(1));
        if(valid==0){canvas.drawText("Courbe indisponible pour cet essai",dp(8),(top+bottom)/2,paint);return;}
        maximum=maximum<=0?1:maximum*1.1;
        for(int tick=0;tick<=3;tick++){
            float y=bottom-(bottom-top)*tick/3f;
            paint.setColor(Color.argb(45,Color.red(foreground),Color.green(foreground),Color.blue(foreground)));
            canvas.drawLine(left,y,right,y,paint);paint.setColor(foreground);
            String label=String.format(Locale.FRANCE,maximum>=100?"%.0f":"%.1f",maximum*tick/3);
            canvas.drawText(label,Math.max(dp(2),left-dp(6)-paint.measureText(label)),y+sp(4),paint);
        }
        paint.setColor(0xff42b96b);paint.setStrokeWidth(dp(2));
        float previousX=0,previousY=0;boolean previous=false;
        for(int i=0;i<samples.length;i++){
            double value=samples[i];
            if(!Double.isFinite(value)||value<0){previous=false;continue;}
            float x=samples.length==1?(left+right)/2:left+(right-left)*i/(samples.length-1f);
            float y=bottom-(float)(value/maximum)*(bottom-top);
            if(previous)canvas.drawLine(previousX,previousY,x,y,paint);
            canvas.drawCircle(x,y,dp(2),paint);previousX=x;previousY=y;previous=true;
        }
        paint.setColor(foreground);paint.setTextSize(sp(11));
        canvas.drawText("1",left,bottom+dp(17),paint);
        String end=Integer.toString(samples.length);canvas.drawText(end,right-paint.measureText(end),bottom+dp(17),paint);
        String axis="Relevés successifs";canvas.drawText(axis,(left+right-paint.measureText(axis))/2,bottom+dp(31),paint);
    }
}
