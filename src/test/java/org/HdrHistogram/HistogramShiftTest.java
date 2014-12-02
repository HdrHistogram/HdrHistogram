/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import org.junit.Assert;
import org.junit.Test;

/**
 * JUnit test for {@link Histogram}
 */
public class HistogramShiftTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units

    @Test
    public void testHistogramShift() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, 3);
        testShiftLowestBucket(histogram);
        testShiftNonLowestBucket(histogram);
    }

    @Test
    public void testIntHistogramShift() throws Exception {
        IntCountsHistogram intCountsHistogram = new IntCountsHistogram(highestTrackableValue, 3);
        testShiftLowestBucket(intCountsHistogram);
        testShiftNonLowestBucket(intCountsHistogram);
    }

    @Test
    public void testShortHistogramShift() throws Exception {
        ShortCountsHistogram shortCountsHistogram = new ShortCountsHistogram(highestTrackableValue, 3);
        testShiftLowestBucket(shortCountsHistogram);
        testShiftNonLowestBucket(shortCountsHistogram);
    }

    @Test
    public void testSynchronizedHistogramShift() throws Exception {
        SynchronizedHistogram synchronizedHistogram = new SynchronizedHistogram(highestTrackableValue, 3);
        testShiftLowestBucket(synchronizedHistogram);
        testShiftNonLowestBucket(synchronizedHistogram);
    }

    @Test(expected = IllegalStateException.class)
    public void testAtomicHistogramShift() throws Exception {
        AtomicHistogram atomicHistogram = new AtomicHistogram(highestTrackableValue, 3);
        testShiftLowestBucket(atomicHistogram);
        testShiftNonLowestBucket(atomicHistogram);
    }

    @Test
    public void testConcurrentHistogramShift() throws Exception {
        ConcurrentHistogram concurrentHistogram = new ConcurrentHistogram(highestTrackableValue, 3);
        testShiftLowestBucket(concurrentHistogram);
        testShiftNonLowestBucket(concurrentHistogram);
    }

    void testShiftLowestBucket(AbstractHistogram histogram) {
        for (int shiftAmount = 0; shiftAmount < 10; shiftAmount++) {
            histogram.reset();
            histogram.recordValueWithCount(0, 500);
            histogram.recordValue(2);
            histogram.recordValue(4);
            histogram.recordValue(5);
            histogram.recordValue(511);
            histogram.recordValue(512);
            histogram.recordValue(1023);
            histogram.recordValue(1024);
            histogram.recordValue(1025);

            AbstractHistogram histogram2 = histogram.copy();

            histogram2.reset();
            histogram2.recordValueWithCount(0, 500);
            histogram2.recordValue(2 << shiftAmount);
            histogram2.recordValue(4 << shiftAmount);
            histogram2.recordValue(5 << shiftAmount);
            histogram2.recordValue(511 << shiftAmount);
            histogram2.recordValue(512 << shiftAmount);
            histogram2.recordValue(1023 << shiftAmount);
            histogram2.recordValue(1024 << shiftAmount);
            histogram2.recordValue(1025 << shiftAmount);

            histogram.shiftValuesLeft(shiftAmount);

            if (!histogram.equals(histogram2)) {
                System.out.println("Not Equal for shift of " + shiftAmount);
            }
            Assert.assertEquals(histogram, histogram2);
        }
    }

    void testShiftNonLowestBucket(AbstractHistogram histogram) {
        for (int shiftAmount = 0; shiftAmount < 10; shiftAmount++) {
            histogram.reset();
            histogram.recordValueWithCount(0, 500);
            histogram.recordValue(2 << 10);
            histogram.recordValue(4 << 10);
            histogram.recordValue(5 << 10);
            histogram.recordValue(511 << 10);
            histogram.recordValue(512 << 10);
            histogram.recordValue(1023 << 10);
            histogram.recordValue(1024 << 10);
            histogram.recordValue(1025 << 10);

            AbstractHistogram origHistogram = histogram.copy();
            AbstractHistogram histogram2 = histogram.copy();

            histogram2.reset();
            histogram2.recordValueWithCount(0, 500);
            histogram2.recordValue((2 << 10)  << shiftAmount);
            histogram2.recordValue((4 << 10) << shiftAmount);
            histogram2.recordValue((5 << 10) << shiftAmount);
            histogram2.recordValue((511 << 10) << shiftAmount);
            histogram2.recordValue((512 << 10) << shiftAmount);
            histogram2.recordValue((1023 << 10) << shiftAmount);
            histogram2.recordValue((1024 << 10) << shiftAmount);
            histogram2.recordValue((1025 << 10) << shiftAmount);

            histogram.shiftValuesLeft(shiftAmount);

            if (!histogram.equals(histogram2)) {
                System.out.println("Not Equal for shift of " + shiftAmount);
            }
            Assert.assertEquals(histogram, histogram2);

            histogram.shiftValuesRight(shiftAmount);

            Assert.assertEquals(histogram, origHistogram);
        }
    }
}
