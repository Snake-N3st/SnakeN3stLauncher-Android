package mc.snakenest.launcher.util;

import java.util.Collection;
import java.util.concurrent.*;

/** A bounded batch: every worker is stopped before success, failure or cancellation is returned. */
public final class ParallelDownloadBatch {
    private ParallelDownloadBatch() { }
    @FunctionalInterface public interface Work { void run() throws Exception; }
    public static void run(Collection<? extends Work> jobs,int concurrency,Runnable progress) throws Exception {
        if(jobs.isEmpty())return;
        ExecutorService pool=Executors.newFixedThreadPool(Math.max(1,Math.min(16,concurrency)),r->{Thread t=new Thread(r,"sn3-game-download");t.setDaemon(true);return t;});
        CompletionService<Void> completed=new ExecutorCompletionService<>(pool);
        try {
            for(Work work:jobs)completed.submit(()->{work.run();return null;});
            pool.shutdown();
            for(int count=0;count<jobs.size();) {
                Future<Void> done=completed.poll(100,TimeUnit.MILLISECONDS);
                if(done!=null) {try{done.get();}catch(ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof Exception e)throw e;if(cause instanceof Error e)throw e;throw new RuntimeException(cause);}count++;}
                progress.run();
            }
        } finally {
            pool.shutdownNow();
            boolean interrupted=Thread.interrupted();
            while(!pool.isTerminated())try{pool.awaitTermination(1,TimeUnit.SECONDS);}catch(InterruptedException ignored){interrupted=true;}
            if(interrupted)Thread.currentThread().interrupt();
        }
    }
}
