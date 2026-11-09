package mc.snakenest.launcher.benchmark;

import java.util.Locale;

/** Interpretation deliberately independent of uncalibrated device score thresholds. */
public final class BenchmarkReport {
    private BenchmarkReport() {}
    public static String describe(double cpu, double gpuPixels, double frameMs, String renderer, boolean software, String error) {
        boolean cpuValid = Double.isFinite(cpu) && cpu > 0;
        boolean gpuValid = !software && error == null && renderer != null && !renderer.isBlank()
                && Double.isFinite(gpuPixels) && gpuPixels > 0 && Double.isFinite(frameMs) && frameMs > 0;
        String cpuText = cpuValid ? String.format(Locale.FRANCE, "%.1f millions d’itérations/s", cpu) : "Mesure indisponible";
        String gpuText = gpuValid ? String.format(Locale.FRANCE, "%.1f Mpixels/s · %.2f ms par image synthétique", gpuPixels, frameMs)
                : "Mesure matérielle indisponible";
        return "BILAN\n" + (cpuValid && gpuValid ? "Mesures JVM et rendu Amethyst obtenues. Les performances de Minecraft ne sont pas validées." : "Mesure partielle : aucune conclusion globale possible.")
            + "\n\nCPU — calcul entier sur un cœur\n" + cpuText
            + "\nChauffe de 0,5 s, mesure de 3 s. Boucle entière dans la JVM sélectionnée par Amethyst. Elle ne reproduit pas la simulation du jeu."
            + "\n\nGPU — rendu via Amethyst / LWJGL\n" + gpuText
            + "\nGPU : " + (renderer == null || renderer.isBlank() ? "non identifié" : renderer)
            + (software ? "\nRendu logiciel détecté : résultat graphique non exploitable." : "")
            + (error == null ? "" : "\nMotif : " + error)
            + "\nSurface fixe 512 × 512, shader de 48 étapes, synchronisation GPU. Chauffe de 0,5 s puis mesure de 4 s."
            + "\nLe JAR utilise LWJGL et la couche de traduction sélectionnée par Amethyst. Il ne charge pas de monde Minecraft : ces millisecondes ne sont pas celles d’une image du jeu."
            + "\n\nUSAGES — validation en jeu nécessaire\nVanilla / petits modpacks : à tester sans shaders ; aucune fluidité garantie."
            + "\nModpacks moyens ou lourds : non validés. Le test ne mesure pas les chunks, les entités, les mods, les allocations ou le ramasse-miettes de la JVM."
            + "\nShaders : non recommandés sans validation préalable de la fluidité en jeu. Un bon débit dans ce petit shader ne démontre pas la capacité à exécuter un shaderpack."
            + "\n\nOBJECTIFS DE FLUIDITÉ\n60 FPS : budget de 16,67 ms par image complète du jeu."
            + "\n120 FPS : budget de 8,33 ms par image complète du jeu."
            + "\nAucun de ces objectifs n’est validé ici. Un écran 120 Hz ne garantit pas 120 FPS. Il faut relever les FPS et les temps d’image dans le modpack, à sa résolution et à sa distance de rendu réelles."
            + "\n\nPORTÉE DU TEST\nTest court dans un JAR : ni FPS Minecraft, ni stabilité prolongée, ni chauffe durable mesurés. Les chiffres CPU/GPU ont des unités différentes et ne forment pas un score global. Ils servent à comparer des essais du même test dans des conditions proches.";
    }
}
