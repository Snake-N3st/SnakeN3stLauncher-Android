package mc.snakenest.launcher.android.snaken3st;

import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.modrinth.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Snake services bound to Amethyst's existing home layout and toolbar. */
public final class SnakeUi extends ContextWrapper {
    private final LauncherActivity host;
    private final View menu;
    private final SnakeRepository repo;
    private LinearLayout content;
    private String openedLogin;
    private String shownError;
    private boolean restoreAttempted;
    private boolean resumed;
    private boolean loginScheduled;
    private AlertDialog accountDialog;
    private final Runnable observer = this::render;
    public SnakeUi(LauncherActivity host, View menu) {
        super(host); this.host=host; this.menu=menu; repo=SnakeRepository.get(host);
        host.bindSnakeActions(this::account, this::play);
        host.getLifecycle().addObserver((androidx.lifecycle.LifecycleEventObserver)(owner,event)->{
            if(event==androidx.lifecycle.Lifecycle.Event.ON_DESTROY && repo.observer==observer) repo.observer=null;
        });
        menu.findViewById(R.id.play_button).setOnClickListener(v -> play());
        ((com.kdt.mcgui.mcModpackSpinner)menu.findViewById(R.id.mc_version_spinner)).setOnEmptyAction(this::selectPack);
        menu.findViewById(R.id.edit_profile_button).setOnClickListener(v -> {
            ModpackSummary pack=selected(); if(pack==null) selectPack(); else manage(pack);
        });
        menu.findViewById(R.id.news_button).setOnClickListener(v -> loadNews());
        ((TextView)menu.findViewById(R.id.news_button)).setText("Actualités");
        menu.findViewById(R.id.install_jar_button).setOnClickListener(v -> {
            if(UnrestrictedMode.enabled(host)) PersonalVersions.runJar(host, false);
            else refreshPacks();
        });
        menu.findViewById(R.id.install_jar_button).setOnLongClickListener(v -> {
            if(!UnrestrictedMode.enabled(host)) return false;
            PersonalVersions.runJar(host, true); return true;
        });
        menu.findViewById(R.id.sn3_personal_versions).setOnClickListener(v -> PersonalVersions.show(host));
        menu.findViewById(R.id.sn3_refresh_modpacks).setOnClickListener(v -> refreshPacks());
        menu.findViewById(R.id.open_files_button).setOnClickListener(v -> {
            ModpackSummary pack=selected(); if(pack==null) selectPack(); else Tools.openPath(host,repo.instance(pack.slug()).toFile(),false);
        });
        host.findViewById(R.id.sn3_account_button).setOnClickListener(v -> {
            // A dialog changes window focus during the click. End its pressed animation first.
            v.setPressed(false); v.jumpDrawablesToCurrentState(); account();
        });
    }
    public void onResume() {
        View accountButton=host.findViewById(R.id.sn3_account_button);
        accountButton.setPressed(false); accountButton.jumpDrawablesToCurrentState();
        resumed=true; repo.observer=observer;
        if(!restoreAttempted && repo.session==null && repo.store.exists() && !repo.busy) {
            restoreAttempted=true;
            if(!repo.protectionEnabled()) repo.unlock(new char[0]);
        } else if(repo.session!=null && repo.pinKey!=null && !repo.busy && !Sn3GameBridge.isRunning(host)) repo.work("Protection des fichiers…",repo::sealAll);
        render(); SnakeUpdater.get(host).onResume(host);
    }
    public void onPause() { resumed=false; if(repo.observer==observer) repo.observer=null; }
    static boolean needsSignIn(boolean hasSession,boolean hasSavedSession,boolean restorationAttempted,boolean busy) {
        return !hasSession&&!busy&&(!hasSavedSession||restorationAttempted);
    }
    private void showLogin() {
        if(loginScheduled||host.getSupportFragmentManager().isStateSaved())return;
        loginScheduled=true;
        menu.post(()->{
            loginScheduled=false;
            if(resumed&&needsSignIn(repo.session!=null,repo.store.exists(),restoreAttempted,repo.busy)&&!Sn3GameBridge.isRunning(host))Sn3LoginFragment.open(host);
        });
    }
    private void runOnUiThread(Runnable r) { host.runOnUiThread(r); }
    private boolean isFinishing() { return host.isFinishing(); }
    private boolean isDestroyed() { return host.isDestroyed(); }
    private ModpackSummary selected() {
        String slug=repo.prefs.getString("selected-pack",null);
        for(ModpackSummary pack:repo.packs) if(pack.slug().equals(slug)) return pack;
        return repo.packs.isEmpty()?null:repo.packs.get(0);
    }
    private void render() {
        if(isFinishing()||isDestroyed()) return;
        if(resumed&&needsSignIn(repo.session!=null,repo.store.exists(),restoreAttempted,repo.busy)) { showLogin(); return; }
        ((TextView)host.findViewById(R.id.sn3_account_button)).setText(repo.offlineMode?offlineLabel():repo.sessionRejected?"Revalider la session Snake N3st":repo.player==null?"Se connecter à Snake N3st":repo.player.username());
        ModpackSummary pack=selected();
        var account=(SnakeAccountView)host.findViewById(R.id.sn3_account_button);
        account.setAvatar(repo.player==null?null:repo.player.avatar(),repo.site(),repo.session==null?0:repo.session.playerId(),repo.offlineMode);
        menu.findViewById(R.id.news_button).setEnabled(!repo.offlineMode);
        boolean unrestricted=UnrestrictedMode.enabled(host);
        ((TextView)menu.findViewById(R.id.install_jar_button)).setText(unrestricted?getString(R.string.sn3_run_jar):repo.offlineMode?"Revenir en ligne":"Actualiser les modpacks");
        menu.findViewById(R.id.sn3_personal_versions).setVisibility(unrestricted?View.VISIBLE:View.GONE);
        menu.findViewById(R.id.sn3_refresh_modpacks).setVisibility(unrestricted?View.VISIBLE:View.GONE);
        ((TextView)menu.findViewById(R.id.sn3_refresh_modpacks)).setText(repo.offlineMode?"Revenir en ligne":"Actualiser les modpacks");
        var picker=(com.kdt.mcgui.mcModpackSpinner)menu.findViewById(R.id.mc_version_spinner);
        picker.setLocalState(repo.offlineMode,repo.removedPacks);
        picker.submitPacks(repo.packs,pack==null?null:pack.slug(),repo.site(),chosen->{
            repo.prefs.edit().putString("selected-pack",chosen.slug()).apply(); render();
        });
        ((TextView)menu.findViewById(R.id.play_button)).setText(repo.busy?"Annuler":Sn3GameBridge.isRunning(host)?"Arrêter Minecraft":repo.sessionRejected && !repo.offlineMode?"Revalider":repo.session==null?"Se connecter":pack==null?"Choisir un modpack":repo.isInstalled(pack.slug())?"Jouer":"Installer");
        if(repo.loginUrl!=null && !repo.loginUrl.equals(openedLogin)) { openedLogin=repo.loginUrl; open(openedLogin); }
        if(repo.error!=null && !repo.error.equals(shownError)) { shownError=repo.error; showFailure(repo.error); }
    }
    private void account() {
        repo.observer=observer;
        if(repo.session==null) Sn3LoginFragment.open(host);
        else panel("Compte Snake N3st",this::showSettings);
    }
    private void play() {
        if(repo.busy) { repo.cancel(); return; }
        if(Sn3GameBridge.isRunning(host)) {
            new AlertDialog.Builder(this).setTitle("Arrêter Minecraft ?").setMessage("Sauvegarde ton monde avant d’arrêter la partie.")
                .setNegativeButton("Annuler",null).setPositiveButton("Arrêter",(d,w)->{
                    startService(new Intent(this,mc.snakenest.launcher.android.services.GameService.class).putExtra("kill",true));
                    repo.main.postDelayed(()->{ if(!Sn3GameBridge.isRunning(host)) repo.work("Protection des fichiers…",repo::sealAll); },1500);
                }).show(); return;
        }
        if(repo.session==null || repo.sessionRejected && !repo.offlineMode) { account(); return; }
        ModpackSummary pack=selected();
        if(pack==null) { selectPack(); return; }
        if (repo.isInstalled(pack.slug())) repo.work("Préparation de " + pack.name() + "…", () -> Sn3GameBridge.launch(host, repo, pack.slug()));
        else if(repo.localOnly(pack.slug())) notice("Installation indisponible","Ce modpack doit être installé en ligne avant de jouer hors connexion.");
        else ModpackManagerFragment.open(host, pack.slug(), true);
    }
    private void refreshPacks() {
        if(repo.session==null) account(); else repo.work("Chargement des modpacks…",repo::refresh);
    }
    private void selectPack() {
        if(repo.session==null || repo.sessionRejected && !repo.offlineMode) { account(); return; }
        if(repo.packs.isEmpty() && repo.offlineMode) { notice("Hors connexion","Aucun modpack installé disponible sur cet appareil."); return; }
        if(repo.packs.isEmpty()) { repo.work("Chargement des modpacks…",repo::refresh); return; }
        ((com.kdt.mcgui.mcModpackSpinner)menu.findViewById(R.id.mc_version_spinner)).showPicker();
    }
    private void panel(String title,Runnable populate) {
        if(accountDialog!=null) accountDialog.dismiss();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(22),dp(22),dp(12)); scroll.addView(content); populate.run();
        accountDialog=new AlertDialog.Builder(this).setView(scroll).setNegativeButton("Fermer",null).create();
        accountDialog.show();
        if(accountDialog.getWindow()!=null) {
            int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(480));
            accountDialog.getWindow().setLayout(width,-2);
            accountDialog.getWindow().setBackgroundDrawable(rounded(getResources().getColor(R.color.background_status_bar),20));
        }
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private void idle(Runnable action) { if (repo.busy) return; if (Sn3GameBridge.isRunning(this)) { notice("Jeu en cours", "Ferme Minecraft avant de modifier les instances ou le compte."); return; } action.run(); }
    private void manage(ModpackSummary pack) {
        ModpackManagerFragment.open(host, pack.slug(), false);
    }
    private EditText field(LinearLayout parent, String hint, String value, int type) {
        EditText e = new EditText(this); e.setHint(hint); e.setInputType(type); e.setText(value); parent.addView(e); return e;
    }
    private void loadNews() {
        if(repo.offlineMode) { notice("Hors connexion","Les actualités sont indisponibles hors connexion."); return; }
        Sn3NewsFragment.open(host);
    }
    private android.graphics.drawable.GradientDrawable rounded(int color,int radius) {
        var background=new android.graphics.drawable.GradientDrawable(); background.setColor(color); background.setCornerRadius(dp(radius)); return background;
    }
    private TextView label(LinearLayout parent,String value,int size,int color) {
        TextView label=new TextView(this); label.setText(value==null?"":value); label.setTextSize(size); label.setTextColor(color); parent.addView(label); return label;
    }
    private void section(String title) {
        TextView label=label(content,title.toUpperCase(Locale.FRENCH),11,getResources().getColor(R.color.secondary_text));
        label.setTypeface(null,android.graphics.Typeface.BOLD); label.setLetterSpacing(.08f); label.setPadding(0,dp(22),0,dp(8));
    }
    private void accountAction(String title,String subtitle,int icon,boolean danger,Runnable action) {
        LinearLayout line=new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL); line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(12),dp(10),dp(12),dp(10)); line.setMinimumHeight(dp(62));
        android.util.TypedValue ripple=new android.util.TypedValue(); getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true);
        line.setBackgroundResource(ripple.resourceId);
        ImageView image=new ImageView(this); image.setImageResource(icon); image.setColorFilter(danger?0xffffaaaa:getResources().getColor(R.color.minebutton_highlight));
        line.addView(image,new LinearLayout.LayoutParams(dp(22),dp(22)));
        LinearLayout words=new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wordsLayout=new LinearLayout.LayoutParams(0,-2,1); wordsLayout.setMarginStart(dp(14)); line.addView(words,wordsLayout);
        TextView heading=label(words,title,15,danger?0xffffaaaa:getResources().getColor(R.color.primary_text)); heading.setTypeface(null,android.graphics.Typeface.BOLD);
        if(subtitle!=null) {TextView sub=label(words,subtitle,12,getResources().getColor(R.color.secondary_text));sub.setPadding(0,dp(3),0,0);}
        TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextSize(23);arrow.setTextColor(getResources().getColor(R.color.secondary_text));line.addView(arrow);
        line.setFocusable(true);line.setClickable(true);line.setContentDescription(title+(subtitle==null?"":", "+subtitle));
        line.setOnClickListener(v->{if(accountDialog!=null)accountDialog.dismiss();action.run();});content.addView(line,new LinearLayout.LayoutParams(-1,-2));
    }
    private void showSettings() {
        int primary=getResources().getColor(R.color.primary_text),secondary=getResources().getColor(R.color.secondary_text);
        LinearLayout identity=new LinearLayout(this);identity.setOrientation(LinearLayout.HORIZONTAL);identity.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.sn3_account_avatar);avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatar.setContentDescription("Avatar du compte");identity.addView(avatar,new LinearLayout.LayoutParams(dp(64),dp(64)));
        if(repo.session!=null) {
            var cached=AvatarDiskCache.read(this,repo.site(),repo.session.playerId());if(cached!=null)avatar.setImageBitmap(cached);
            if(!repo.offlineMode && repo.player!=null)RemoteIconLoader.load(this,repo.player.avatar(),repo.site(),image->{if(image!=null)avatar.setImageDrawable(image);});
        }
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wordsLayout=new LinearLayout.LayoutParams(0,-2,1);wordsLayout.setMarginStart(dp(16));identity.addView(words,wordsLayout);
        String username=repo.player==null?"Compte Snake N3st":repo.player.username();
        if(repo.player==null)try{username=repo.cachedUsername();}catch(Exception ignored){}
        TextView name=label(words,username,23,primary);name.setTypeface(null,android.graphics.Typeface.BOLD);
        if(repo.player!=null && repo.player.email()!=null && !repo.player.email().isBlank())label(words,repo.player.email(),12,secondary);
        String state=repo.offlineMode?"Hors connexion":repo.sessionRejected?"Session à revalider":"Connecté à Snake N3st";
        TextView status=label(words,state,12,repo.sessionRejected?0xffffc18a:getResources().getColor(R.color.minebutton_highlight));status.setPadding(0,dp(6),0,0);
        content.addView(identity);label(content,repo.site().replace("https://",""),12,secondary).setPadding(0,dp(14),0,0);
        section("Session");
        if(repo.sessionRejected && !repo.offlineMode) {
            accountAction("Revalider la session","Réessayer avec la clé enregistrée",android.R.drawable.ic_popup_sync,false,()->idle(()->repo.work("Validation…",repo::refresh)));
            accountAction("Se reconnecter","Valider cet appareil dans le navigateur",android.R.drawable.ic_menu_myplaces,false,()->idle(repo::login));
        }
        accountAction(repo.offlineMode?"Revenir en ligne":"Passer hors connexion",repo.offlineMode?"Actualiser le compte et les modpacks":"Jouer avec les modpacks déjà installés",android.R.drawable.ic_menu_revert,false,()->idle(()->{
            if(repo.offlineMode)repo.work("Reconnexion…",repo::refresh);else enterOffline();
        }));
        section("Sécurité");
        boolean androidProtection=repo.deviceProtection.enabled();
        accountAction(androidProtection?"Protection Android activée":repo.pin.isEnabled()?"Remplacer l’ancien PIN":"Protéger avec Android",
            androidProtection?"Empreinte ou verrouillage du téléphone":repo.pin.isEnabled()?"Conserver les données et utiliser le déverrouillage du téléphone":"Protéger la session et les fichiers sensibles",android.R.drawable.ic_lock_lock,false,()->idle(()->{
            if(androidProtection)new AlertDialog.Builder(this).setTitle("Désactiver la protection Android ?")
                .setMessage("Les fichiers protégés seront déchiffrés. La session restera chiffrée dans Android Keystore, sans demande de déverrouillage du téléphone.")
                .setNegativeButton("Annuler",null).setPositiveButton("Désactiver",(d,w)->authenticateDevice(repo::disableProtection)).show();
            else authenticateDevice(repo::enableDeviceProtection);
        }));
        accountAction("Se déconnecter","Retirer la session de cet appareil",android.R.drawable.ic_lock_power_off,true,()->idle(()->new AlertDialog.Builder(this)
            .setTitle("Se déconnecter ?").setMessage(repo.offlineMode?"La session sera supprimée de cet appareil. La clé distante ne pourra pas être révoquée hors connexion.":"La clé de cet appareil sera révoquée sur le site et sa session locale supprimée.")
            .setNegativeButton("Annuler",null).setPositiveButton("Déconnexion",(d,w)->repo.logout()).show()));
        section("Launcher");
        if(GameCrashReports.latest(this)!=null)accountAction("Dernier rapport de jeu","Consulter, enregistrer ou partager le rapport",android.R.drawable.ic_menu_info_details,false,()->{
            var latest=GameCrashReports.latest(this);if(latest!=null)startActivity(new Intent(this,mc.snakenest.launcher.android.ExitActivity.class).putExtra("report",latest.getFileName().toString()));
        });
        accountAction("Mises à jour du launcher",repo.offlineMode?"Connexion nécessaire":"Rechercher une nouvelle version",android.R.drawable.ic_popup_sync,false,()->{
            if(repo.offlineMode)notice("Hors connexion","Les mises à jour sont indisponibles hors connexion.");else SnakeUpdater.get(host).check(host,true);
        });
        Switch automatic=new Switch(this);automatic.setText("Vérification automatique");automatic.setTextColor(secondary);automatic.setTextSize(13);automatic.setPadding(dp(12),0,dp(12),0);
        automatic.setChecked(SnakeUpdater.get(host).automatic());automatic.setOnCheckedChangeListener((v,enabled)->SnakeUpdater.get(host).setAutomatic(enabled));content.addView(automatic);
        accountAction("Signaler un bug","Envoyer un rapport à l’équipe",android.R.drawable.ic_menu_info_details,false,this::reportBug);
        accountAction("Site Snake N3st","Ouvrir dans le navigateur",android.R.drawable.ic_menu_view,false,()->open(repo.site()));
        accountAction("Code source et licences","Basé sur Amethyst / PojavLauncher",android.R.drawable.ic_menu_help,false,()->open("https://github.com/Snake-N3st/SnakeN3stLauncher-Android"));
    }
    private void authenticateDevice(Runnable action) {
        if(accountDialog!=null)accountDialog.dismiss();
        try {
            repo.deviceProtection.prepare();
            DeviceAuthFragment.authenticate(host,action,message->{if(!host.isFinishing()&&!host.isDestroyed())notice("Déverrouillage Android",message);});
        }catch(Exception failure){notice("Déverrouillage Android indisponible","Vérifie le verrouillage de ton téléphone dans les paramètres Android, puis réessaie. La protection existante est conservée.");}
    }
    private void reportBug() {
        if(repo.offlineMode) { notice("Hors connexion","L’envoi de rapports nécessite une connexion au site."); return; }
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),0,dp(20),0);
        EditText summary=field(form,"Résumé","",android.text.InputType.TYPE_CLASS_TEXT);
        EditText description=field(form,"Comment reproduire le problème ?","",android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(this).setTitle("Signaler un bug").setMessage("Le résumé, ta description et la version du système seront envoyés au site Snake N3st.")
            .setView(form).setNegativeButton("Annuler",null).setPositiveButton("Envoyer",(d,w) -> repo.work("Envoi du rapport…",() -> {
                if(summary.getText().toString().isBlank()) throw new IllegalArgumentException("Le résumé est obligatoire.");
                boolean ok=new mc.snakenest.launcher.bugreport.BugReportApiClient(repo.http,URI.create(repo.site()),repo.client())
                    .submit(mc.snakenest.launcher.bugreport.BugReportType.MANUAL,summary.getText().toString(),description.getText().toString(),null,null);
                if(!ok) throw new java.io.IOException("Le rapport n’a pas pu être envoyé.");
                runOnUiThread(() -> notice("Rapport envoyé","Merci, le rapport a été reçu par le site."));
            })).show();
    }
    private String offlineLabel() {
        try { return repo.cachedUsername()+" · Hors connexion"; } catch(Exception ignored) { return "Compte local · Hors connexion"; }
    }
    private void enterOffline() {
        try { repo.goOffline(); } catch(Exception failure) { notice("Hors connexion indisponible",failure.getMessage()); }
    }
    private void showFailure(String message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Opération impossible").setMessage(message).setPositiveButton("Fermer",null);
        if(repo.session!=null && repo.offlineAvailable && !repo.offlineMode) dialog.setNeutralButton("Passer hors connexion",(d,w)->enterOffline());
        dialog.show();
    }
    void notice(String title,String message) { if(!isFinishing()&&!isDestroyed()) new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show(); }
    private void open(String url) { try { Uri uri=Uri.parse(url); if(!"https".equals(uri.getScheme())) throw new IllegalArgumentException("Adresse HTTPS requise"); startActivity(new Intent(Intent.ACTION_VIEW,uri)); } catch(Exception e){ notice("Navigateur indisponible",e.getMessage()); } }
}
