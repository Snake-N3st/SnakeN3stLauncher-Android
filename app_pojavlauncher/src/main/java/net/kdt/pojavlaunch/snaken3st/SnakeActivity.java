package net.kdt.pojavlaunch.snaken3st;

import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import net.kdt.pojavlaunch.*;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.modrinth.*;
import mc.snakenest.launcher.news.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Native Android front end for the same Snake N3st services as the desktop launcher. */
public final class SnakeActivity extends AppCompatActivity {
    private SnakeRepository repo;
    private LinearLayout content;
    private String section = "packs";
    private String openedLogin;
    private String shownError;
    private boolean restoreAttempted;
    private boolean displayedBusy;
    private TextView statusLabel;
    private final Runnable observer = this::render;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        repo = SnakeRepository.get(this);
        if (state != null) { section = state.getString("section", "packs"); openedLogin = state.getString("login"); }
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("section", section); out.putString("login", openedLogin); }
    @Override protected void onResume() {
        super.onResume(); repo.observer = observer; render();
        if (!restoreAttempted && repo.session == null && repo.store.exists() && !repo.busy) {
            restoreAttempted = true;
            if (repo.pin.isEnabled()) askPin(false); else repo.unlock(new char[0]);
        } else if (repo.session != null && repo.pinKey != null && !repo.busy && !Sn3GameBridge.isRunning(this)) repo.work("Protection des fichiers…", repo::sealAll);
    }
    @Override protected void onPause() { if (repo.observer == observer) repo.observer = null; super.onPause(); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private void render() {
        if (isFinishing() || isDestroyed()) return;
        if (repo.loginUrl != null && !repo.loginUrl.equals(openedLogin)) { openedLogin = repo.loginUrl; open(openedLogin); }
        if (repo.busy && displayedBusy && statusLabel != null) { statusLabel.setText(repo.status); return; }
        displayedBusy = repo.busy;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(18), dp(20), dp(24));
        content.setBackgroundColor(Color.rgb(18, 22, 29)); scroll.addView(content); setContentView(scroll);
        text("SNAKE N3ST", 28, Color.rgb(109, 221, 165));
        text("Minecraft Java · Android", 15, Color.LTGRAY);
        text(repo.player == null ? "Compte Snake N3st" : repo.player.username() + " · " + repo.player.role(), 17, Color.WHITE);
        LinearLayout nav = row();
        button(nav, "Modpacks", () -> { section = "packs"; render(); });
        button(nav, "Actualités", () -> { section = "news"; loadNews(); });
        button(nav, "Paramètres", () -> { section = "settings"; render(); });
        statusLabel = text(repo.status, 14, Color.LTGRAY);
        if (Sn3GameBridge.isRunning(this)) button(content,"Arrêter Minecraft", () -> new AlertDialog.Builder(this).setTitle("Arrêter Minecraft ?")
            .setMessage("La partie sera interrompue. Sauvegarde ton monde avant de continuer.")
            .setNegativeButton("Annuler",null).setPositiveButton("Arrêter",(d,w) -> {
                startService(new Intent(this,net.kdt.pojavlaunch.services.GameService.class).putExtra("kill",true));
                repo.main.postDelayed(() -> { if (!Sn3GameBridge.isRunning(this) && !repo.busy) repo.work("Protection des fichiers…",repo::sealAll); },1500);
            }).show());
        if (repo.busy) { ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true); content.addView(progress); button(content, "Annuler", repo::cancel); }
        if ("news".equals(section)) showNews();
        else if ("settings".equals(section)) showSettings();
        else showPacks();
        if (repo.error != null && !repo.error.equals(shownError)) { shownError = repo.error; notice("Opération impossible", repo.error); }
        if (repo.loginUrl != null && !repo.loginUrl.equals(openedLogin)) { openedLogin = repo.loginUrl; open(openedLogin); }
    }
    private TextView text(String value, int sp, int color) {
        TextView v = new TextView(this); v.setText(value == null ? "" : value); v.setTextSize(sp); v.setTextColor(color);
        v.setPadding(0, dp(8), 0, dp(6)); content.addView(v); return v;
    }
    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); content.addView(row); return row; }
    private void button(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run());
        parent.addView(b, new LinearLayout.LayoutParams(parent == content ? -1 : 0, -2, parent == content ? 0 : 1));
    }
    private void idle(Runnable action) { if (repo.busy) return; if (Sn3GameBridge.isRunning(this)) { notice("Jeu en cours", "Ferme Minecraft avant de modifier les instances ou le compte."); return; } action.run(); }
    private void showPacks() {
        if (repo.session == null) {
            text("Retrouve les modpacks de ton serveur et connecte-toi avec ton compte Snake N3st.", 17, Color.WHITE);
            if (repo.pin.isEnabled() && repo.pinKey == null) button(content, "Déverrouiller", () -> askPin(false));
            else button(content, "Se connecter sur le site", () -> idle(repo::login));
            if (repo.client().isBlank()) text("Le client_id doit être défini lors de la compilation.", 15, Color.LTGRAY);
            return;
        }
        button(content, "Actualiser", () -> repo.work("Chargement des modpacks…", repo::refresh));
        if (repo.packs.isEmpty()) text("Aucun modpack chargé. Utilise Actualiser.", 16, Color.LTGRAY);
        for (ModpackSummary pack : repo.packs) {
            text(pack.name(), 23, Color.WHITE); text(pack.description(), 15, Color.LTGRAY);
            text((pack.latestVersion() == null ? "Aucune version" : pack.latestVersion()) + (pack.restricted() ? " · Accès restreint" : ""), 14, Color.LTGRAY);
            LinearLayout actions = row();
            button(actions, "Jouer", () -> idle(() -> repo.work("Préparation de " + pack.name() + "…", () -> Sn3GameBridge.launch(this, repo, pack.slug()))));
            button(actions, "Gérer", () -> manage(pack));
        }
    }
    private void manage(ModpackSummary pack) {
        new AlertDialog.Builder(this).setTitle(pack.name()).setItems(new String[]{"Installer / mettre à jour", "Réparer", "Mémoire et arguments JVM", "Canal de distribution", "Version précise", "Mods installés", "Rechercher sur Modrinth"}, (d, index) -> idle(() -> {
            switch (index) {
                case 0: repo.work("Synchronisation…", () -> repo.sync(pack.slug(), false)); break;
                case 1: new AlertDialog.Builder(this).setTitle("Réparer le modpack ?").setMessage("Les fichiers gérés seront retéléchargés. Les réglages personnels protégés par .filesmetas sont conservés.")
                        .setNegativeButton("Annuler", null).setPositiveButton("Réparer", (x,y) -> repo.work("Réparation…", () -> repo.sync(pack.slug(), true))).show(); break;
                case 2: packSettings(pack.slug()); break;
                case 3: channels(pack.slug()); break;
                case 4: versions(pack.slug()); break;
                case 5: installedMods(pack.slug()); break;
                case 6: searchMods(pack.slug()); break;
            }
        })).show();
    }
    private void packSettings(String slug) {
        ModpackSettings s = repo.settings(slug);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),0,dp(20),0);
        EditText memory = field(form, "Mémoire (Mo)", String.valueOf(s.memoryMb()), android.text.InputType.TYPE_CLASS_NUMBER);
        EditText args = field(form, "Arguments JVM", s.extraJvmArgs(), android.text.InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Réglages du modpack").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w) -> {
            try {
                int mb = Integer.parseInt(memory.getText().toString());
                if (mb < 256 || mb > Tools.getTotalDeviceMemory(this)) throw new IllegalArgumentException("Mémoire hors limites pour cet appareil.");
                String jvm = args.getText().toString(); if (jvm.contains("sn3.token")) throw new IllegalArgumentException("Le jeton est géré automatiquement.");
                new ModpackSettingsStore(repo.dirs).save(slug, new ModpackSettings(mb, jvm, s.channel(), null));
            } catch (Exception e) { notice("Réglages invalides",e.getMessage()); }
        }).show();
    }
    private EditText field(LinearLayout parent, String hint, String value, int type) {
        EditText e = new EditText(this); e.setHint(hint); e.setInputType(type); e.setText(value); parent.addView(e); return e;
    }
    private void channels(String slug) { repo.work("Chargement des canaux…", () -> {
        var channels = repo.auth().fetchChannels(); String[] labels = new String[channels.size()+1]; labels[0] = "Canal par défaut du site";
        for (int i=0;i<channels.size();i++) labels[i+1] = channels.get(i).name() + (channels.get(i).restricted() ? " · Accès restreint" : "") + (channels.get(i).isDefault() ? " · Par défaut" : "");
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Canal de distribution").setItems(labels,(d,which) -> {
            ModpackSettings s = repo.settings(slug);
            new ModpackSettingsStore(repo.dirs).save(slug,new ModpackSettings(s.memoryMb(),s.extraJvmArgs(),which==0?null:channels.get(which-1).name(),null));
            repo.prefs.edit().remove("version."+slug).apply();
        }).show());
    }); }
    private void versions(String slug) { repo.work("Chargement des versions…", () -> {
        var versions = repo.api().listVersions(slug,repo.requireSession()); String[] labels = new String[versions.size()+1]; labels[0] = "Suivre le canal";
        for (int i=0;i<versions.size();i++) labels[i+1] = versions.get(i).version();
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Version du modpack").setItems(labels,(d,which) -> {
            if (which==0) repo.prefs.edit().remove("version."+slug).apply(); else repo.prefs.edit().putString("version."+slug,versions.get(which-1).version()).apply();
        }).show());
    }); }
    private void showNews() {
        button(content,"Actualiser les actualités", this::loadNews);
        for (Post post : repo.posts) {
            text(post.title(),22,Color.WHITE); text(post.description(),15,Color.LTGRAY);
            button(content,"Lire l’article",() -> repo.work("Chargement de l’article…",() -> {
                Post full = new NewsApiClient(repo.http, URI.create(repo.site())).getPost(post.id());
                runOnUiThread(() -> notice(full.title(), Html.fromHtml(full.content()==null?"":full.content(),Html.FROM_HTML_MODE_LEGACY).toString()));
            }));
        }
    }
    private void loadNews() { repo.work("Chargement des actualités…",() -> repo.posts = new NewsApiClient(repo.http, URI.create(repo.site())).listPosts()); }
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
        button(content,"Paramètres avancés Amethyst",() -> idle(() -> startActivity(new Intent(this,LauncherActivity.class))));
        button(content,"Éditeur des contrôles",() -> startActivity(new Intent(this,CustomControlsActivity.class)));
        button(content,"Signaler un bug",this::reportBug);
        button(content,"Mises à jour Android",() -> open("https://github.com/Snake-N3st/SnakeN3stLauncher-Android/releases"));
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
    private void installedMods(String slug) { repo.work("Lecture des mods…",() -> {
        Path mods = repo.instance(slug).resolve("mods"); Files.createDirectories(mods);
        List<Path> files; try(var stream = Files.list(mods)) { files=stream.filter(Files::isRegularFile).sorted().toList(); }
        String[] labels = files.stream().map(f -> f.getFileName().toString()).toArray(String[]::new);
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Mods installés").setItems(labels,(d,which) -> idle(() -> repo.work("Modification du mod…",() -> {
            Path file=files.get(which); String name=file.getFileName().toString();
            ModpackManifest m=repo.manifest(slug);
            for (ManifestFile managed : m.files()) if (managed.path().equals("mods/"+name.replaceFirst("\\.disabled$",""))) throw new IllegalStateException("Ce mod est géré par le modpack. Il sera conservé.");
            Path target=file.resolveSibling(name.endsWith(".disabled")?name.substring(0,name.length()-9):name+".disabled");
            Files.move(file,target);
        }))).setNegativeButton("Fermer",null).setMessage(files.isEmpty()?"Aucun mod installé":"Touche un mod personnel pour l’activer ou le désactiver.").show());
    }); }
    private void searchMods(String slug) {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        EditText query=field(form,"Nom du mod","",android.text.InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Modrinth").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Rechercher",(d,w) -> repo.work("Recherche Modrinth…",() -> {
            var hits=new ModrinthApiClient(repo.http).search(query.getText().toString(),20);
            String[] labels=hits.stream().map(ModrinthSearchHit::title).toArray(String[]::new);
            runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Résultats Modrinth").setItems(labels,(x,which) -> chooseModVersion(slug,hits.get(which).projectId())).show());
        })).show();
    }
    private void chooseModVersion(String slug,String project) { repo.work("Versions compatibles…",() -> {
        ModpackManifest m=repo.manifest(slug);
        var versions=new ModrinthApiClient(repo.http).versions(project).stream().filter(v->v.isCompatible(m.mcVersion(),m.loader())).toList();
        if (versions.isEmpty()) throw new IllegalStateException("Aucune version compatible avec ce modpack.");
        String[] labels=versions.stream().map(ModrinthVersion::versionNumber).toArray(String[]::new);
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Installer un mod").setItems(labels,(d,which) -> idle(() -> new AlertDialog.Builder(this).setTitle("Installer ce mod ?")
            .setMessage("Les dépendances obligatoires compatibles seront installées également.").setNegativeButton("Annuler",null)
            .setPositiveButton("Installer",(x,y)->repo.work("Installation du mod…",()->new SnakeMods(repo).install(slug,m,versions.get(which)))).show())).show());
    }); }
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
