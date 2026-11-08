package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InstanceStateRegistryTest {

    @Test
    void twoDifferentSlugsHaveFullyIndependentActivity() {
        InstanceStateRegistry registry = new InstanceStateRegistry();

        assertTrue(registry.beginInstalling("pack-a"));
        Process processB = mock(Process.class);
        registry.transitionToRunning("pack-b", processB);

        assertEquals(InstanceActivity.INSTALLING, registry.get("pack-a").orElseThrow().activity());
        assertEquals(InstanceActivity.RUNNING, registry.get("pack-b").orElseThrow().activity());
    }

    @Test
    void beginInstallingFailsIfTheSlugAlreadyHasActivity() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        assertTrue(registry.beginInstalling("pack-a"));

        assertFalse(registry.beginInstalling("pack-a"), "a second install must not silently clobber the first one's tracking");
        assertEquals(InstanceActivity.INSTALLING, registry.get("pack-a").orElseThrow().activity());
    }

    @Test
    void beginInstallingForADifferentSlugStillSucceedsWhileAnotherIsBusy() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.transitionToRunning("pack-a", mock(Process.class));

        assertTrue(registry.beginInstalling("pack-b"), "an unrelated modpack's activity must never block another one");
    }

    @Test
    void attachInstallTaskAttachesTheFutureToTheBareSnapshot() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-a");
        Future<?> task = CompletableFuture.completedFuture(null);

        registry.attachInstallTask("pack-a", task);

        assertEquals(task, registry.get("pack-a").orElseThrow().installTask());
    }

    @Test
    void attachInstallTaskIsANoOpOnceTheSlugHasMovedOn() {
        // Simulates the install having already finished (and moved to RUNNING) by the time the
        // submitting thread gets back around to attaching the task - the exact race this method
        // exists to guard against.
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-a");
        registry.transitionToRunning("pack-a", mock(Process.class));

        registry.attachInstallTask("pack-a", CompletableFuture.completedFuture(null));

        assertEquals(InstanceActivity.RUNNING, registry.get("pack-a").orElseThrow().activity());
    }

    @Test
    void attachInstallTaskIsANoOpForAnUntrackedSlug() {
        InstanceStateRegistry registry = new InstanceStateRegistry();

        registry.attachInstallTask("never-started", CompletableFuture.completedFuture(null));

        assertTrue(registry.get("never-started").isEmpty());
    }

    @Test
    void beginStoppingTransitionsFromRunningAndReturnsTheProcess() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        Process process = mock(Process.class);
        registry.transitionToRunning("pack-a", process);

        Optional<Process> result = registry.beginStopping("pack-a");

        assertEquals(Optional.of(process), result);
        assertEquals(InstanceActivity.STOPPING, registry.get("pack-a").orElseThrow().activity());
    }

    @Test
    void beginStoppingIsANoOpUnlessCurrentlyRunning() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-a");

        assertTrue(registry.beginStopping("pack-a").isEmpty());
        assertEquals(InstanceActivity.INSTALLING, registry.get("pack-a").orElseThrow().activity());
    }

    @Test
    void beginStoppingOnAnUntrackedSlugIsEmpty() {
        InstanceStateRegistry registry = new InstanceStateRegistry();

        assertTrue(registry.beginStopping("never-started").isEmpty());
    }

    @Test
    void processForReturnsTheProcessWhileRunningOrStopping() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        Process process = mock(Process.class);
        registry.transitionToRunning("pack-a", process);
        assertEquals(Optional.of(process), registry.processFor("pack-a"));

        registry.beginStopping("pack-a");
        assertEquals(Optional.of(process), registry.processFor("pack-a"), "still killable after Arreter was already clicked once");
    }

    @Test
    void processForIsEmptyWhileOnlyInstalling() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-a");

        assertTrue(registry.processFor("pack-a").isEmpty());
    }

    @Test
    void cancelInstallingCancelsTheAttachedTaskOnlyWhileInstalling() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-a");
        @SuppressWarnings("unchecked")
        Future<Object> task = mock(Future.class);
        registry.attachInstallTask("pack-a", task);

        registry.cancelInstalling("pack-a");

        verify(task).cancel(true);
    }

    @Test
    void cancelInstallingIsANoOpWithoutAnAttachedTask() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-a");

        registry.cancelInstalling("pack-a"); // must not throw
    }

    @Test
    void cancelInstallingIsANoOpForADifferentActivity() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        @SuppressWarnings("unchecked")
        Future<Object> task = mock(Future.class);
        registry.transitionToRunning("pack-a", mock(Process.class));

        registry.cancelInstalling("pack-a");

        verifyNoInteractions(task);
    }

    @Test
    void clearIfInstallingRemovesTheEntryOnlyWhileStillInstalling() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-a");

        registry.clearIfInstalling("pack-a");

        assertTrue(registry.get("pack-a").isEmpty());
    }

    @Test
    void clearIfInstallingDoesNotClobberARunningEntry() {
        // A successful install immediately followed by launching the game: clearIfInstalling
        // still gets called (unconditionally, from a finally block) but must not erase the
        // RUNNING state that transitionToRunning already set.
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-a");
        registry.transitionToRunning("pack-a", mock(Process.class));

        registry.clearIfInstalling("pack-a");

        assertEquals(InstanceActivity.RUNNING, registry.get("pack-a").orElseThrow().activity());
    }

    @Test
    void clearIfProcessMatchesOnlyClearsWhenTheProcessIsStillTheSameOne() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        Process oldProcess = mock(Process.class);
        registry.transitionToRunning("pack-a", oldProcess);

        Process unrelatedProcess = mock(Process.class);
        registry.clearIfProcessMatches("pack-a", unrelatedProcess);
        assertTrue(registry.get("pack-a").isPresent(), "a stale exit callback for an old process must not clear a newer episode");

        registry.clearIfProcessMatches("pack-a", oldProcess);
        assertTrue(registry.get("pack-a").isEmpty());
    }

    @Test
    void isAnyActiveReflectsAnyTrackedSlugAtAll() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        assertFalse(registry.isAnyActive());

        registry.beginInstalling("pack-a");
        assertTrue(registry.isAnyActive());

        registry.clearIfInstalling("pack-a");
        assertFalse(registry.isAnyActive());
    }

    @Test
    void anyRunningSlugFindsARunningModpackAmongSeveral() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-installing");
        registry.transitionToRunning("pack-running", mock(Process.class));

        assertEquals(Optional.of("pack-running"), registry.anyRunningSlug());
    }

    @Test
    void anyRunningSlugIsEmptyWhenNothingIsRunning() {
        InstanceStateRegistry registry = new InstanceStateRegistry();
        registry.beginInstalling("pack-a");

        assertTrue(registry.anyRunningSlug().isEmpty());
    }
}
