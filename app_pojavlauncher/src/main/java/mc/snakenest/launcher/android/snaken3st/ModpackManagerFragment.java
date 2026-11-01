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
    private TextView state, progressText, error;
    private ProgressBar progress;
    private Button install, play;
    private final List<View> mutationControls = new ArrayList<>();
    private record LocalMod(Path file, InstalledModInfo info, boolean managed, String project) { }
    private final Runnable observer = () -> {
        if (!ready()) return;
        boolean finished = wasBusy && !repo.busy;
        wasBusy = repo.busy; updateState();
        if (finished) { scanMods(); renderPage(); }
    };
    private final ProgressListener progressListener = new ProgressListener() {
        @Override public void onProgressStarted() { onMain(() -> { progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true); }); }
        @Override public void onProgressUpdated(int percent, int resource, Object... arguments) {
            onMain(() -> {
                progress.setVisibility(View.VISIBLE); progress.setIndeterminate(percent < 0);
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
            if (repo.busy) { progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true); progressText.setText(repo.status); }
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
        content = view.findViewById(R.id.sn3_manager_content); state = view.findViewById(R.id.sn3_manager_state);
        progressText = view.findViewById(R.id.sn3_manager_progress_text); error = view.findViewById(R.id.sn3_manager_error);
        progress = view.findViewById(R.id.sn3_manager_progress); install = view.findViewById(R.id.sn3_manager_install); play = view.findViewById(R.id.sn3_manager_play);
        ModpackSummary summary = repo.packs.stream().filter(pack -> pack.slug().equals(slug)).findFirst().orElse(null);
        ((TextView)view.findViewById(R.id.sn3_manager_title)).setText(summary == null ? slug : summary.name());
        ((TextView)view.findViewById(R.id.sn3_manager_description)).setText(summary == null ? "Gérer l’installation et les mods de cette instance." : summary.description());
        ImageView icon = view.findViewById(R.id.sn3_manager_icon);
        if (summary != null) RemoteIconLoader.load(requireContext(), summary.image(), repo.site(), drawable -> { if (ready() && drawable != null) icon.setImageDrawable(drawable); });
        install.setOnClickListener(v -> {
            if (repo.busy) repo.cancel();
            else operation("Installation / mise à jour…", () -> Sn3GameBridge.install(host(), repo, slug));
        });
        play.setOnClickListener(v -> operation("Préparation du jeu…", () -> Sn3GameBridge.launch(host(), repo, slug)));
        view.findViewById(R.id.sn3_manager_tab_instance).setOnClickListener(v -> showPage(0));
        view.findViewById(R.id.sn3_manager_tab_mods).setOnClickListener(v -> showPage(1));
        view.findViewById(R.id.sn3_manager_tab_search).setOnClickListener(v -> showPage(2));
        updateState(); renderPage(); scanMods();
    }
    @Override public void onResume() {
        super.onResume(); repo.addScreenObserver(observer); wasBusy = repo.busy;
        ProgressKeeper.addListener("sn3", progressListener);
        ProgressKeeper.addListener(com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT, minecraftProgressListener);
        updateState();
        if (autoInstall) { autoInstall = false; operation("Installation du modpack…", () -> Sn3GameBridge.install(host(), repo, slug)); }
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
        state.setText(repo.busy ? repo.status : running ? "Une partie est en cours" : installed ? "Installé sur cet appareil" : "Non installé ou installation à terminer");
        install.setText(repo.busy ? "Annuler" : installed ? "Mettre à jour" : "Installer");
        install.setEnabled(!running); play.setVisibility(installed ? View.VISIBLE : View.GONE); play.setEnabled(!repo.busy && !running);
        error.setText(repo.error); error.setVisibility(repo.error == null ? View.GONE : View.VISIBLE);
        if (!repo.busy) { progress.setVisibility(View.GONE); progressText.setText(""); }
        for (View control : mutationControls) control.setEnabled(!repo.busy && !running);
    }
    private void operation(String label, SnakeRepository.Work work) {
        if (repo.busy) return;
        if (Sn3GameBridge.isRunning(requireContext())) { error.setText("Ferme Minecraft avant de modifier cette instance."); error.setVisibility(View.VISIBLE); return; }
        if (repo.session == null || repo.sessionRejected) { error.setText("Reviens au compte pour valider ta connexion avant de continuer."); error.setVisibility(View.VISIBLE); return; }
        repo.work(label, work);
    }
    private void showPage(int selected) { page = selected; renderPage(); }
    private void renderPage() {
        if (!ready()) return;
        content.removeAllViews(); mutationControls.clear();
        if (page == 0) instancePage(); else if (page == 1) modsPage(); else searchPage();
        updateState();
    }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(requireContext()); view.setText(value == null ? "" : value); view.setTextSize(size);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(8), 0, dp(6)); content.addView(view); return view;
    }
    private Button button(LinearLayout parent, String label, Runnable action, boolean mutates) {
        Button view = new Button(requireContext()); view.setText(label); view.setAllCaps(false); view.setOnClickListener(v -> action.run());
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        if (mutates) mutationControls.add(view);
        return view;
    }
    private void separator() {
        View line = new View(requireContext()); line.setBackgroundColor(getResources().getColor(R.color.background_bottom_bar));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1)); params.setMargins(0, dp(8), 0, dp(8)); content.addView(line, params);
    }
    private void instancePage() {
        text("Installation et préférences", 19, true);
        text("L’installation prépare le modpack, Minecraft et son loader sans démarrer le jeu. Les ressources partagées et les runtimes sont réutilisés.", 14, false);
        button(content, "Réparer les fichiers du modpack", () -> new AlertDialog.Builder(requireContext()).setTitle("Réparer ce modpack ?")
            .setMessage("Les fichiers gérés seront retéléchargés. Les réglages personnels protégés par .filesmetas sont conservés.")
            .setNegativeButton("Annuler", null).setPositiveButton("Réparer", (d,w) -> operation("Réparation…", () -> repo.sync(slug, true))).show(), true);
        button(content, "Mémoire et arguments JVM", this::settingsDialog, true);
        button(content, "Canal de distribution", this::chooseChannel, true);
        button(content, "Version du modpack", this::choosePackVersion, true);
        button(content, "Rendu, runtime et contrôles Amethyst", this::editAmethystProfile, true);
        separator();
        text("Désinstallation", 19, true);
        text("Supprime uniquement cette instance locale et ses préférences. Les mondes et les ajouts personnels de cette instance seront perdus.", 14, false);
        button(content, "Désinstaller ce modpack", this::confirmUninstall, true);
    }
    private void modsPage() {
        text("Mods installés", 19, true);
        text("Les mods fournis par le pack restent gérés par celui-ci. Tu peux gérer séparément tes ajouts personnels.", 14, false);
        button(content, "Actualiser la liste", this::scanMods, false);
        if (scanning) text("Lecture des fichiers…", 14, false);
        else if (mods.isEmpty()) text("Aucun mod installé. Installe le pack ou ouvre Modrinth pour ajouter un mod compatible.", 14, false);
        for (LocalMod mod : mods) {
            separator(); text(mod.info().name(), 17, true);
            text((mod.info().version() == null ? "Version inconnue" : mod.info().version()) + (mod.info().author() == null ? "" : " · " + mod.info().author()), 14, false);
            String filename = mod.file().getFileName().toString();
            text(filename + " · " + (mod.managed() ? "Fourni par le modpack" : "Ajout personnel") + (filename.endsWith(".disabled") ? " · Désactivé" : ""), 13, false);
            if (!mod.managed()) {
                button(content, filename.endsWith(".disabled") ? "Activer" : "Désactiver", () -> changeMod(mod, false), true);
                button(content, "Versions / mettre à jour", () -> {
                    if (mod.project() != null) loadVersions(mod.project(), mod.info().name());
                    else { query = mod.info().name(); versions = null; showPage(2); search(); }
                }, true);
                button(content, "Supprimer cet ajout", () -> new AlertDialog.Builder(requireContext()).setTitle("Supprimer ce mod personnel ?")
                    .setMessage(filename + " sera supprimé de cette instance.").setNegativeButton("Annuler", null)
                    .setPositiveButton("Supprimer", (d,w) -> changeMod(mod, true)).show(), true);
            }
        }
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
                        loaded.add(new LocalMod(file, ModMetadataReader.read(file), managed.contains("mods/"+base), project));
                    }
                }
                onMain(() -> { if (generation == scanGeneration) { scanning = false; mods = List.copyOf(loaded); if (page == 1) renderPage(); } });
            } catch (Exception exception) {
                onMain(() -> { if (generation == scanGeneration) { scanning = false; if (page == 1) renderPage(); error.setText("Lecture des mods impossible : " + exception.getMessage()); error.setVisibility(View.VISIBLE); } });
            }
        });
    }
    private void changeMod(LocalMod mod, boolean remove) {
        operation(remove ? "Suppression du mod…" : "Modification du mod…", () -> {
            Path directory = repo.instance(slug).resolve("mods").toAbsolutePath().normalize();
            Path file = mod.file().toAbsolutePath().normalize();
            if (!directory.equals(file.getParent()) || Files.isSymbolicLink(directory)) throw new IOException("Chemin de mod non sûr");
            String name = file.getFileName().toString(); String base = name.endsWith(".disabled") ? name.substring(0,name.length()-9) : name;
            for (ManifestFile managed : repo.manifest(slug).files()) if (managed.path().equals("mods/"+base)) throw new IOException("Ce fichier est fourni par le modpack et ne peut pas être modifié ici.");
            if (remove) {
                Files.deleteIfExists(file);
                var edit = repo.prefs.edit();
                for (var pref : repo.prefs.getAll().entrySet()) if (pref.getKey().startsWith("mod."+slug+".") && base.equals(pref.getValue())) edit.remove(pref.getKey());
                edit.apply();
            } else Files.move(file, file.resolveSibling(name.endsWith(".disabled") ? base : name+".disabled"));
        });
    }
    private void searchPage() {
        text("Ajouter ou mettre à jour un mod", 19, true);
        EditText input = new EditText(requireContext()); input.setSingleLine(true); input.setHint("Rechercher sur Modrinth"); input.setText(query); content.addView(input);
        button(content, "Rechercher", () -> { query = input.getText().toString().trim(); versions = null; search(); }, true);
        if (versions != null) {
            button(content, "Retour aux résultats", () -> { versions = null; renderPage(); }, false);
            text("Versions compatibles · " + versionTitle, 17, true);
            for (ModrinthVersion version : versions) {
                separator(); text(version.name(), 16, true); text(version.versionNumber() + " · " + version.versionType(), 14, false);
                text("Les dépendances obligatoires compatibles seront installées avec cette version.", 13, false);
                button(content, "Installer cette version", () -> operation("Installation de " + versionTitle + "…", () -> new SnakeMods(repo).install(slug, versionManifest, version)), true);
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
        operation("Recherche Modrinth…", () -> {
            List<ModrinthSearchHit> result = new ModrinthApiClient(repo.http).search(query, 20);
            onMain(() -> { hits = result; versions = null; page = 2; renderPage(); });
        });
    }
    private void loadVersions(String project, String title) {
        operation("Chargement des versions compatibles…", () -> {
            ModpackManifest manifest = repo.manifest(slug);
            List<ModrinthVersion> compatible = new ModrinthApiClient(repo.http).versions(project).stream()
                    .filter(version -> version.isCompatible(manifest.mcVersion(),manifest.loader())).toList();
            if (compatible.isEmpty()) throw new IOException("Aucune version compatible avec ce modpack.");
            onMain(() -> { versionManifest = manifest; versionTitle = title; versions = compatible; page = 2; renderPage(); });
        });
    }
    private void settingsDialog() {
        ModpackSettings settings = repo.settings(slug);
        LinearLayout form = new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(16),0,dp(16),0);
        EditText memory = new EditText(requireContext()); memory.setHint("Mémoire (Mo)"); memory.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); memory.setText(String.valueOf(settings.memoryMb())); form.addView(memory);
        EditText arguments = new EditText(requireContext()); arguments.setHint("Arguments JVM"); arguments.setText(settings.extraJvmArgs()); form.addView(arguments);
        new AlertDialog.Builder(requireContext()).setTitle("Réglages du modpack").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w) -> {
            try {
                int megabytes = Integer.parseInt(memory.getText().toString()); String args = arguments.getText().toString();
                if (megabytes < 256 || megabytes > Tools.getTotalDeviceMemory(requireContext()) || args.contains("sn3.token")) throw new IllegalArgumentException("Mémoire ou arguments JVM invalides.");
                if (!repo.busy && !Sn3GameBridge.isRunning(requireContext())) new ModpackSettingsStore(repo.dirs).save(slug,new ModpackSettings(megabytes,args,settings.channel(),settings.javaPath()));
            } catch (Exception exception) { error.setText(exception.getMessage()); error.setVisibility(View.VISIBLE); }
        }).show();
    }
    private void chooseChannel() {
        operation("Chargement des canaux…", () -> {
            var choices = repo.auth().fetchChannels(); String[] labels = new String[choices.size()+1]; labels[0] = "Canal par défaut du site";
            for (int index=0; index<choices.size(); index++) labels[index+1] = choices.get(index).name() + (choices.get(index).restricted() ? " · Restreint" : "");
            onMain(() -> new AlertDialog.Builder(requireContext()).setTitle("Canal de distribution").setItems(labels,(d,index) -> {
                if (repo.busy) return;
                ModpackSettings current = repo.settings(slug); new ModpackSettingsStore(repo.dirs).save(slug,new ModpackSettings(current.memoryMb(),current.extraJvmArgs(),index==0?null:choices.get(index-1).name(),current.javaPath()));
                repo.prefs.edit().remove("version."+slug).apply(); renderPage();
            }).setNegativeButton("Annuler",null).show());
        });
    }
    private void choosePackVersion() {
        operation("Chargement des versions du pack…", () -> {
            var choices = repo.api().listVersions(slug,repo.requireSession()); String[] labels = new String[choices.size()+1]; labels[0] = "Suivre le canal";
            for (int index=0; index<choices.size(); index++) labels[index+1] = choices.get(index).version();
            onMain(() -> new AlertDialog.Builder(requireContext()).setTitle("Version du modpack").setItems(labels,(d,index) -> {
                if (repo.busy) return;
                if (index==0) repo.prefs.edit().remove("version."+slug).apply(); else repo.prefs.edit().putString("version."+slug,choices.get(index-1).version()).apply(); renderPage();
            }).setNegativeButton("Annuler",null).show());
        });
    }
    private void editAmethystProfile() {
        operation("Préparation du profil…", () -> {
            ModpackManifest manifest = repo.manifest(slug); String id = UUID.nameUUIDFromBytes(("sn3:"+slug).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles.load();
            var profiles = mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles;
            if (!profiles.containsKey(id)) {
                var profile = new mc.snakenest.launcher.android.value.launcherprofiles.MinecraftProfile(); profile.name = "Snake N3st — "+slug;
                profile.gameDir = repo.instance(slug).toString(); profile.lastVersionId = manifest.mcVersion(); profile.javaArgs = repo.settings(slug).extraJvmArgs(); profiles.put(id,profile);
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
