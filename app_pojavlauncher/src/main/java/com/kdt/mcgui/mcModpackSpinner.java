package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.transition.Slide;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import fr.spse.extended_view.ExtendedTextView;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.snaken3st.RemoteIconLoader;
import mc.snakenest.launcher.modpack.ModpackSummary;

/** Fork of Amethyst's mcVersionSpinner, backed by the Snake N3st catalog. */
public final class mcModpackSpinner extends ExtendedTextView {
    private List<ModpackSummary> packs=List.of();
    private String selectedSlug;
    private String baseUrl;
    private String selectedImage;
    private Consumer<ModpackSummary> onSelected;
    private Runnable onEmpty;
    private ListView list;
    private PopupWindow popup;
    private final PackAdapter adapter=new PackAdapter();

    public mcModpackSpinner(@NonNull Context context) { super(context); init(); }
    public mcModpackSpinner(@NonNull Context context,@Nullable AttributeSet attrs) { super(context,attrs); init(); }
    public mcModpackSpinner(@NonNull Context context,@Nullable AttributeSet attrs,int style) { super(context,attrs,style); init(); }

    private void init() {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,getResources().getDimensionPixelSize(R.dimen._12ssp));
        setGravity(Gravity.CENTER_VERTICAL);
        int start=getResources().getDimensionPixelOffset(R.dimen._17sdp);
        setPaddingRelative(start,0,getResources().getDimensionPixelOffset(R.dimen._5sdp),0);
        setCompoundDrawablePadding(start);
        setOnClickListener(v->showPicker());
    }
    public void setOnEmptyAction(Runnable action) { onEmpty=action; }
    public void submitPacks(List<ModpackSummary> values,String slug,String origin,Consumer<ModpackSummary> listener) {
        boolean changed=!packs.equals(values) || !Objects.equals(selectedSlug,slug);
        packs=List.copyOf(values); selectedSlug=slug; baseUrl=origin; onSelected=listener;
        ModpackSummary selected=null;
        for(ModpackSummary pack:packs) if(pack.slug().equals(slug)) { selected=pack; break; }
        setText(selected==null?"Choisir un modpack Snake N3st":selected.name());
        String image=selected==null?null:selected.image();
        String imageKey=origin+"|"+image;
        if(!Objects.equals(selectedImage,imageKey)) {
            selectedImage=imageKey; setStartIcon(this,fallback());
            if(image!=null) RemoteIconLoader.load(getContext(),image,origin,icon->{
                if(icon!=null && imageKey.equals(selectedImage)) setStartIcon(this,icon);
            });
        }
        if(changed) adapter.notifyDataSetChanged();
    }
    public void showPicker() {
        if(packs.isEmpty()) { if(onEmpty!=null) onEmpty.run(); return; }
        if(popup==null) createPopup();
        if(popup.isShowing()) { popup.dismiss(); return; }
        int offset=-getResources().getDimensionPixelOffset(R.dimen._4sdp);
        popup.showAsDropDown(this,0,offset);
        post(()->{ for(int i=0;i<packs.size();i++) if(packs.get(i).slug().equals(selectedSlug)) { list.setSelection(i); break; } });
    }
    private void createPopup() {
        // Reuse Amethyst's popup, row layout, dimensions and slide transition.
        list=(ListView)inflate(getContext(),R.layout.spinner_mc_version,null);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent,view,position,id)->{
            ModpackSummary pack=packs.get(position); popup.dismiss();
            if(onSelected!=null) onSelected.accept(pack);
        });
        popup=new PopupWindow(list,ViewGroup.LayoutParams.MATCH_PARENT,getResources().getDimensionPixelOffset(R.dimen._184sdp));
        popup.setElevation(5); popup.setClippingEnabled(false); popup.setOutsideTouchable(true); popup.setFocusable(true);
        popup.setTouchInterceptor((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_OUTSIDE) { popup.dismiss(); return true; }
            return false;
        });
        popup.setEnterTransition(new Slide(Gravity.BOTTOM)); popup.setExitTransition(new Slide(Gravity.BOTTOM));
    }
    private Drawable fallback() { return ResourcesCompat.getDrawable(getResources(),R.drawable.ic_folder,getContext().getTheme()); }
    private static void setStartIcon(ExtendedTextView view,Drawable icon) {
        view.setCompoundDrawablesRelative(icon,null,view.getCompoundsDrawables()[2],null);
    }
    @Override protected void onDetachedFromWindow() { if(popup!=null) popup.dismiss(); super.onDetachedFromWindow(); }
    private final class PackAdapter extends BaseAdapter {
        @Override public int getCount() { return packs.size(); }
        @Override public ModpackSummary getItem(int position) { return packs.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position,View recycled,ViewGroup parent) {
            ExtendedTextView row=recycled instanceof ExtendedTextView?(ExtendedTextView)recycled:
                (ExtendedTextView)LayoutInflater.from(parent.getContext()).inflate(R.layout.item_version_profile_layout,parent,false);
            ModpackSummary pack=getItem(position); row.setText(pack.name());
            row.setBackgroundColor(pack.slug().equals(selectedSlug)?0x3CFFFFFF:Color.TRANSPARENT);
            String key=baseUrl+"|"+pack.image();
            if(!Objects.equals(row.getTag(R.id.sn3_image_url),key)) {
                row.setTag(R.id.sn3_image_url,key); setStartIcon(row,fallback());
                if(pack.image()!=null) RemoteIconLoader.load(getContext(),pack.image(),baseUrl,icon->{
                    if(icon!=null && key.equals(row.getTag(R.id.sn3_image_url))) setStartIcon(row,icon);
                });
            }
            return row;
        }
    }
}
