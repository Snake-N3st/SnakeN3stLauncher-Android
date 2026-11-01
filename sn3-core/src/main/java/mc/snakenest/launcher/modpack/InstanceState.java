package mc.snakenest.launcher.modpack;

import java.util.concurrent.Future;

/**
 * One immutable snapshot of "what is currently happening to this one modpack instance" - see
 * {@link InstanceStateRegistry}, which is the only thing that ever constructs or replaces one.
 */
public record InstanceState(String slug, InstanceActivity activity, Future<?> installTask, Process gameProcess) {
}
