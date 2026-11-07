package mc.snakenest.launcher.android.snaken3st;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import mc.snakenest.launcher.android.LauncherActivity;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.progresskeeper.ProgressKeeper;
import mc.snakenest.launcher.android.progresskeeper.ProgressListener;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.modrinth.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Instance and mod management inside Amethyst's normal fragment/back navigation. */
public final class ModpackManagerFragment extends Fragment {
    public static final String TAG = "Sn3ModpackManager";
    private SnakeRepository repo;
    private LauncherActivity activity;
    private String slug;
    private int page;
    private boolean autoInstall;
    private boolean wasBusy;
    private int scanGeneration;
    private boolean scanning;
    private String query = "";
    private String versionTitle;
    private ModpackManifest versionManifest;
    private List<ModrinthSearchHit> hits = List.of();
    private List<ModrinthVersion> versions;
    private List<LocalMod> mods = List.of();
    private LinearLayout content;
    private TabHost tabs;
    private final LinearLayout[] pages = new LinearLayout[3];
    private Path replacedFile;
    private TextView state, progressText, error;
    private ProgressBar progress;
    private Button install, play, offline;
    private final List<View> mutationControls = new ArrayList<>();
    private record LocalMod(Path file, InstalledModInfo info, boolean managed, String project, android.graphics.Bitmap icon) { }
    private final Runnable observer = () -> {
        if (!ready()) return;
        boolean finished = wasBusy && !repo.busy;
        wasBusy = repo.busy; updateState();
        if (finished) { scanMods(); renderPage(); }
    };
    private final ProgressListener progressListener = new ProgressListener() {
        @Override public void onProgressStarted() { onMain(() -> { progressText.setVisibility(View.VISIBLE); progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true); }); }
        @Override public void onProgressUpdated(int percent, int resource, Object... arguments) {
            onMain(() -> {
                progressText.setVisibility(View.VISIBLE); progress.setVisibility(View.VISIBLE); progress.setIndeterminate(percent < 0);
                if (percent >= 0) progress.setProgress(percent);
                try { progressText.setText(getString(resource, arguments)); }
                catch (RuntimeException ignored) { progressText.setText(repo.status); }
            });
        }
        @Override public void onProgressEnded() { onMain(() -> { progress.setVisibility(View.GONE); progressText.setText(repo.status); }); }
    };

    private final ProgressListener minecraftProgressListener = new ProgressListener() {
        @Override public void onProgressStarted() { progressListener.onProgressStarted(); }
        @Override public void onProgressUpdated(int percent, int resource, Object... arguments) {
            progressListener.onProgressUpdated(percent, resource, arguments);
        }
        @Override public void onProgressEnded() { onMain(() -> {
            if (repo.busy) { progressText.setVisibility(View.VISIBLE); progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true); progressText.setText(repo.status); }
            else { progress.setVisibility(View.GONE); progressText.setText(""); }
        }); }
    };

    public static void open(LauncherActivity activity, String slug, boolean startInstall) {
        SnakeRepository.requireSlug(slug);
        Bundle arguments = new Bundle(); arguments.putString("slug", slug); arguments.putBoolean("install", startInstall);
        Tools.swapFragment(activity, ModpackManagerFragment.class, TAG, arguments);
    }
    public ModpackManagerFragment() { super(R.layout.fragment_sn3_modpack_manager); }
    @Override public void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved); slug = requireArguments().getString("slug"); SnakeRepository.requireSlug(slug);
        repo = SnakeRepository.get(requireContext());
        page = saved == null ? 0 : saved.getInt("page");
        query = saved == null ? "" : saved.getString("query", "");
        autoInstall = saved == null && requireArguments().getBoolean("install", false);
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out); out.putInt("page", page); out.putString("query", query);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        activity = (LauncherActivity)requireActivity();
        state = view.findViewById(R.id.sn3_manager_state);
        progressText = view.findViewById(R.id.sn3_manager_progress_text); error = view.findViewById(R.id.sn3_manager_error);
        progress = view.findViewById(R.id.sn3_manager_progress); install = view.findViewById(R.id.sn3_manager_install); play = view.findViewById(R.id.sn3_manager_play);
        offline = view.findViewById(R.id.sn3_manager_offline);
        offline.setOnClickListener(v -> { try { repo.goOffline(); renderPage(); } catch(IllegalStateException failure) { error.setText(failure.getMessage()); error.setVisibility(View.VISIBLE); } });
        ModpackSummary summary = repo.packs.stream().filter(pack -> pack.slug().equals(slug)).findFirst().orElse(null);
        ((TextView)view.findViewById(R.id.sn3_manager_title)).setText(summary == null ? slug : summary.name());
        ((TextView)view.findViewById(R.id.sn3_manager_description)).setText(summary == null ? "Gérer l’installation et les mods de cette instance." : summary.description());
        ImageView icon = view.findViewById(R.id.sn3_manager_icon);
        if (summary != null) {
            java.util.function.Consumer<android.graphics.drawable.Drawable> apply = drawable -> { if(ready() && drawable!=null) icon.setImageDrawable(drawable); };
            if(repo.localOnly(slug)) SnakePackIcons.loadLocal(requireContext(),summary,apply); else SnakePackIcons.load(requireContext(),summary,repo.site(),apply);
        }
        install.setOnClickListener(v -> {
            if (repo.busy) repo.cancel();
            else if(repo.localOnly(slug)) return;
            else if (repo.isInstalled(slug)) confirmRepair();
            else operation("Installation…", () -> Sn3GameBridge.install(host(), repo, slug));
        });
        play.setOnClickListener(v -> operation("Préparation du jeu…", () -> Sn3GameBridge.launch(host(), repo, slug)));
        tabs = view.findViewById(R.id.sn3_manager_tabs); tabs.setup();
        String[] titles = {"Instance", "Mods", "Modrinth"};
        for (int index = 0; index < titles.length; index++) {
            final int position = index;
            TextView indicator = new TextView(requireContext()); indicator.setText(titles[index]);
            indicator.setTextSize(14); indicator.setGravity(android.view.Gravity.CENTER);
            indicator.setTextColor(getResources().getColor(R.color.primary_text));
            indicator.setBackgroundResource(R.drawable.sn3_tab_background);
            indicator.setLayoutParams(new TabWidget.LayoutParams(0, dp(48), 1));
            tabs.addTab(tabs.newTabSpec(String.valueOf(index)).setIndicator(indicator).setContent(tag -> {
                ScrollView scroll = new ScrollView(requireContext()); scroll.setFillViewport(true);
                LinearLayout panel = new LinearLayout(requireContext()); panel.setOrientation(LinearLayout.VERTICAL);
                panel.setPadding(dp(12), dp(8), dp(12), dp(16)); scroll.addView(panel);
                pages[position] = panel; return scroll;
            }));
        }
        tabs.setOnTabChangedListener(tag -> { page = Integer.parseInt(tag); content = pages[page]; renderPage(); });
        tabs.setCurrentTab(Math.max(0,Math.min(2,page))); content = pages[page];
        updateState(); renderPage(); scanMods();
    }
    @Override public void onResume() {
        super.onResume(); repo.addScreenObserver(observer); wasBusy = repo.busy;
        ProgressKeeper.addListener("sn3", progressListener);
        ProgressKeeper.addListener(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT, minecraftProgressListener);
        updateState();
        if (autoInstall && !repo.localOnly(slug)) { autoInstall = false; operation("Installation du modpack…", () -> Sn3GameBridge.install(host(), repo, slug)); }
    }
    @Override public void onPause() {
        repo.removeScreenObserver(observer); ProgressKeeper.removeListener("sn3", progressListener);
        ProgressKeeper.removeListener(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT, minecraftProgressListener);
        super.onPause();
    }
    @Override public void onDestroyView() { scanGeneration++; scanning = false; super.onDestroyView(); }
    private LauncherActivity host() { return activity; }
    private boolean ready() { return isAdded() && getView() != null; }
    private void onMain(Runnable action) { repo.main.post(() -> { if (ready()) action.run(); }); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void updateState() {
        if (!ready()) return;
        boolean installed = !repo.busy && repo.isInstalled(slug);
        boolean running = Sn3GameBridge.isRunning(requireContext());
        state.setText(repo.removedPacks.contains(slug)?"Retiré du site":repo.offlineMode?"Hors connexion · téléchargements indisponibles":repo.busy ? repo.status : running ? "Une partie est en cours" : installed ? "Installé sur cet appareil" : "Non installé ou installation à terminer");
        install.setText(repo.busy ? "Annuler" : installed ? "Réparer" : "Installer");
        install.setEnabled(!running && (repo.busy || !repo.localOnly(slug))); play.setVisibility(installed ? View.VISIBLE : View.GONE); play.setEnabled(!repo.busy && !running);
        offline.setVisibility(!repo.busy && repo.error != null && repo.session != null && repo.offlineAvailable && !repo.offlineMode ? View.VISIBLE : View.GONE);
        error.setText(repo.error); error.setVisibility(repo.error == null ? View.GONE : View.VISIBLE);
        if (!repo.busy) { progress.setVisibility(View.GONE); progressText.setVisibility(View.GONE); progressText.setText(""); }
        for (View control : mutationControls) control.setEnabled(!repo.busy && !running && !(repo.localOnly(slug) && "sn3-online".equals(control.getTag())));
    }
    private void operation(String label, SnakeRepository.Work work) {
        if (repo.busy) return;
        if (Sn3GameBridge.isRunning(requireContext())) { error.setText("Ferme Minecraft avant de modifier cette instance."); error.setVisibility(View.VISIBLE); return; }
        if (repo.session == null || repo.sessionRejected && !repo.offlineMode) { error.setText("Reviens au compte pour valider ta connexion avant de continuer."); error.setVisibility(View.VISIBLE); return; }
        repo.work(label, work);
    }
    private void showPage(int selected) { if (tabs.getCurrentTab() == selected) renderPage(); else tabs.setCurrentTab(selected); }
    private void renderPage() {
        if (!ready()) return;
        content.removeAllViews(); mutationControls.clear();
        if (page == 0) instancePage(); else if (page == 1) modsPage(); else if(repo.localOnly(slug)) text("La recherche et les mises à jour de mods sont indisponibles hors connexion ou pour un modpack retiré du site.",16,false); else searchPage();
        updateState();
    }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(requireContext()); view.setText(value == null ? "" : value); view.setTextSize(size);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(8), 0, dp(6)); content.addView(view); return view;
    }
    private Button button(LinearLayout parent, String label, Runnable action, boolean mutates) {
        Button view = new Button(requireContext()); view.setText(label); view.setAllCaps(false); view.setOnClickListener(v -> action.run());
        view.setTextSize(14); view.setMinHeight(dp(48));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        if (mutates) mutationControls.add(view);
        if (label.equals("Versions") || label.equals("Canal de distribution") || label.equals("Version du modpack")) view.setTag("sn3-online");
        return view;
    }
    private void separator() {
        View line = new View(requireContext()); line.setBackgroundColor(getResources().getColor(R.color.background_bottom_bar));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1)); params.setMargins(0, dp(8), 0, dp(8)); content.addView(line, params);
    }
    private void instancePage() {
        text("Préférences de l’instance", 18, true);
        var jvm = new SnakeJvmSettings(requireContext());
        text("Mémoire : " + (jvm.memoryOverride(slug) ? "réglage propre au modpack" : "réglage global Amethyst")
                + " · " + jvm.resolve(slug).memoryMb() + " Mo\nArguments JVM : "
                + (jvm.argumentsOverride(slug) ? "réglage propre au modpack" : "réglage global Amethyst"), 14, false);
        button(content, "Réglages Java de ce modpack", this::settingsDialog, true);
        button(content, "Canal de distribution", this::chooseChannel, true);
        button(content, "Version du modpack", this::choosePackVersion, true);
        button(content, "Rendu, runtime et contrôles Amethyst", this::editAmethystProfile, true);
        separator();
        text("Désinstallation", 19, true);
        text("Supprime uniquement cette instance locale et ses préférences. Les mondes et les ajouts personnels de cette instance seront perdus.", 14, false);
        button(content, "Désinstaller ce modpack", this::confirmUninstall, true);
    }
    private void confirmRepair() {
        new AlertDialog.Builder(requireContext()).setTitle("Réparer ce modpack ?")
            .setMessage("Restaurer les fichiers et les versions officiels du pack ? Les modifications de ses mods seront réinitialisées. Tes mondes, options et mods personnels seront conservés.")
            .setNegativeButton("Annuler", null).setPositiveButton("Réparer", (dialog, which) -> operation("Réparation…", () -> Sn3GameBridge.repair(host(), repo, slug))).show();
    }
    private void modsPage() {
        text(scanning ? "Lecture des mods…" : mods.size() + " mods installés", 18, true);
        if (!scanning && mods.isEmpty()) text("Aucun mod installé. Ajoute un mod depuis Modrinth.", 14, false);
        for (LocalMod mod : mods) {
            LinearLayout card = new LinearLayout(requireContext()); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setColor(getResources().getColor(R.color.background_bottom_bar)); background.setCornerRadius(dp(8)); card.setBackground(background);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1,-2); cardParams.bottomMargin=dp(8); content.addView(card,cardParams);
            LinearLayout heading = new LinearLayout(requireContext()); heading.setGravity(android.view.Gravity.CENTER_VERTICAL); card.addView(heading);
            ImageView icon = new ImageView(requireContext()); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (mod.icon() == null) icon.setImageResource(R.drawable.ic_folder); else icon.setImageBitmap(mod.icon());
            heading.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout details = new LinearLayout(requireContext()); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(10),0,dp(6),0);
            heading.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView name = new TextView(requireContext()); name.setText(mod.info().name()); name.setTextSize(15); name.setTextColor(getResources().getColor(R.color.primary_text)); name.setTypeface(name.getTypeface(),android.graphics.Typeface.BOLD); details.addView(name);
            TextView metadata = new TextView(requireContext()); metadata.setText((mod.info().version()==null?"Version inconnue":mod.info().version())+(mod.managed()?" · Pack":" · Personnel")); metadata.setTextSize(12); details.addView(metadata);
            String filename = mod.file().getFileName().toString(); boolean disabled = filename.endsWith(".disabled");
            Switch enabled = new Switch(requireContext()); enabled.setChecked(!disabled); enabled.setContentDescription((disabled?"Activer ":"Désactiver ")+mod.info().name());
            heading.addView(enabled); mutationControls.add(enabled);
            enabled.setOnClickListener(control -> { enabled.setChecked(!disabled); changeMod(mod,false); });
            LinearLayout actions = new LinearLayout(requireContext()); card.addView(actions);
            Button version = button(actions,"Versions",()-> {
                replacedFile=mod.file();
                if(mod.project()!=null) loadVersions(mod.project(),mod.info().name());
                else { query=mod.info().name(); versions=null; showPage(2); search(); }
            },true);
            version.setLayoutParams(new LinearLayout.LayoutParams(0,dp(44),1));
            Button remove = button(actions,"Supprimer",()->new AlertDialog.Builder(requireContext()).setTitle("Supprimer ce mod ?")
                .setMessage("Retirer « "+mod.info().name()+" » de cette instance ?"+(mod.managed()?" Réparer permettra de restaurer la version du pack.":""))
                .setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(dialog,which)->changeMod(mod,true)).show(),true);
            remove.setLayoutParams(new LinearLayout.LayoutParams(0,dp(44),1));
        }
    }
    private android.graphics.Bitmap readIcon(Path file, String path) {
        if (path == null) return null;
        try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(file.toFile())) {
            var entry = archive.getEntry(path); if (entry == null || entry.getSize() > 2*1024*1024) return null;
            byte[] bytes;
            try (var input = archive.getInputStream(entry); var output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while((count=input.read(buffer))!=-1) { if(output.size()+count>2*1024*1024)return null; output.write(buffer,0,count); }
                bytes=output.toByteArray();
            }
            var options = new android.graphics.BitmapFactory.Options(); options.inJustDecodeBounds=true;
            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(options.outWidth<=0 || options.outHeight<=0 || options.outWidth>4096 || options.outHeight>4096)return null;
            options.inSampleSize=1; while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>128)options.inSampleSize*=2;
            options.inJustDecodeBounds=false; return android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        } catch(Exception ignored) { return null; }
    }
    private void scanMods() {
        if (!ready() || scanning) return;
        scanning = true; int generation = ++scanGeneration;
        if (page == 1) renderPage();
        repo.executor.execute(() -> {
            List<LocalMod> loaded = new ArrayList<>();
            try {
                Path root = repo.instance(slug).resolve("mods");
                Set<String> managed = new LocalManifestStore().load(repo.instance(slug)).map(StoredManifest::pathToHash)
                        .filter(Objects::nonNull).map(Map::keySet).orElse(Set.of());
                Map<String, ?> preferences = repo.prefs.getAll();
                if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) try (var files = Files.list(root)) {
                    for (Path file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                        String name = file.getFileName().toString();
                        if (!name.endsWith(".jar") && !name.endsWith(".jar.disabled")) continue;
                        String base = name.endsWith(".disabled") ? name.substring(0, name.length()-9) : name;
                        String project = null;
                        for (var pref : preferences.entrySet()) if (pref.getKey().startsWith("mod."+slug+".") && base.equals(pref.getValue())) project = pref.getKey().substring(("mod."+slug+".").length());
                        InstalledModInfo info = ModMetadataReader.read(file);
                        loaded.add(new LocalMod(file, info, managed.contains("mods/"+base), project, readIcon(file,info.iconPath())));
                    }
                }
                onMain(() -> { if (generation == scanGeneration) { scanning = false; mods = List.copyOf(loaded); if (page == 1) renderPage(); } });
            } catch (Exception exception) {
                onMain(() -> { if (generation == scanGeneration) { scanning = false; if (page == 1) renderPage(); error.setText("Lecture des mods impossible : " + exception.getMessage()); error.setVisibility(View.VISIBLE); } });
            }
        });
    }
    private void changeMod(LocalMod mod, boolean remove) {
        operation(remove ? "Suppression du mod…" : "Modification du mod…", () -> repo.changeMod(slug, mod.file(), remove));
    }
    private void searchPage() {
        text("Ajouter ou mettre à jour un mod", 19, true);
        EditText input = new EditText(requireContext()); input.setSingleLine(true); input.setHint("Rechercher sur Modrinth"); input.setText(query); content.addView(input);
        button(content, "Rechercher", () -> { query = input.getText().toString().trim(); versions = null; replacedFile = null; search(); }, true);
        if (versions != null) {
            button(content, "Retour aux résultats", () -> { versions = null; renderPage(); }, false);
            text("Versions compatibles · " + versionTitle, 17, true);
            for (ModrinthVersion version : versions) {
                separator(); text(version.name(), 16, true); text(version.versionNumber() + " · " + version.versionType(), 14, false);
                text("Les dépendances obligatoires compatibles seront installées avec cette version.", 13, false);
                button(content, "Installer cette version", () -> operation("Installation de " + versionTitle + "…", () -> new SnakeMods(repo).install(slug, versionManifest, version, replacedFile)), true);
            }
        } else if (hits.isEmpty()) text("Lance une recherche pour afficher les mods et leurs versions compatibles avec ce pack.", 14, false);
        else for (ModrinthSearchHit hit : hits) {
            separator();
            LinearLayout heading = new LinearLayout(requireContext()); heading.setOrientation(LinearLayout.HORIZONTAL); heading.setGravity(android.view.Gravity.CENTER_VERTICAL); content.addView(heading);
            ImageView icon = new ImageView(requireContext()); icon.setImageResource(R.drawable.ic_folder); heading.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView title = new TextView(requireContext()); title.setText(hit.title()); title.setTextSize(17); title.setPadding(dp(10),0,0,0); heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            RemoteIconLoader.load(requireContext(),hit.iconUrl(),"https://modrinth.com",drawable -> { if (ready() && drawable != null) icon.setImageDrawable(drawable); });
            text(hit.description(), 14, false);
            button(content, "Voir les versions compatibles", () -> loadVersions(hit.projectId(),hit.title()), true);
        }
    }
    private void search() {
        if(repo.localOnly(slug)) return;
        operation("Recherche Modrinth…", () -> {
            List<ModrinthSearchHit> result = new ModrinthApiClient(repo.http).search(query, 20);
            onMain(() -> { hits = result; versions = null; showPage(2); });
        });
    }
    private void loadVersions(String project, String title) {
        if(repo.localOnly(slug)) return;
        operation("Chargement des versions compatibles…", () -> {
            ModpackManifest manifest = repo.manifest(slug);
            List<ModrinthVersion> compatible = new ModrinthApiClient(repo.http).versions(project).stream()
                    .filter(version -> version.isCompatible(manifest.mcVersion(),manifest.loader())).toList();
            if (compatible.isEmpty()) throw new IOException("Aucune version compatible avec ce modpack.");
            onMain(() -> { versionManifest = manifest; versionTitle = title; versions = compatible; showPage(2); });
        });
    }
    private void settingsDialog() {
        ModpackSettings settings = new ModpackSettingsStore(repo.dirs).load(slug,ModpackSettings.defaults());
        SnakeJvmSettings jvm = new SnakeJvmSettings(requireContext());
        LinearLayout form = new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(18),dp(8),dp(18),dp(12));
        TextView explanation = new TextView(requireContext());
        explanation.setText("Les réglages globaux se trouvent dans Paramètres → Java. Les exceptions ci-dessous concernent uniquement ce modpack.");
        explanation.setPadding(0,0,0,dp(16)); form.addView(explanation);
        TextView memoryTitle = new TextView(requireContext()); memoryTitle.setText("Mémoire du jeu"); memoryTitle.setTypeface(null,android.graphics.Typeface.BOLD); form.addView(memoryTitle);
        CheckBox globalMemory = new CheckBox(requireContext()); globalMemory.setText("Utiliser l’allocation globale ("+jvm.globalMemory()+" Mo)"); globalMemory.setChecked(!jvm.memoryOverride(slug)); form.addView(globalMemory);
        EditText memory = new EditText(requireContext()); memory.setHint("Allocation propre au modpack (Mo)"); memory.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); memory.setText(String.valueOf(settings.memoryMb())); form.addView(memory);
        TextView argumentsTitle = new TextView(requireContext()); argumentsTitle.setText("Arguments JVM"); argumentsTitle.setTypeface(null,android.graphics.Typeface.BOLD); argumentsTitle.setPadding(0,dp(18),0,0); form.addView(argumentsTitle);
        CheckBox globalArguments = new CheckBox(requireContext()); globalArguments.setText("Utiliser les arguments JVM globaux"); globalArguments.setChecked(!jvm.argumentsOverride(slug)); form.addView(globalArguments);
        TextView argumentsHelp = new TextView(requireContext()); argumentsHelp.setText("Les arguments propres remplacent les arguments globaux. Laisse le champ vide pour ne fournir aucun argument personnalisé. La mémoire se règle ci-dessus, sans -Xmx ni -Xms."); form.addView(argumentsHelp);
        EditText arguments = new EditText(requireContext()); arguments.setHint("Arguments propres à ce modpack"); arguments.setText(settings.extraJvmArgs()); arguments.setMinLines(2); form.addView(arguments);
        TextView validation = new TextView(requireContext()); validation.setTextColor(0xffffaaaa); form.addView(validation);
        Runnable visibility = () -> { memory.setVisibility(globalMemory.isChecked()?View.GONE:View.VISIBLE); arguments.setVisibility(globalArguments.isChecked()?View.GONE:View.VISIBLE); argumentsHelp.setVisibility(globalArguments.isChecked()?View.GONE:View.VISIBLE); };
        globalMemory.setOnCheckedChangeListener((v,checked)->visibility.run()); globalArguments.setOnCheckedChangeListener((v,checked)->visibility.run()); visibility.run();
        ScrollView scroll = new ScrollView(requireContext()); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Java · ce modpack").setView(scroll).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                if(repo.busy || Sn3GameBridge.isRunning(requireContext())) throw new IllegalStateException("Attends la fin de l’opération ou ferme le jeu avant de modifier ces réglages.");
                int megabytes = globalMemory.isChecked()?settings.memoryMb():Integer.parseInt(memory.getText().toString());
                String args = arguments.getText().toString();
                if(!globalMemory.isChecked() && (megabytes<256 || megabytes>Tools.getTotalDeviceMemory(requireContext()))) throw new IllegalArgumentException("Allocation hors des limites de cet appareil.");
                if(!globalArguments.isChecked() && (args.contains("sn3.token") || args.matches("(?s).*(?:^|\\s)-Xm[sx]\\S*.*"))) throw new IllegalArgumentException("Règle la mémoire avec son allocation ; le jeton est géré par le launcher.");
                jvm.save(slug,megabytes,args,!globalMemory.isChecked(),!globalArguments.isChecked());
                dialog.dismiss(); renderPage();
            } catch(Exception failure) { validation.setText(failure instanceof NumberFormatException?"Indique une allocation en Mo.":failure.getMessage()); }
        }));
        dialog.show();
    }
    private void chooseChannel() {
        operation("Chargement des canaux…", () -> {
            repo.requireOnline(slug); var choices = repo.auth().fetchChannels(); String[] labels = new String[choices.size()+1]; labels[0] = "Canal par défaut du site";
            for (int index=0; index<choices.size(); index++) labels[index+1] = choices.get(index).name() + (choices.get(index).restricted() ? " · Restreint" : "");
            onMain(() -> new AlertDialog.Builder(requireContext()).setTitle("Canal de distribution").setItems(labels,(d,index) -> {
                if (repo.busy) return;
                ModpackSettings current = new ModpackSettingsStore(repo.dirs).load(slug,ModpackSettings.defaults()); new ModpackSettingsStore(repo.dirs).save(slug,new ModpackSettings(current.memoryMb(),current.extraJvmArgs(),index==0?null:choices.get(index-1).name(),current.javaPath()));
                repo.prefs.edit().remove("version."+slug).apply(); renderPage();
            }).setNegativeButton("Annuler",null).show());
        });
    }
    private void choosePackVersion() {
        operation("Chargement des versions du pack…", () -> {
            repo.requireOnline(slug); var choices = repo.api().listVersions(slug,repo.requireSession()); String[] labels = new String[choices.size()+1]; labels[0] = "Suivre le canal";
            for (int index=0; index<choices.size(); index++) labels[index+1] = choices.get(index).version();
            onMain(() -> new AlertDialog.Builder(requireContext()).setTitle("Version du modpack").setItems(labels,(d,index) -> {
                if (repo.busy) return;
                if (index==0) repo.prefs.edit().remove("version."+slug).apply(); else repo.prefs.edit().putString("version."+slug,choices.get(index-1).version()).apply(); renderPage();
            }).setNegativeButton("Annuler",null).show());
        });
    }
    private void editAmethystProfile() {
        operation("Préparation du profil…", () -> {
            ModpackManifest manifest = repo.localOnly(slug) ? null : repo.manifest(slug); String id = UUID.nameUUIDFromBytes(("sn3:"+slug).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles.load();
            var profiles = mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles;
            if (!profiles.containsKey(id)) {
                if(manifest==null) throw new java.io.IOException("Profil local manquant. Reviens en ligne pour installer ce modpack.");
                var profile = new mc.snakenest.launcher.android.value.launcherprofiles.MinecraftProfile(); profile.name = "Snake N3st — "+slug;
                profile.gameDir = repo.instance(slug).toString(); profile.lastVersionId = manifest.mcVersion(); profile.javaArgs = ""; SnakePackIcons.applyToProfile(requireContext(),slug,profile); profiles.put(id,profile);
                mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles.write();
            }
            mc.snakenest.launcher.android.prefs.LauncherPreferences.DEFAULT_PREF.edit().putString(mc.snakenest.launcher.android.prefs.LauncherPreferences.PREF_KEY_CURRENT_PROFILE,id).commit();
            onMain(() -> Tools.swapFragment(host(),mc.snakenest.launcher.android.fragments.ProfileEditorFragment.class,mc.snakenest.launcher.android.fragments.ProfileEditorFragment.TAG,null));
        });
    }
    private void confirmUninstall() {
        new AlertDialog.Builder(requireContext()).setTitle("Désinstaller ce modpack ?")
            .setMessage("Tous les fichiers locaux de cette instance seront supprimés définitivement : mondes/sauvegardes, mods personnels, options, réglages et profil de jeu. Les autres modpacks, comptes, runtimes Java et ressources partagées seront conservés.")
            .setNegativeButton("Annuler",null).setPositiveButton("Désinstaller",(d,w) -> operation("Désinstallation…", () -> repo.uninstall(slug))).show();
    }
}
