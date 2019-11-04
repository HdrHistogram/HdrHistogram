/**
 * HistogramPerfTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import org.junit.jupiter.api.Test;

/**
 * JUnit test for {@link org.HdrHistogram.Histogram}
 */
public class HistogramPerfTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
    static final int numberOfSignificantValueDigits = 3;
    static final long testValueLevel = 12340;
    static final long warmupLoopLength = 50000;
    static final long rawTimingLoopCount = 800000000L;
    static final long rawDoubleTimingLoopCount = 300000000L;
    static final long singleWriterIntervalTimingLoopCount = 100000000L;
    static final long singleWriterDoubleIntervalTimingLoopCount = 100000000L;
    static final long intervalTimingLoopCount = 40000000L;
    static final long synchronizedTimingLoopCount = 180000000L;
    static final long atomicTimingLoopCount = 80000000L;
    static final long concurrentTimingLoopCount = 50000000L;

    void recordLoop(AbstractHistogram histogram, long loopCount) {
        for (long i = 0; i < loopCount; i++)
            histogram.recordValue(testValueLevel + (i & 0x8000));
    }

    void recordLoopWithExpectedInterval(AbstractHistogram histogram, long loopCount, long expectedInterval) {
        for (long i = 0; i < loopCount; i++)
            histogram.recordValueWithExpectedInterval(testValueLevel + (i & 0x8000), expectedInterval);
    }

    void recordLoopWithExpectedInterval(Recorder histogram, long loopCount, long expectedInterval) {
        for (long i = 0; i < loopCount; i++)
            histogram.recordValueWithExpectedInterval(testValueLevel + (i & 0x8000), expectedInterval);
    }

    void recordLoopWithExpectedInterval(SingleWriterRecorder histogram, long loopCount, long expectedInterval) {
        for (long i = 0; i < loopCount; i++)
            histogram.recordValueWithExpectedInterval(testValueLevel + (i & 0x8000), expectedInterval);
    }

    void recordLoopWithExpectedInterval(DoubleRecorder histogram, long loopCount, long expectedInterval) {
        for (long i = 0; i < loopCount; i++)
            histogram.recordValueWithExpectedInterval(testValueLevel + (i & 0x8000), expectedInterval);
    }

    void recordLoopWithExpectedInterval(SingleWriterDoubleRecorder histogram, long loopCount, long expectedInterval) {
        for (long i = 0; i < loopCount; i++)
            histogram.recordValueWithExpectedInterval(testValueLevel + (i & 0x8000), expectedInterval);
    }

    void recordLoopDoubleWithExpectedInterval(DoubleHistogram histogram, long loopCount, double expectedInterval) {
        for (long i = 0; i < loopCount; i++)
            histogram.recordValueWithExpectedInterval(testValueLevel + (i & 0x8000), expectedInterval);
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

    public void testRawRecordingSpeedSingleValue(String label, AbstractHistogram histogram, long timingLoopCount) throws Exception {
        System.out.println("\nTiming recording speed with single value per recording:");
        // Warm up:
        long startTime = System.nanoTime();
        recordLoop(histogram, warmupLoopLength);
        long endTime = System.nanoTime();
        long deltaUsec = (endTime - startTime) / 1000L;
        long rate = 1000000 * warmupLoopLength / deltaUsec;
        System.out.println(label + "Warmup: " + warmupLoopLength + " value recordings completed in " +
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
        System.out.println(label + "Hot code timing:");
        System.out.println(label + timingLoopCount + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        rate = 1000000 * histogram.getTotalCount() / deltaUsec;
        System.out.println(label + histogram.getTotalCount() + " raw recorded entries completed in " +
                deltaUsec + " usec, rate = " + rate + " recorded values per sec.");
    }

    public void testRawRecordingSpeedAtExpectedInterval(String label, AbstractHistogram histogram,
                                                        long expectedInterval, long timingLoopCount) throws Exception {
        System.out.println("\nTiming recording speed with expectedInterval = " + expectedInterval + " :");
        // Warm up:
        long startTime = System.nanoTime();
        recordLoopWithExpectedInterval(histogram, warmupLoopLength, expectedInterval);
        long endTime = System.nanoTime();
        long deltaUsec = (endTime - startTime) / 1000L;
        long rate = 1000000 * warmupLoopLength / deltaUsec;
        System.out.println(label + "Warmup: " + warmupLoopLength + " value recordings completed in " +
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
        System.out.println(label + "Hot code timing:");
        System.out.println(label + timingLoopCount + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        rate = 1000000 * histogram.getTotalCount() / deltaUsec;
        System.out.println(label + histogram.getTotalCount() + " raw recorded entries completed in " +
                deltaUsec + " usec, rate = " + rate + " recorded values per sec.");
    }

    public void testRawRecordingSpeedAtExpectedInterval(String label, Recorder intervalHistogram,
                                                        long expectedInterval, long timingLoopCount) throws Exception {
        System.out.println("\nTiming recording speed with expectedInterval = " + expectedInterval + " :");
        // Warm up:
        long startTime = System.nanoTime();
        recordLoopWithExpectedInterval(intervalHistogram, warmupLoopLength, expectedInterval);
        long endTime = System.nanoTime();
        long deltaUsec = (endTime - startTime) / 1000L;
        long rate = 1000000 * warmupLoopLength / deltaUsec;
        System.out.println(label + "Warmup: " + warmupLoopLength + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        intervalHistogram.reset();
        // Wait a bit to make sure compiler had a cache to do it's stuff:
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        startTime = System.nanoTime();
        recordLoopWithExpectedInterval(intervalHistogram, timingLoopCount, expectedInterval);
        endTime = System.nanoTime();
        deltaUsec = (endTime - startTime) / 1000L;
        rate = 1000000 * timingLoopCount / deltaUsec;
        System.out.println(label + "Hot code timing:");
        System.out.println(label + timingLoopCount + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        Histogram histogram = intervalHistogram.getIntervalHistogram();
        rate = 1000000 * histogram.getTotalCount() / deltaUsec;
        System.out.println(label + histogram.getTotalCount() + " raw recorded entries completed in " +
                deltaUsec + " usec, rate = " + rate + " recorded values per sec.");
    }

    public void testRawRecordingSpeedAtExpectedInterval(String label, SingleWriterRecorder intervalHistogram,
                                                        long expectedInterval, long timingLoopCount) throws Exception {
        System.out.println("\nTiming recording speed with expectedInterval = " + expectedInterval + " :");
        // Warm up:
        long startTime = System.nanoTime();
        recordLoopWithExpectedInterval(intervalHistogram, warmupLoopLength, expectedInterval);
        long endTime = System.nanoTime();
        long deltaUsec = (endTime - startTime) / 1000L;
        long rate = 1000000 * warmupLoopLength / deltaUsec;
        System.out.println(label + "Warmup: " + warmupLoopLength + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        intervalHistogram.reset();
        // Wait a bit to make sure compiler had a cache to do it's stuff:
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        startTime = System.nanoTime();
        recordLoopWithExpectedInterval(intervalHistogram, timingLoopCount, expectedInterval);
        endTime = System.nanoTime();
        deltaUsec = (endTime - startTime) / 1000L;
        rate = 1000000 * timingLoopCount / deltaUsec;
        System.out.println(label + "Hot code timing:");
        System.out.println(label + timingLoopCount + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        Histogram histogram = intervalHistogram.getIntervalHistogram();
        rate = 1000000 * histogram.getTotalCount() / deltaUsec;
        System.out.println(label + histogram.getTotalCount() + " raw recorded entries completed in " +
                deltaUsec + " usec, rate = " + rate + " recorded values per sec.");
    }

    public void testRawRecordingSpeedAtExpectedInterval(String label, SingleWriterDoubleRecorder intervalHistogram,
                                                        long expectedInterval, long timingLoopCount) throws Exception {
        System.out.println("\nTiming recording speed with expectedInterval = " + expectedInterval + " :");
        // Warm up:
        long startTime = System.nanoTime();
        recordLoopWithExpectedInterval(intervalHistogram, warmupLoopLength, expectedInterval);
        long endTime = System.nanoTime();
        long deltaUsec = (endTime - startTime) / 1000L;
        long rate = 1000000 * warmupLoopLength / deltaUsec;
        System.out.println(label + "Warmup: " + warmupLoopLength + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        intervalHistogram.reset();
        // Wait a bit to make sure compiler had a cache to do it's stuff:
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        startTime = System.nanoTime();
        recordLoopWithExpectedInterval(intervalHistogram, timingLoopCount, expectedInterval);
        endTime = System.nanoTime();
        deltaUsec = (endTime - startTime) / 1000L;
        rate = 1000000 * timingLoopCount / deltaUsec;
        System.out.println(label + "Hot code timing:");
        System.out.println(label + timingLoopCount + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        DoubleHistogram histogram = intervalHistogram.getIntervalHistogram();
        rate = 1000000 * histogram.getTotalCount() / deltaUsec;
        System.out.println(label + histogram.getTotalCount() + " raw recorded entries completed in " +
                deltaUsec + " usec, rate = " + rate + " recorded values per sec.");
    }

    public void testRawRecordingSpeedAtExpectedInterval(String label, DoubleRecorder intervalHistogram,
                                                        long expectedInterval, long timingLoopCount) throws Exception {
        System.out.println("\nTiming recording speed with expectedInterval = " + expectedInterval + " :");
        // Warm up:
        long startTime = System.nanoTime();
        recordLoopWithExpectedInterval(intervalHistogram, warmupLoopLength, expectedInterval);
        long endTime = System.nanoTime();
        long deltaUsec = (endTime - startTime) / 1000L;
        long rate = 1000000 * warmupLoopLength / deltaUsec;
        System.out.println(label + "Warmup: " + warmupLoopLength + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        intervalHistogram.reset();
        // Wait a bit to make sure compiler had a cache to do it's stuff:
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        startTime = System.nanoTime();
        recordLoopWithExpectedInterval(intervalHistogram, timingLoopCount, expectedInterval);
        endTime = System.nanoTime();
        deltaUsec = (endTime - startTime) / 1000L;
        rate = 1000000 * timingLoopCount / deltaUsec;
        System.out.println(label + "Hot code timing:");
        System.out.println(label + timingLoopCount + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        DoubleHistogram histogram = intervalHistogram.getIntervalHistogram();
        rate = 1000000 * histogram.getTotalCount() / deltaUsec;
        System.out.println(label + histogram.getTotalCount() + " raw recorded entries completed in " +
                deltaUsec + " usec, rate = " + rate + " recorded values per sec.");
    }

    public void testRawDoubleRecordingSpeedAtExpectedInterval(String label, DoubleHistogram histogram,
                                                        long expectedInterval, long timingLoopCount) throws Exception {
        System.out.println("\nTiming recording speed with expectedInterval = " + expectedInterval + " :");
        // Warm up:
        long startTime = System.nanoTime();
        recordLoopDoubleWithExpectedInterval(histogram, warmupLoopLength, expectedInterval);
        long endTime = System.nanoTime();
        long deltaUsec = (endTime - startTime) / 1000L;
        long rate = 1000000 * warmupLoopLength / deltaUsec;
        System.out.println(label + "Warmup: " + warmupLoopLength + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        histogram.reset();
        // Wait a bit to make sure compiler had a cache to do it's stuff:
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        startTime = System.nanoTime();
        recordLoopDoubleWithExpectedInterval(histogram, timingLoopCount, expectedInterval);
        endTime = System.nanoTime();
        deltaUsec = (endTime - startTime) / 1000L;
        rate = 1000000 * timingLoopCount / deltaUsec;
        System.out.println(label + "Hot code timing:");
        System.out.println(label + timingLoopCount + " value recordings completed in " +
                deltaUsec + " usec, rate = " + rate + " value recording calls per sec.");
        rate = 1000000 * histogram.getTotalCount() / deltaUsec;
        System.out.println(label + histogram.getTotalCount() + " raw recorded entries completed in " +
                deltaUsec + " usec, rate = " + rate + " recorded values per sec.");
    }

    @Test
    public void testRawRecordingSpeedSingleValue() throws Exception {
        AbstractHistogram histogram;
        histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming Histogram:");
        testRawRecordingSpeedSingleValue("Histogram: ", histogram, rawTimingLoopCount);
    }

    @Test
    public void testRawRecordingSpeed() throws Exception {
        AbstractHistogram histogram;
        histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming Histogram:");
        testRawRecordingSpeedAtExpectedInterval("Histogram: ", histogram, 1000000000, rawTimingLoopCount);
    }

    @Test
    public void testRawPackedRecordingSpeedSingleValue() throws Exception {
        AbstractHistogram histogram;
        histogram = new PackedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming PackedHistogram:");
        testRawRecordingSpeedSingleValue("PackedHistogram: ", histogram, rawTimingLoopCount);
    }

    @Test
    public void testRawPackedRecordingSpeed() throws Exception {
        AbstractHistogram histogram;
        histogram = new PackedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming PackedHistogram:");
        testRawRecordingSpeedAtExpectedInterval("PackedHistogram: ", histogram, 1000000000, rawTimingLoopCount);
    }

    @Test
    public void testSingleWriterIntervalRecordingSpeed() throws Exception {
        SingleWriterRecorder histogramRecorder;
        histogramRecorder = new SingleWriterRecorder(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming SingleWriterIntervalHistogramRecorder:");
        testRawRecordingSpeedAtExpectedInterval("SingleWriterRecorder: ", histogramRecorder, 1000000000, singleWriterIntervalTimingLoopCount);
    }

    @Test
    public void testIntervalRecordingSpeed() throws Exception {
        Recorder histogramRecorder;
        histogramRecorder = new Recorder(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming IntervalHistogramRecorder:");
        testRawRecordingSpeedAtExpectedInterval("Recorder: ", histogramRecorder, 1000000000, intervalTimingLoopCount);
    }

    @Test
    public void testRawDoubleRecordingSpeed() throws Exception {
        DoubleHistogram histogram;
        histogram = new DoubleHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming DoubleHistogram:");
        testRawDoubleRecordingSpeedAtExpectedInterval("DoubleHistogram: ", histogram, 1000000000, rawDoubleTimingLoopCount);
    }

    @Test
    public void testDoubleIntervalRecordingSpeed() throws Exception {
        DoubleRecorder histogramRecorder;
        histogramRecorder = new DoubleRecorder(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming IntervalDoubleHistogramRecorder:");
        testRawRecordingSpeedAtExpectedInterval("DoubleRecorder: ", histogramRecorder, 1000000000, intervalTimingLoopCount);
    }

    @Test
    public void testSingleWriterDoubleIntervalRecordingSpeed() throws Exception {
        SingleWriterDoubleRecorder histogramRecorder;
        histogramRecorder = new SingleWriterDoubleRecorder(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming SingleWriterIntervalDoubleHistogramRecorder:");
        testRawRecordingSpeedAtExpectedInterval("SingleWriterDoubleRecorder: ", histogramRecorder, 1000000000, singleWriterDoubleIntervalTimingLoopCount);
    }

    @Test
    public void testRawSyncronizedRecordingSpeed() throws Exception {
        AbstractHistogram histogram;
        histogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming SynchronizedHistogram:");
        testRawRecordingSpeedAtExpectedInterval("SynchronizedHistogram: ", histogram, 1000000000, synchronizedTimingLoopCount);
    }

    @Test
    public void testRawAtomicRecordingSpeed() throws Exception {
        AbstractHistogram histogram;
        histogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming AtomicHistogram:");
        testRawRecordingSpeedAtExpectedInterval("AtomicHistogram: ", histogram, 1000000000, atomicTimingLoopCount);
    }


    @Test
    public void testRawConcurrentRecordingSpeed() throws Exception {
        AbstractHistogram histogram;
        histogram = new ConcurrentHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        System.out.println("\n\nTiming ConcurrentHistogram:");
        testRawRecordingSpeedAtExpectedInterval("AtomicHistogram: ", histogram, 1000000000, concurrentTimingLoopCount);
    }

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
        LeadingZerosSpeedLoop(rawTimingLoopCount);
        endTime = System.nanoTime();
        deltaUsec = (endTime - startTime) / 1000L;
        rate = 1000000 * rawTimingLoopCount / deltaUsec;
        System.out.println("Hot code timing:");
        System.out.println(rawTimingLoopCount + " Leading Zero loops completed in " +
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
