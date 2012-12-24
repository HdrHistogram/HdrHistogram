/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
 */

package test.org.HdrHistogram;

import org.HdrHistogram.*;
import org.junit.*;

/**
 * JUnit test for {@link Histogram}
 */
public class HistogramTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
    static final int numberOfSignificantValueDigits = 3;
    // static final long testValueLevel = 12340;
    static final long testValueLevel = 4;

    @Test
    public void testConstructionArgumentRanges() throws Exception {
        Boolean thrown = false;
        Histogram histogram = null;

        try {
            // This should throw:
            histogram = new Histogram(1, numberOfSignificantValueDigits);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        Assert.assertEquals(histogram, null);

        thrown = false;
        try {
            // This should throw:
            histogram = new Histogram(highestTrackableValue, 6);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        Assert.assertEquals(histogram, null);

        thrown = false;
        try {
            // This should throw:
            histogram = new Histogram(highestTrackableValue, -1);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        Assert.assertEquals(histogram, null);
    }

    @Test
    public void testConstructionArgumentGets() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals(highestTrackableValue, histogram.getHighestTrackableValue());
        Assert.assertEquals(numberOfSignificantValueDigits, histogram.getNumberOfSignificantValueDigits());
    }

    @Test
    public void testGetEstimatedFootprintInBytes() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        // Estimated size is supposed to be:
        //      (log2RoundedUp((1.0 * highestTrackableValue) / largestValueWithSingleUnitResolution) + 2) *
        //      roundedUpToNearestPowerOf2(largestValueWithSingleUnitResolution) *
        //      8 bytes
        long expectedSize = 256 +
                ((8 *
                 ((long)(
                        Math.ceil(
                         Math.log(highestTrackableValue / (2 * Math.pow(10, numberOfSignificantValueDigits)))
                                 / Math.log(2)
                        )
                       + 2)) *
                    (1 << (64 - Long.numberOfLeadingZeros(2 * (long) Math.pow(10, numberOfSignificantValueDigits))))
                 ) / 2);
        Assert.assertEquals(expectedSize, histogram.getEstimatedFootprintInBytes());
    }

    @Test
    public void testRecordValue() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        Assert.assertEquals(1L, histogram.getHistogramData().getCountAtValue(testValueLevel));
        Assert.assertEquals(1L, histogram.getHistogramData().getTotalCount());
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testRecordValue_Overflow_ShouldThrowException() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(highestTrackableValue * 3);
    }

    @org.junit.Test
    public void testRecordValueWithExpectedInterval() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel, testValueLevel/4);
        Histogram rawHistogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        rawHistogram.recordValue(testValueLevel);
        // The data will include corrected samples:
        Assert.assertEquals(1L, histogram.getHistogramData().getCountAtValue((testValueLevel * 1 )/4));
        Assert.assertEquals(1L, histogram.getHistogramData().getCountAtValue((testValueLevel * 2 )/4));
        Assert.assertEquals(1L, histogram.getHistogramData().getCountAtValue((testValueLevel * 3 )/4));
        Assert.assertEquals(1L, histogram.getHistogramData().getCountAtValue((testValueLevel * 4 )/4));
        Assert.assertEquals(4L, histogram.getHistogramData().getTotalCount());
        // But the raw data will not:
        Assert.assertEquals(0L, rawHistogram.getHistogramData().getCountAtValue((testValueLevel * 1 )/4));
        Assert.assertEquals(0L, rawHistogram.getHistogramData().getCountAtValue((testValueLevel * 2 )/4));
        Assert.assertEquals(0L, rawHistogram.getHistogramData().getCountAtValue((testValueLevel * 3 )/4));
        Assert.assertEquals(1L, rawHistogram.getHistogramData().getCountAtValue((testValueLevel * 4 )/4));
        Assert.assertEquals(1L, rawHistogram.getHistogramData().getTotalCount());
    }

    @Test
    public void testReset() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.reset();
        Assert.assertEquals(0L, histogram.getHistogramData().getCountAtValue(testValueLevel));
        Assert.assertEquals(0L, histogram.getHistogramData().getTotalCount());
    }

    @Test
    public void testAdd() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Histogram other = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        other.recordValue(testValueLevel);
        histogram.add(other);
        Assert.assertEquals(2L, histogram.getHistogramData().getCountAtValue(testValueLevel));
        Assert.assertEquals(2L, histogram.getHistogramData().getTotalCount());
        Histogram incompatibleOther = new Histogram(highestTrackableValue * 2, numberOfSignificantValueDigits);
        boolean thrown = false;
        try {
            // This should throw:
            histogram.add(incompatibleOther);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }


    @Test
    public void testSizeOfEquivalentValueRange() {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals("Size of equivalent range for value 1 is 1",
                1, histogram.sizeOfEquivalentValueRange(1));
        Assert.assertEquals("Size of equivalent range for value 2500 is 2",
                2, histogram.sizeOfEquivalentValueRange(2500));
        Assert.assertEquals("Size of equivalent range for value 8191 is 4",
                4, histogram.sizeOfEquivalentValueRange(8191));
        Assert.assertEquals("Size of equivalent range for value 8192 is 8",
                8, histogram.sizeOfEquivalentValueRange(8192));
        Assert.assertEquals("Size of equivalent range for value 10000 is 8",
                8, histogram.sizeOfEquivalentValueRange(10000));
    }

    @Test
    public void testLowestEquivalentValue() {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals("The lowest equivalent value to 10007 is 10000",
                10000, histogram.lowestEquivalentValue(10007));
        Assert.assertEquals("The lowest equivalent value to 10009 is 10008",
                10008, histogram.lowestEquivalentValue(10009));
    }

    @Test
    public void testHighestEquivalentValue() {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals("The highest equivalent value to 8180 is 8183",
                8183, histogram.highestEquivalentValue(8180));
        Assert.assertEquals("The highest equivalent value to 8187 is 8191",
                8191, histogram.highestEquivalentValue(8191));
        Assert.assertEquals("The highest equivalent value to 8193 is 8199",
                8199, histogram.highestEquivalentValue(8193));
        Assert.assertEquals("The highest equivalent value to 9995 is 9999",
                9999, histogram.highestEquivalentValue(9995));
        Assert.assertEquals("The highest equivalent value to 10007 is 10007",
                10007, histogram.highestEquivalentValue(10007));
        Assert.assertEquals("The highest equivalent value to 10008 is 10015",
                10015, histogram.highestEquivalentValue(10008));
    }

    @Test
    public void testMedianEquivalentValue() {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals("The median equivalent value to 4 is 4",
                4, histogram.medianEquivalentValue(4));
        Assert.assertEquals("The median equivalent value to 5 is 5",
                5, histogram.medianEquivalentValue(5));
        Assert.assertEquals("The median equivalent value to 4000 is 4001",
                4001, histogram.medianEquivalentValue(4000));
        Assert.assertEquals("The median equivalent value to 8000 is 8002",
                8002, histogram.medianEquivalentValue(8000));
        Assert.assertEquals("The median equivalent value to 10007 is 10004",
                10004, histogram.medianEquivalentValue(10007));
    }

    @Test
    public void testNextNonEquivalentValue() {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertNotSame(null, histogram);
    }
}
