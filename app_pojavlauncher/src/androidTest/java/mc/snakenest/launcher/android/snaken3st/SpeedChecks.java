package mc.snakenest.launcher.android.snaken3st;

import java.util.concurrent.atomic.AtomicLong;
import mc.snakenest.launcher.android.tasks.SpeedCalculator;

final class SpeedChecks {
    static int run() {
        AtomicLong time=new AtomicLong();SpeedCalculator speed=new SpeedCalculator(2,time::get);int checks=0;
        check(speed.feed(0)==0);checks++;
        check(speed.feed(1024)==0);checks++;
        time.set(1_000_000_000L);check(speed.feed(1024)==1024);checks++;
        check(speed.feed(2048)==1024);checks++;
        time.set(2_000_000_000L);check(speed.feed(3072)==1536);checks++;
        time.set(3_000_000_000L);check(speed.feed(0)==0);checks++;
        time.set(2_000_000_000L);check(speed.feed(100)==0);checks++;
        time.set(4_000_000_000L);double resumed=speed.feed(100);check(Double.isFinite(resumed)&&resumed>=0);checks++;
        return checks;
    }
    private static void check(boolean value){if(!value)throw new AssertionError("Transfer speed check failed");}
}
