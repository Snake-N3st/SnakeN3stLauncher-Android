package mc.snakenest.launcher.android.snaken3st;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import mc.snakenest.launcher.android.LauncherActivity;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.news.*;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** News inside Amethyst's normal fragment navigation; article content stays in the browser. */
public final class Sn3NewsFragment extends Fragment {
    public static final String TAG="Sn3News";
    private SnakeRepository repo;
    private LinearLayout feed;
    private TextView state;
    private Button refresh,offline;
    private ProgressBar progress;
    private java.util.List<Post> rendered;
    private boolean wasOffline;
    private final Runnable observer=()->{if(isAdded()&&getView()!=null)render();};

    public static void open(LauncherActivity host) {Tools.swapFragment(host,Sn3NewsFragment.class,TAG,null);}
    @Override public void onCreate(@Nullable Bundle saved) {super.onCreate(saved);repo=SnakeRepository.get(requireContext());}
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle saved) {
        LinearLayout root=column();root.setBackgroundColor(color(R.color.background_app));root.setPadding(dp(18),dp(12),dp(18),0);
        LinearLayout toolbar=new LinearLayout(requireContext());toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(requireContext());back.setText("Retour");back.setAllCaps(false);back.setOnClickListener(v->getParentFragmentManager().popBackStack());toolbar.addView(back);
        Space space=new Space(requireContext());toolbar.addView(space,new LinearLayout.LayoutParams(0,1,1));
        refresh=new Button(requireContext());refresh.setText("Actualiser");refresh.setAllCaps(false);refresh.setOnClickListener(v->refresh());toolbar.addView(refresh);root.addView(toolbar);
        TextView title=text("Actualités",27,color(R.color.primary_text));title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(12),0,dp(4));root.addView(title);
        TextView subtitle=text("Les nouveautés et les rendez-vous de Snake N3st.",13,color(R.color.secondary_text));subtitle.setPadding(0,0,0,dp(14));root.addView(subtitle);
        progress=new ProgressBar(requireContext(),null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        state=text("",13,color(R.color.secondary_text));state.setPadding(0,dp(8),0,dp(8));state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(state);
        offline=new Button(requireContext());offline.setText("Passer hors connexion");offline.setAllCaps(false);offline.setOnClickListener(v->{try{repo.goOffline();render();}catch(Exception failure){notice("Hors connexion",failure.getMessage());}});root.addView(offline);
        ScrollView scroll=new ScrollView(requireContext());scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setPadding(0,0,0,dp(18));
        feed=column();scroll.addView(feed);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return root;
    }
    @Override public void onResume() {
        super.onResume();repo.addScreenObserver(observer);render();
        if(repo.posts.isEmpty()&&!repo.busy&&!repo.offlineMode)refresh();
    }
    @Override public void onPause() {repo.removeScreenObserver(observer);super.onPause();}
    @Override public void onDestroyView() {rendered=null;feed=null;super.onDestroyView();}
    private void refresh() {
        if(repo.offlineMode){render();return;}
        repo.work("Chargement des actualités…",()->repo.posts=new NewsApiClient(repo.http,URI.create(repo.site())).listPosts());
    }
    private void render() {
        if(feed==null)return;
        refresh.setEnabled(!repo.busy&&!repo.offlineMode);progress.setVisibility(repo.busy?View.VISIBLE:View.GONE);
        String message=repo.offlineMode?"Les actualités sont indisponibles hors connexion.":repo.error!=null?repo.error:repo.busy?"Chargement des actualités…":"";
        state.setText(message);state.setTextColor(repo.error!=null&&!repo.offlineMode?0xffffaaaa:color(R.color.secondary_text));state.setVisibility(message.isBlank()?View.GONE:View.VISIBLE);
        offline.setVisibility(repo.offlineAvailable&&repo.session!=null&&!repo.offlineMode&&!repo.busy?View.VISIBLE:View.GONE);
        if(rendered==repo.posts&&wasOffline==repo.offlineMode)return;
        rendered=repo.posts;wasOffline=repo.offlineMode;feed.removeAllViews();
        if(repo.offlineMode)return;
        java.util.List<Post> posts=new ArrayList<>(repo.posts);posts.sort(Comparator.comparing((Post post)->published(post.publishedAt())).reversed());
        if(posts.isEmpty()) {
            TextView empty=text("Les prochaines nouvelles du serveur apparaîtront ici.",15,color(R.color.secondary_text));empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(48),dp(16),dp(48));feed.addView(empty);return;
        }
        for(int index=0;index<posts.size();index++) {
            if(index==1){TextView heading=text("Toutes les nouvelles",17,color(R.color.primary_text));heading.setTypeface(null,Typeface.BOLD);heading.setPadding(0,dp(8),0,dp(14));feed.addView(heading);}
            addArticle(posts.get(index),index==0);
        }
    }
    private void addArticle(Post post,boolean featured) {
        LinearLayout card=column();card.setBackground(rounded(color(R.color.background_status_bar),14));card.setClipToOutline(true);
        LinearLayout.LayoutParams cardLayout=new LinearLayout.LayoutParams(-1,-2);cardLayout.bottomMargin=dp(14);feed.addView(card,cardLayout);
        boolean horizontal=!featured||getResources().getConfiguration().screenWidthDp>=600;
        if(horizontal){card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);}
        FrameLayout cover=new FrameLayout(requireContext());cover.setBackground(rounded(0xff24452e,0));
        TextView fallback=text("SNAKE\nN3ST",featured?21:15,0xffdcefd5);fallback.setGravity(Gravity.CENTER);fallback.setTypeface(null,Typeface.BOLD);cover.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(requireContext());image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cover.addView(image,new FrameLayout.LayoutParams(-1,-1));
        int imageWidth=horizontal?dp(featured?250:106):-1;
        card.addView(cover,new LinearLayout.LayoutParams(imageWidth,dp(featured?190:130)));
        if(post.image()!=null&&!post.image().isBlank())RemoteIconLoader.loadCover(requireContext(),post.image(),repo.site(),drawable->{if(drawable!=null)image.setImageDrawable(drawable);});
        LinearLayout words=column();words.setPadding(dp(featured?18:14),dp(16),dp(16),dp(16));card.addView(words,new LinearLayout.LayoutParams(horizontal?0:-1,-2,horizontal?1:0));
        TextView kicker=text(featured?"À LA UNE":"ACTUALITÉ",10,color(R.color.minebutton_highlight));kicker.setTypeface(null,Typeface.BOLD);kicker.setLetterSpacing(.08f);words.addView(kicker);
        TextView title=text(plain(post.title()),featured?22:16,color(R.color.primary_text));title.setTypeface(null,Typeface.BOLD);title.setMaxLines(featured?3:2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,dp(7),0,dp(6));words.addView(title);
        String excerpt=plain(post.description());
        if(featured&&!excerpt.isBlank()){TextView description=text(excerpt,13,color(R.color.secondary_text));description.setMaxLines(3);description.setEllipsize(TextUtils.TruncateAt.END);description.setPadding(0,0,0,dp(12));words.addView(description);}
        TextView metadata=text(metadata(post),11,color(R.color.secondary_text));metadata.setMaxLines(2);metadata.setEllipsize(TextUtils.TruncateAt.END);words.addView(metadata);
        TextView link=text("Lire l’article  ↗",13,color(R.color.minebutton_highlight));link.setTypeface(null,Typeface.BOLD);link.setPadding(0,dp(12),0,0);words.addView(link);
        card.setFocusable(true);card.setClickable(true);card.setContentDescription("Lire l’article : "+plain(post.title())+", "+metadata(post));
        android.util.TypedValue ripple=new android.util.TypedValue();requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true);
        card.setForeground(requireContext().getDrawable(ripple.resourceId));card.setOnClickListener(v->open(post.url()));
    }
    private static Instant published(String value){try{return OffsetDateTime.parse(value).toInstant();}catch(Exception ignored){return Instant.MIN;}}
    private static String metadata(Post post){String date="";try{date=DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.FRENCH).format(OffsetDateTime.parse(post.publishedAt()));}catch(Exception ignored){}String author=post.author()==null?"":plain(post.author().name());return date.isBlank()?author:author.isBlank()?date:date+" · "+author;}
    private static String plain(String value){return value==null?"":Html.fromHtml(value,Html.FROM_HTML_MODE_LEGACY).toString().replace("**","").replace("__","").replaceAll("\\s+"," ").trim();}
    private void open(String value){try{Uri uri=Uri.parse(value);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)throw new IllegalArgumentException("Adresse d’article invalide.");startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception failure){notice("Navigateur indisponible",failure.getMessage());}}
    private void notice(String title,String message){if(isAdded())new AlertDialog.Builder(requireContext()).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(requireContext());layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private TextView text(String value,int size,int color){TextView text=new TextView(requireContext());text.setText(value);text.setTextSize(size);text.setTextColor(color);return text;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;}
    private int color(int id){return getResources().getColor(id);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
