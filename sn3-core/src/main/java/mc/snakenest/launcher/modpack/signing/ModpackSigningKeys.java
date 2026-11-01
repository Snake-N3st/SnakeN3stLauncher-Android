package mc.snakenest.launcher.modpack.signing;

import mc.snakenest.launcher.util.Hex;
import mc.snakenest.launcher.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The modpack <em>root</em> public keys this launcher pins - the only key it has to know: every modpack's own
 * key is introduced by a certificate the root signed, so adding a modpack never touches the pin.
 *
 * <p>Read from {@code sn3.signing.modpackRootKeys}, a comma-separated list of 64-hex Ed25519 keys that the site
 * bakes into the bootstrap's {@code jvm.prop} and the bootstrap forwards when it spawns the launcher - not a
 * constant, for the reason given in the bootstrap's {@code TrustedSigningKeys}: a build run against another
 * site has another vault and therefore another root. A list, so a rotation is "publish a bootstrap that pins
 * both".
 *
 * <p>No key means no verification - the transition, and a launcher started by hand in development. A malformed
 * entry is skipped with an error in the log; if that leaves none, the launcher verifies nothing rather than
 * failing closed on a typo, which would be a fleet-wide outage.
 */
public final class ModpackSigningKeys {

    public static final String PROPERTY = "sn3.signing.modpackRootKeys";

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{64}");

    private final List<byte[]> roots;

    private ModpackSigningKeys(List<byte[]> roots) {
        this.roots = roots;
    }

    public static ModpackSigningKeys none() {
        return new ModpackSigningKeys(List.of());
    }

    public static ModpackSigningKeys of(List<byte[]> roots) {
        return new ModpackSigningKeys(List.copyOf(roots));
    }

    public static ModpackSigningKeys fromSystemProperties() {
        return fromProperties(System::getProperty);
    }

    public static ModpackSigningKeys fromProperties(Function<String, String> property) {
        String value = property.apply(PROPERTY);
        if (value == null || value.isBlank()) {
            return none();
        }

        List<byte[]> keys = new ArrayList<>();
        for (String entry : value.split(",")) {
            String hex = entry.trim();
            if (hex.isEmpty()) {
                continue;
            }
            if (!KEY.matcher(hex).matches()) {
                Log.error(ModpackSigningKeys.class, "Ignoring a malformed key in " + PROPERTY + ": expected 64 lowercase hex characters", null);
                continue;
            }
            keys.add(Hex.decode(hex));
        }
        return new ModpackSigningKeys(List.copyOf(keys));
    }

    public List<byte[]> roots() {
        return roots;
    }

    public boolean isConfigured() {
        return !roots.isEmpty();
    }
}
