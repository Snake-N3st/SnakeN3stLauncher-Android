package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import mc.snakenest.launcher.android.*;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.android.value.launcherprofiles.LauncherProfiles;
import mc.snakenest.launcher.bugreport.CrashReportText;
import mc.snakenest.launcher.util.AtomicFiles;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Private, bounded, sanitized crash snapshots. Original game files are never modified. */
public final class GameCrashReports {
    private GameCrashReports() { }
    public record Run(long started,String gameDirectory,String pack,String name,String version,String runtime,String renderer,int memory,boolean offline,boolean game) { }
    public record Report(int code,boolean signal,String timestamp,Run run,String details) { }
    private static Path runs(Context context){return context.getNoBackupFilesDir().toPath().resolve("snake-n3st/last-game-run.json");}
    private static Path reports(Context context){return context.getFilesDir().toPath().resolve("sn3-crash-reports");}
    public static void recordLaunch(Activity activity,File directory,String runtimeHome) {
        try {
            String pack=null;
            Path instances=activity.getNoBackupFilesDir().toPath().resolve("snake-n3st/instances").toAbsolutePath().normalize();
            Path actual=(directory==null?new File(Tools.DIR_GAME_NEW):directory).toPath().toAbsolutePath().normalize();
            if(instances.equals(actual.getParent())&&actual.getFileName().toString().matches("[a-z0-9]+(?:-[a-z0-9]+)*"))pack=actual.getFileName().toString();
            var profile=LauncherProfiles.getCurrentProfile();
            Run run=new Run(System.currentTimeMillis(),actual.toString(),pack,CrashReportText.sanitize(profile.name),profile.lastVersionId,
                    new File(runtimeHome).getName(),Tools.LOCAL_RENDERER,LauncherPreferences.PREF_RAM_ALLOCATION,
                    activity.getIntent().getBooleanExtra("sn3.offline",false),activity instanceof MainActivity);
            AtomicFiles.write(runs(activity),Tools.GLOBAL_GSON.toJson(run).getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){/* Reporting must never prevent a game launch. */}
    }
    private static Run readRun(Context context) {
        try{Path file=runs(context);if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)<32768)return Tools.GLOBAL_GSON.fromJson(Files.readString(file),Run.class);}catch(Exception ignored){}
        return new Run(System.currentTimeMillis(),null,null,"Minecraft",null,null,null,0,false,true);
    }
    public static Report collect(Context context,int code,boolean signal) {
        Run run=readRun(context);String timestamp=Instant.now().toString();StringBuilder body=new StringBuilder();
        body.append("RAPPORT DE PLANTAGE SNAKE N3ST\n\n").append("Date : ").append(timestamp)
            .append("\n").append(signal?"Signal : ":"Code de sortie : ").append(code)
            .append("\nModpack / profil : ").append(value(run.name())).append("\nIdentifiant : ").append(value(run.pack()))
            .append("\nMinecraft : ").append(value(run.version())).append("\nRuntime Java : ").append(value(run.runtime()))
            .append("\nRendu : ").append(value(run.renderer())).append("\nMémoire : ").append(run.memory()).append(" Mo")
            .append("\nMode : ").append(run.offline()?"Hors connexion":"En ligne")
            .append("\nLauncher : ").append(BuildConfig.VERSION_NAME)
            .append("\nAndroid : ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")")
            .append("\nAppareil : ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            .append("\nArchitecture : ").append(String.join(", ",Build.SUPPORTED_ABIS)).append("\n");
        Path game=validGameDirectory(context,run.gameDirectory());
        if(game!=null) {
            appendLatest(body,"Rapport Minecraft",game.resolve("crash-reports"),"crash-",".txt",run.started());
            appendLatest(body,"Erreur native Java",game,"hs_err_pid",".log",run.started());
            appendFile(body,"Journal Minecraft",game.resolve("logs/latest.log"),64*1024,true);
        }
        Path home=Tools.DIR_GAME_HOME==null?null:Path.of(Tools.DIR_GAME_HOME);
        if(home!=null)appendFile(body,"Journal du launcher et de Java",home.resolve("latestlog.txt"),64*1024,true);
        return new Report(code,signal,timestamp,run,CrashReportText.sanitize(body.toString()));
    }
    private static Path validGameDirectory(Context context,String directory) {
        if(directory==null)return null;
        try {
            Path path=Path.of(directory).toAbsolutePath().normalize();
            if(Files.isSymbolicLink(path))return null;
            Path privateRoot=context.getNoBackupFilesDir().toPath().resolve("snake-n3st/instances").toAbsolutePath().normalize();
            Path external=context.getExternalFilesDir(null).toPath().toAbsolutePath().normalize();
            return path.startsWith(privateRoot)||path.startsWith(external)||Tools.DIR_GAME_HOME!=null&&path.startsWith(Path.of(Tools.DIR_GAME_HOME).toAbsolutePath().normalize())?path:null;
        }catch(Exception ignored){return null;}
    }
    private static void appendLatest(StringBuilder body,String title,Path directory,String prefix,String suffix,long started) {
        Path latest=null;
        try(var files=Files.list(directory)) {
            latest=files.filter(p->p.getFileName().toString().startsWith(prefix)&&p.getFileName().toString().endsWith(suffix))
                .filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&modified(p)>=started-5000)
                .max(Comparator.comparingLong(GameCrashReports::modified)).orElse(null);
        }catch(Exception ignored){}
        if(latest==null)body.append("\n=== ").append(title).append(" ===\nAucun fichier pour ce lancement.\n");
        else appendFile(body,title,latest,96*1024,false);
    }
    private static long modified(Path file){try{return Files.getLastModifiedTime(file).toMillis();}catch(Exception ignored){return 0;}}
    private static void appendFile(StringBuilder body,String title,Path file,int limit,boolean tailOnly) {
        body.append("\n=== ").append(title).append(" ===\n");
        try {
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)){body.append("Fichier indisponible.\n");return;}
            try(RandomAccessFile input=new RandomAccessFile(file.toFile(),"r")) {
                long length=input.length();
                if(length<=limit){byte[] bytes=new byte[(int)length];input.readFully(bytes);body.append(new String(bytes,StandardCharsets.UTF_8));}
                else {
                    if(!tailOnly){byte[] first=new byte[limit/2];input.readFully(first);String head=new String(first,StandardCharsets.UTF_8);int newline=head.lastIndexOf('\n');if(newline>=0)body.append(head,0,newline+1);}
                    body.append("\n[Extrait : lignes intermédiaires omises]\n");
                    byte[] last=new byte[tailOnly?limit:limit/2];input.seek(length-last.length);input.readFully(last);String tail=new String(last,StandardCharsets.UTF_8);int newline=tail.indexOf('\n');if(newline>=0)body.append(tail.substring(newline+1));
                }
            }
            body.append('\n');
        }catch(Exception ignored){body.append("Lecture impossible.\n");}
    }
    public static Path save(Context context,Report report)throws IOException {
        String name="game-crash-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".json";
        Report safe=new Report(report.code(),report.signal(),report.timestamp(),report.run(),CrashReportText.sanitize(report.details()));
        Path file=reports(context).resolve(name);AtomicFiles.write(file,Tools.GLOBAL_GSON.toJson(safe).getBytes(StandardCharsets.UTF_8));
        try(var files=Files.list(reports(context))) {
            for(Path old:files.filter(p->p.getFileName().toString().startsWith("game-crash-")&&p.toString().endsWith(".json")).sorted(Comparator.comparingLong(GameCrashReports::modified).reversed()).skip(10).toList())Files.deleteIfExists(old);
        }catch(Exception ignored){}
        return file;
    }
    public static Report read(Context context,String name)throws IOException {
        if(name==null||!name.matches("game-crash-[A-Za-z0-9-]+\\.json"))throw new IOException("Rapport invalide");
        Path file=reports(context).resolve(name);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>2*1024*1024)throw new IOException("Rapport indisponible");
        Report report=Tools.GLOBAL_GSON.fromJson(Files.readString(file),Report.class);
        if(report==null||report.run()==null||report.details()==null)throw new IOException("Rapport incomplet");
        return new Report(report.code(),report.signal(),report.timestamp(),report.run(),CrashReportText.sanitize(report.details()));
    }
    public static Path latest(Context context) {
        try(var files=Files.list(reports(context))){return files.filter(p->p.getFileName().toString().startsWith("game-crash-")&&p.toString().endsWith(".json")).max(Comparator.comparingLong(GameCrashReports::modified)).orElse(null);}catch(Exception ignored){return null;}
    }
    private static String value(String value){return value==null||value.isBlank()?"Non disponible":value;}
}
