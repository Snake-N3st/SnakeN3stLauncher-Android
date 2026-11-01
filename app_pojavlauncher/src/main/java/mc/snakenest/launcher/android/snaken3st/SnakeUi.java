package mc.snakenest.launcher.android.snaken3st;

import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.modrinth.*;
import mc.snakenest.launcher.news.*;
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
        ((TextView)menu.findViewById(R.id.install_jar_button)).setText("Actualiser les modpacks");
        menu.findViewById(R.id.install_jar_button).setOnClickListener(v -> {
            if(repo.session==null) account(); else repo.work("Chargement des modpacks…",repo::refresh);
        });
        menu.findViewById(R.id.install_jar_button).setOnLongClickListener(null);
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
        repo.observer=observer; render();
        SnakeUpdater.get(host).onResume(host);
        if(!restoreAttempted && repo.session==null && repo.store.exists() && !repo.busy) {
            restoreAttempted=true; if(repo.pin.isEnabled()) askPin(false); else repo.unlock(new char[0]);
        } else if(repo.session!=null && repo.pinKey!=null && !repo.busy && !Sn3GameBridge.isRunning(host)) repo.work("Protection des fichiers…",repo::sealAll);
    }
    public void onPause() { if(repo.observer==observer) repo.observer=null; }
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
        ((TextView)host.findViewById(R.id.sn3_account_button)).setText(repo.sessionRejected?"Revalider la session Snake N3st":repo.player==null?"Se connecter à Snake N3st":repo.player.username());
        ModpackSummary pack=selected();
        var account=(SnakeAccountView)host.findViewById(R.id.sn3_account_button);
        account.setAvatar(repo.player==null?null:repo.player.avatar(),repo.site());
        var picker=(com.kdt.mcgui.mcModpackSpinner)menu.findViewById(R.id.mc_version_spinner);
        picker.submitPacks(repo.packs,pack==null?null:pack.slug(),repo.site(),chosen->{
            repo.prefs.edit().putString("selected-pack",chosen.slug()).apply(); render();
        });
        ((TextView)menu.findViewById(R.id.play_button)).setText(repo.busy?"Annuler":Sn3GameBridge.isRunning(host)?"Arrêter Minecraft":repo.sessionRejected?"Revalider":repo.session==null?"Se connecter":pack==null?"Choisir un modpack":repo.isInstalled(pack.slug())?"Jouer":"Installer");
        if(repo.loginUrl!=null && !repo.loginUrl.equals(openedLogin)) { openedLogin=repo.loginUrl; open(openedLogin); }
        if(repo.error!=null && !repo.error.equals(shownError)) { shownError=repo.error; notice("Opération impossible",repo.error); }
    }
    private void account() {
        repo.observer=observer;
        if (repo.sessionRejected) {
            new AlertDialog.Builder(this).setTitle("Revalider cet appareil")
                .setMessage("La requête de session a été refusée. La clé est conservée ; vérifie aussi l’horloge de cet appareil.")
                .setNeutralButton("Annuler",null)
                .setNegativeButton("Réessayer",(d,w)->repo.work("Validation de la session…",repo::refresh))
                .setPositiveButton("Se reconnecter",(d,w)->idle(repo::login)).show();
            return;
        }
        if(repo.session==null) {
            if(repo.pin.isEnabled() && repo.pinKey==null) askPin(false); else idle(repo::login);
        } else panel("Compte Snake N3st",this::showSettings);
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
        if(repo.session==null || repo.sessionRejected) { account(); return; }
        ModpackSummary pack=selected();
        if(pack==null) { selectPack(); return; }
        if (repo.isInstalled(pack.slug())) repo.work("Préparation de " + pack.name() + "…", () -> Sn3GameBridge.launch(host, repo, pack.slug()));
        else ModpackManagerFragment.open(host, pack.slug(), true);
    }
    private void selectPack() {
        if(repo.session==null || repo.sessionRejected) { account(); return; }
        if(repo.packs.isEmpty()) { repo.work("Chargement des modpacks…",repo::refresh); return; }
        ((com.kdt.mcgui.mcModpackSpinner)menu.findViewById(R.id.mc_version_spinner)).showPicker();
    }
    private void panel(String title,Runnable populate) {
        ScrollView scroll=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(8),dp(20),dp(8)); scroll.addView(content); populate.run();
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Fermer",null).show();
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int sp, int color) {
        TextView v = new TextView(this); v.setText(value == null ? "" : value); v.setTextSize(sp);
        v.setPadding(0, dp(8), 0, dp(6)); content.addView(v); return v;
    }
    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); content.addView(row); return row; }
    private void button(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run());
        parent.addView(b, new LinearLayout.LayoutParams(parent == content ? -1 : 0, -2, parent == content ? 0 : 1));
    }
    private void idle(Runnable action) { if (repo.busy) return; if (Sn3GameBridge.isRunning(this)) { notice("Jeu en cours", "Ferme Minecraft avant de modifier les instances ou le compte."); return; } action.run(); }
    private void manage(ModpackSummary pack) {
        ModpackManagerFragment.open(host, pack.slug(), false);
    }
    private EditText field(LinearLayout parent, String hint, String value, int type) {
        EditText e = new EditText(this); e.setHint(hint); e.setInputType(type); e.setText(value); parent.addView(e); return e;
    }
    private void showNews() {
        button(content,"Actualiser les actualités", this::loadNews);
        for (Post post : repo.posts) {
            text(post.title(),22,Color.WHITE); text(post.description(),15,Color.LTGRAY);
            button(content,"Lire l’article",() -> open(post.url()));
        }
    }
    private void loadNews() { repo.work("Chargement des actualités…",() -> {
        repo.posts=new NewsApiClient(repo.http,URI.create(repo.site())).listPosts();
        runOnUiThread(()->panel("Actualités Snake N3st",this::showNews));
    }); }
    private void showSettings() {
        text("Compte et sécurité",22,Color.WHITE);
        text(repo.site(),14,Color.LTGRAY);
        if (repo.player != null) { text(repo.player.username()+"\n"+repo.player.email(),16,Color.WHITE);
            if (!repo.pin.isEnabled()) button(content,"Protéger avec un PIN",() -> idle(() -> askPin(true)));
            else { text("Protection par PIN activée",16,Color.LTGRAY);
                button(content,"Désactiver le PIN", () -> idle(() -> new AlertDialog.Builder(this).setTitle("Désactiver le PIN ?")
                    .setMessage("Les fichiers .cryptlist seront déchiffrés et la session restera protégée par Android Keystore.")
                    .setNegativeButton("Annuler",null).setPositiveButton("Désactiver",(d,w)->repo.disablePin()).show()));
            }
            button(content,"Se déconnecter",() -> idle(() -> new AlertDialog.Builder(this).setTitle("Se déconnecter ?").setMessage("La clé sera révoquée sur le site et la session locale supprimée.")
                    .setNegativeButton("Annuler",null).setPositiveButton("Déconnexion",(d,w) -> repo.logout()).show()));
        }
        text("Jeu et commandes tactiles",22,Color.WHITE);

        button(content,"Éditeur des contrôles",() -> startActivity(new Intent(this,CustomControlsActivity.class)));
        button(content,"Signaler un bug",this::reportBug);
        button(content,"Rechercher une mise à jour du launcher",() -> SnakeUpdater.get(host).check(host,true));
        Switch automaticUpdates = new Switch(this); automaticUpdates.setText("Vérifier automatiquement les mises à jour");
        automaticUpdates.setChecked(SnakeUpdater.get(host).automatic());
        automaticUpdates.setOnCheckedChangeListener((view,enabled) -> SnakeUpdater.get(host).setAutomatic(enabled)); content.addView(automaticUpdates);
        button(content,"Site Snake N3st",() -> open(repo.site()));
        button(content,"Code source et licences",() -> open("https://github.com/Snake-N3st/SnakeN3stLauncher-Android"));
        text("Snake N3st pour Android\nBasé sur Amethyst / PojavLauncher. Cœur Snake N3st sous GPL-3.0 ; voir les licences dans le dépôt.",14,Color.LTGRAY);
    }
    private void askPin(boolean create) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),0,dp(20),0);
        EditText pin = field(form,"PIN (4 à 12 chiffres)","", android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        EditText confirm = create ? field(form,"Confirmer le PIN","",android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) : null;
        new AlertDialog.Builder(this).setTitle(create?"Créer un PIN":"Déverrouiller le compte").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Valider",(d,w) -> {
            String value = pin.getText().toString();
            if (create && !value.equals(confirm.getText().toString())) { notice("PIN invalide","Les codes ne correspondent pas."); return; }
            if (create) repo.setPin(value.toCharArray()); else repo.unlock(value.toCharArray());
        }).show();
    }
    private void reportBug() {
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
    void notice(String title,String message) { if(!isFinishing()&&!isDestroyed()) new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show(); }
    private void open(String url) { try { Uri uri=Uri.parse(url); if(!"https".equals(uri.getScheme())) throw new IllegalArgumentException("Adresse HTTPS requise"); startActivity(new Intent(Intent.ACTION_VIEW,uri)); } catch(Exception e){ notice("Navigateur indisponible",e.getMessage()); } }
}
