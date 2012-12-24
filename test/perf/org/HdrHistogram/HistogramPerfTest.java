/**
 * HistogramPerfTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
 */

package perf.org.HdrHistogram;

import org.HdrHistogram.*;
import org.junit.*;

/**
 * JUnit test for {@link Histogram}
 */
public class HistogramPerfTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
    static final int numberOfSignificantValueDigits = 3;
    static final long testValueLevel = 12340;
    static final long warmupLoopLength = 50000;
    static final long timingLoopCount = 200000000L;

    void recordLoop(Histogram histogram, long loopCount) {
        for (long i = 0; i < loopCount; i++)
            histogram.recordValue(testValueLevel + (i & 0x80000), 10000);
    }

    @Test
    public void testRawRecordingSpeed() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        // Warm up:
        long startTime = System.nanoTime();
        recordLoop(histogram, warmupLoopLength);
        long endTime = System.nanoTime();
        long deltaUsec = (endTime - startTime) / 1000L;
        long rate = 1000000 * warmupLoopLength / deltaUsec;
        System.out.println("Warmup:\n" + warmupLoopLength + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        histogram.reset();
        // Wait a bit to make sure compiler had a cache to do it's stuff:
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        startTime = System.nanoTime();
        recordLoop(histogram, timingLoopCount);
        endTime = System.nanoTime();
        deltaUsec = (endTime - startTime) / 1000L;
        rate = 1000000 * timingLoopCount / deltaUsec;
        System.out.println("Timing:");
        System.out.println(timingLoopCount + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        rate = 1000000 * histogram.getHistogramData().getTotalCount() / deltaUsec;
        System.out.println(histogram.getHistogramData().getTotalCount() + " raw recorded entries completed in " +
                deltaUsec + " usec, rate = " + rate + " recorded values per sec.");
    }
}
