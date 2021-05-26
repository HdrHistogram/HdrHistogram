/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import static org.HdrHistogram.HistogramTestUtils.constructHistogram;
import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.util.zip.Deflater;

import static org.HdrHistogram.HistogramTestUtils.constructDoubleHistogram;

/**
 * JUnit test for {@link Histogram}
 */
public class DoubleHistogramTest {
    static final long trackableValueRangeSize = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
    static final int numberOfSignificantValueDigits = 3;
    // static final long testValueLevel = 12340;
    static final double testValueLevel = 4.0;

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testTrackableValueRangeMustBeGreaterThanTwo(final Class histoClass) throws Exception
    {
        Assertions.assertThrows(IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        DoubleHistogram histogram =
                                constructDoubleHistogram(histoClass, 1, numberOfSignificantValueDigits);
                    }
                });
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testNumberOfSignificantValueDigitsMustBeLessThanSix(final Class histoClass) throws Exception
    {
        Assertions.assertThrows(IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        DoubleHistogram histogram =
                                constructDoubleHistogram(histoClass, trackableValueRangeSize, 6);
                    }
                });
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testNumberOfSignificantValueDigitsMustBePositive(final Class histoClass) throws Exception
    {
        Assertions.assertThrows(IllegalArgumentException.class,
                new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        DoubleHistogram histogram =
                                constructDoubleHistogram(histoClass, trackableValueRangeSize, -1);
                    }
                });
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testConstructionArgumentGets(Class histoClass) throws Exception {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        // Record 1.0, and verify that the range adjust to it:
        histogram.recordValue(Math.pow(2.0, 20));
        histogram.recordValue(1.0);
        assertEquals(1.0, histogram.getCurrentLowestTrackableNonZeroValue(), 0.001);
        assertEquals(trackableValueRangeSize, histogram.getHighestToLowestValueRatio());
        assertEquals(numberOfSignificantValueDigits, histogram.getNumberOfSignificantValueDigits());

        DoubleHistogram histogram2 =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        // Record a larger value, and verify that the range adjust to it too:
        histogram2.recordValue(2048.0 * 1024.0 * 1024.0);
        assertEquals(2048.0 * 1024.0 * 1024.0, histogram2.getCurrentLowestTrackableNonZeroValue(), 0.001);

        DoubleHistogram histogram3 =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        // Record a value that is 1000x outside of the initially set range, which should scale us by 1/1024x:
        histogram3.recordValue(1/1000.0);
        assertEquals(1.0/1024, histogram3.getCurrentLowestTrackableNonZeroValue(), 0.001);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testDataRange(Class histoClass) {
        // A trackableValueRangeSize histigram
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(0.0);  // Include a zero value to make sure things are handled right.
        assertEquals(1L, histogram.getCountAtValue(0.0));

        double topValue = 1.0;
        try {
            while (true) {
                histogram.recordValue(topValue);
                topValue *= 2.0;
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
        }
        assertEquals(1L << 33, topValue, 0.00001);
        assertEquals(1L, histogram.getCountAtValue(0.0));

        histogram = constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(0.0); // Include a zero value to make sure things are handled right.

        double bottomValue = 1L << 33;
        try {
            while (true) {
                histogram.recordValue(bottomValue);
                bottomValue /= 2.0;
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("Bottom value at exception point = " + bottomValue);
        }
        assertEquals(1.0, bottomValue, 0.00001);

        long expectedRange = 1L << (findContainingBinaryOrderOfMagnitude(trackableValueRangeSize) + 1);
        assertEquals(expectedRange, (topValue / bottomValue), 0.00001);
        assertEquals(1L, histogram.getCountAtValue(0.0));
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testRecordValue(Class histoClass) throws Exception {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        assertEquals(1L, histogram.getCountAtValue(testValueLevel));
        assertEquals(1L, histogram.getTotalCount());
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testRecordValue_Overflow_ShouldThrowException(final Class histoClass) throws Exception {
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class,
                new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        DoubleHistogram histogram =
                                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
                        histogram.recordValue(trackableValueRangeSize * 3);
                        histogram.recordValue(1.0);
                    }
                });
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testRecordValueWithExpectedInterval(Class histoClass) throws Exception {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(0);
        histogram.recordValueWithExpectedInterval(testValueLevel, testValueLevel/4);
        DoubleHistogram rawHistogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        rawHistogram.recordValue(0);
        rawHistogram.recordValue(testValueLevel);
        // The raw data will not include corrected samples:
        assertEquals(1L, rawHistogram.getCountAtValue(0));
        assertEquals(0L, rawHistogram.getCountAtValue((testValueLevel * 1 )/4));
        assertEquals(0L, rawHistogram.getCountAtValue((testValueLevel * 2 )/4));
        assertEquals(0L, rawHistogram.getCountAtValue((testValueLevel * 3 )/4));
        assertEquals(1L, rawHistogram.getCountAtValue((testValueLevel * 4 )/4));
        assertEquals(2L, rawHistogram.getTotalCount());
        // The data will include corrected samples:
        assertEquals(1L, histogram.getCountAtValue(0));
        assertEquals(1L, histogram.getCountAtValue((testValueLevel * 1 )/4));
        assertEquals(1L, histogram.getCountAtValue((testValueLevel * 2 )/4));
        assertEquals(1L, histogram.getCountAtValue((testValueLevel * 3 )/4));
        assertEquals(1L, histogram.getCountAtValue((testValueLevel * 4 )/4));
        assertEquals(5L, histogram.getTotalCount());
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testReset(final Class histoClass) throws Exception {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(10);
        histogram.recordValue(100);
        Assert.assertEquals(histogram.getMinValue(), Math.min(10.0, testValueLevel), 1.0);
        Assert.assertEquals(histogram.getMaxValue(), Math.max(100.0, testValueLevel), 1.0);
        histogram.reset();
        assertEquals(0L, histogram.getCountAtValue(testValueLevel));
        assertEquals(0L, histogram.getTotalCount());
        histogram.recordValue(20);
        histogram.recordValue(80);
        Assert.assertEquals(histogram.getMinValue(), 20.0, 1.0);
        Assert.assertEquals(histogram.getMaxValue(), 80.0, 1.0);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testAdd(final Class histoClass) throws Exception {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        DoubleHistogram other =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);

        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 1000);
        other.recordValue(testValueLevel);
        other.recordValue(testValueLevel * 1000);
        histogram.add(other);
        assertEquals(2L, histogram.getCountAtValue(testValueLevel));
        assertEquals(2L, histogram.getCountAtValue(testValueLevel * 1000));
        assertEquals(4L, histogram.getTotalCount());

        DoubleHistogram biggerOther =
                constructDoubleHistogram(histoClass, trackableValueRangeSize * 2, numberOfSignificantValueDigits);
        biggerOther.recordValue(testValueLevel);
        biggerOther.recordValue(testValueLevel * 1000);

        // Adding the smaller histogram to the bigger one should work:
        biggerOther.add(histogram);
        assertEquals(3L, biggerOther.getCountAtValue(testValueLevel));
        assertEquals(3L, biggerOther.getCountAtValue(testValueLevel * 1000));
        assertEquals(6L, biggerOther.getTotalCount());

        // Since we are auto-sized, trying to add a larger histogram into a smaller one should work if no
        // overflowing data is there:
        try {
            // This should throw:
            histogram.add(biggerOther);
        } catch (ArrayIndexOutOfBoundsException e) {
            fail("Should not thow with out of bounds error");
        }

        // But trying to add smaller values to a larger histogram that actually uses it's range should throw an AIOOB:
        histogram.recordValue(1.0);
        other.recordValue(1.0);
        biggerOther.recordValue(trackableValueRangeSize * 8);

        try {
            // This should throw:
            biggerOther.add(histogram);
            fail("Should have thown with out of bounds error");
        } catch (ArrayIndexOutOfBoundsException e) {
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testAddWithAutoResize(final Class histoClass) {
        DoubleHistogram histo1 = constructDoubleHistogram(histoClass, 3);
        histo1.setAutoResize(true);
        histo1.recordValue(6.0);
        histo1.recordValue(1.0);
        histo1.recordValue(5.0);
        histo1.recordValue(8.0);
        histo1.recordValue(3.0);
        histo1.recordValue(7.0);
        DoubleHistogram histo2 = constructDoubleHistogram(histoClass, 3);
        histo2.setAutoResize(true);
        histo2.recordValue(9.0);
        DoubleHistogram histo3 = constructDoubleHistogram(histoClass, 3);
        histo3.setAutoResize(true);
        histo3.recordValue(4.0);
        histo3.recordValue(2.0);
        histo3.recordValue(10.0);

        DoubleHistogram merged = constructDoubleHistogram(histoClass, 3);
        merged.setAutoResize(true);
        merged.add(histo1);
        merged.add(histo2);
        merged.add(histo3);

        assertEquals(merged.getTotalCount(),
                histo1.getTotalCount() + histo2.getTotalCount() + histo3.getTotalCount());
        assertEquals(1.0, merged.getMinValue(), 0.01);
        assertEquals(10.0, merged.getMaxValue(), 0.01);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testSizeOfEquivalentValueRange(final Class histoClass) {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(1.0);
        assertEquals("Size of equivalent range for value 1 is 1",
                1.0/1024.0, histogram.sizeOfEquivalentValueRange(1), 0.001);
        assertEquals("Size of equivalent range for value 2500 is 2",
                2, histogram.sizeOfEquivalentValueRange(2500), 0.001);
        assertEquals("Size of equivalent range for value 8191 is 4",
                4, histogram.sizeOfEquivalentValueRange(8191), 0.001);
        assertEquals("Size of equivalent range for value 8192 is 8",
                8, histogram.sizeOfEquivalentValueRange(8192), 0.001);
        assertEquals("Size of equivalent range for value 10000 is 8",
                8, histogram.sizeOfEquivalentValueRange(10000), 0.001);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testLowestEquivalentValue(final Class histoClass) {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(1.0);
        assertEquals("The lowest equivalent value to 10007 is 10000",
                10000, histogram.lowestEquivalentValue(10007), 0.001);
        assertEquals("The lowest equivalent value to 10009 is 10008",
                10008, histogram.lowestEquivalentValue(10009), 0.001);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testHighestEquivalentValue(final Class histoClass) {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(1.0);
        assertEquals("The highest equivalent value to 8180 is 8183",
                8183.99999, histogram.highestEquivalentValue(8180), 0.001);
        assertEquals("The highest equivalent value to 8187 is 8191",
                8191.99999, histogram.highestEquivalentValue(8191), 0.001);
        assertEquals("The highest equivalent value to 8193 is 8199",
                8199.99999, histogram.highestEquivalentValue(8193), 0.001);
        assertEquals("The highest equivalent value to 9995 is 9999",
                9999.99999, histogram.highestEquivalentValue(9995), 0.001);
        assertEquals("The highest equivalent value to 10007 is 10007",
                10007.99999, histogram.highestEquivalentValue(10007), 0.001);
        assertEquals("The highest equivalent value to 10008 is 10015",
                10015.99999, histogram.highestEquivalentValue(10008), 0.001);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testMedianEquivalentValue(final Class histoClass) {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(1.0);
        assertEquals("The median equivalent value to 4 is 4",
                4.002, histogram.medianEquivalentValue(4), 0.001);
        assertEquals("The median equivalent value to 5 is 5",
                5.002, histogram.medianEquivalentValue(5), 0.001);
        assertEquals("The median equivalent value to 4000 is 4001",
                4001, histogram.medianEquivalentValue(4000), 0.001);
        assertEquals("The median equivalent value to 8000 is 8002",
                8002, histogram.medianEquivalentValue(8000), 0.001);
        assertEquals("The median equivalent value to 10007 is 10004",
                10004, histogram.medianEquivalentValue(10007), 0.001);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testNextNonEquivalentValue(final Class histoClass) {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass, trackableValueRangeSize, numberOfSignificantValueDigits);
        assertNotSame(null, histogram);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testMaxValue(final Class histoClass) {
        DoubleHistogram histogram = constructDoubleHistogram(histoClass, 1_000_000_000, 2);
        Assertions.assertNotSame(null, histogram);
        histogram.recordValue(2.5362386543);
        double maxValue = histogram.getMaxValue();
        Assertions.assertEquals(maxValue, histogram.highestEquivalentValue(2.5362386543));
    }

    void testDoubleHistogramSerialization(DoubleHistogram histogram) throws Exception {
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getCurrentHighestTrackableValue() - 1, histogram.getCurrentHighestTrackableValue() / 1000);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        ByteArrayInputStream bis = null;
        ObjectInput in = null;
        DoubleHistogram newHistogram = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(histogram);
            Deflater compresser = new Deflater();
            compresser.setInput(bos.toByteArray());
            compresser.finish();
            byte [] compressedOutput = new byte[1024*1024];
            int compressedDataLength = compresser.deflate(compressedOutput);
            System.out.println("Serialized form of " + histogram.getClass() + " with internalHighestToLowestValueRatio = " +
                    histogram.getHighestToLowestValueRatio() + "\n and a numberOfSignificantValueDigits = " +
                    histogram.getNumberOfSignificantValueDigits() + " is " + bos.toByteArray().length +
                    " bytes long. Compressed form is " + compressedDataLength + " bytes long.");
            System.out.println("   (estimated footprint was " + histogram.getEstimatedFootprintInBytes() + " bytes)");
            bis = new ByteArrayInputStream(bos.toByteArray());
            in = new ObjectInputStream(bis);
            newHistogram = (DoubleHistogram) in.readObject();
        } finally {
            if (out != null) out.close();
            bos.close();
            if (in !=null) in.close();
            if (bis != null) bis.close();
        }
        assertNotNull(newHistogram);
        assertEqual(histogram, newHistogram);
    }

    private void assertEqual(DoubleHistogram expectedHistogram, DoubleHistogram actualHistogram) {
        assertEquals(expectedHistogram, actualHistogram);
        Assert.assertTrue(expectedHistogram.hashCode() == actualHistogram.hashCode());
        assertEquals(
                expectedHistogram.getCountAtValue(testValueLevel),
                actualHistogram.getCountAtValue(testValueLevel));
        assertEquals(
                expectedHistogram.getCountAtValue(testValueLevel * 10),
                actualHistogram.getCountAtValue(testValueLevel * 10));
        assertEquals(
                expectedHistogram.getTotalCount(),
                actualHistogram.getTotalCount());
    }

    @Test
    public void equalsWillNotThrowClassCastException() {
        SynchronizedDoubleHistogram synchronizedDoubleHistogram = new SynchronizedDoubleHistogram(1);
        IntCountsHistogram other = new IntCountsHistogram(1);
        synchronizedDoubleHistogram.equals(other);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testSerialization(final Class histoClass) throws Exception {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass,trackableValueRangeSize, 3);
        testDoubleHistogramSerialization(histogram);
        histogram = constructDoubleHistogram(histoClass,trackableValueRangeSize, 2);
        testDoubleHistogramSerialization(histogram);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
    })
    public void testSerializationWithInternals(final Class histoClass) throws Exception {
        DoubleHistogram histogram =
                constructDoubleHistogram(histoClass,trackableValueRangeSize, 3);
        testDoubleHistogramSerialization(histogram);
        DoubleHistogram withIntHistogram =
                constructDoubleHistogram(histoClass,trackableValueRangeSize, 3, IntCountsHistogram.class);
        testDoubleHistogramSerialization(withIntHistogram);
        DoubleHistogram withShortHistogram =
                constructDoubleHistogram(histoClass,trackableValueRangeSize, 3, ShortCountsHistogram.class);
        testDoubleHistogramSerialization(withShortHistogram);
        histogram = constructDoubleHistogram(histoClass,trackableValueRangeSize, 2, Histogram.class);
        testDoubleHistogramSerialization(histogram);
        withIntHistogram = constructDoubleHistogram(histoClass,trackableValueRangeSize, 2, IntCountsHistogram.class);
        testDoubleHistogramSerialization(withIntHistogram);
        withShortHistogram = constructDoubleHistogram(histoClass,trackableValueRangeSize, 2, ShortCountsHistogram.class);
        testDoubleHistogramSerialization(withShortHistogram);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testCopy(final Class histoClass) throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram:");
        assertEqual(histogram, histogram.copy());

        DoubleHistogram withIntHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                IntCountsHistogram.class);
        withIntHistogram.recordValue(testValueLevel);
        withIntHistogram.recordValue(testValueLevel * 10);
        withIntHistogram.recordValueWithExpectedInterval(withIntHistogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram backed by IntHistogram:");
        assertEqual(withIntHistogram, withIntHistogram.copy());

        DoubleHistogram withShortHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                ShortCountsHistogram.class);
        withShortHistogram.recordValue(testValueLevel);
        withShortHistogram.recordValue(testValueLevel * 10);
        withShortHistogram.recordValueWithExpectedInterval(withShortHistogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram backed by ShortHistogram:");
        assertEqual(withShortHistogram, withShortHistogram.copy());

        DoubleHistogram withConcurrentHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                ConcurrentHistogram.class);
        withConcurrentHistogram.recordValue(testValueLevel);
        withConcurrentHistogram.recordValue(testValueLevel * 10);
        withConcurrentHistogram.recordValueWithExpectedInterval(withConcurrentHistogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram backed by ConcurrentHistogram:");
        assertEqual(withConcurrentHistogram, withConcurrentHistogram.copy());

        DoubleHistogram withSyncHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                SynchronizedHistogram.class);
        withSyncHistogram.recordValue(testValueLevel);
        withSyncHistogram.recordValue(testValueLevel * 10);
        withSyncHistogram.recordValueWithExpectedInterval(withSyncHistogram.getCurrentHighestTrackableValue() - 1, 31000);

        System.out.println("Testing copy of DoubleHistogram backed by SynchronizedHistogram:");
        assertEqual(withSyncHistogram, withSyncHistogram.copy());
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testCopyInto(final Class histoClass) throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        DoubleHistogram targetHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        histogram.recordValue(testValueLevel);
        histogram.recordValue(testValueLevel * 10);
        histogram.recordValueWithExpectedInterval(histogram.getCurrentHighestTrackableValue() - 1,
                histogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram:");
        histogram.copyInto(targetHistogram);
        assertEqual(histogram, targetHistogram);

        histogram.recordValue(testValueLevel * 20);

        histogram.copyInto(targetHistogram);
        assertEqual(histogram, targetHistogram);

        DoubleHistogram withIntHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                IntCountsHistogram.class);
        DoubleHistogram targetWithIntHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                IntCountsHistogram.class);
        withIntHistogram.recordValue(testValueLevel);
        withIntHistogram.recordValue(testValueLevel * 10);
        withIntHistogram.recordValueWithExpectedInterval(withIntHistogram.getCurrentHighestTrackableValue() - 1,
                withIntHistogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram backed by IntHistogram:");
        withIntHistogram.copyInto(targetWithIntHistogram);
        assertEqual(withIntHistogram, targetWithIntHistogram);

        withIntHistogram.recordValue(testValueLevel * 20);

        withIntHistogram.copyInto(targetWithIntHistogram);
        assertEqual(withIntHistogram, targetWithIntHistogram);

        DoubleHistogram withShortHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                ShortCountsHistogram.class);
        DoubleHistogram targetWithShortHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                ShortCountsHistogram.class);
        withShortHistogram.recordValue(testValueLevel);
        withShortHistogram.recordValue(testValueLevel * 10);
        withShortHistogram.recordValueWithExpectedInterval(withShortHistogram.getCurrentHighestTrackableValue() - 1,
                withShortHistogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram backed by a ShortHistogram:");
        withShortHistogram.copyInto(targetWithShortHistogram);
        assertEqual(withShortHistogram, targetWithShortHistogram);

        withShortHistogram.recordValue(testValueLevel * 20);

        withShortHistogram.copyInto(targetWithShortHistogram);
        assertEqual(withShortHistogram, targetWithShortHistogram);

        DoubleHistogram withConcurrentHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                ConcurrentHistogram.class);
        DoubleHistogram targetWithConcurrentHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                ConcurrentHistogram.class);
        withConcurrentHistogram.recordValue(testValueLevel);
        withConcurrentHistogram.recordValue(testValueLevel * 10);
        withConcurrentHistogram.recordValueWithExpectedInterval(withConcurrentHistogram.getCurrentHighestTrackableValue() - 1,
                withConcurrentHistogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram backed by ConcurrentHistogram:");
        withConcurrentHistogram.copyInto(targetWithConcurrentHistogram);
        assertEqual(withConcurrentHistogram, targetWithConcurrentHistogram);

        withConcurrentHistogram.recordValue(testValueLevel * 20);

        withConcurrentHistogram.copyInto(targetWithConcurrentHistogram);
        assertEqual(withConcurrentHistogram, targetWithConcurrentHistogram);

        ConcurrentDoubleHistogram concurrentHistogram =
                new ConcurrentDoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        ConcurrentDoubleHistogram targetConcurrentHistogram =
                new ConcurrentDoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        concurrentHistogram.recordValue(testValueLevel);
        concurrentHistogram.recordValue(testValueLevel * 10);
        concurrentHistogram.recordValueWithExpectedInterval(concurrentHistogram.getCurrentHighestTrackableValue() - 1,
                concurrentHistogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for actual ConcurrentHistogram:");
        concurrentHistogram.copyInto(targetConcurrentHistogram);
        assertEqual(concurrentHistogram, targetConcurrentHistogram);

        concurrentHistogram.recordValue(testValueLevel * 20);

        concurrentHistogram.copyInto(targetConcurrentHistogram);
        assertEqual(concurrentHistogram, targetConcurrentHistogram);

        DoubleHistogram withSyncHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                SynchronizedHistogram.class);
        DoubleHistogram targetWithSyncHistogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits,
                SynchronizedHistogram.class);
        withSyncHistogram.recordValue(testValueLevel);
        withSyncHistogram.recordValue(testValueLevel * 10);
        withSyncHistogram.recordValueWithExpectedInterval(withSyncHistogram.getCurrentHighestTrackableValue() - 1,
                withSyncHistogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for DoubleHistogram backed by SynchronizedHistogram:");
        withSyncHistogram.copyInto(targetWithSyncHistogram);
        assertEqual(withSyncHistogram, targetWithSyncHistogram);

        withSyncHistogram.recordValue(testValueLevel * 20);

        withSyncHistogram.copyInto(targetWithSyncHistogram);
        assertEqual(withSyncHistogram, targetWithSyncHistogram);

        SynchronizedDoubleHistogram syncHistogram =
                new SynchronizedDoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        SynchronizedDoubleHistogram targetSyncHistogram =
                new SynchronizedDoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        syncHistogram.recordValue(testValueLevel);
        syncHistogram.recordValue(testValueLevel * 10);
        syncHistogram.recordValueWithExpectedInterval(syncHistogram.getCurrentHighestTrackableValue() - 1,
                syncHistogram.getCurrentHighestTrackableValue() / 1000);

        System.out.println("Testing copyInto for actual SynchronizedDoubleHistogram:");
        syncHistogram.copyInto(targetSyncHistogram);
        assertEqual(syncHistogram, targetSyncHistogram);

        syncHistogram.recordValue(testValueLevel * 20);

        syncHistogram.copyInto(targetSyncHistogram);
        assertEqual(syncHistogram, targetSyncHistogram);
    }

    private int findContainingBinaryOrderOfMagnitude(long longNumber) {
        int pow2ceiling = 64 - Long.numberOfLeadingZeros(longNumber); // smallest power of 2 containing value
        pow2ceiling = Math.min(pow2ceiling, 62);
        return pow2ceiling;
    }

    private void genericResizeTest(DoubleHistogram h) {
        h.recordValue(0);
        h.recordValue(5);
        h.recordValue(1);
        h.recordValue(8);
        h.recordValue(9);

        Assert.assertEquals(9.0, h.getValueAtPercentile(100), 0.1d);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testResize(final Class histoClass) {
        // Verify resize behvaior for various underlying internal integer histogram implementations:
        genericResizeTest(constructDoubleHistogram(histoClass, 2));
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
    })
    public void testResizeInternals(final Class histoClass) {
        // Verify resize behvaior for various underlying internal integer histogram implementations:
        genericResizeTest(constructDoubleHistogram(histoClass, 2));
        genericResizeTest(constructDoubleHistogram(histoClass,2, IntCountsHistogram.class));
        genericResizeTest(constructDoubleHistogram(histoClass,2, ShortCountsHistogram.class));
        genericResizeTest(constructDoubleHistogram(histoClass,2, ConcurrentHistogram.class));
        genericResizeTest(constructDoubleHistogram(histoClass,2, SynchronizedHistogram.class));
        genericResizeTest(constructDoubleHistogram(histoClass,2, PackedHistogram.class));
        genericResizeTest(constructDoubleHistogram(histoClass,2, PackedConcurrentHistogram.class));
    }
}
