package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import java.io.*;
import java.util.Properties;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.prefs.LauncherPreferences;
import mc.snakenest.launcher.android.multirt.MultiRTUtils;
import mc.snakenest.launcher.android.multirt.Runtime;
import mc.snakenest.launcher.android.value.launcherprofiles.*;

/** Launcher-side metadata only. All measurements execute in the selected Amethyst JVM. */
final class DeviceBenchmark {
    record Configuration(String runtime, String renderer) {}
    static Configuration configuration(Context context) {
        Tools.initStorageConstants(context);LauncherPreferences.loadPreferences(context);LauncherProfiles.load();
        MinecraftProfile profile=LauncherProfiles.mainProfileJson.profiles.get(LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,""));String name=profile==null?LauncherPreferences.PREF_DEFAULT_RUNTIME:Tools.getSelectedRuntime(profile);
        if(name==null || name.isEmpty())throw new IllegalStateException("Installe et sélectionne un runtime Java dans les paramètres avant le benchmark.");
        if(profile!=null && profile.lastVersionId!=null && new File(Tools.DIR_HOME_VERSION+"/"+profile.lastVersionId+"/"+profile.lastVersionId+".json").isFile()) {
            mc.snakenest.launcher.android.JMinecraftVersionList.Version version=Tools.getVersionInfo(profile.lastVersionId);
            int required=version.javaVersion==null?8:version.javaVersion.majorVersion;
            name=Tools.pickRuntime(profile,required);
        }
        Runtime runtime=MultiRTUtils.forceReread(name);
        if(runtime.javaVersion<8 || runtime.versionString==null)throw new IllegalStateException("Le runtime Java sélectionné n’est pas installé ou n’est pas valide.");
        String renderer=profile==null?null:profile.pojavRendererName;
        if(renderer==null || renderer.isEmpty())renderer="opengles_mobileglues";
        if("vulkan_zink".equals(renderer))renderer="opengles3_desktopgl_zink_kopper";
        if(!Tools.checkRendererCompatible(context,renderer))throw new IllegalStateException("Le moteur de rendu sélectionné n’est pas compatible avec cet appareil.");
        return new Configuration(name,renderer);
    }
    record Measurement(double cpu,double gpu,double frameMillis,String renderer,boolean software,String error,java.util.List<Double> cpuSamples,java.util.List<Double> gpuSamples,String runtimeContext) {}
    static Measurement readResult(File file) throws IOException {
        Properties p=properties(file);
        return new Measurement(number(p,"cpu"),number(p,"gpu"),number(p,"frame_ms"),p.getProperty("renderer"),Boolean.parseBoolean(p.getProperty("software","false")),p.getProperty("error"),samples(p,"cpu_samples"),samples(p,"gpu_samples"),runtimeContext(p));
    }
    private static java.util.List<Double> samples(Properties p,String key) {
        java.util.List<Double> values=new java.util.ArrayList<>();
        for(String piece:p.getProperty(key,"").split(",")){try{double value=Double.parseDouble(piece);if(Double.isFinite(value)&&value>0&&values.size()<128)values.add(value);}catch(NumberFormatException ignored){}}
        return java.util.List.copyOf(values);
    }
    private static Properties properties(File file) throws IOException {
        if(!file.isFile() || file.length()>32768)throw new IOException("Le processus JVM s’est arrêté sans résultat. Le moteur ou le runtime peut être incompatible.");
        Properties p=new Properties();try(InputStream in=new FileInputStream(file)){p.load(in);}
        return p;
    }
    static String read(File file) throws IOException {
        Measurement m=readResult(file);
        return mc.snakenest.launcher.benchmark.BenchmarkReport.describe(m.cpu(),m.gpu(),m.frameMillis(),m.renderer(),m.software(),m.error())+runtimeContext(properties(file));
    }
    private static String runtimeContext(Properties p) {
        return "\n\nENVIRONNEMENT JVM MESURÉ\nRuntime : "+p.getProperty("java","?")+"\nVM : "+p.getProperty("vm","?")+"\nTas maximal : "+p.getProperty("heap_mib","?")+" Mio\nVendeur GL : "+p.getProperty("vendor","?")+"\nVersion GL : "+p.getProperty("gl","?")+"\nTraduction effective : "+p.getProperty("translation","?");
    }
    private static double number(Properties p,String key){try{return Double.parseDouble(p.getProperty(key,"NaN"));}catch(NumberFormatException ignored){return Double.NaN;}}
}
