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
    static final long timingLoopCount = 400000000L;

    void recordLoopWithExpectedInterval(Histogram histogram, long loopCount, long expectedInterval) {
        for (long i = 0; i < loopCount; i++)
            histogram.recordValue(testValueLevel + (i & 0x8000), expectedInterval);
    }

    long LeadingZerosSpeedLoop(long loopCount) {
        long sum = 0;
        for (long i = 0; i < loopCount; i++) {
            // long val = testValueLevel + (i & 0x8000);
            long val = testValueLevel;
            sum += Long.numberOfLeadingZeros(val);
            sum += Long.numberOfLeadingZeros(val);
            sum += Long.numberOfLeadingZeros(val);
            sum += Long.numberOfLeadingZeros(val);
            sum += Long.numberOfLeadingZeros(val);
            sum += Long.numberOfLeadingZeros(val);
            sum += Long.numberOfLeadingZeros(val);
            sum += Long.numberOfLeadingZeros(val);
        }
        return sum;
    }

    public void testRawRecordingSpeedAtExpectedInterval(long expectedInterval) throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\nTiming recording speed with expectedInterval = " + expectedInterval + " :");
        // Warm up:
        long startTime = System.nanoTime();
        recordLoopWithExpectedInterval(histogram, warmupLoopLength, expectedInterval);
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
        recordLoopWithExpectedInterval(histogram, timingLoopCount, expectedInterval);
        endTime = System.nanoTime();
        deltaUsec = (endTime - startTime) / 1000L;
        rate = 1000000 * timingLoopCount / deltaUsec;
        System.out.println("Hot code timing:");
        System.out.println(timingLoopCount + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        rate = 1000000 * histogram.getHistogramData().getTotalCount() / deltaUsec;
        System.out.println(histogram.getHistogramData().getTotalCount() + " raw recorded entries completed in " +
                deltaUsec + " usec, rate = " + rate + " recorded values per sec.");
    }

    @Test
    public void testRawRecordingSpeed() throws Exception {
        testRawRecordingSpeedAtExpectedInterval(1000000000);
        // testRawRecordingSpeedAtExpectedInterval(10000);
    }

    @Test
    public void testLeadingZerosSpeed() throws Exception {
        System.out.println("\nTiming LeadingZerosSpeed :");
        long startTime = System.nanoTime();
        LeadingZerosSpeedLoop(warmupLoopLength);
        long endTime = System.nanoTime();
        long deltaUsec = (endTime - startTime) / 1000L;
        long rate = 1000000 * warmupLoopLength / deltaUsec;
        System.out.println("Warmup:\n" + warmupLoopLength + " Leading Zero loops completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        // Wait a bit to make sure compiler had a cache to do it's stuff:
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        startTime = System.nanoTime();
        LeadingZerosSpeedLoop(timingLoopCount);
        endTime = System.nanoTime();
        deltaUsec = (endTime - startTime) / 1000L;
        rate = 1000000 * timingLoopCount / deltaUsec;
        System.out.println("Hot code timing:");
        System.out.println(timingLoopCount + " Leading Zero loops completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
    }

    public static void main(String[] args) {
        try {
            HistogramPerfTest test = new HistogramPerfTest();
            test.testLeadingZerosSpeed();
            Thread.sleep(1000000);
        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }
    }

}
