package mc.snakenest.launcher.modpack;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * What every modpack instance is currently doing - installing, running, or being stopped - one
 * entry per slug, each fully independent of the others. Replaces an earlier single-field design
 * ({@code LauncherApp}'s old {@code active}) that could only ever describe one modpack at a time:
 * starting an install on a second modpack while a first one was still running silently overwrote
 * the only record of the first one, so its own "Arreter"/"Annuler" buttons became no-ops (they
 * read the now-wrong global state) and a freshly (re)built page/card for it rendered as idle even
 * though it was genuinely still active. Every method here is scoped to one {@code slug} instead,
 * so two different modpacks can be tracked - and controlled - completely independently.
 *
 * <p>Backed by a {@link ConcurrentHashMap} and built entirely out of its atomic
 * {@code compute}/{@code computeIfPresent}/{@code putIfAbsent} operations rather than a
 * read-then-write pair on a field - not just cleaner, but genuinely race-free in a way a
 * {@code volatile} snapshot field only approximated (see {@link #attachInstallTask}).
 *
 * <p>Pure Java, no Swing and no dependency on {@code LauncherApp} - independently unit-tested
 * without reflection, unlike the private-field/private-method design it replaces.
 */
public final class InstanceStateRegistry {

    private final ConcurrentHashMap<String, InstanceState> states = new ConcurrentHashMap<>();

    /**
     * Starts a new installing episode for {@code slug} - the "Demarrer"/"Reparer" action, called
     * right before the background task is submitted.
     *
     * @return {@code false} if {@code slug} already has an activity in flight (installing,
     *         running, or being stopped) - the caller should abandon the attempt rather than
     *         overwrite it. This is the guard that never existed before: nothing previously
     *         stopped a second install/launch for the same slug from clobbering the first one's
     *         tracking mid-flight.
     */
    public boolean beginInstalling(String slug) {
        return states.putIfAbsent(slug, new InstanceState(slug, InstanceActivity.INSTALLING, null, null)) == null;
    }

    /**
     * Fills in {@code task} on the bare {@code INSTALLING} snapshot {@link #beginInstalling}
     * created - called right after {@code backgroundExecutor.submit(...)} returns, since the
     * {@link Future} doesn't exist before then.
     *
     * <p>A no-op unless {@code slug}'s entry is still exactly the bare snapshot this is trying to
     * complete (still {@code INSTALLING}, task still {@code null}) - if the background task is
     * fast enough to have already moved {@code slug} on to {@code RUNNING} (or cleared it, on a
     * very fast failure) by the time the submitting thread gets back around to calling this, that
     * stale write must not clobber the newer, correct state. {@code computeIfPresent} makes this
     * check-and-replace genuinely atomic, unlike the read-then-conditionally-write this replaces.
     */
    public void attachInstallTask(String slug, Future<?> task) {
        states.computeIfPresent(slug, (s, state) ->
                state.activity() == InstanceActivity.INSTALLING && state.installTask() == null
                        ? new InstanceState(slug, InstanceActivity.INSTALLING, task, null)
                        : state);
    }

    /** {@code INSTALLING} -&gt; {@code RUNNING}, once the game process actually spawns after a successful install. */
    public void transitionToRunning(String slug, Process process) {
        states.put(slug, new InstanceState(slug, InstanceActivity.RUNNING, null, process));
    }

    /**
     * {@code RUNNING} -&gt; {@code STOPPING} - the "Arreter" click.
     *
     * @return the process to {@code destroy()}, or empty if {@code slug} wasn't genuinely
     *         {@code RUNNING} right now (a stale click, or it already exited) - the caller should
     *         treat that as a no-op rather than acting on a process that isn't there.
     */
    public Optional<Process> beginStopping(String slug) {
        InstanceState[] before = new InstanceState[1];
        states.computeIfPresent(slug, (s, state) -> {
            if (state.activity() != InstanceActivity.RUNNING) {
                return state;
            }
            before[0] = state;
            return new InstanceState(slug, InstanceActivity.STOPPING, null, state.gameProcess());
        });
        return before[0] == null ? Optional.empty() : Optional.of(before[0].gameProcess());
    }

    /**
     * The process to force-kill outright - the "Tuer" click. Valid whether {@code slug} is
     * {@code RUNNING} or already {@code STOPPING} (force-killing doesn't require having asked
     * nicely first), so this deliberately doesn't check {@link InstanceState#activity()} at all -
     * only whether there's a process on file for this slug right now.
     */
    public Optional<Process> processFor(String slug) {
        InstanceState state = states.get(slug);
        return state != null && state.gameProcess() != null ? Optional.of(state.gameProcess()) : Optional.empty();
    }

    /**
     * Interrupts {@code slug}'s install task, if it's still {@code INSTALLING} - the "Annuler"
     * click. Doesn't clear the entry itself: the task's own {@code finally} block does that via
     * {@link #clearIfInstalling}, once it has actually observed the cancellation.
     */
    public void cancelInstalling(String slug) {
        InstanceState state = states.get(slug);
        if (state != null && state.activity() == InstanceActivity.INSTALLING && state.installTask() != null) {
            state.installTask().cancel(true);
        }
    }

    /**
     * Clears {@code slug}'s entry, but only if it's still the {@code INSTALLING} episode - a
     * no-op if it already moved on to {@code RUNNING} (a successful install immediately followed
     * by launching the game) or was never this slug's to begin with. Called wherever an
     * install/repair ends without ever reaching {@link #transitionToRunning} (cancelled or
     * failed).
     */
    public void clearIfInstalling(String slug) {
        states.computeIfPresent(slug, (s, state) -> state.activity() == InstanceActivity.INSTALLING ? null : state);
    }

    /**
     * Clears {@code slug}'s entry, but only if it's still tracking {@code process} - a no-op if a
     * newer episode (a fresh install/launch started right after this one exited) already replaced
     * it. Called from the game process's exit callback.
     */
    public void clearIfProcessMatches(String slug, Process process) {
        states.computeIfPresent(slug, (s, state) -> state.gameProcess() == process ? null : state);
    }

    /** {@code slug}'s current activity, if any - what a freshly (re)built page/card restores itself from. */
    public Optional<InstanceState> get(String slug) {
        return Optional.ofNullable(states.get(slug));
    }

    /**
     * Whether any modpack, anywhere, has activity right now - the coarse guard behind the "Migrer"
     * and "Nettoyer" actions, which move/relink/delete files across every instance at once and
     * can't safely run while any one of them is mid-write. Unlike every other method here, this
     * one is deliberately global rather than per-slug: those two actions have nothing to do with
     * any single modpack.
     */
    public boolean isAnyActive() {
        return !states.isEmpty();
    }

    /**
     * The slug of whichever modpack is genuinely {@code RUNNING} right now, if any - best-effort
     * context attached to a manual "Signaler un bug" report. Arbitrary among several: this is
     * metadata for a human reading the report, not a correctness-sensitive choice.
     */
    public Optional<String> anyRunningSlug() {
        return states.values().stream()
                .filter(state -> state.activity() == InstanceActivity.RUNNING)
                .map(InstanceState::slug)
                .findAny();
    }
}
