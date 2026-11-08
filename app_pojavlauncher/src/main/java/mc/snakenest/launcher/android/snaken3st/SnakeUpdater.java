package mc.snakenest.launcher.android.snaken3st;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Lifecycle;
import com.google.gson.*;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.android.progresskeeper.ProgressKeeper;
import mc.snakenest.launcher.android.services.ProgressService;
import mc.snakenest.launcher.update.android.AndroidUpdateCatalog;
import mc.snakenest.launcher.update.android.AndroidUpdateCatalog.Candidate;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** GitHub release updates. Android always owns installation and the final confirmation. */
public final class SnakeUpdater {
    private static final String REPOSITORY = "/Snake-N3st/SnakeN3stLauncher-Android/releases/download/";
    private static final String LATEST = "https://api.github.com/repos/Snake-N3st/SnakeN3stLauncher-Android/releases/latest";
    private static final long CHECK_INTERVAL = 6 * 60 * 60 * 1000L;
    private static SnakeUpdater instance;
    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WeakReference<LauncherActivity> host = new WeakReference<>(null);
    private WeakReference<AlertDialog> progressDialog = new WeakReference<>(null);
    private Candidate available;
    private boolean checking, downloading, prompted, pendingPermission, readyToInstall;
    private volatile boolean cancelled;
    private volatile Thread worker;
    private volatile HttpURLConnection activeConnection;
    private int percent;
    private String progressText = "";

