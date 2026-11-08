package mc.snakenest.launcher.android.snaken3st;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import mc.snakenest.launcher.android.LauncherActivity;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.Tools;
import java.util.Arrays;

/** Snake sign-in and Android device authentication, with legacy PIN migration support. */
public final class Sn3LoginFragment extends Fragment {
    public static final String TAG="Sn3Login";
    private SnakeRepository repo;
    private TextView heading,description,status,error;
    private EditText pin;
    private Button connect,secondary,cancel;
    private ProgressBar progress;
    private String openedChallenge;
    private String browserError;
    private boolean leaving;
    private final int[] toolbarIds={R.id.account_spinner,R.id.sn3_account_button,R.id.setting_button};
    private final int[] toolbarVisibility=new int[3];
    private final Runnable observer=()->{if(isAdded()&&getView()!=null)render();};

    public static void open(LauncherActivity host) {
        var manager=host.getSupportFragmentManager();
        if(manager.isStateSaved() || manager.findFragmentById(R.id.container_fragment) instanceof Sn3LoginFragment)return;
        Tools.swapFragment(host,Sn3LoginFragment.class,TAG,null);
    }
    @Override public void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);repo=SnakeRepository.get(requireContext());
        if(saved!=null)openedChallenge=saved.getString("openedChallenge");
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);out.putString("openedChallenge",openedChallenge);
    }
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle saved) {
        ScrollView scroll=new ScrollView(requireContext());scroll.setFillViewport(true);scroll.setBackgroundColor(color(R.color.background_app));
        FrameLayout stage=new FrameLayout(requireContext());stage.setPadding(dp(24),dp(28),dp(24),dp(28));scroll.addView(stage,new ScrollView.LayoutParams(-1,-2));
        LinearLayout column=column();column.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams columnParams=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);stage.addView(column,columnParams);
        scroll.addOnLayoutChangeListener((view,l,t,r,b,oldL,oldT,oldR,oldB)->{
            int width=Math.min(Math.max(1,r-l-dp(48)),dp(520));
            if(column.getLayoutParams().width!=width){column.getLayoutParams().width=width;column.requestLayout();}
        });
        ImageView logo=new ImageView(requireContext());logo.setImageResource(R.drawable.sn3_logo);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription("Snake N3st");
        column.addView(logo,new LinearLayout.LayoutParams(dp(92),dp(92)));
        TextView brand=text("SNAKE N3ST LAUNCHER",11,color(R.color.minebutton_highlight));brand.setTypeface(null,Typeface.BOLD);brand.setLetterSpacing(.12f);brand.setGravity(Gravity.CENTER);brand.setPadding(0,dp(16),0,dp(10));column.addView(brand);
        TextView title=text("Tes modpacks,\nà portée de main.",30,color(R.color.primary_text));title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);column.addView(title);
        TextView intro=text("Retrouve les modpacks de la communauté sur ton téléphone.",15,color(R.color.secondary_text));intro.setGravity(Gravity.CENTER);intro.setPadding(dp(8),dp(12),dp(8),dp(24));column.addView(intro);
        LinearLayout card=column();card.setPadding(dp(22),dp(22),dp(22),dp(18));card.setBackground(rounded(color(R.color.background_status_bar),20));column.addView(card,new LinearLayout.LayoutParams(-1,-2));
        heading=text("Connexion Snake N3st",21,color(R.color.primary_text));heading.setTypeface(null,Typeface.BOLD);card.addView(heading);
        description=text("Valide cet appareil dans ton navigateur, puis reviens ici. Aucun mot de passe du site à saisir dans le launcher.",14,color(R.color.secondary_text));description.setPadding(0,dp(10),0,dp(16));card.addView(description);
        pin=new EditText(requireContext());pin.setHint("Ancien PIN du launcher");pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setSingleLine(true);pin.setMaxLines(1);pin.setSaveEnabled(false);card.addView(pin,new LinearLayout.LayoutParams(-1,-2));
        progress=new ProgressBar(requireContext(),null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);card.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        status=text("",13,color(R.color.secondary_text));status.setPadding(0,dp(10),0,dp(8));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(status);
        error=text("",13,0xffffaaaa);error.setPadding(0,dp(6),0,dp(8));error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);card.addView(error);
        connect=button("Se connecter avec Snake N3st",this::connect);connect.setTypeface(null,Typeface.BOLD);connect.setTextColor(0xff12200f);
        connect.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{color(R.color.minebutton_highlight),0xff71756e}));card.addView(connect,new LinearLayout.LayoutParams(-1,-2));
        secondary=button("Ouvrir le navigateur",()->{if(repo.busy)open(repo.loginUrl);else if(!Sn3GameBridge.isRunning(requireContext())){browserError=null;repo.login();}});card.addView(secondary,new LinearLayout.LayoutParams(-1,-2));
        cancel=button("Annuler la connexion",()->{repo.cancel();render();});card.addView(cancel,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout links=new LinearLayout(requireContext());links.setGravity(Gravity.CENTER);links.setPadding(0,dp(14),0,0);
        Button site=button("Site Snake N3st",()->open(repo.site()));site.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));links.addView(site,new LinearLayout.LayoutParams(0,-2,1));
        Button discord=button("Aide sur Discord",()->open("https://discord.gg/wCJZpreVDj"));discord.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));links.addView(discord,new LinearLayout.LayoutParams(0,-2,1));column.addView(links,new LinearLayout.LayoutParams(-1,-2));
        TextView footer=text("Minecraft Java Edition · Basé sur Amethyst",11,color(R.color.secondary_text));footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(10),0,0);column.addView(footer);
        return scroll;
    }
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved) {
        super.onViewCreated(view,saved);
        for(int index=0;index<toolbarIds.length;index++) {
            View toolbar=requireActivity().findViewById(toolbarIds[index]);toolbarVisibility[index]=toolbar.getVisibility();toolbar.setVisibility(View.GONE);
        }
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if(repo.busy){repo.cancel();render();}
                else if(repo.session!=null)goHome();
                else requireActivity().finish();
            }
        });
    }
    @Override public void onResume() {
        super.onResume();repo.addScreenObserver(observer);render();
    }
    @Override public void onPause() {repo.removeScreenObserver(observer);super.onPause();}
    @Override public void onDestroyView() {
        for(int index=0;index<toolbarIds.length;index++) {
            View toolbar=requireActivity().findViewById(toolbarIds[index]);if(toolbar!=null)toolbar.setVisibility(toolbarVisibility[index]);
        }
        pin=null;connect=null;super.onDestroyView();
    }
    private void render() {
        if(connect==null)return;
        if(repo.session!=null&&!repo.busy){goHome();return;}
        boolean saved=repo.store.exists(),locked=repo.protectionEnabled()&&repo.pinKey==null;
        boolean androidProtection=repo.deviceProtection.enabled();
        boolean waiting=repo.busy&&repo.loginUrl!=null;
        heading.setText(locked?"Déverrouiller le launcher":saved?"Retrouver ta session":"Connexion Snake N3st");
        description.setText(locked?androidProtection?"Déverrouille ta session avec l’authentification Android : empreinte ou code, schéma ou mot de passe du téléphone.":"Déverrouille avec l’ancien PIN du launcher. Tu pourras le remplacer par le déverrouillage Android depuis ton profil.":saved?"Retrouve la session enregistrée sur cet appareil.":"Valide cet appareil dans ton navigateur, puis reviens ici. Aucun mot de passe du site à saisir dans le launcher.");
        pin.setVisibility(locked&&!androidProtection?View.VISIBLE:View.GONE);pin.setEnabled(!repo.busy);
        progress.setVisibility(repo.busy?View.VISIBLE:View.GONE);
        String state=repo.busy?repo.cancellationRequested?"Annulation…":waiting?"Valide la connexion dans le navigateur. Le launcher attend ta confirmation.":repo.status:"Annulé".equals(repo.status)?"Connexion annulée. Tu peux réessayer.":"";
        status.setText(state);status.setVisibility(state.isBlank()?View.GONE:View.VISIBLE);
        String failure=browserError!=null?browserError:repo.error;error.setText(failure);error.setVisibility(failure==null?View.GONE:View.VISIBLE);
        connect.setText(repo.busy?waiting?"Validation en attente…":"Préparation…":locked?androidProtection?"Déverrouiller avec Android":"Déverrouiller avec l’ancien PIN":saved?"Restaurer ma session":repo.error==null?"Se connecter avec Snake N3st":"Réessayer la connexion");
        connect.setEnabled(!repo.busy);
        secondary.setText(waiting?"Ouvrir le navigateur":"Se connecter à un autre compte");
        secondary.setVisibility(waiting || !repo.busy&&saved&&!locked ? View.VISIBLE:View.GONE);secondary.setEnabled(!repo.busy || !repo.cancellationRequested);
        cancel.setVisibility(repo.busy?View.VISIBLE:View.GONE);cancel.setEnabled(!repo.cancellationRequested);
        if(waiting&&!repo.cancellationRequested&&!repo.loginUrl.equals(openedChallenge)) {
            openedChallenge=repo.loginUrl;open(openedChallenge);
        }
    }
    private void connect() {
        if(repo.busy||Sn3GameBridge.isRunning(requireContext()))return;
        browserError=null;
        if(repo.deviceProtection.enabled()&&repo.pinKey==null) {
            DeviceAuthFragment.authenticate(requireActivity(),repo::unlockDevice,message->{if(isAdded()){browserError=message;render();}});
        } else if(repo.pin.isEnabled()&&repo.pinKey==null) {
            char[] code=new char[pin.length()];pin.getText().getChars(0,pin.length(),code,0);pin.setText("");
            if(code.length<4||code.length>12){Arrays.fill(code,'\0');browserError="Le PIN doit contenir entre 4 et 12 chiffres.";render();return;}
            repo.unlock(code);
        } else if(repo.store.exists()) repo.unlock(new char[0]);
        else repo.login();
    }
    private void goHome() {
        if(leaving||getParentFragmentManager().isStateSaved())return;
        leaving=true;Tools.backToMainMenu((LauncherActivity)requireActivity());
    }
    private void open(String value) {
        if(value==null)return;
        try {
            Uri uri=Uri.parse(value);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();
            startActivity(new Intent(Intent.ACTION_VIEW,uri));
        } catch(Exception failure) {browserError="Le navigateur n’a pas pu être ouvert. Réessaie avec « Ouvrir le navigateur ».";render();}
    }
    private Button button(String label,Runnable action){Button button=new Button(requireContext());button.setText(label);button.setTextSize(14);button.setAllCaps(false);button.setMinHeight(dp(48));button.setOnClickListener(v->action.run());return button;}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(requireContext());layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private TextView text(String value,int size,int color){TextView text=new TextView(requireContext());text.setText(value);text.setTextSize(size);text.setTextColor(color);return text;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable background=new GradientDrawable();background.setColor(color);background.setCornerRadius(dp(radius));return background;}
    private int color(int id){return getResources().getColor(id);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
