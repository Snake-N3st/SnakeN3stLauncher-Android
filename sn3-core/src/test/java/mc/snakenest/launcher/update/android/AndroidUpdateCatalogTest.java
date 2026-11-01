package mc.snakenest.launcher.update.android;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class AndroidUpdateCatalogTest {
    private static final String RELEASE = "mc.snakenest.launcher.android";
    private static final String DEBUG = RELEASE + ".debug";
    private static final String URL = "https://github.com/Snake-N3st/SnakeN3stLauncher-Android/releases/download/v1.0.0/SnakeN3stLauncher-Android-debug.apk";
    private static final String HASH = "A".repeat(64);

    private static JsonObject variant(String applicationId, long versionCode) {
        JsonObject value = new JsonObject();
        value.addProperty("applicationId", applicationId);
        value.addProperty("versionCode", versionCode);
        value.addProperty("versionName", "0.1-beta");
        value.addProperty("apkUrl", URL);
        value.addProperty("sha256", HASH);
        value.addProperty("size", 12345678L);
        value.addProperty("minSdk", 26);
        return value;
    }
    private static String catalogue(JsonObject... variants) {
        JsonObject root = new JsonObject(); root.addProperty("schema", 1);
        root.addProperty("releaseNotes", "Nouvelle version Android");
        JsonArray array = new JsonArray(); for (JsonObject variant : variants) array.add(variant);
        root.add("variants", array); return root.toString();
    }

    @Test void selectsExactInstalledVariantByLongVersionCodeRegardlessOfVersionName() throws Exception {
        var selected = AndroidUpdateCatalog.parse(catalogue(variant(RELEASE, 500), variant(DEBUG, 3000000000L)), DEBUG, 2999999999L, 35).orElseThrow();
        assertEquals(DEBUG, selected.applicationId());
        assertEquals(3000000000L, selected.versionCode());
        assertEquals("0.1-beta", selected.versionName());
        assertEquals(URI.create(URL), selected.apkUrl());
        assertEquals(HASH.toLowerCase(java.util.Locale.ROOT), selected.sha256());
        assertEquals(12345678L, selected.size());
        assertEquals(26, selected.minSdk());
        assertEquals("Nouvelle version Android", selected.releaseNotes());
    }

    @Test void releaseNeverSelectsDebugAndDebugNeverSelectsRelease() throws Exception {
        assertTrue(AndroidUpdateCatalog.parse(catalogue(variant(RELEASE, 200)), DEBUG, 100, 35).isEmpty());
        assertTrue(AndroidUpdateCatalog.parse(catalogue(variant(DEBUG, 200)), RELEASE, 100, 35).isEmpty());
    }

    @Test void equalOrOlderVersionCodeNeverUpdatesEvenWithHigherLookingVersionName() throws Exception {
        JsonObject value = variant(DEBUG, 100); value.addProperty("versionName", "99999.0");
        assertTrue(AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35).isEmpty());
        value.addProperty("versionCode", 99);
        assertTrue(AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35).isEmpty());
    }

    @Test void incompatibleSdkReturnsEmptyAndExactMinimumIsAccepted() throws Exception {
        JsonObject value = variant(DEBUG, 101); value.addProperty("minSdk", 35);
        assertTrue(AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 34).isEmpty());
        assertTrue(AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35).isPresent());
        value.addProperty("minSdk", 25);
        assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35));
    }

    @Test void rejectsForeignOriginsRepositoriesCredentialsQueriesAndPathSmuggling() {
        String prefix = "https://github.com/Snake-N3st/SnakeN3stLauncher-Android/releases/download/";
        for (String url : new String[]{
                URL.replace("https://", "http://"), URL.replace("github.com", "github.com.evil.test"),
                URL.replace("github.com", "github.com@evil.test"), URL.replace("github.com", "evil@github.com"),
                URL.replace("github.com", "github.com:443"), URL.replace("Snake-N3st/", "Other-Owner/"),
                URL.replace("SnakeN3stLauncher-Android/", "AnotherRepo/"), URL + "?download=1", URL + "#fragment",
                prefix + "../app.apk", prefix + "v1/../../app.apk", prefix + "%2e%2e/app.apk",
                prefix + "v1%2Fother/app.apk", prefix + "v1/app%2Fother.apk", prefix + "v1//app.apk",
                prefix + "v1/app.zip", prefix + "v1/", prefix + "v1/app.apk/extra"}) {
            JsonObject value = variant(DEBUG, 101); value.addProperty("apkUrl", url);
            assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35), url);
        }
    }

    @Test void rejectsMalformedHashesAndSizes() {
        for (String hash : new String[]{"", "f".repeat(63), "f".repeat(65), "g".repeat(64), " " + HASH}) {
            JsonObject value = variant(DEBUG, 101); value.addProperty("sha256", hash);
            assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35));
        }
        for (long size : new long[]{0, -1, AndroidUpdateCatalog.MAX_APK_SIZE + 1, Long.MAX_VALUE}) {
            JsonObject value = variant(DEBUG, 101); value.addProperty("size", size);
            assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35));
        }
    }

    @Test void rejectsCoercedFractionalOverflowingOrMissingNumbers() {
        for (String field : new String[]{"versionCode", "size", "minSdk"}) {
            JsonObject value = variant(DEBUG, 101); value.addProperty(field, "123");
            assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35));
            value.addProperty(field, 26.5);
            assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35));
            value.addProperty(field, new java.math.BigInteger("9223372036854775808"));
            assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35));
            value.remove(field);
            assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(value), DEBUG, 100, 35));
        }
    }

    @Test void rejectsUnknownOrDuplicateApplicationVariants() {
        assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(variant("foreign.app", 101)), DEBUG, 100, 35));
        assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(catalogue(variant(DEBUG, 101), variant(DEBUG, 102)), DEBUG, 100, 35));
    }

    @Test void rejectsMalformedSchemaStructureAndLenientJson() {
        for (String json : new String[]{"", "null", "[]", "{}", "{\"schema\":2,\"variants\":[]}",
                "{\"schema\":\"1\",\"variants\":[]}", "{\"schema\":1,\"variants\":{}}",
                "{schema:1,variants:[]}", "{\"schema\":1,\"variants\":[],}",
                catalogue(variant(DEBUG, 101)) + " {}"}) {
            assertThrows(IOException.class, () -> AndroidUpdateCatalog.parse(json, DEBUG, 100, 35), json);
        }
    }

    @Test void releaseNotesAreOptional() throws Exception {
        JsonObject root = com.google.gson.JsonParser.parseString(catalogue(variant(DEBUG, 101))).getAsJsonObject();
        root.remove("releaseNotes");
        assertNull(AndroidUpdateCatalog.parse(root.toString(), DEBUG, 100, 35).orElseThrow().releaseNotes());
    }
}
