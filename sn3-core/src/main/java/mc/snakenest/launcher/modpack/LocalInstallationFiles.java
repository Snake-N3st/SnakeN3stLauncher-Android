package mc.snakenest.launcher.modpack;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/** Read-only offline preflight. Local mod choices deliberately replace the official file list. */
public final class LocalInstallationFiles {
    private LocalInstallationFiles() { }
    public static void requireInstance(Path instance, StoredManifest manifest, Map<String,ModOverrides.Choice> choices) throws IOException {
        Path root=instance.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Instance locale manquante ou non sûre.");
        if (manifest==null || manifest.pathToHash()==null) throw new IOException("Inventaire local manquant. Reviens en ligne pour réparer.");
        for (String name:manifest.pathToHash().keySet()) {
            String desired=name;
            if(name.startsWith("mods/") && name.indexOf('/',5)<0) {
                ModOverrides.Choice choice=choices.get(name.substring(5));
                if(choice!=null) {
                    if(choice.removed()) continue;
                    desired="mods/"+choice.filename()+(choice.disabled()?".disabled":"");
                }
            }
            requireFile(root,desired,true);
        }
    }
    public static void requireFile(Path root,String relative,boolean allowEncrypted) throws IOException {
        Path base=root.toAbsolutePath().normalize();
        if(relative==null || relative.isBlank() || relative.indexOf('\\')>=0 || relative.indexOf('\0')>=0) throw new IOException("Chemin local invalide.");
        Path raw=Path.of(relative);
        if(raw.isAbsolute() || raw.startsWith("..") || !raw.equals(raw.normalize())) throw new IOException("Chemin local non sûr.");
        Path file=base.resolve(raw).normalize();
        if(!file.startsWith(base) || file.equals(base)) throw new IOException("Chemin local non sûr.");
        for(Path parent=file;parent!=null && parent.startsWith(base);parent=parent.getParent())
            if(Files.isSymbolicLink(parent)) throw new IOException("Lien symbolique interdit dans l’installation locale.");
        if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) return;
        Path encrypted=file.resolveSibling(file.getFileName()+".sn3enc");
        if(allowEncrypted && Files.isRegularFile(encrypted,LinkOption.NOFOLLOW_LINKS)) return;
        throw new IOException("Fichier local manquant : "+relative+". Reviens en ligne pour réparer ; aucun téléchargement n’a été lancé.");
    }
}
