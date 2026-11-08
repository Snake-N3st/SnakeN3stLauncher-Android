package mc.snakenest.launcher.modrinth;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModInstallPlannerTest {
    private static ModrinthVersion v(String id,String project,ModrinthDependency...dependencies){return new ModrinthVersion(id,project,id,id,List.of("1.21.1"),List.of("fabric"),"release",List.of(),List.of(dependencies));}
    private static ModrinthDependency dep(String project,String version){return new ModrinthDependency(version,project,"required");}
    private static ModInstallPlanner.Resolver resolver(ModrinthVersion...versions){return new ModInstallPlanner.Resolver(){
        public List<ModrinthVersion> versions(String project){return Arrays.stream(versions).filter(v->v.projectId().equals(project)).toList();}
        public ModrinthVersion version(String id)throws IOException{return Arrays.stream(versions).filter(v->v.id().equals(id)).findFirst().orElseThrow(()->new IOException("missing"));}
    };}
    @Test void resolvesTransitiveDependenciesBeforeParents()throws Exception {
        var c=v("c1","c");var b=v("b1","b",dep("c",null));var root=v("a1","a",dep("b",null),dep("c",null));
        assertEquals(List.of(c,b),ModInstallPlanner.dependencies(root,"1.21.1","fabric",List.of(),resolver(b,c)));
    }
    @Test void honorsPinnedVersionWithoutProjectId()throws Exception {
        var old=v("b1","b");var latest=v("b2","b");var root=v("a1","a",dep(null,"b1"));
        assertEquals(List.of(old),ModInstallPlanner.dependencies(root,"1.21.1","fabric",List.of(),resolver(latest,old)));
    }
    @Test void reusesActiveCompatibleDependencyWithoutUpdatingIt()throws Exception {
        var installed=v("b1","b");var latest=v("b2","b");
        assertEquals(List.of(),ModInstallPlanner.dependencies(v("a1","a",dep("b",null)),"1.21.1","fabric",List.of(installed),resolver(latest)));
    }
    @Test void installedDifferentVersionDoesNotSatisfyAnExactPin()throws Exception {
        var old=v("b1","b");var latest=v("b2","b");
        assertEquals(List.of(old),ModInstallPlanner.dependencies(v("a1","a",dep("b","b1")),"1.21.1","fabric",List.of(latest),resolver(old)));
    }
    @Test void rejectsDependencyCycles() {
        var a=v("a1","a",dep("b",null));var b=v("b1","b",dep("a",null));
        assertThrows(IOException.class,()->ModInstallPlanner.dependencies(a,"1.21.1","fabric",List.of(),resolver(a,b)));
    }
    @Test void rejectsTwoDifferentPinsForTheSameProject() {
        var b1=v("b1","b");var b2=v("b2","b");
        assertThrows(IOException.class,()->ModInstallPlanner.dependencies(v("a1","a",dep("b","b1"),dep("b","b2")),"1.21.1","fabric",List.of(),resolver(b1,b2)));
    }
    @Test void rejectsPinnedIncompatibleVersion() {
        var incompatible=new ModrinthVersion("b1","b","b1","b1",List.of("1.20.1"),List.of("forge"),"release",List.of(),List.of());
        assertThrows(IOException.class,()->ModInstallPlanner.dependencies(v("a1","a",dep(null,"b1")),"1.21.1","fabric",List.of(),resolver(incompatible)));
    }
    @Test void ignoresOptionalAndEmbeddedDependencies()throws Exception {
        var root=v("a1","a",new ModrinthDependency(null,"b","optional"),new ModrinthDependency(null,"c","embedded"));
        assertEquals(List.of(),ModInstallPlanner.dependencies(root,"1.21.1","fabric",List.of(),resolver()));
    }
}
