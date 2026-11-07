package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.nio.file.*;
import java.util.Comparator;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.modpack.*;
import mc.snakenest.launcher.util.AppDirs;

/** Isolated preferences and directory: never modifies an actual account, pack or global setting. */
final class JvmSettingsChecks {
    static void run(Instrumentation instrumentation) {
        Bundle result=new Bundle();int passed=0;Path root=null;
        Context context=instrumentation.getTargetContext();String suffix=Long.toString(System.nanoTime());
        String flagsName="sn3-jvm-test-flags-"+suffix,globalsName="sn3-jvm-test-globals-"+suffix;
        String oldArgs=LauncherPreferences.PREF_CUSTOM_JAVA_ARGS;
        try {
            var flags=context.getSharedPreferences(flagsName,Context.MODE_PRIVATE);
            var globals=context.getSharedPreferences(globalsName,Context.MODE_PRIVATE);
            globals.edit().putInt("allocation",1536).putString("javaArgs","-Dglobal=one").commit();
            root=Files.createTempDirectory(context.getCacheDir().toPath(),"sn3-jvm-fixture-");
            AppDirs dirs=new AppDirs(root);ModpackSettingsStore store=new ModpackSettingsStore(dirs);
            var policy=new SnakeJvmSettings(flags,globals,dirs);
            var initial=policy.resolve("new-pack");
            check(initial.memoryMb()==1536&&initial.extraJvmArgs().equals("-Dglobal=one")&&!policy.memoryOverride("new-pack")&&!policy.argumentsOverride("new-pack"));passed++;
            policy.initialFlags("installed-pack");
            store.save("installed-pack",new ModpackSettings(4096,"-Dsite=recommendation",null,null));
            check(policy.resolve("installed-pack").memoryMb()==1536&&policy.resolve("installed-pack").extraJvmArgs().equals("-Dglobal=one"));passed++;
            store.save("legacy-pack",new ModpackSettings(768,"-Dlegacy=one","beta","runtime-choice"));
            check(policy.memoryOverride("legacy-pack")&&policy.argumentsOverride("legacy-pack")&&policy.resolve("legacy-pack").memoryMb()==768);passed++;
            policy.save("legacy-pack",1024,"-Dpack=one",true,true);
            var custom=policy.resolve("legacy-pack");check(custom.memoryMb()==1024&&custom.extraJvmArgs().equals("-Dpack=one")&&"beta".equals(custom.channel())&&"runtime-choice".equals(custom.javaPath()));passed++;
            policy.save("legacy-pack",1024,"",false,true);
            check(policy.resolve("legacy-pack").memoryMb()==1536&&policy.resolve("legacy-pack").extraJvmArgs().isEmpty());passed++;
            policy.save("legacy-pack",1024,"-Dpack=one",true,false);
            check(policy.resolve("legacy-pack").memoryMb()==1024&&policy.resolve("legacy-pack").extraJvmArgs().equals("-Dglobal=one"));passed++;
            globals.edit().putInt("allocation",1792).putString("javaArgs","-Dglobal=two").commit();
            check(policy.resolve("new-pack").memoryMb()==1792&&policy.resolve("new-pack").extraJvmArgs().equals("-Dglobal=two"));passed++;
            check(globals.getInt("allocation",0)==1792&&globals.getString("javaArgs","").equals("-Dglobal=two"));passed++;
            Activity[] holder=new Activity[1]; instrumentation.runOnMainSync(()->holder[0]=new Activity());
            Activity fake=holder[0];fake.setIntent(new Intent().putExtra(Sn3GameBridge.ARGUMENTS,""));
            LauncherPreferences.PREF_CUSTOM_JAVA_ARGS="-Dglobal=one";
            check(Sn3GameBridge.effectiveArguments(fake,"-Dstale=profile").isEmpty());passed++;
            fake.setIntent(new Intent().putExtra(Sn3GameBridge.ARGUMENTS,"-Dpack=two"));
            check(Sn3GameBridge.effectiveArguments(fake,"-Dstale=profile").equals("-Dpack=two"));passed++;
            fake.setIntent(new Intent());check(Sn3GameBridge.effectiveArguments(fake,"-Dnative=one").equals("-Dnative=one"));passed++;
            check(Sn3GameBridge.effectiveArguments(fake,"").equals("-Dglobal=one"));passed++;
            var cold=new RuntimeFixture();
            check(mc.snakenest.launcher.android.NewJREUtil.chooseRuntime(21,null,cold).equals("External-21")&&cold.downloads.equals(java.util.List.of(21)));passed++;
            check(mc.snakenest.launcher.android.NewJREUtil.chooseRuntime(21,null,cold).equals("External-21")&&cold.downloads.size()==1);passed++;
            var installed=new RuntimeFixture();installed.versions.put("Internal-21",21);installed.versions.put("External-25",25);
            check(mc.snakenest.launcher.android.NewJREUtil.chooseRuntime(21,"Internal-21",installed).equals("Internal-21")&&installed.downloads.isEmpty());passed++;
            var old=new RuntimeFixture();old.versions.put("Internal",8);old.versions.put("Internal-21",21);
            check(mc.snakenest.launcher.android.NewJREUtil.chooseRuntime(21,"Internal",old).equals("Internal-21")&&old.downloads.isEmpty());passed++;
            var java16=new RuntimeFixture();mc.snakenest.launcher.android.NewJREUtil.chooseRuntime(16,null,java16);check(java16.downloads.equals(java.util.List.of(17)));passed++;
            var java8=new RuntimeFixture();check(mc.snakenest.launcher.android.NewJREUtil.chooseRuntime(8,null,java8).equals("External-8")&&java8.downloads.equals(java.util.List.of(8)));passed++;
            var failed=new RuntimeFixture();failed.downloadWorks=false;boolean refused=false;
            try{mc.snakenest.launcher.android.NewJREUtil.chooseRuntime(21,null,failed);}catch(IllegalStateException expected){refused=true;}check(refused);passed++;
            var planner=new mc.snakenest.launcher.android.tasks.MinecraftDownloader();
            var tasks=planner.getClass().getDeclaredField("mScheduledDownloadTasks");tasks.setAccessible(true);tasks.set(planner,new java.util.ArrayList<>());
            var schedule=planner.getClass().getDeclaredMethod("scheduleDownload",java.io.File.class,int.class,String.class,String.class,long.class,boolean.class);schedule.setAccessible(true);
            java.net.ProxySelector previousProxy=java.net.ProxySelector.getDefault();
            try {
                java.net.ProxySelector.setDefault(new java.net.ProxySelector(){
                    @Override public java.util.List<java.net.Proxy> select(java.net.URI uri){throw new AssertionError("Scheduling must not issue HTTP or HEAD requests");}
                    @Override public void connectFailed(java.net.URI uri,java.net.SocketAddress address,java.io.IOException failure){}
                });
                java.io.File target=root.resolve("planned-artifact.jar").toFile();
                schedule.invoke(planner,target,0,"https://not-requested.invalid/file",null,0L,false);
                schedule.invoke(planner,target,0,"https://not-requested.invalid/duplicate",null,0L,false);
                check(((java.util.List<?>)tasks.get(planner)).size()==1);passed++;
            } finally {java.net.ProxySelector.setDefault(previousProxy);}
            store.save("native-pack",new ModpackSettings(1024,"-Dstored=old",null,null));
            String profileId=java.util.UUID.nameUUIDFromBytes("sn3:native-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            com.google.gson.JsonObject profile=new com.google.gson.JsonObject();profile.addProperty("gameDir",dirs.instance("native-pack").toString());profile.addProperty("javaArgs","-Dnative=kept");
            com.google.gson.JsonObject profiles=new com.google.gson.JsonObject();profiles.add(profileId,profile);com.google.gson.JsonObject document=new com.google.gson.JsonObject();document.add("profiles",profiles);
            Path profileFile=root.resolve("launcher_profiles.json");Files.writeString(profileFile,document.toString());
            var profilePath=SnakeJvmSettings.class.getDeclaredField("profilesFile");profilePath.setAccessible(true);profilePath.set(policy,profileFile);
            check(policy.resolve("native-pack").extraJvmArgs().equals("-Dnative=kept"));passed++;
            passed+=DownloadIoChecks.run(context);
            passed+=SpeedChecks.run();
            result.putBoolean("success",true);
        } catch(Throwable failure) {result.putBoolean("success",false);result.putString("message",failure.getClass().getSimpleName()+": "+failure.getMessage());}
        finally {
            LauncherPreferences.PREF_CUSTOM_JAVA_ARGS=oldArgs;
            context.deleteSharedPreferences(flagsName);context.deleteSharedPreferences(globalsName);
            try {if(root!=null)try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}catch(Exception cleanup){result.putBoolean("success",false);result.putString("cleanup",cleanup.getClass().getSimpleName());}
            result.putInt("checks",passed);instrumentation.finish(result.getBoolean("success")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
    private static final class RuntimeFixture implements mc.snakenest.launcher.android.NewJREUtil.RuntimeAccess {
        final java.util.Map<String,Integer> versions=new java.util.HashMap<>();
        final java.util.List<Integer> downloads=new java.util.ArrayList<>();boolean downloadWorks=true;
        @Override public int version(String name){return versions.getOrDefault(name,0);}
        @Override public String findInstalled(int minimum){return versions.entrySet().stream().filter(e->e.getValue()>=minimum).min(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);}
        @Override public void download(int version){downloads.add(version);if(downloadWorks)versions.put("External-"+version,version);}
    }
    private static void check(boolean condition){if(!condition)throw new AssertionError("JVM settings policy assertion failed");}
}
