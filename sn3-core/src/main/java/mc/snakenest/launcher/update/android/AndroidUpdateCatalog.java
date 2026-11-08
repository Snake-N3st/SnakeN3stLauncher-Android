package mc.snakenest.launcher.update.android;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Untrusted release metadata; only this launcher's exact GitHub release assets are accepted. */
public final class AndroidUpdateCatalog {
    public static final long MAX_APK_SIZE = 512L * 1024 * 1024;
    private static final String APPLICATION_ID = "mc.snakenest.launcher.android";
    private static final String RELEASE_PATH = "/Snake-N3st/SnakeN3stLauncher-Android/releases/download/";
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private AndroidUpdateCatalog() { }

    public record Candidate(String applicationId, long versionCode, String versionName, URI apkUrl,
                            String sha256, long size, int minSdk, String releaseNotes) { }

    /** Empty means there is no newer compatible variant, never that malformed metadata was accepted. */
    public static Optional<Candidate> parse(String json, String applicationId, long currentVersionCode, int sdk)
            throws IOException {
        if (!knownApplication(applicationId) || currentVersionCode < 0 || sdk < 0)
            throw new IllegalArgumentException("Invalid installed Android application identity");
        if (json == null || json.length() > 512 * 1024) throw invalid("Catalogue is missing or too large");
        try {
            JsonElement parsed = JSON.fromJson(json, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) throw invalid("Catalogue must be an object");
            JsonObject root = parsed.getAsJsonObject();
            if (integer(root, "schema") != 1) throw invalid("Unsupported catalogue schema");
            String notes = null;
            if (root.has("releaseNotes") && !root.get("releaseNotes").isJsonNull()) {
                notes = string(root, "releaseNotes");
                if (notes.length() > 65536) throw invalid("Release notes are too large");
            }
            JsonElement variantsValue = root.get("variants");
            if (variantsValue == null || !variantsValue.isJsonArray()) throw invalid("Missing catalogue variants");
            JsonArray variants = variantsValue.getAsJsonArray();
            if (variants.size() > 2) throw invalid("Too many application variants");
            Set<String> identities = new HashSet<>();
            Candidate selected = null;
            for (JsonElement value : variants) {
                if (!value.isJsonObject()) throw invalid("Variant must be an object");
                JsonObject variant = value.getAsJsonObject();
                String id = string(variant, "applicationId");
                if (!knownApplication(id) || !identities.add(id)) throw invalid("Unknown or duplicate application variant");
                long code = integer(variant, "versionCode");
                if (code <= 0) throw invalid("Invalid Android version code");
                String name = string(variant, "versionName");
                if (name.isBlank() || name.length() > 128) throw invalid("Invalid Android version name");
                URI apk = apkUri(string(variant, "apkUrl"));
                String hash = string(variant, "sha256");
                if (!hash.matches("[0-9a-fA-F]{64}")) throw invalid("Invalid APK SHA-256");
                long size = integer(variant, "size");
                if (size <= 0 || size > MAX_APK_SIZE) throw invalid("Invalid APK size");
                long minimum = integer(variant, "minSdk");
                if (minimum < 26 || minimum > Integer.MAX_VALUE) throw invalid("Invalid minimum Android SDK");
                Candidate candidate = new Candidate(id, code, name, apk, hash.toLowerCase(Locale.ROOT),
                        size, (int) minimum, notes);
                if (id.equals(applicationId) && code > currentVersionCode && minimum <= sdk) selected = candidate;
            }
            return Optional.ofNullable(selected);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Invalid Android update catalogue", e);
        }
    }

    private static boolean knownApplication(String id) {
        return APPLICATION_ID.equals(id) || (APPLICATION_ID + ".debug").equals(id);
    }

    private static String string(JsonObject object, String field) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw invalid("Missing or invalid " + field);
        return value.getAsString();
    }

    private static long integer(JsonObject object, String field) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw invalid("Missing or invalid " + field);
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException | NumberFormatException e) { throw invalid("Invalid integer " + field); }
    }

    private static URI apkUri(String value) throws IOException {
        if (value.length() > 2048) throw invalid("APK URL is too long");
        URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException e) { throw invalid("Invalid APK URL"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getRawAuthority())
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawPath() == null || !uri.getRawPath().startsWith(RELEASE_PATH))
            throw invalid("APK URL must point to this launcher's GitHub release assets");
        String[] segments = uri.getRawPath().substring(RELEASE_PATH.length()).split("/", -1);
        // Reject encoded separators and dot paths rather than letting HTTP normalization choose a different asset.
        if (segments.length != 2 || !safeSegment(segments[0]) || !safeSegment(segments[1]) || !segments[1].endsWith(".apk"))
            throw invalid("Invalid GitHub APK release path");
        return uri;
    }

    private static boolean safeSegment(String segment) {
        return !segment.equals(".") && !segment.equals("..") && segment.matches("[A-Za-z0-9][A-Za-z0-9._+-]*");
    }

    private static IOException invalid(String message) { return new IOException(message); }
}
