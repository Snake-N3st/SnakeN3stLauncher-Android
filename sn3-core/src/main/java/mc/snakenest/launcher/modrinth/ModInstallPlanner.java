package mc.snakenest.launcher.modrinth;

import java.io.IOException;
import java.util.*;

/** Resolves a stable dependency-first plan before any file is modified or downloaded. */
public final class ModInstallPlanner {
    private ModInstallPlanner() { }
    public interface Resolver {
        List<ModrinthVersion> versions(String project) throws Exception;
        ModrinthVersion version(String id) throws Exception;
    }
    public static List<ModrinthVersion> dependencies(ModrinthVersion root,String minecraft,String loader,
            Collection<ModrinthVersion> active,Resolver resolver) throws Exception {
        var graph=new Graph(minecraft,loader,active,resolver);
        graph.visit(root,true);
        return List.copyOf(graph.ordered);
    }
    private static final class Graph {
        final String minecraft,loader;final Collection<ModrinthVersion> active;final Resolver resolver;
        final Map<String,String> projects=new HashMap<>();final Set<String> visiting=new HashSet<>(),done=new HashSet<>();
        final List<ModrinthVersion> ordered=new ArrayList<>();
        Graph(String minecraft,String loader,Collection<ModrinthVersion> active,Resolver resolver){this.minecraft=minecraft;this.loader=loader;this.active=active;this.resolver=resolver;}
        void visit(ModrinthVersion current,boolean root)throws Exception {
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Installation annulée");
            if(!current.isCompatible(minecraft,loader))throw new IOException("Version incompatible : "+current.name());
            String prior=projects.putIfAbsent(current.projectId(),current.id());
            if(prior!=null&&!prior.equals(current.id()))throw new IOException("Dépendances contradictoires pour "+current.projectId());
            if(visiting.contains(current.id()))throw new IOException("Cycle de dépendances : "+current.name());
            if(done.contains(current.id()))return;
            if(done.size()+visiting.size()>=256)throw new IOException("Trop de dépendances");
            visiting.add(current.id());
            if(current.dependencies()!=null)for(var dependency:current.dependencies())if(dependency.isRequired()) {
                if(dependency.projectId()==null&&dependency.versionId()==null)throw new IOException("Dépendance externe : installation manuelle requise");
                ModrinthVersion installed=active.stream().filter(v->v.isCompatible(minecraft,loader))
                    .filter(v->dependency.projectId()==null||dependency.projectId().equals(v.projectId()))
                    .filter(v->dependency.versionId()==null||dependency.versionId().equals(v.id())).findFirst().orElse(null);
                if(installed!=null){String chosen=projects.putIfAbsent(installed.projectId(),installed.id());if(chosen!=null&&!chosen.equals(installed.id()))throw new IOException("Dépendances contradictoires pour "+installed.projectId());continue;}
                ModrinthVersion chosen=dependency.versionId()!=null?resolver.version(dependency.versionId()):resolver.versions(dependency.projectId()).stream()
                    .filter(v->v.isCompatible(minecraft,loader)).findFirst().orElseThrow(()->new IOException("Dépendance compatible introuvable : "+dependency.projectId()));
                if(dependency.projectId()!=null&&!dependency.projectId().equals(chosen.projectId()))throw new IOException("Projet de dépendance inattendu");
                visit(chosen,false);
            }
            visiting.remove(current.id());done.add(current.id());if(!root)ordered.add(current);
        }
    }
}
