package mc.snakenest.launcher.bugreport;

import java.util.regex.Pattern;

/** Filters diagnostics before they reach the UI, an export or a remote report. */
public final class CrashReportText {
    private CrashReportText() { }
    private static final Pattern BEARER=Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern FIELD=Pattern.compile("(?i)((?:-D|--)?(?:sn3\\.token|access[_-]?token|refresh[_-]?token|auth[_-]?token|session[_-]?token|token|seed|password|signature|public[_-]?key)[\\x22\\x27]?\\s*(?:[:=]|\\s)\\s*[\\x22\\x27]?)([^\\s,\\x22\\x27&}\\]]+)");
    private static final Pattern SEED_HEX=Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])");
    private static final Pattern AUTH=Pattern.compile("(?i)\\b(?:Proxy-)?Authorization\\s*:\\s*(?:Basic|Bearer)\\s+[^\\s]+");
    public static String forSubmission(String text) {
        String safe=sanitize(text);
        if(safe.length()<=65000)return safe;
        return safe.substring(0,12000)+"\n[Rapport abrégé pour l’envoi ; copie complète disponible dans le launcher]\n"+safe.substring(safe.length()-52000);
    }
    public static String sanitize(String text) {
        if(text==null)return "";
        String safe=BEARER.matcher(AUTH.matcher(text).replaceAll("Authorization: [masqué]")).replaceAll("Bearer [masqué]");
        safe=FIELD.matcher(safe).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(match.group(1)+"[masqué"+(match.group(2).equals("[masqué")?"":"]")));
        return SEED_HEX.matcher(safe).replaceAll("[masqué]");
    }
}
