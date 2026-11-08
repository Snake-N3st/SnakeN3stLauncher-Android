package mc.snakenest.launcher.util;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static java.time.Duration.ofSeconds;

class ParallelDownloadBatchTest {
    @Test void runsIndependentlyWithABoundedNumberOfWorkers() {
        assertTimeoutPreemptively(ofSeconds(4),()->{
            AtomicInteger active=new AtomicInteger(),peak=new AtomicInteger(),finished=new AtomicInteger();CountDownLatch first=new CountDownLatch(2);
            List<ParallelDownloadBatch.Work> jobs=new ArrayList<>();
            for(int i=0;i<8;i++)jobs.add(()->{int n=active.incrementAndGet();peak.accumulateAndGet(n,Math::max);first.countDown();try{assertTrue(first.await(1,TimeUnit.SECONDS));Thread.sleep(15);finished.incrementAndGet();}finally{active.decrementAndGet();}});
            ParallelDownloadBatch.run(jobs,2,()->{});
            assertEquals(2,peak.get());assertEquals(8,finished.get());assertEquals(0,active.get());
        });
    }
    @Test void stopsOtherWorkersBeforeReturningAFailure() {
        assertTimeoutPreemptively(ofSeconds(4),()->{
            AtomicInteger active=new AtomicInteger();CountDownLatch started=new CountDownLatch(1);
            var jobs=List.<ParallelDownloadBatch.Work>of(()->{active.incrementAndGet();started.countDown();try{Thread.sleep(10000);}finally{active.decrementAndGet();}},()->{assertTrue(started.await(1,TimeUnit.SECONDS));throw new IOException("fixture failure");});
            assertThrows(IOException.class,()->ParallelDownloadBatch.run(jobs,2,()->{}));assertEquals(0,active.get());
        });
    }
    @Test void cancellationWaitsForWorkersToExit() {
        assertTimeoutPreemptively(ofSeconds(4),()->{
            CountDownLatch started=new CountDownLatch(1);AtomicInteger active=new AtomicInteger();AtomicBoolean cancelled=new AtomicBoolean();
            Thread caller=new Thread(()->{try{ParallelDownloadBatch.run(List.of(()->{active.incrementAndGet();started.countDown();try{Thread.sleep(10000);}finally{active.decrementAndGet();}}),2,()->{});}catch(InterruptedException expected){cancelled.set(true);}catch(Exception failure){throw new RuntimeException(failure);}});
            caller.start();assertTrue(started.await(1,TimeUnit.SECONDS));caller.interrupt();caller.join(2000);assertFalse(caller.isAlive());assertTrue(cancelled.get());assertEquals(0,active.get());
        });
    }
    @Test void emptyInstallDoesNotCreateAZeroCapacityQueue() throws Exception {ParallelDownloadBatch.run(List.of(),8,()->fail("Empty install needs no progress callback"));}
}
