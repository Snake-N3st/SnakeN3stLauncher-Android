package mc.snakenest.launcher.modrinth;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ModBrowserPolicyTest {
    private ModrinthSearchHit hit(String id,List<String> versions,List<String> loaders){return new ModrinthSearchHit(id,id,id,"description","author",null,loaders,versions,null,null,1);}
    private ModrinthVersion version(String id,List<String> minecraft,List<String> loaders){return new ModrinthVersion(id,"project",id,id,minecraft,loaders,"release",List.of(),List.of());}
    @Test void requiresBothTheMinecraftVersionAndLoader(){assertFalse(ModBrowserPolicy.compatible(hit("forge",List.of("1.21"),List.of("forge")),"1.21","fabric"));assertFalse(ModBrowserPolicy.compatible(hit("old",List.of("1.20"),List.of("fabric")),"1.21","fabric"));assertTrue(ModBrowserPolicy.compatible(hit("ok",List.of("1.21"),List.of("fabric")),"1.21","fabric"));}
    @Test void keepsIncompatibleProjectsVisibleAfterCompatibleOnes(){var bad=hit("bad",List.of("1.20"),List.of("fabric"));var good=hit("good",List.of("1.21"),List.of("fabric"));assertEquals(List.of(good,bad),ModBrowserPolicy.projects(List.of(bad,good),"1.21","fabric"));}
    @Test void preservesPopularityOrderWithinEachGroup(){var a=hit("a",List.of("1.21"),List.of("fabric"));var b=hit("b",List.of("1.21"),List.of("fabric"));assertEquals(List.of(a,b),ModBrowserPolicy.projects(List.of(a,b),"1.21","fabric"));}
    @Test void picksNewestCompatibleReleaseAndKeepsAllVersions(){var newestBad=version("new-forge",List.of("1.21"),List.of("forge"));var latest=version("latest",List.of("1.21"),List.of("fabric"));var older=version("older",List.of("1.21"),List.of("fabric"));var versions=List.of(newestBad,latest,older);assertEquals(latest,ModBrowserPolicy.latest(versions,"1.21","fabric").orElseThrow());assertEquals(List.of(latest,older,newestBad),ModBrowserPolicy.versions(versions,"1.21","fabric"));}
    @Test void missingCompatibilityMetadataIsNotAClaimOfSupport(){assertFalse(ModBrowserPolicy.compatible(hit("unknown",null,null),"1.21","fabric"));assertFalse(ModBrowserPolicy.compatible(version("unknown",null,null),"1.21","fabric"));}
    @Test void neverOffersAnIncompatibleQuickInstall(){assertTrue(ModBrowserPolicy.latest(List.of(version("wrong",List.of("1.20"),List.of("fabric"))),"1.21","fabric").isEmpty());}
}
