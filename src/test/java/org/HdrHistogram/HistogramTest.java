/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
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
    public void testEmptyHistogram() throws Exception {
        Histogram histogram = new Histogram(3);
        long min = histogram.getMinValue();
        Assert.assertEquals(0, min);
        long max = histogram.getMaxValue();
        Assert.assertEquals(0, max);
        double mean = histogram.getMean();
        Assert.assertEquals(0, mean, 0.0000000000001D);
        double stddev = histogram.getStdDeviation();
        Assert.assertEquals(0, stddev, 0.0000000000001D);
        double pcnt = histogram.getPercentileAtOrBelowValue(0);
        Assert.assertEquals(100.0, pcnt, 0.0000000000001D);
    }

    @Test
    public void testConstructionArgumentGets() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals(1, histogram.getLowestDiscernibleValue());
        Assert.assertEquals(highestTrackableValue, histogram.getHighestTrackableValue());
        Assert.assertEquals(numberOfSignificantValueDigits, histogram.getNumberOfSignificantValueDigits());
        Histogram histogram2 = new Histogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals(1000, histogram2.getLowestDiscernibleValue());
        verifyMaxValue(histogram);
    }

    @Test
    public void testGetEstimatedFootprintInBytes() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        /*
        *     largestValueWithSingleUnitResolution = 2 * (10 ^ numberOfSignificantValueDigits);
        *     subBucketSize = roundedUpToNearestPowerOf2(largestValueWithSingleUnitResolution);

        *     expectedHistogramFootprintInBytes = 512 +
        *          ({primitive type size} / 2) *
        *          (log2RoundedUp((trackableValueRangeSize) / subBucketSize) + 2) *
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
        verifyMaxValue(histogram);
    }

    @Test
    public void testRecordValue() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        Assert.assertEquals(1L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(1L, histogram.getTotalCount());
        verifyMaxValue(histogram);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testRecordValue_Overflow_ShouldThrowException() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(highestTrackableValue * 3);
    }


    @Test
    public void testConstructionWithLargeNumbers() throws Exception {
        Histogram histogram = new Histogram(20000000, 100000000, 5);
        histogram.recordValue(100000000);
        histogram.recordValue(20000000);
        histogram.recordValue(30000000);
        Assert.assertTrue(histogram.valuesAreEquivalent(20000000, histogram.getValueAtPercentile(50.0)));
        Assert.assertTrue(histogram.valuesAreEquivalent(30000000, histogram.getValueAtPercentile(83.33)));
        Assert.assertTrue(histogram.valuesAreEquivalent(100000000, histogram.getValueAtPercentile(83.34)));
        Assert.assertTrue(histogram.valuesAreEquivalent(100000000, histogram.getValueAtPercentile(99.0)));
    }

    @Test
    public void testRecordValueWithExpectedInterval() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValueWithExpectedInterval(testValueLevel, testValueLevel/4);
        Histogram rawHistogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        rawHistogram.recordValue(testValueLevel);
        // The data will include corrected samples:
        Assert.assertEquals(1L, histogram.getCountAtValue((testValueLevel * 1 )/4));
        Assert.assertEquals(1L, histogram.getCountAtValue((testValueLevel * 2 )/4));
        Assert.assertEquals(1L, histogram.getCountAtValue((testValueLevel * 3 )/4));
        Assert.assertEquals(1L, histogram.getCountAtValue((testValueLevel * 4 )/4));
        Assert.assertEquals(4L, histogram.getTotalCount());
        // But the raw data will not:
        Assert.assertEquals(0L, rawHistogram.getCountAtValue((testValueLevel * 1 )/4));
        Assert.assertEquals(0L, rawHistogram.getCountAtValue((testValueLevel * 2 )/4));
        Assert.assertEquals(0L, rawHistogram.getCountAtValue((testValueLevel * 3 )/4));
        Assert.assertEquals(1L, rawHistogram.getCountAtValue((testValueLevel * 4 )/4));
        Assert.assertEquals(1L, rawHistogram.getTotalCount());

        verifyMaxValue(histogram);
    }

    @Test
    public void testReset() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.reset();
        Assert.assertEquals(0L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(0L, histogram.getTotalCount());
        verifyMaxValue(histogram);
    }

    @Test
    public void testAdd() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Histogram other = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 1000);
        other.recordValue(testValueLevel);
        other.recordValue(testValueLevel * 1000);
        histogram.add(other);
        Assert.assertEquals(2L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(2L, histogram.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(4L, histogram.getTotalCount());

        Histogram biggerOther = new Histogram(highestTrackableValue * 2, numberOfSignificantValueDigits);
        biggerOther.recordValue(testValueLevel);
        biggerOther.recordValue(testValueLevel * 1000);
        biggerOther.recordValue(highestTrackableValue * 2);

        // Adding the smaller histogram to the bigger one should work:
        biggerOther.add(histogram);
        Assert.assertEquals(3L, biggerOther.getCountAtValue(testValueLevel));
        Assert.assertEquals(3L, biggerOther.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(1L, biggerOther.getCountAtValue(highestTrackableValue * 2)); // overflow smaller hist...
        Assert.assertEquals(7L, biggerOther.getTotalCount());

        // But trying to add a larger histogram into a smaller one should throw an AIOOB:
        boolean thrown = false;
        try {
            // This should throw:
            histogram.add(biggerOther);
        } catch (ArrayIndexOutOfBoundsException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);

        verifyMaxValue(histogram);
        verifyMaxValue(other);
        verifyMaxValue(biggerOther);
    }

    @Test
    public void testSubtract() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Histogram other = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 1000);
        other.recordValue(testValueLevel);
        other.recordValue(testValueLevel * 1000);
        histogram.add(other);
        Assert.assertEquals(2L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(2L, histogram.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(4L, histogram.getTotalCount());
        histogram.add(other);
        Assert.assertEquals(3L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(3L, histogram.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(6L, histogram.getTotalCount());
        histogram.subtract(other);
        Assert.assertEquals(2L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(2L, histogram.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(4L, histogram.getTotalCount());
        // Subtracting down to zero counts should work:
        histogram.subtract(histogram);
        Assert.assertEquals(0L, histogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(0L, histogram.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(0L, histogram.getTotalCount());
        // But subtracting down to negative counts should not:
        boolean thrown = false;
        try {
            // This should throw:
            histogram.subtract(other);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);


        Histogram biggerOther = new Histogram(highestTrackableValue * 2, numberOfSignificantValueDigits);
        biggerOther.recordValue(testValueLevel);
        biggerOther.recordValue(testValueLevel * 1000);
        biggerOther.recordValue(highestTrackableValue * 2);
        biggerOther.add(biggerOther);
        biggerOther.add(biggerOther);
        Assert.assertEquals(4L, biggerOther.getCountAtValue(testValueLevel));
        Assert.assertEquals(4L, biggerOther.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(4L, biggerOther.getCountAtValue(highestTrackableValue * 2)); // overflow smaller hist...
        Assert.assertEquals(12L, biggerOther.getTotalCount());

        // Subtracting the smaller histogram from the bigger one should work:
        biggerOther.subtract(other);
        Assert.assertEquals(3L, biggerOther.getCountAtValue(testValueLevel));
        Assert.assertEquals(3L, biggerOther.getCountAtValue(testValueLevel * 1000));
        Assert.assertEquals(4L, biggerOther.getCountAtValue(highestTrackableValue * 2)); // overflow smaller hist...
        Assert.assertEquals(10L, biggerOther.getTotalCount());

        // But trying to subtract a larger histogram into a smaller one should throw an AIOOB:
        thrown = false;
        try {
            // This should throw:
            histogram.subtract(biggerOther);
        } catch (ArrayIndexOutOfBoundsException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);

        verifyMaxValue(histogram);
        verifyMaxValue(other);
        verifyMaxValue(biggerOther);
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
        verifyMaxValue(histogram);
    }

    @Test
    public void testScaledSizeOfEquivalentValueRange() {
        Histogram histogram = new Histogram(1024, highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals("Size of equivalent range for value 1 * 1024 is 1 * 1024",
                1 * 1024, histogram.sizeOfEquivalentValueRange(1 * 1024));
        Assert.assertEquals("Size of equivalent range for value 2500 * 1024 is 2 * 1024",
                2 * 1024, histogram.sizeOfEquivalentValueRange(2500 * 1024));
        Assert.assertEquals("Size of equivalent range for value 8191 * 1024 is 4 * 1024",
                4 * 1024, histogram.sizeOfEquivalentValueRange(8191 * 1024));
        Assert.assertEquals("Size of equivalent range for value 8192 * 1024 is 8 * 1024",
                8 * 1024, histogram.sizeOfEquivalentValueRange(8192 * 1024));
        Assert.assertEquals("Size of equivalent range for value 10000 * 1024 is 8 * 1024",
                8 * 1024, histogram.sizeOfEquivalentValueRange(10000 * 1024));
        verifyMaxValue(histogram);
    }

    @Test
    public void testLowestEquivalentValue() {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals("The lowest equivalent value to 10007 is 10000",
                10000, histogram.lowestEquivalentValue(10007));
        Assert.assertEquals("The lowest equivalent value to 10009 is 10008",
                10008, histogram.lowestEquivalentValue(10009));
        verifyMaxValue(histogram);
    }


    @Test
    public void testScaledLowestEquivalentValue() {
        Histogram histogram = new Histogram(1024, highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals("The lowest equivalent value to 10007 * 1024 is 10000 * 1024",
                10000 * 1024, histogram.lowestEquivalentValue(10007 * 1024));
        Assert.assertEquals("The lowest equivalent value to 10009 * 1024 is 10008 * 1024",
                10008 * 1024, histogram.lowestEquivalentValue(10009 * 1024));
        verifyMaxValue(histogram);
    }

    @Test
    public void testHighestEquivalentValue() {
        Histogram histogram = new Histogram(1024, highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals("The highest equivalent value to 8180 * 1024 is 8183 * 1024 + 1023",
                8183 * 1024 + 1023, histogram.highestEquivalentValue(8180 * 1024));
        Assert.assertEquals("The highest equivalent value to 8187 * 1024 is 8191 * 1024 + 1023",
                8191 * 1024 + 1023, histogram.highestEquivalentValue(8191 * 1024));
        Assert.assertEquals("The highest equivalent value to 8193 * 1024 is 8199 * 1024 + 1023",
                8199 * 1024 + 1023, histogram.highestEquivalentValue(8193 * 1024));
        Assert.assertEquals("The highest equivalent value to 9995 * 1024 is 9999 * 1024 + 1023",
                9999 * 1024 + 1023, histogram.highestEquivalentValue(9995 * 1024));
        Assert.assertEquals("The highest equivalent value to 10007 * 1024 is 10007 * 1024 + 1023",
                10007 * 1024 + 1023, histogram.highestEquivalentValue(10007 * 1024));
        Assert.assertEquals("The highest equivalent value to 10008 * 1024 is 10015 * 1024 + 1023",
                10015 * 1024 + 1023, histogram.highestEquivalentValue(10008 * 1024));
        verifyMaxValue(histogram);
    }


    @Test
    public void testScaledHighestEquivalentValue() {
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
        verifyMaxValue(histogram);
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
        verifyMaxValue(histogram);
    }

    @Test
    public void testScaledMedianEquivalentValue() {
        Histogram histogram = new Histogram(1024, highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertEquals("The median equivalent value to 4 * 1024 is 4 * 1024 + 512",
                4 * 1024 + 512, histogram.medianEquivalentValue(4 * 1024));
        Assert.assertEquals("The median equivalent value to 5 * 1024 is 5 * 1024 + 512",
                5 * 1024 + 512, histogram.medianEquivalentValue(5 * 1024));
        Assert.assertEquals("The median equivalent value to 4000 * 1024 is 4001 * 1024",
                4001 * 1024, histogram.medianEquivalentValue(4000 * 1024));
        Assert.assertEquals("The median equivalent value to 8000 * 1024 is 8002 * 1024",
                8002 * 1024, histogram.medianEquivalentValue(8000 * 1024));
        Assert.assertEquals("The median equivalent value to 10007 * 1024 is 10004 * 1024",
                10004 * 1024, histogram.medianEquivalentValue(10007 * 1024));
        verifyMaxValue(histogram);
    }

    @Test
    public void testNextNonEquivalentValue() {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Assert.assertNotSame(null, histogram);
    }

    void testAbstractSerialization(AbstractHistogram histogram) throws Exception {
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 255);
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
            System.out.println("Serialized form of " + histogram.getClass() + " with trackableValueRangeSize = " +
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
                expectedHistogram.getCountAtValue(testValueLevel),
                actualHistogram.getCountAtValue(testValueLevel));
        Assert.assertEquals(
                expectedHistogram.getCountAtValue(testValueLevel * 10),
                actualHistogram.getCountAtValue(testValueLevel * 10));
        Assert.assertEquals(
                expectedHistogram.getTotalCount(),
                actualHistogram.getTotalCount());
        Assert.assertEquals(
                expectedHistogram.getNeededByteBufferCapacity(),
                actualHistogram.getNeededByteBufferCapacity());
        verifyMaxValue(expectedHistogram);
        verifyMaxValue(actualHistogram);
    }

    @Test
    public void testSerialization() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, 3);
        testAbstractSerialization(histogram);
        IntCountsHistogram intCountsHistogram = new IntCountsHistogram(highestTrackableValue, 3);
        testAbstractSerialization(intCountsHistogram);
        ShortCountsHistogram shortCountsHistogram = new ShortCountsHistogram(highestTrackableValue, 3);
        testAbstractSerialization(shortCountsHistogram);
        histogram = new Histogram(highestTrackableValue, 2);
        testAbstractSerialization(histogram);
        intCountsHistogram = new IntCountsHistogram(highestTrackableValue, 2);
        testAbstractSerialization(intCountsHistogram);
        shortCountsHistogram = new ShortCountsHistogram(highestTrackableValue, 4); // With 2 decimal points, shorts overflow here
        testAbstractSerialization(shortCountsHistogram);
    }

    @Test(expected = IllegalStateException.class)
    public void testOverflow() throws Exception {
        ShortCountsHistogram histogram = new ShortCountsHistogram(highestTrackableValue, 2);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        // This should overflow a ShortHistogram:
        histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 500);
    }
    
    @Test
    public void testCopy() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of Histogram:");
        assertEqual(histogram, histogram.copy());

        IntCountsHistogram intCountsHistogram = new IntCountsHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        intCountsHistogram.recordValue(testValueLevel);
        intCountsHistogram.recordValue(testValueLevel * 10);
        intCountsHistogram.recordValueWithExpectedInterval(intCountsHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of IntHistogram:");
        assertEqual(intCountsHistogram, intCountsHistogram.copy());
  
        ShortCountsHistogram shortCountsHistogram = new ShortCountsHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        shortCountsHistogram.recordValue(testValueLevel);
        shortCountsHistogram.recordValue(testValueLevel * 10);
        shortCountsHistogram.recordValueWithExpectedInterval(shortCountsHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of ShortHistogram:");
        assertEqual(shortCountsHistogram, shortCountsHistogram.copy());
  
        AtomicHistogram atomicHistogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        atomicHistogram.recordValue(testValueLevel);
        atomicHistogram.recordValue(testValueLevel * 10);
        atomicHistogram.recordValueWithExpectedInterval(atomicHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of AtomicHistogram:");
        assertEqual(atomicHistogram, atomicHistogram.copy());

        ConcurrentHistogram concurrentHistogram = new ConcurrentHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        concurrentHistogram.recordValue(testValueLevel);
        concurrentHistogram.recordValue(testValueLevel * 10);
        concurrentHistogram.recordValueWithExpectedInterval(concurrentHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of ConcurrentHistogram:");
        assertEqual(concurrentHistogram, concurrentHistogram.copy());
  
        SynchronizedHistogram syncHistogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        syncHistogram.recordValue(testValueLevel);
        syncHistogram.recordValue(testValueLevel * 10);
        syncHistogram.recordValueWithExpectedInterval(syncHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of SynchronizedHistogram:");
        assertEqual(syncHistogram, syncHistogram.copy());
    }

    @Test
    public void testScaledCopy() throws Exception {
        Histogram histogram = new Histogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of scaled Histogram:");
        assertEqual(histogram, histogram.copy());

        IntCountsHistogram intCountsHistogram = new IntCountsHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        intCountsHistogram.recordValue(testValueLevel);
        intCountsHistogram.recordValue(testValueLevel * 10);
        intCountsHistogram.recordValueWithExpectedInterval(intCountsHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of scaled IntHistogram:");
        assertEqual(intCountsHistogram, intCountsHistogram.copy());

        ShortCountsHistogram shortCountsHistogram = new ShortCountsHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        shortCountsHistogram.recordValue(testValueLevel);
        shortCountsHistogram.recordValue(testValueLevel * 10);
        shortCountsHistogram.recordValueWithExpectedInterval(shortCountsHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of scaled ShortHistogram:");
        assertEqual(shortCountsHistogram, shortCountsHistogram.copy());

        AtomicHistogram atomicHistogram = new AtomicHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        atomicHistogram.recordValue(testValueLevel);
        atomicHistogram.recordValue(testValueLevel * 10);
        atomicHistogram.recordValueWithExpectedInterval(atomicHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of scaled AtomicHistogram:");
        assertEqual(atomicHistogram, atomicHistogram.copy());

        ConcurrentHistogram concurrentHistogram = new ConcurrentHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        concurrentHistogram.recordValue(testValueLevel);
        concurrentHistogram.recordValue(testValueLevel * 10);
        concurrentHistogram.recordValueWithExpectedInterval(concurrentHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of scaled ConcurrentHistogram:");
        assertEqual(concurrentHistogram, concurrentHistogram.copy());

        SynchronizedHistogram syncHistogram = new SynchronizedHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        syncHistogram.recordValue(testValueLevel);
        syncHistogram.recordValue(testValueLevel * 10);
        syncHistogram.recordValueWithExpectedInterval(syncHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of scaled SynchronizedHistogram:");
        assertEqual(syncHistogram, syncHistogram.copy());
    }

    @Test
    public void testCopyInto() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        Histogram targetHistogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for Histogram:");
        histogram.copyInto(targetHistogram);
        assertEqual(histogram, targetHistogram);

        histogram.recordValue(testValueLevel * 20);

        histogram.copyInto(targetHistogram);
        assertEqual(histogram, targetHistogram);


        IntCountsHistogram intCountsHistogram = new IntCountsHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        IntCountsHistogram targetIntCountsHistogram = new IntCountsHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        intCountsHistogram.recordValue(testValueLevel);
        intCountsHistogram.recordValue(testValueLevel * 10);
        intCountsHistogram.recordValueWithExpectedInterval(intCountsHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for IntHistogram:");
        intCountsHistogram.copyInto(targetIntCountsHistogram);
        assertEqual(intCountsHistogram, targetIntCountsHistogram);

        intCountsHistogram.recordValue(testValueLevel * 20);

        intCountsHistogram.copyInto(targetIntCountsHistogram);
        assertEqual(intCountsHistogram, targetIntCountsHistogram);


        ShortCountsHistogram shortCountsHistogram = new ShortCountsHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        ShortCountsHistogram targetShortCountsHistogram = new ShortCountsHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        shortCountsHistogram.recordValue(testValueLevel);
        shortCountsHistogram.recordValue(testValueLevel * 10);
        shortCountsHistogram.recordValueWithExpectedInterval(shortCountsHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for ShortHistogram:");
        shortCountsHistogram.copyInto(targetShortCountsHistogram);
        assertEqual(shortCountsHistogram, targetShortCountsHistogram);

        shortCountsHistogram.recordValue(testValueLevel * 20);

        shortCountsHistogram.copyInto(targetShortCountsHistogram);
        assertEqual(shortCountsHistogram, targetShortCountsHistogram);


        AtomicHistogram atomicHistogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        AtomicHistogram targetAtomicHistogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        atomicHistogram.recordValue(testValueLevel);
        atomicHistogram.recordValue(testValueLevel * 10);
        atomicHistogram.recordValueWithExpectedInterval(atomicHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for AtomicHistogram:");
        atomicHistogram.copyInto(targetAtomicHistogram);
        assertEqual(atomicHistogram, targetAtomicHistogram);

        atomicHistogram.recordValue(testValueLevel * 20);

        atomicHistogram.copyInto(targetAtomicHistogram);
        assertEqual(atomicHistogram, targetAtomicHistogram);


        ConcurrentHistogram concurrentHistogram = new ConcurrentHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        ConcurrentHistogram targetConcurrentHistogram = new ConcurrentHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        concurrentHistogram.recordValue(testValueLevel);
        concurrentHistogram.recordValue(testValueLevel * 10);
        concurrentHistogram.recordValueWithExpectedInterval(concurrentHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for ConcurrentHistogram:");
        concurrentHistogram.copyInto(targetConcurrentHistogram);
        assertEqual(concurrentHistogram, targetConcurrentHistogram);

        concurrentHistogram.recordValue(testValueLevel * 20);

        concurrentHistogram.copyInto(targetConcurrentHistogram);
        assertEqual(concurrentHistogram, targetConcurrentHistogram);


        SynchronizedHistogram syncHistogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        SynchronizedHistogram targetSyncHistogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        syncHistogram.recordValue(testValueLevel);
        syncHistogram.recordValue(testValueLevel * 10);
        syncHistogram.recordValueWithExpectedInterval(syncHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for SynchronizedHistogram:");
        syncHistogram.copyInto(targetSyncHistogram);
        assertEqual(syncHistogram, targetSyncHistogram);

        syncHistogram.recordValue(testValueLevel * 20);

        syncHistogram.copyInto(targetSyncHistogram);
        assertEqual(syncHistogram, targetSyncHistogram);
    }

    @Test
    public void testScaledCopyInto() throws Exception {
        Histogram histogram = new Histogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        Histogram targetHistogram = new Histogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for scaled Histogram:");
        histogram.copyInto(targetHistogram);
        assertEqual(histogram, targetHistogram);

        histogram.recordValue(testValueLevel * 20);

        histogram.copyInto(targetHistogram);
        assertEqual(histogram, targetHistogram);


        IntCountsHistogram intCountsHistogram = new IntCountsHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        IntCountsHistogram targetIntCountsHistogram = new IntCountsHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        intCountsHistogram.recordValue(testValueLevel);
        intCountsHistogram.recordValue(testValueLevel * 10);
        intCountsHistogram.recordValueWithExpectedInterval(intCountsHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for scaled IntHistogram:");
        intCountsHistogram.copyInto(targetIntCountsHistogram);
        assertEqual(intCountsHistogram, targetIntCountsHistogram);

        intCountsHistogram.recordValue(testValueLevel * 20);

        intCountsHistogram.copyInto(targetIntCountsHistogram);
        assertEqual(intCountsHistogram, targetIntCountsHistogram);


        ShortCountsHistogram shortCountsHistogram = new ShortCountsHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        ShortCountsHistogram targetShortCountsHistogram = new ShortCountsHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        shortCountsHistogram.recordValue(testValueLevel);
        shortCountsHistogram.recordValue(testValueLevel * 10);
        shortCountsHistogram.recordValueWithExpectedInterval(shortCountsHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for scaled ShortHistogram:");
        shortCountsHistogram.copyInto(targetShortCountsHistogram);
        assertEqual(shortCountsHistogram, targetShortCountsHistogram);

        shortCountsHistogram.recordValue(testValueLevel * 20);

        shortCountsHistogram.copyInto(targetShortCountsHistogram);
        assertEqual(shortCountsHistogram, targetShortCountsHistogram);


        AtomicHistogram atomicHistogram = new AtomicHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        AtomicHistogram targetAtomicHistogram = new AtomicHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        atomicHistogram.recordValue(testValueLevel);
        atomicHistogram.recordValue(testValueLevel * 10);
        atomicHistogram.recordValueWithExpectedInterval(atomicHistogram.getHighestTrackableValue() - 1, 31000);

        atomicHistogram.copyInto(targetAtomicHistogram);
        assertEqual(atomicHistogram, targetAtomicHistogram);

        atomicHistogram.recordValue(testValueLevel * 20);

        System.out.println("Testing copyInto for scaled AtomicHistogram:");
        atomicHistogram.copyInto(targetAtomicHistogram);
        assertEqual(atomicHistogram, targetAtomicHistogram);


        ConcurrentHistogram concurrentHistogram = new ConcurrentHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        ConcurrentHistogram targetConcurrentHistogram = new ConcurrentHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        concurrentHistogram.recordValue(testValueLevel);
        concurrentHistogram.recordValue(testValueLevel * 10);
        concurrentHistogram.recordValueWithExpectedInterval(concurrentHistogram.getHighestTrackableValue() - 1, 31000);

        concurrentHistogram.copyInto(targetConcurrentHistogram);
        assertEqual(concurrentHistogram, targetConcurrentHistogram);

        concurrentHistogram.recordValue(testValueLevel * 20);

        System.out.println("Testing copyInto for scaled ConcurrentHistogram:");
        concurrentHistogram.copyInto(targetConcurrentHistogram);
        assertEqual(concurrentHistogram, targetConcurrentHistogram);


        SynchronizedHistogram syncHistogram = new SynchronizedHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        SynchronizedHistogram targetSyncHistogram = new SynchronizedHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
        syncHistogram.recordValue(testValueLevel);
        syncHistogram.recordValue(testValueLevel * 10);
        syncHistogram.recordValueWithExpectedInterval(syncHistogram.getHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copyInto for scaled SynchronizedHistogram:");
        syncHistogram.copyInto(targetSyncHistogram);
        assertEqual(syncHistogram, targetSyncHistogram);

        syncHistogram.recordValue(testValueLevel * 20);

        syncHistogram.copyInto(targetSyncHistogram);
        assertEqual(syncHistogram, targetSyncHistogram);
    }

    public void verifyMaxValue(AbstractHistogram histogram) {
        long computedMaxValue = 0;
        for (int i = 0; i < histogram.countsArrayLength; i++) {
            if (histogram.getCountAtIndex(i) > 0) {
                computedMaxValue = histogram.valueFromIndex(i);
            }
        }
        computedMaxValue = (computedMaxValue == 0) ? 0 : histogram.highestEquivalentValue(computedMaxValue);
        Assert.assertEquals(computedMaxValue, histogram.getMaxValue());
    }
}
