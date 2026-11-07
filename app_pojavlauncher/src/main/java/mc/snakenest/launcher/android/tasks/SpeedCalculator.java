package mc.snakenest.launcher.android.tasks;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Moving-average bytes/second from a monotonic clock, including consecutive same-tick updates. */
public class SpeedCalculator {
    private final LongSupplier clock;
    private final double[] samples;
    private long lastNanos,lastBytes;
    private int index,count;
    private double sum;

    public SpeedCalculator(){this(64);}
    public SpeedCalculator(int depth){this(depth,System::nanoTime);}
    /** Injectable monotonic clock for deterministic transfer tests. */
    public SpeedCalculator(int depth,LongSupplier clock) {
        if(depth<1)throw new IllegalArgumentException("Average depth must be positive");
        this.clock=Objects.requireNonNull(clock);samples=new double[depth];lastNanos=clock.getAsLong();
    }
    public double feed(long bytes) {
        long now=clock.getAsLong(),elapsed=now-lastNanos;
        bytes=Math.max(0,bytes);
        if(bytes<lastBytes || elapsed<0) {
            Arrays.fill(samples,0);index=0;count=0;sum=0;lastBytes=bytes;lastNanos=now;return 0;
        }
        // Retain the byte delta until time advances: no division by zero and no lost bytes.
        if(elapsed==0)return count==0?0:Math.max(0,sum/count);
        double speed=(bytes-lastBytes)*1_000_000_000d/elapsed;
        lastBytes=bytes;lastNanos=now;
        if(!Double.isFinite(speed)||speed<0)speed=0;
        sum-=samples[index];sum+=speed;samples[index]=speed;
        index=(index+1)%samples.length;if(count<samples.length)count++;
        return Math.max(0,sum/count);
    }
}