    private SnakeUpdater(Context context) {
        app=context.getApplicationContext(); prefs=app.getSharedPreferences("sn3-updates",Context.MODE_PRIVATE);
        SnakeRepository.get(app).addScreenObserver(() -> {
            if(!offline() && available!=null && !prompted && automatic() && !downloading && idle()) showAvailable();
        });
    }
    public static synchronized SnakeUpdater get(Context context) { if(instance==null)instance=new SnakeUpdater(context); return instance; }
    private boolean offline() { return SnakeRepository.get(app).offlineMode; }
    void onEnterOffline() {
        cancelled=true; prompted=true; pendingPermission=false; readyToInstall=false;
        HttpURLConnection connection=activeConnection; if(connection!=null)connection.disconnect();
        Thread running=worker; if(running!=null)running.interrupt(); dismissProgress();
    }
    public boolean automatic() { return prefs.getBoolean("automatic",true); }
    public void setAutomatic(boolean value) { prefs.edit().putBoolean("automatic",value).apply(); if(!value)prompted=true; }
    public void onResume(LauncherActivity activity) {
        if(host.get()!=activity)dismissProgress();
        host=new WeakReference<>(activity);
        // Fragment.onResume can precede the Activity's RESUMED lifecycle state.
        main.post(() -> resume(activity));
    }
    private void resume(LauncherActivity activity) {
        if(activity()!=activity || offline())return;
        if(pendingPermission && app.getPackageManager().canRequestPackageInstalls()) { pendingPermission=false; install(); return; }
        if(downloading) { showProgress(); return; }
        if(readyToInstall && idle()) { readyToInstall=false; install(); return; }
        if(available!=null && !prompted && automatic() && idle()) { showAvailable(); return; }
        long elapsed=System.currentTimeMillis()-prefs.getLong("last-check",0);
        if(automatic() && !checking && idle() && (elapsed>=CHECK_INTERVAL || elapsed<0)) check(activity,false);
    }
    public void check(LauncherActivity activity, boolean manual) {
        host=new WeakReference<>(activity);
        if(offline()) { if(manual)notice("Mode hors connexion","Reconnecte-toi pour rechercher une mise à jour du launcher."); return; }
        if(downloading) { showProgress(); return; }
        if(checking) { if(manual)Toast.makeText(activity,"Recherche déjà en cours…",Toast.LENGTH_SHORT).show(); return; }
        if(available!=null && manual) { prompted=false; showAvailable(); return; }
        checking=true; prefs.edit().putLong("last-check",System.currentTimeMillis()).apply();
        if(manual)Toast.makeText(activity,"Recherche des mises à jour…",Toast.LENGTH_SHORT).show();
        executor.execute(()-> {
            worker=Thread.currentThread();
            try {
                Optional<Candidate> result=findUpdate();
                main.post(()-> {
                    checking=false; if(offline())return; available=result.orElse(null); prompted=false;
                    if(available!=null) { if(manual || automatic() && idle())showAvailable(); }
                    else if(manual)notice("Mises à jour","Aucune mise à jour compatible publiée pour cette édition du launcher.");
                });
            } catch(Exception exception) {
                main.post(()-> { checking=false; if(manual && !offline())requestError("Impossible de vérifier les mises à jour.\n"+message(exception)); });
            } finally { worker=null; Thread.interrupted(); }
        });
    }
    private void requestError(String text) {
        LauncherActivity value=activity(); if(value==null)return;
        AlertDialog.Builder dialog=new AlertDialog.Builder(value).setTitle("Requête impossible").setMessage(text).setPositiveButton("Fermer",null);
        if(SnakeRepository.get(app).session!=null && !SnakeRepository.get(app).busy && !Sn3GameBridge.isRunning(app))dialog.setNegativeButton("Passer hors connexion",(d,w)-> {
            try { SnakeRepository.get(app).goOffline(); }
            catch(Exception exception) { notice("Mode hors connexion indisponible",message(exception)); }
        });
        dialog.show();
    }
    private LauncherActivity activity() {
        LauncherActivity value=host.get();
        return value==null || value.isFinishing() || value.isDestroyed()
                || !value.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED) ? null : value;
    }
    private boolean idle() { return !SnakeRepository.get(app).busy && !Sn3GameBridge.isRunning(app); }
    private void notice(String title,String text) { LauncherActivity value=activity(); if(value!=null)new AlertDialog.Builder(value).setTitle(title).setMessage(text).setPositiveButton("OK",null).show(); }
    private void showAvailable() {
        LauncherActivity value=activity(); Candidate update=available; if(value==null || update==null || !idle() || offline())return;
        prompted=true;
        String notes=update.releaseNotes()==null?"":update.releaseNotes(); if(notes.length()>1200)notes=notes.substring(0,1200)+"…";
        boolean ready=apk().isFile() && apk().length()==update.size();
        new AlertDialog.Builder(value).setTitle("Snake N3st "+update.versionName())
            .setMessage("Une mise à jour est disponible ("+String.format(Locale.getDefault(),"%.1f",update.size()/1048576.0)+" Mo).\nTes comptes, modpacks et mondes seront conservés."+(notes.isBlank()?"":"\n\n"+notes))
            .setNegativeButton("Plus tard",null).setPositiveButton(ready?"Vérifier et installer":"Télécharger",(dialog,which)->download(update)).show();
    }
    private File apk() { return new File(app.getCacheDir(),"sn3-updates/update.apk"); }
    private Optional<Candidate> findUpdate() throws Exception {
        String release=readText(URI.create(LATEST),1024*1024,true);
        if(release==null)return Optional.empty();
        JsonObject json=JsonParser.parseString(release).getAsJsonObject();
        if(json.has("draft") && json.get("draft").getAsBoolean() || json.has("prerelease") && json.get("prerelease").getAsBoolean())return Optional.empty();
        JsonArray assets=json.getAsJsonArray("assets"); if(assets==null)return Optional.empty();
        URI manifest=null;
        for(JsonElement element:assets) {
            JsonObject asset=element.getAsJsonObject();
            if(asset.has("name") && "android-update.json".equals(asset.get("name").getAsString())) {
                if(manifest!=null)throw new IOException("Manifeste de mise à jour ambigu");
                manifest=URI.create(asset.get("browser_download_url").getAsString());
                if(!trustedAsset(manifest))throw new IOException("Source de mise à jour refusée");
            }
        }
        if(manifest==null)return Optional.empty();
        return AndroidUpdateCatalog.parse(readText(manifest,512*1024,false),app.getPackageName(),BuildConfig.VERSION_CODE,Build.VERSION.SDK_INT);
    }
    private static boolean trustedAsset(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost())
                && uri.getPort()==-1 && uri.getRawUserInfo()==null && uri.getRawQuery()==null && uri.getRawFragment()==null
                && uri.getRawPath().startsWith(REPOSITORY) && !uri.getRawPath().contains("%") && !uri.getRawPath().contains("..");
    }
    private HttpURLConnection open(URI start) throws IOException {
        URI uri=start;
        for(int redirects=0;redirects<=5;redirects++) {
            if(offline() || Thread.currentThread().isInterrupted())throw new InterruptedIOException("Mode hors connexion");
            String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo()!=null || uri.getPort()!=-1
                    || host==null || !Set.of("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com").contains(host.toLowerCase(Locale.ROOT)))
                throw new IOException("Redirection de mise à jour refusée");
            HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection(); activeConnection=connection;
            if(offline()) { connection.disconnect(); throw new InterruptedIOException("Mode hors connexion"); }
            connection.setConnectTimeout(10000); connection.setReadTimeout(15000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","SnakeN3stLauncher-Android/"+BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept",host.equals("api.github.com")?"application/vnd.github+json":"application/octet-stream");
            int code;
            try { code=connection.getResponseCode(); }
            catch(IOException exception) { connection.disconnect(); throw exception; }
            if(code>=300 && code<400) {
                String location=connection.getHeaderField("Location"); connection.disconnect();
                if(location==null || redirects==5)throw new IOException("Trop de redirections");
                uri=uri.resolve(location); continue;
            }
            return connection;
        }
        throw new IOException("Téléchargement indisponible");
    }
    private String readText(URI uri,int limit,boolean allow404) throws IOException {
        HttpURLConnection connection=open(uri);
        try {
            if(allow404 && connection.getResponseCode()==404)return null;
            if(connection.getResponseCode()!=200)throw new IOException("GitHub a refusé la requête ("+connection.getResponseCode()+")");
            if(connection.getContentLengthLong()>limit)throw new IOException("Réponse de mise à jour trop volumineuse");
            try(InputStream input=connection.getInputStream(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] bytes=new byte[8192]; int count;
                while((count=input.read(bytes))!=-1) { if(output.size()+count>limit)throw new IOException("Réponse trop volumineuse"); output.write(bytes,0,count); }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }
    private void download(Candidate update) {
        if(offline()) { notice("Mode hors connexion","Reconnecte-toi avant de télécharger une mise à jour."); return; }
        if(downloading || !idle()) { notice("Mise à jour","Termine l’opération ou ferme Minecraft avant de mettre à jour le launcher."); return; }
        downloading=true; cancelled=false; percent=0; progressText="Préparation…"; showProgress();
        ProgressKeeper.submitProgress("sn3-update",0,R.string.sn3_update_start); ProgressService.startService(app);
        executor.execute(()-> {
            worker=Thread.currentThread(); File target=apk(); File temporary=new File(target.getParentFile(),"update.part");
            try {
                if(cancelled)throw new InterruptedException();
                Files.createDirectories(target.getParentFile().toPath());
                if(!target.isFile() || target.length()!=update.size() || !update.sha256().equalsIgnoreCase(hash(target))) {
                    HttpURLConnection connection=open(update.apkUrl());
                    try {
                        if(connection.getResponseCode()!=200)throw new IOException("Téléchargement refusé ("+connection.getResponseCode()+")");
                        long declared=connection.getContentLengthLong();
                        if(declared>=0 && declared!=update.size())throw new IOException("Taille de mise à jour inattendue");
                        MessageDigest digest=MessageDigest.getInstance("SHA-256"); long received=0, last=0;
                        try(InputStream input=connection.getInputStream(); OutputStream output=new FileOutputStream(temporary)) {
                            byte[] buffer=new byte[128*1024]; int count;
                            while((count=input.read(buffer))!=-1) {
                                if(cancelled || Thread.currentThread().isInterrupted())throw new InterruptedException();
                                received+=count; if(received>update.size())throw new IOException("Taille de mise à jour dépassée");
                                digest.update(buffer,0,count); output.write(buffer,0,count);
                                long now=System.nanoTime(); if(now-last>100_000_000L) { report(received,update.size()); last=now; }
                            }
                        }
                        if(received!=update.size() || !update.sha256().equalsIgnoreCase(hex(digest.digest())))throw new IOException("L’empreinte de la mise à jour ne correspond pas");
                        Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                    } finally { connection.disconnect(); }
                }
                verifyArchive(target,update);
                main.post(()-> { downloading=false; dismissProgress(); readyToInstall=true; if(activity()!=null) { readyToInstall=false; install(); } });
            } catch(Exception exception) {
                boolean stopped=cancelled || exception instanceof InterruptedException || Thread.currentThread().isInterrupted();
                main.post(()-> { downloading=false; dismissProgress(); if(!stopped && !offline())requestError(message(exception)); });
            } finally {
                temporary.delete(); worker=null; Thread.interrupted(); ProgressKeeper.submitProgress("sn3-update",-1,-1);
            }
        });
    }
    private void report(long received,long size) {
        int value=(int)Math.min(99,received*99.0/size);
        ProgressKeeper.submitProgress("sn3-update",value,R.string.sn3_update_download,received/1048576.0,size/1048576.0);
        main.post(()-> { percent=value; progressText=String.format(Locale.getDefault(),"%.1f Mo / %.1f Mo",received/1048576.0,size/1048576.0); updateProgress(); });
    }
    private static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(file)) { byte[] buffer=new byte[128*1024]; int count; while((count=input.read(buffer))!=-1) { if(Thread.currentThread().isInterrupted())throw new InterruptedException(); digest.update(buffer,0,count); } }
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes) { StringBuilder text=new StringBuilder(); for(byte value:bytes)text.append(String.format(Locale.ROOT,"%02x",value&255)); return text.toString(); }
    @SuppressWarnings("deprecation") private void verifyArchive(File file,Candidate update) throws Exception {
        PackageManager manager=app.getPackageManager(); int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo installed=manager.getPackageInfo(app.getPackageName(),flags), archive=manager.getPackageArchiveInfo(file.toString(),flags);
        if(archive==null || !app.getPackageName().equals(archive.packageName))throw new IOException("Cet APK ne correspond pas à cette édition du launcher");
        long code=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        if(code!=update.versionCode() || code<=BuildConfig.VERSION_CODE || archive.applicationInfo==null || archive.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)
            throw new IOException("Version de l’APK incompatible");
        if(!signers(installed).equals(signers(archive)) || signers(installed).isEmpty())throw new IOException("La signature de cet APK ne correspond pas à l’application installée");
    }
    @SuppressWarnings("deprecation") private static Set<String> signers(PackageInfo info) {
        Signature[] values=Build.VERSION.SDK_INT>=28 && info.signingInfo!=null?info.signingInfo.getApkContentsSigners():info.signatures;
        Set<String> result=new HashSet<>(); if(values!=null)for(Signature signature:values)result.add(signature.toCharsString()); return result;
    }
    private void install() {
        LauncherActivity value=activity(); Candidate update=available;
        if(value==null || update==null || offline())return;
        if(!idle()) { notice("Mise à jour prête","Ferme Minecraft et termine les opérations en cours, puis relance la mise à jour depuis ton compte."); return; }
        if(!app.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(value).setTitle("Autoriser les mises à jour")
                .setMessage("Android demande d’autoriser Snake N3st à installer ses mises à jour. Tu confirmeras ensuite l’installation dans l’écran Android.")
                .setNegativeButton("Plus tard",null).setPositiveButton("Ouvrir les paramètres",(dialog,which)-> {
                    pendingPermission=true;
                    try { value.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+app.getPackageName()))); }
                    catch(RuntimeException exception) { pendingPermission=false; notice("Paramètres indisponibles",message(exception)); }
                }).show(); return;
        }
        try {
            // Re-read the downloaded package at handoff; Android verifies its signature again.
            verifyArchive(apk(),update);
            Uri uri=FileProvider.getUriForFile(app,app.getPackageName()+".updates",apk());
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); intent.setClipData(ClipData.newRawUri("Snake N3st",uri));
            value.startActivity(intent);
        } catch(Exception exception) { notice("Installation impossible",message(exception)); }
    }
    private void showProgress() {
        LauncherActivity value=activity(); AlertDialog existing=progressDialog.get(); if(value==null || existing!=null && existing.isShowing())return;
        LinearLayout box=new LinearLayout(value); box.setOrientation(LinearLayout.VERTICAL); int padding=Math.round(20*value.getResources().getDisplayMetrics().density); box.setPadding(padding,padding,padding,padding);
        TextView text=new TextView(value); text.setTag("update-text"); box.addView(text);
        ProgressBar bar=new ProgressBar(value,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setTag("update-bar"); box.addView(bar);
        AlertDialog dialog=new AlertDialog.Builder(value).setTitle("Mise à jour du launcher").setView(box).setCancelable(false)
                .setNegativeButton("Annuler",(d,w)-> { cancelled=true; Thread running=worker; if(running!=null)running.interrupt(); })
                .setNeutralButton("En arrière-plan",(d,w)-> { }).create();
        progressDialog=new WeakReference<>(dialog); dialog.show(); updateProgress();
    }
    private void updateProgress() {
        AlertDialog dialog=progressDialog.get(); if(dialog==null || !dialog.isShowing() || dialog.getWindow()==null)return;
        android.view.View root=dialog.getWindow().getDecorView();
        TextView text=root.findViewWithTag("update-text"); ProgressBar bar=root.findViewWithTag("update-bar");
        if(text!=null)text.setText(progressText); if(bar!=null)bar.setProgress(percent);
    }
    private void dismissProgress() { AlertDialog dialog=progressDialog.get(); if(dialog!=null)dialog.dismiss(); progressDialog.clear(); }
    private static String message(Exception exception) { return exception.getMessage()==null?exception.getClass().getSimpleName():exception.getMessage(); }
}
