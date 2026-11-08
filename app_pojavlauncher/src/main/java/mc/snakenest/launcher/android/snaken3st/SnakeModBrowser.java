package mc.snakenest.launcher.android.snaken3st;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.provider.OpenableColumns;
import mc.snakenest.launcher.android.R;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.modrinth.*;
import mc.snakenest.launcher.util.Sha1;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Search, project details and explicit version selection, matching the PC mod manager. */
final class SnakeModBrowser {
    private final ModpackManagerFragment host;
    private final SnakeRepository repo;
    private final String slug;
    private final ModrinthApiClient api;
    private final ActivityResultLauncher<String[]> picker;
    private String query="",filter="Toutes les versions";
    private boolean loaded,needsIdentity=true,showVersions;
    private int generation;
    private ModpackManifest manifest;
    private ModrinthSearchHit selected;
    private Path explicitReplacement;
    private List<ModrinthSearchHit> hits=List.of();
    private List<ModrinthVersion> releases=List.of();
    private Map<String,Installed> installed=Map.of();
    private final Map<String,List<ModrinthVersion>> releaseCache=new HashMap<>();
    private LinearLayout panel;
    private record Installed(Path file,ModrinthVersion version,boolean disabled) { }
    SnakeModBrowser(ModpackManagerFragment host,SnakeRepository repo,String slug) {
        this.host=host;this.repo=repo;this.slug=slug;api=new ModrinthApiClient(repo.http);
        picker=host.registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null)confirmImport(uri);});
    }
    void save(android.os.Bundle out){out.putString("modQuery",query);}
    void restore(android.os.Bundle saved){if(saved!=null)query=saved.getString("modQuery","");}
    boolean hasBack(){return selected!=null;}
    boolean back(){if(selected==null)return false;if(showVersions)showVersions=false;else{selected=null;explicitReplacement=null;}host.redrawMods();return true;}
    void dirty(){needsIdentity=true;}
    void render(LinearLayout content) {
        panel=content;
        if(selected==null)results();else if(showVersions)versions();else detail();
        if(!loaded&&!repo.busy&&!repo.localOnly(slug)){loaded=true;panel.post(()->{if(host.ready())search();});}
    }
    private void results() {
        title("Découvrir des mods",21);label(manifest==null?"Catalogue Modrinth":"Minecraft "+manifest.mcVersion()+" · "+manifest.loader()+" · compatibles en premier",13);
        LinearLayout row=new LinearLayout(host.requireContext());row.setGravity(Gravity.CENTER_VERTICAL);panel.addView(row);
        EditText input=new EditText(host.requireContext());input.setSingleLine(true);input.setHint("Rechercher sur Modrinth");input.setText(query);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);row.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        Runnable search=()->{query=input.getText().toString().trim();search();};
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search.run();return true;}return false;});
        action(row,"Rechercher",search,true).setLayoutParams(new LinearLayout.LayoutParams(dp(116),-2));
        actions("Mods populaires",()->{query="";search();},"Importer un .jar",this::pickImport);
        label(!loaded?"Chargement du catalogue…":hits.isEmpty()?"Aucun résultat. Essaie un autre nom ou importe un fichier .jar.":(query.isBlank()?"Mods populaires":"Résultats pour « "+query+" »")+" · "+hits.size()+" résultats",14);
        for(ModrinthSearchHit hit:hits) {
            boolean compatible=manifest!=null&&ModBrowserPolicy.compatible(hit,manifest.mcVersion(),manifest.loader());
            LinearLayout card=card();if(!compatible)card.setAlpha(.65f);heading(card,hit.title(),"par "+value(hit.author()),hit.iconUrl());
            text(card,hit.description(),13,false);Installed local=installed.get(hit.projectId());
            String state=compatible?"Compatible":"Incompatible avec cette version de Minecraft ou ce chargeur";
            if(local!=null)state+=" · "+(local.disabled()?"Désactivé":"Installé")+" : "+local.version().versionNumber();
            var cached=releaseCache.get(hit.projectId());
            boolean update=local!=null&&cached!=null&&best(cached).map(v->!v.id().equals(local.version().id())).orElse(false);
            if(update)state+=" · Mise à jour disponible";
            TextView badge=text(card,state,12,true);if(!compatible)badge.setTextColor(0xffc3a4a4);
            LinearLayout controls=new LinearLayout(host.requireContext());card.addView(controls);
            action(controls,"Détails",()->open(hit,null,false),false);
            if(compatible&&(local==null||local.disabled()||update))action(controls,update?"Mettre à jour":local!=null?"Réactiver":"Installation rapide",()->open(hit,null,true),true);
        }
    }
    private void detail() {
        action(panel,"Retour aux résultats",()->{selected=null;explicitReplacement=null;host.redrawMods();},false);
        heading(panel,selected.title(),"par "+value(selected.author()),selected.iconUrl());
        label(selected.description(),15);
        action(panel,"Voir sur Modrinth",()->{try{host.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://modrinth.com/mod/"+selected.projectId())));}catch(Exception ignored){Toast.makeText(host.requireContext(),"Aucun navigateur disponible",Toast.LENGTH_SHORT).show();}},false);
        label("Compatibilité : Minecraft "+manifest.mcVersion()+" · "+manifest.loader(),13);
        Installed local=installed.get(selected.projectId());
        if(local!=null)label((local.disabled()?"Désactivé":"Installé")+" · "+local.version().versionNumber()+"\n"+local.file().getFileName(),14);
        Optional<ModrinthVersion> latest=best(releases);
        if(latest.isPresent()) {
            ModrinthVersion v=latest.get();
            boolean same=local!=null&&local.version().id().equals(v.id())&&!local.disabled();
            label("Dernière version compatible : "+v.versionNumber()+" · "+releaseType(v),14);
            if(!same)action(panel,local==null?"Installation rapide":"Installer la dernière version compatible",()->prepare(v),true);
            else label("La dernière version compatible est déjà installée.",14);
        }else label("Aucune version compatible disponible. Les autres versions restent consultables.",14);
        action(panel,"Choisir une version",()->{showVersions=true;host.redrawMods();},false);
        if(local!=null)action(panel,"Retirer ce mod",()->remove(local),true);
    }
    private void versions() {
        action(panel,"Retour à la fiche du mod",()->{showVersions=false;host.redrawMods();},false);title("Versions · "+selected.title(),20);
        Spinner types=new Spinner(host.requireContext());String[] labels={"Toutes les versions","Versions stables","Bêta","Alpha"};types.setAdapter(new ArrayAdapter<>(host.requireContext(),android.R.layout.simple_spinner_dropdown_item,labels));
        int selectedIndex=Arrays.asList(labels).indexOf(filter);types.setSelection(Math.max(0,selectedIndex));panel.addView(types);
        types.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){if(!filter.equals(labels[i])){filter=labels[i];host.redrawMods();}}});
        Installed local=installed.get(selected.projectId());int count=0;
        for(ModrinthVersion version:ModBrowserPolicy.versions(releases,manifest.mcVersion(),manifest.loader())) {
            if(!filter.equals("Toutes les versions")&&!Objects.equals(version.versionType(),filter.equals("Versions stables")?"release":filter.equals("Bêta")?"beta":"alpha"))continue;
            count++;boolean compatible=ModBrowserPolicy.compatible(version,manifest.mcVersion(),manifest.loader());boolean same=local!=null&&local.version().id().equals(version.id());
            LinearLayout card=card();if(!compatible)card.setAlpha(.65f);text(card,version.name(),16,true);text(card,version.versionNumber()+" · "+releaseType(version),13,false);
            text(card,"Minecraft : "+String.join(", ",version.gameVersions()==null?List.of():version.gameVersions())+"\nChargeurs : "+String.join(", ",version.loaders()==null?List.of():version.loaders()),12,false);
            text(card,(compatible?"Compatible":"Incompatible")+(same?local.disabled()?" · Installée, désactivée":" · Version installée":""),13,true);
            if(same)action(card,"Retirer cette version",()->remove(local),true);
            else {Button button=action(card,compatible?"Installer cette version":"Installation incompatible",()->prepare(version),true);if(!compatible){host.disableMutation(button);button.setEnabled(false);}}
        }
        if(count==0)label("Aucune version pour ce filtre.",14);
    }
    private void search() {
        if(repo.localOnly(slug))return;
        int request=++generation;selected=null;explicitReplacement=null;
        host.operation("Recherche Modrinth…",()->{
            manifest=repo.manifest(slug);identify();
            List<ModrinthSearchHit> result=query.isBlank()?api.browsePopular(40):api.search(query,40);
            List<ModrinthSearchHit> sorted=ModBrowserPolicy.projects(result,manifest.mcVersion(),manifest.loader());
            // Only installed visible projects need an update check; avoid a request for every result.
            for(ModrinthSearchHit hit:sorted)if(installed.containsKey(hit.projectId())&&!releaseCache.containsKey(hit.projectId()))try{releaseCache.put(hit.projectId(),api.versions(hit.projectId()));}catch(IOException ignored){}
            host.onMain(()->{if(request==generation){hits=sorted;loaded=true;host.redrawMods();}});
        });
    }
    void openInstalled(Path path,String project,String name,String author) {
        explicitReplacement=path;loaded=true;
        host.operation("Identification du mod…",()->{
            manifest=repo.manifest(slug);needsIdentity=true;identify();String id=project;
            if(id==null)for(var e:installed.entrySet())if(e.getValue().file().equals(path)){id=e.getKey();break;}
            if(id==null){host.onMain(()->{query=name;selected=null;host.openModCatalogue();afterIdle(this::search);});return;}
            var p=api.getProject(id);
            // Project details endpoint has no compatibility facets: versions determine exact support.
            String resolved=id;List<ModrinthVersion> list=api.versions(id);releaseCache.put(id,list);
            ModrinthSearchHit hit=new ModrinthSearchHit(resolved,resolved,p.title(),p.description(),value(author),p.iconUrl(),List.of(),List.of(),null,null,0);
            host.onMain(()->{selected=hit;releases=list;showVersions=false;host.openModCatalogue();});
        });
    }
    private void open(ModrinthSearchHit hit,Path replacement,boolean quick) {
        host.operation("Chargement du mod…",()->{
            if(manifest==null)manifest=repo.manifest(slug);identify();List<ModrinthVersion> list=releaseCache.get(hit.projectId());if(list==null){list=api.versions(hit.projectId());releaseCache.put(hit.projectId(),list);}
            List<ModrinthVersion> result=list;
            host.onMain(()->{selected=hit;explicitReplacement=replacement;releases=result;showVersions=false;host.redrawMods();
                // Queue after repo.work releases its busy state.
                if(quick)repo.main.post(()->{if(host.ready())best(releases).ifPresent(this::prepare);});});
        });
    }
    private Optional<ModrinthVersion> best(List<ModrinthVersion> list){return ModBrowserPolicy.latest(list,manifest.mcVersion(),manifest.loader());}
    private void prepare(ModrinthVersion version) {
        if(!host.ready())return;
        if(repo.busy){repo.main.postDelayed(()->{if(host.ready())prepare(version);},80);return;}
        Path replacement=explicitReplacement;Installed local=installed.get(version.projectId());if(replacement==null&&local!=null)replacement=local.file();Path target=replacement;
        host.operation("Vérification des dépendances…",()->{
            SnakeMods engine=new SnakeMods(repo);SnakeMods.InstallPlan plan=engine.plan(slug,manifest,version);
            StringBuilder description=new StringBuilder(version.name()).append("\n").append(String.format(Locale.FRANCE,"Taille des fichiers : %.1f Mo",plan.totalBytes()/1048576.0));
            if(target!=null)description.append("\nRemplace : ").append(target.getFileName());
            if(!plan.dependencies().isEmpty()){description.append("\n\nDépendances obligatoires :");for(var dep:plan.dependencies())description.append("\n• ").append(dep.name()).append(" · ").append(dep.versionNumber());}
            else description.append("\nAucune dépendance supplémentaire à installer.");
            host.onMain(()->new AlertDialog.Builder(host.requireContext()).setTitle("Installer ce mod ?").setMessage(description.toString()).setNegativeButton("Annuler",null)
                .setPositiveButton("Installer",(d,w)->host.operation("Installation de "+version.name()+"…",()->{engine.install(slug,manifest,plan,target);needsIdentity=true;refreshIdentity();host.onMain(host::redrawMods);})).show());
        });
    }
    private void identify() throws Exception {
        if(!needsIdentity)return;Map<String,Path> paths=new LinkedHashMap<>();Path mods=repo.instance(slug).resolve("mods");
        if(Files.isDirectory(mods,LinkOption.NOFOLLOW_LINKS))try(var files=Files.list(mods)){for(Path path:files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&(p.toString().endsWith(".jar")||p.toString().endsWith(".jar.disabled"))).toList())try(InputStream input=Files.newInputStream(path)){paths.put(Sha1.hex(input),path);}}
        Map<String,Installed> found=new LinkedHashMap<>();
        if(!paths.isEmpty())for(var entry:api.lookupByHashes(new ArrayList<>(paths.keySet())).entrySet()){var v=entry.getValue();Path path=paths.get(entry.getKey());if(v!=null&&path!=null)found.merge(v.projectId(),new Installed(path,v,path.toString().endsWith(".disabled")),(old,next)->old.disabled()?next:old);}
        installed=Map.copyOf(found);needsIdentity=false;
    }
    private void afterIdle(Runnable action){if(!host.ready())return;if(repo.busy)repo.main.postDelayed(()->afterIdle(action),80);else action.run();}
    private void refreshIdentity() throws Exception {
        try{identify();}catch(Exception failure){if(Thread.currentThread().isInterrupted())throw failure;/* A metadata service failure must not turn a successful file install into a failure. */}
    }
    private void remove(Installed local) {
        new AlertDialog.Builder(host.requireContext()).setTitle("Retirer ce mod ?").setMessage("Supprimer « "+local.file().getFileName()+" » de cette instance ? Réparer permet de restaurer les mods du pack.")
            .setNegativeButton("Annuler",null).setPositiveButton("Retirer",(d,w)->host.operation("Suppression du mod…",()->{repo.changeMod(slug,local.file(),true);needsIdentity=true;refreshIdentity();host.onMain(host::redrawMods);})).show();
    }
    void pickImport(){if(!repo.busy&&!repo.localOnly(slug))picker.launch(new String[]{"application/java-archive","application/octet-stream","application/zip","*/*"});}
    private void confirmImport(Uri uri) {
        String filename=null;try(var cursor=host.requireContext().getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())filename=cursor.getString(0);}catch(Exception ignored){}
        if(filename==null||!filename.toLowerCase(Locale.ROOT).endsWith(".jar")){Toast.makeText(host.requireContext(),"Choisis un fichier de mod .jar",Toast.LENGTH_LONG).show();return;}
        String name=filename;new AlertDialog.Builder(host.requireContext()).setTitle("Importer ce mod ?").setMessage(name+"\nLes fichiers locaux ne sont pas vérifiés compatibles par Modrinth. Un fichier portant le même nom sera remplacé.")
            .setNegativeButton("Annuler",null).setPositiveButton("Importer",(d,w)->host.operation("Import du mod…",()->{ModpackManifest current=repo.manifest(slug);try(InputStream input=repo.context.getContentResolver().openInputStream(uri)){if(input==null)throw new IOException("Fichier inaccessible");new SnakeMods(repo).importJar(slug,current,input,name);}needsIdentity=true;refreshIdentity();host.onMain(host::refreshLocalMods);})).show();
    }
    private LinearLayout card(){LinearLayout view=new LinearLayout(host.requireContext());view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(12),dp(10),dp(12),dp(10));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(host.getResources().getColor(R.color.background_bottom_bar));bg.setCornerRadius(dp(10));view.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);panel.addView(view,p);return view;}
    private void heading(LinearLayout parent,String name,String author,String url){LinearLayout row=new LinearLayout(host.requireContext());row.setGravity(Gravity.CENTER_VERTICAL);parent.addView(row);ImageView icon=new ImageView(host.requireContext());icon.setImageResource(R.drawable.ic_folder);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout words=new LinearLayout(host.requireContext());words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(12),0,0,0);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));text(words,name,18,true);text(words,author,12,false);RemoteIconLoader.load(host.requireContext(),url,"https://modrinth.com",drawable->{if(host.ready()&&drawable!=null)icon.setImageDrawable(drawable);});}
    private TextView text(LinearLayout parent,String value,int size,boolean bold){TextView view=new TextView(host.requireContext());view.setText(value==null?"":value);view.setTextSize(size);view.setTextColor(host.getResources().getColor(bold?R.color.primary_text:R.color.secondary_text));if(bold)view.setTypeface(null,Typeface.BOLD);view.setPadding(0,dp(5),0,dp(7));parent.addView(view);return view;}
    private void label(String value,int size){text(panel,value,size,false);}private void title(String value,int size){text(panel,value,size,true);}
    private Button action(LinearLayout parent,String label,Runnable action,boolean mutation){Button b=host.button(parent,label,action,mutation);if(mutation&&!label.equals("Installation incompatible")&&(label.startsWith("Install")||label.equals("Mettre à jour")||label.equals("Réactiver")))b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(host.getResources().getColor(R.color.minebutton_color)));if(parent.getOrientation()==LinearLayout.HORIZONTAL)b.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));return b;}
    private void actions(String a,Runnable aa,String b,Runnable bb){LinearLayout row=new LinearLayout(host.requireContext());panel.addView(row);action(row,a,aa,true);action(row,b,bb,true);}
    private String releaseType(ModrinthVersion v){return "release".equals(v.versionType())?"Stable":"beta".equals(v.versionType())?"Bêta":"Alpha";}
    private String value(String value){return value==null||value.isBlank()?"Auteur non renseigné":value;}
    private int dp(int value){return Math.round(value*host.getResources().getDisplayMetrics().density);}
}
