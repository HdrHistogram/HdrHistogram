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
 * JUnit test for {@link org.HdrHistogram.Histogram}
 */
public class HistogramAutosizingTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units

    @Test
    public void testHistogramAutoSizingEdges() throws Exception {
        Histogram histogram = new Histogram(3);
        histogram.recordValue((1L << 62) - 1);
        Assert.assertEquals(52, histogram.bucketCount);
        Assert.assertEquals(54272, histogram.countsArrayLength);
        histogram.recordValue(Long.MAX_VALUE);
        Assert.assertEquals(53, histogram.bucketCount);
        Assert.assertEquals(55296, histogram.countsArrayLength);
    }

    @Test
    public void testDoubleHistogramAutoSizingEdges() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(3);
        histogram.recordValue(1);
        histogram.recordValue(1L << 48);
        histogram.recordValue((1L << 52) - 1);
        Assert.assertEquals(52, histogram.integerValuesHistogram.bucketCount);
        Assert.assertEquals(54272, histogram.integerValuesHistogram.countsArrayLength);
        histogram.recordValue((1L << 53) - 1);
        Assert.assertEquals(53, histogram.integerValuesHistogram.bucketCount);
        Assert.assertEquals(55296, histogram.integerValuesHistogram.countsArrayLength);

        DoubleHistogram histogram2 = new DoubleHistogram(2);
        histogram2.recordValue(1);
        histogram2.recordValue(1L << 48);
        histogram2.recordValue((1L << 54) - 1);
        Assert.assertEquals(55, histogram2.integerValuesHistogram.bucketCount);
        Assert.assertEquals(7168, histogram2.integerValuesHistogram.countsArrayLength);
        histogram2.recordValue((1L << 55) - 1);
        Assert.assertEquals(56, histogram2.integerValuesHistogram.bucketCount);
        Assert.assertEquals(7296, histogram2.integerValuesHistogram.countsArrayLength);

        DoubleHistogram histogram3 = new DoubleHistogram(2);
        histogram3.recordValue(1E50);
        histogram3.recordValue((1L << 48) * 1E50);
        histogram3.recordValue(((1L << 54) - 1) * 1E50);
        Assert.assertEquals(55, histogram3.integerValuesHistogram.bucketCount);
        Assert.assertEquals(7168, histogram3.integerValuesHistogram.countsArrayLength);
        histogram3.recordValue(((1L << 55) - 1) * 1E50);
        Assert.assertEquals(56, histogram3.integerValuesHistogram.bucketCount);
        Assert.assertEquals(7296, histogram3.integerValuesHistogram.countsArrayLength);
    }

    @Test
    public void testHistogramAutoSizing() throws Exception {
        Histogram histogram = new Histogram(3);
        for (int i = 0; i < 63; i++) {
            long value = 1L << i;
            histogram.recordValue(value);
        }
        Assert.assertEquals(53, histogram.bucketCount);
        Assert.assertEquals(55296, histogram.countsArrayLength);
    }

    @Test
    public void testConcurrentHistogramAutoSizing() throws Exception {
        ConcurrentHistogram histogram = new ConcurrentHistogram(3);
        for (int i = 9; i < 63; i++) {
            long value = 1L << i;
            histogram.recordValue(value);
        }
    }

    @Test
    public void testSynchronizedHistogramAutoSizing() throws Exception {
        SynchronizedHistogram histogram = new SynchronizedHistogram(3);
        for (int i = 0; i < 63; i++) {
            long value = 1L << i;
            histogram.recordValue(value);
        }
    }

    @Test
    public void testIntCountsHistogramAutoSizing() throws Exception {
        IntCountsHistogram histogram = new IntCountsHistogram(3);
        for (int i = 0; i < 63; i++) {
            long value = 1L << i;
            histogram.recordValue(value);
        }
    }

    @Test
    public void testShortCountsHistogramAutoSizing() throws Exception {
        ShortCountsHistogram histogram = new ShortCountsHistogram(3);
        for (int i = 0; i < 63; i++) {
            long value = 1L << i;
            histogram.recordValue(value);
        }
    }

    @Test
    public void testDoubleHistogramAutoSizingUp() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(2);
        for (int i = 0; i < 55; i++) {
            double value = 1L << i;
            histogram.recordValue(value);
        }
    }

    @Test
    public void testDoubleHistogramAutoSizingDown() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(2);
        for (int i = 0; i < 56; i++) {
            double value = (1L << 45) * 1.0 / (1L << i);
            histogram.recordValue(value);
        }
    }

    @Test
    public void testConcurrentDoubleHistogramAutoSizingDown() throws Exception {
        ConcurrentDoubleHistogram histogram = new ConcurrentDoubleHistogram(2);
        for (int i = 0; i < 56; i++) {
            double value = (1L << 45) * 1.0 / (1L << i);
            histogram.recordValue(value);
        }
    }

    @Test
    public void testSynchronizedDoubleHistogramAutoSizingDown() throws Exception {
        SynchronizedDoubleHistogram histogram = new SynchronizedDoubleHistogram(2);
        for (int i = 0; i < 56; i++) {
            double value = (1L << 45) * 1.0 / (1L << i);
            histogram.recordValue(value);
        }
    }

    @Test
    public void testAutoSizingAdd() throws Exception {
        Histogram histogram1 = new Histogram(2);
        Histogram histogram2 = new Histogram(2);

        histogram1.recordValue(1000L);
        histogram1.recordValue(1000000000L);

        histogram2.add(histogram1);

        Assert.assertTrue("Max should be equivalent to 1000000000L",
                histogram2.valuesAreEquivalent(histogram2.getMaxValue(), 1000000000L)
                );
    }

    @Test
    public void testAutoSizingAcrossContinuousRange() throws Exception {
        Histogram histogram = new Histogram(2);

        for (long i = 0; i < 10000000L; i++) {
            histogram.recordValue(i);
        }
    }

    @Test
    public void testAutoSizingAcrossContinuousRangeConcurrent() throws Exception {
        Histogram histogram = new ConcurrentHistogram(2);

        for (long i = 0; i < 1000000L; i++) {
            histogram.recordValue(i);
        }
    }

    @Test
    public void testAutoSizingAddDouble() throws Exception {
        DoubleHistogram histogram1 = new DoubleHistogram(2);
        DoubleHistogram histogram2 = new DoubleHistogram(2);

        histogram1.recordValue(1000L);
        histogram1.recordValue(1000000000L);

        histogram2.add(histogram1);

        Assert.assertTrue("Max should be equivalent to 1000000000L",
                histogram2.valuesAreEquivalent(histogram2.getMaxValue(), 1000000000L)
        );
    }

}
