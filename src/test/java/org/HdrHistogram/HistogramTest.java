/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
 */

package org.HdrHistogram;

import org.junit.*;
import java.io.*;
import java.util.zip.Deflater;

import java.io.ByteArrayOutputStream;

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
        /*
        *     largestValueWithSingleUnitResolution = 2 * (10 ^ numberOfSignificantValueDigits);
        *     subBucketSize = roundedUpToNearestPowerOf2(largestValueWithSingleUnitResolution);

        *     expectedHistogramFootprintInBytes = 512 +
        *          ({primitive type size} / 2) *
        *          (log2RoundedUp((highestTrackableValue) / subBucketSize) + 2) *
        *          subBucketSize
        */
        long largestValueWithSingleUnitResolution = 2 * (long) Math.pow(10, numberOfSignificantValueDigits);
        int subBucketCountMagnitude = (int) Math.ceil(Math.log(largestValueWithSingleUnitResolution)/Math.log(2));
        int subBucketSize = (int) Math.pow(2, (subBucketCountMagnitude));

        long expectedSize = 512 +
                ((8 *
                 ((long)(
                        Math.ceil(
                         Math.log(highestTrackableValue / subBucketSize)
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

    void testAbstractSerialization(AbstractHistogram histogram) throws Exception {
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValue(histogram.getHighestTrackableValue() - 1, 31);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        ByteArrayInputStream bis = null;
        ObjectInput in = null;
        AbstractHistogram newHistogram = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(histogram);
            Deflater compresser = new Deflater();
            compresser.setInput(bos.toByteArray());
            compresser.finish();
            byte [] compressedOutput = new byte[1024*1024];
            int compressedDataLength = compresser.deflate(compressedOutput);
            System.out.println("Serialized form of " + histogram.getClass() + " with highestTrackableValue = " +
                    histogram.getHighestTrackableValue() + "\n and a numberOfSignificantValueDigits = " +
                    histogram.getNumberOfSignificantValueDigits() + " is " + bos.toByteArray().length +
                    " bytes long. Compressed form is " + compressedDataLength + " bytes long.");
            System.out.println("   (estimated footprint was " + histogram.getEstimatedFootprintInBytes() + " bytes)");
            bis = new ByteArrayInputStream(bos.toByteArray());
            in = new ObjectInputStream(bis);
            newHistogram = (AbstractHistogram) in.readObject();
        } finally {
            if (out != null) out.close();
            bos.close();
            if (in !=null) in.close();
            if (bis != null) bis.close();
        }
        Assert.assertNotNull(newHistogram);
        assertEqual(histogram, newHistogram);
    }

    private void assertEqual(AbstractHistogram expectedHistogram, AbstractHistogram actualHistogram) {
        Assert.assertEquals(expectedHistogram, actualHistogram);
        Assert.assertEquals(
                expectedHistogram.getHistogramData().getCountAtValue(testValueLevel),
                actualHistogram.getHistogramData().getCountAtValue(testValueLevel));
        Assert.assertEquals(
                expectedHistogram.getHistogramData().getCountAtValue(testValueLevel * 10),
                actualHistogram.getHistogramData().getCountAtValue(testValueLevel * 10));
        Assert.assertEquals(
                expectedHistogram.getHistogramData().getTotalCount(),
                actualHistogram.getHistogramData().getTotalCount());
    }

    @Test
    public void testSerialization() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, 3);
        testAbstractSerialization(histogram);
        IntHistogram intHistogram = new IntHistogram(highestTrackableValue, 3);
        testAbstractSerialization(intHistogram);
        ShortHistogram shortHistogram = new ShortHistogram(highestTrackableValue, 3);
        testAbstractSerialization(shortHistogram);
        histogram = new Histogram(highestTrackableValue, 2);
        testAbstractSerialization(histogram);
        intHistogram = new IntHistogram(highestTrackableValue, 2);
        testAbstractSerialization(intHistogram);
        shortHistogram = new ShortHistogram(highestTrackableValue, 2);
        testAbstractSerialization(shortHistogram);
    }

    @Test
    public void testOverflow() throws Exception {
        ShortHistogram histogram = new ShortHistogram(highestTrackableValue, 2);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        Assert.assertFalse(histogram.hasOverflowed());
        // This should overflow a ShortHistogram:
        histogram.recordValue(histogram.getHighestTrackableValue() - 1, 500);
        Assert.assertTrue(histogram.hasOverflowed());
        System.out.println("Histogram percentile output should show overflow:");
        histogram.getHistogramData().outputPercentileDistribution(System.out, 5, 100.0);
    }
    
    @Test
    public void testCopy() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValue(histogram.getHighestTrackableValue() - 1, 31);
        
        assertEqual(histogram, histogram.copy());
  
        IntHistogram intHistogram = new IntHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        intHistogram.recordValue(testValueLevel);
        intHistogram.recordValue(testValueLevel * 10);
        intHistogram.recordValue(intHistogram.getHighestTrackableValue() - 1, 31);
        
        assertEqual(intHistogram, intHistogram.copy());
  
        ShortHistogram shortHistogram = new ShortHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        shortHistogram.recordValue(testValueLevel);
        shortHistogram.recordValue(testValueLevel * 10);
        shortHistogram.recordValue(shortHistogram.getHighestTrackableValue() - 1, 31);
        
        assertEqual(shortHistogram, shortHistogram.copy());
  
        AtomicHistogram atomicHistogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        atomicHistogram.recordValue(testValueLevel);
        atomicHistogram.recordValue(testValueLevel * 10);
        atomicHistogram.recordValue(atomicHistogram.getHighestTrackableValue() - 1, 31);
        
        assertEqual(atomicHistogram, atomicHistogram.copy());
  
        SynchronizedHistogram syncHistogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        syncHistogram.recordValue(testValueLevel);
        syncHistogram.recordValue(testValueLevel * 10);
        syncHistogram.recordValue(syncHistogram.getHighestTrackableValue() - 1, 31);
        
        assertEqual(syncHistogram, syncHistogram.copy());
    }
}
