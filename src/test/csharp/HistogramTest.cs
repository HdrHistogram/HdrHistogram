/*
 * Written by Matt Warren, and released to the public domain,
 * as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This is a .NET port of the original Java version, which was written by
 * Gil Tene as described in
 * https://github.com/HdrHistogram/HdrHistogram
 */

using System;
using HdrHistogram.NET.Utilities;
using NUnit.Framework;
using Assert = HdrHistogram.NET.Test.AssertEx;

namespace HdrHistogram.NET.Test
{
    public class HistogramTest
    {
        static long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
        static int numberOfSignificantValueDigits = 3;
        //static long testValueLevel = 12340;
        static long testValueLevel = 4;

        [Test]
        public void testConstructionArgumentRanges()  
        {
            Boolean thrown = false;
            Histogram histogram = null;

            try
            {
                // This should throw:
                histogram = new Histogram(1, numberOfSignificantValueDigits);
            }
            catch (ArgumentException) 
            {
                thrown = true;
            }
            Assert.assertTrue(thrown);
            Assert.assertEquals(histogram, null);

            thrown = false;
            try 
            {
                // This should throw:
                histogram = new Histogram(highestTrackableValue, 6);
            }
            catch (ArgumentException) 
            {
                thrown = true;
            }
            Assert.assertTrue(thrown);
            Assert.assertEquals(histogram, null);

            thrown = false;
            try 
            {
                // This should throw:
                histogram = new Histogram(highestTrackableValue, -1);
            }
            catch (ArgumentException) 
            {
                thrown = true;
            }
            Assert.assertTrue(thrown);
            Assert.assertEquals(histogram, null);
        }

        [Test]
        public void testConstructionArgumentGets()  
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            Assert.assertEquals(1, histogram.getLowestTrackableValue());
            Assert.assertEquals(highestTrackableValue, histogram.getHighestTrackableValue());
            Assert.assertEquals(numberOfSignificantValueDigits, histogram.getNumberOfSignificantValueDigits());
            Histogram histogram2 = new Histogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            Assert.assertEquals(1000, histogram2.getLowestTrackableValue());
        }

        [Test]
        public void testGetEstimatedFootprintInBytes()  
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            /*
            *     largestValueWithSingleUnitResolution = 2 * (10 ^ numberOfSignificantValueDigits);
            *     subBucketSize = roundedUpToNearestPowerOf2(largestValueWithSingleUnitResolution);

            *     expectedHistogramFootprintInBytes = 512 +
            *          ({primitive type size} / 2) *
            *          (log2RoundedUp((highestTrackableValue) / subBucketSize) + 2) *
            *          subBucketSize
            */
            long largestValueWithSingleUnitResolution = 2 * (long) Math.Pow(10, numberOfSignificantValueDigits);
            int subBucketCountMagnitude = (int)Math.Ceiling(Math.Log(largestValueWithSingleUnitResolution) / Math.Log(2));
            int subBucketSize = (int) Math.Pow(2, (subBucketCountMagnitude));

            long expectedSize = 512 +
                    ((8 *
                     ((long)(
                            Math.Ceiling(
                             Math.Log(highestTrackableValue / subBucketSize)
                                     / Math.Log(2)
                            )
                           + 2)) *
                        (1 << (64 - MiscUtilities.numberOfLeadingZeros(2 * (long)Math.Pow(10, numberOfSignificantValueDigits))))
                     ) / 2);
            Assert.assertEquals(expectedSize, histogram.getEstimatedFootprintInBytes());
        }

        [Test]
        public void testRecordValue()  
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            histogram.recordValue(testValueLevel);
            Assert.assertEquals(1L, histogram.getHistogramData().getCountAtValue(testValueLevel));
            Assert.assertEquals(1L, histogram.getHistogramData().getTotalCount());
        }

        [Test]
        [ExpectedException(typeof(IndexOutOfRangeException))]
        public void testRecordValue_Overflow_ShouldThrowException()  
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            histogram.recordValue(highestTrackableValue * 3);
        }

        [Test]
        public void testRecordValueWithExpectedInterval()  
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            histogram.recordValueWithExpectedInterval(testValueLevel, testValueLevel/4);
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

        [Test]
        public void testReset()  
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            histogram.recordValue(testValueLevel);
            histogram.reset();
            Assert.assertEquals(0L, histogram.getHistogramData().getCountAtValue(testValueLevel));
            Assert.assertEquals(0L, histogram.getHistogramData().getTotalCount());
        }

        [Test]
        public void testAdd()  
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            Histogram other = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            histogram.recordValue(testValueLevel);
            histogram.recordValue(testValueLevel * 1000);
            other.recordValue(testValueLevel);
            other.recordValue(testValueLevel * 1000);
            histogram.add(other);
            Assert.assertEquals(2L, histogram.getHistogramData().getCountAtValue(testValueLevel));
            Assert.assertEquals(2L, histogram.getHistogramData().getCountAtValue(testValueLevel * 1000));
            Assert.assertEquals(4L, histogram.getHistogramData().getTotalCount());

            Histogram biggerOther = new Histogram(highestTrackableValue * 2, numberOfSignificantValueDigits);
            biggerOther.recordValue(testValueLevel);
            biggerOther.recordValue(testValueLevel * 1000);

            // Adding the smaller histogram to the bigger one should work:
            biggerOther.add(histogram);
            Assert.assertEquals(3L, biggerOther.getHistogramData().getCountAtValue(testValueLevel));
            Assert.assertEquals(3L, biggerOther.getHistogramData().getCountAtValue(testValueLevel * 1000));
            Assert.assertEquals(6L, biggerOther.getHistogramData().getTotalCount());

            // But trying to add a larger histogram into a smaller one should throw an AIOOB:
            bool thrown = false;
            try 
            {
                // This should throw:
                histogram.add(biggerOther);
            }
            catch (ArgumentOutOfRangeException)
            {
                thrown = true;
            }
            Assert.assertTrue(thrown);
        }

        [Test]
        public void testSizeOfEquivalentValueRange() 
        {
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

        [Test]
        public void testScaledSizeOfEquivalentValueRange() 
        {
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
        }

        [Test]
        public void testLowestEquivalentValue() 
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            Assert.assertEquals("The lowest equivalent value to 10007 is 10000",
                    10000, histogram.lowestEquivalentValue(10007));
            Assert.assertEquals("The lowest equivalent value to 10009 is 10008",
                    10008, histogram.lowestEquivalentValue(10009));
        }

        [Test]
        public void testScaledLowestEquivalentValue() 
        {
            Histogram histogram = new Histogram(1024, highestTrackableValue, numberOfSignificantValueDigits);
            Assert.assertEquals("The lowest equivalent value to 10007 * 1024 is 10000 * 1024",
                    10000 * 1024, histogram.lowestEquivalentValue(10007 * 1024));
            Assert.assertEquals("The lowest equivalent value to 10009 * 1024 is 10008 * 1024",
                    10008 * 1024, histogram.lowestEquivalentValue(10009 * 1024));
        }

        [Test]
        public void testHighestEquivalentValue() 
        {
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
        }

        [Test]
        public void testScaledHighestEquivalentValue() 
        {
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

        [Test]
        public void testMedianEquivalentValue() 
        {
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

        [Test]
        public void testScaledMedianEquivalentValue() 
        {
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
        }

        [Test]
        public void testNextNonEquivalentValue() 
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            Assert.assertNotSame(null, histogram);
        }

        //void testAbstractSerialization(AbstractHistogram histogram) throws Exception {
        //    histogram.recordValue(testValueLevel);
        //    histogram.recordValue(testValueLevel * 10);
        //    histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 31);
        //    ByteArrayOutputStream bos = new ByteArrayOutputStream();
        //    ObjectOutput out = null;
        //    ByteArrayInputStream bis = null;
        //    ObjectInput in = null;
        //    AbstractHistogram newHistogram = null;
        //    try {
        //        out = new ObjectOutputStream(bos);
        //        out.writeObject(histogram);
        //        Deflater compresser = new Deflater();
        //        compresser.setInput(bos.toByteArray());
        //        compresser.finish();
        //        byte [] compressedOutput = new byte[1024*1024];
        //        int compressedDataLength = compresser.deflate(compressedOutput);
        //        Console.WriteLine("Serialized form of " + histogram.getClass() + " with highestTrackableValue = " +
        //                histogram.getHighestTrackableValue() + "\n and a numberOfSignificantValueDigits = " +
        //                histogram.getNumberOfSignificantValueDigits() + " is " + bos.toByteArray().length +
        //                " bytes long. Compressed form is " + compressedDataLength + " bytes long.");
        //        Console.WriteLine("   (estimated footprint was " + histogram.getEstimatedFootprintInBytes() + " bytes)");
        //        bis = new ByteArrayInputStream(bos.toByteArray());
        //        in = new ObjectInputStream(bis);
        //        newHistogram = (AbstractHistogram) in.readObject();
        //    } finally {
        //        if (out != null) out.close();
        //        bos.close();
        //        if (in !=null) in.close();
        //        if (bis != null) bis.close();
        //    }
        //    Assert.assertNotNull(newHistogram);
        //    assertEqual(histogram, newHistogram);
        //}

        private void assertEqual(AbstractHistogram expectedHistogram, AbstractHistogram actualHistogram)
        {
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

        //[Test]
        //public void testSerialization() throws Exception {
        //    Histogram histogram = new Histogram(highestTrackableValue, 3);
        //    testAbstractSerialization(histogram);
        //    IntHistogram intHistogram = new IntHistogram(highestTrackableValue, 3);
        //    testAbstractSerialization(intHistogram);
        //    ShortHistogram shortHistogram = new ShortHistogram(highestTrackableValue, 3);
        //    testAbstractSerialization(shortHistogram);
        //    histogram = new Histogram(highestTrackableValue, 2);
        //    testAbstractSerialization(histogram);
        //    intHistogram = new IntHistogram(highestTrackableValue, 2);
        //    testAbstractSerialization(intHistogram);
        //    shortHistogram = new ShortHistogram(highestTrackableValue, 2);
        //    testAbstractSerialization(shortHistogram);
        //}

        [Test]
        public void testOverflow()  
        {
            ShortHistogram histogram = new ShortHistogram(highestTrackableValue, 2);
            histogram.recordValue(testValueLevel);
            histogram.recordValue(testValueLevel * 10);
            Assert.assertFalse(histogram.hasOverflowed());
            // This should overflow a ShortHistogram:
            histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 500);
            Assert.assertTrue(histogram.hasOverflowed());
            Console.WriteLine("Histogram percentile output should show overflow:");
            histogram.getHistogramData().outputPercentileDistribution(Console.Out, 5, 100.0);
            Console.WriteLine("\nHistogram percentile output should be in CSV format and show overflow:");
            histogram.getHistogramData().outputPercentileDistribution(Console.Out, 5, 100.0, true);
            Console.WriteLine("");
        }

        [Test]
        public void testReestablishTotalCount()  
        {
            ShortHistogram histogram = new ShortHistogram(highestTrackableValue, 2);
            histogram.recordValue(testValueLevel);
            histogram.recordValue(testValueLevel * 10);
            Assert.assertFalse(histogram.hasOverflowed());
            // This should overflow a ShortHistogram:
            histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 500);
            Assert.assertTrue(histogram.hasOverflowed());
            histogram.reestablishTotalCount();
            Assert.assertFalse(histogram.hasOverflowed());
        }

        [Test]
        public void testCopy()
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            histogram.recordValue(testValueLevel);
            histogram.recordValue(testValueLevel * 10);
            histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of Histogram:");
            assertEqual(histogram, histogram.copy());

            IntHistogram intHistogram = new IntHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            intHistogram.recordValue(testValueLevel);
            intHistogram.recordValue(testValueLevel * 10);
            intHistogram.recordValueWithExpectedInterval(intHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of IntHistogram:");
            assertEqual(intHistogram, intHistogram.copy());

            ShortHistogram shortHistogram = new ShortHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            shortHistogram.recordValue(testValueLevel);
            shortHistogram.recordValue(testValueLevel * 10);
            shortHistogram.recordValueWithExpectedInterval(shortHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of ShortHistogram:");
            assertEqual(shortHistogram, shortHistogram.copy());

            AtomicHistogram atomicHistogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            atomicHistogram.recordValue(testValueLevel);
            atomicHistogram.recordValue(testValueLevel * 10);
            atomicHistogram.recordValueWithExpectedInterval(atomicHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of AtomicHistogram:");
            assertEqual(atomicHistogram, atomicHistogram.copy());

            SynchronizedHistogram syncHistogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            syncHistogram.recordValue(testValueLevel);
            syncHistogram.recordValue(testValueLevel * 10);
            syncHistogram.recordValueWithExpectedInterval(syncHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of SynchronizedHistogram:");
            assertEqual(syncHistogram, syncHistogram.copy());
        }

        [Test]
        public void testScaledCopy()  
        {
            Histogram histogram = new Histogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            histogram.recordValue(testValueLevel);
            histogram.recordValue(testValueLevel * 10);
            histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of scaled Histogram:");
            assertEqual(histogram, histogram.copy());

            IntHistogram intHistogram = new IntHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            intHistogram.recordValue(testValueLevel);
            intHistogram.recordValue(testValueLevel * 10);
            intHistogram.recordValueWithExpectedInterval(intHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of scaled IntHistogram:");
            assertEqual(intHistogram, intHistogram.copy());

            ShortHistogram shortHistogram = new ShortHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            shortHistogram.recordValue(testValueLevel);
            shortHistogram.recordValue(testValueLevel * 10);
            shortHistogram.recordValueWithExpectedInterval(shortHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of scaled ShortHistogram:");
            assertEqual(shortHistogram, shortHistogram.copy());

            AtomicHistogram atomicHistogram = new AtomicHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            atomicHistogram.recordValue(testValueLevel);
            atomicHistogram.recordValue(testValueLevel * 10);
            atomicHistogram.recordValueWithExpectedInterval(atomicHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of scaled AtomicHistogram:");
            assertEqual(atomicHistogram, atomicHistogram.copy());

            SynchronizedHistogram syncHistogram = new SynchronizedHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            syncHistogram.recordValue(testValueLevel);
            syncHistogram.recordValue(testValueLevel * 10);
            syncHistogram.recordValueWithExpectedInterval(syncHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copy of scaled SynchronizedHistogram:");
            assertEqual(syncHistogram, syncHistogram.copy());
        }

        [Test]
        public void testCopyInto()  
        {
            Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            Histogram targetHistogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
            histogram.recordValue(testValueLevel);
            histogram.recordValue(testValueLevel * 10);
            histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copyInto for Histogram:");
            histogram.copyInto(targetHistogram);
            assertEqual(histogram, targetHistogram);

            histogram.recordValue(testValueLevel * 20);

            histogram.copyInto(targetHistogram);
            assertEqual(histogram, targetHistogram);

            IntHistogram intHistogram = new IntHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            IntHistogram targetIntHistogram = new IntHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            intHistogram.recordValue(testValueLevel);
            intHistogram.recordValue(testValueLevel * 10);
            intHistogram.recordValueWithExpectedInterval(intHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copyInto for IntHistogram:");
            intHistogram.copyInto(targetIntHistogram);
            assertEqual(intHistogram, targetIntHistogram);

            intHistogram.recordValue(testValueLevel * 20);

            intHistogram.copyInto(targetIntHistogram);
            assertEqual(intHistogram, targetIntHistogram);

            ShortHistogram shortHistogram = new ShortHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            ShortHistogram targetShortHistogram = new ShortHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            shortHistogram.recordValue(testValueLevel);
            shortHistogram.recordValue(testValueLevel * 10);
            shortHistogram.recordValueWithExpectedInterval(shortHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copyInto for ShortHistogram:");
            shortHistogram.copyInto(targetShortHistogram);
            assertEqual(shortHistogram, targetShortHistogram);

            shortHistogram.recordValue(testValueLevel * 20);

            shortHistogram.copyInto(targetShortHistogram);
            assertEqual(shortHistogram, targetShortHistogram);

            AtomicHistogram atomicHistogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            AtomicHistogram targetAtomicHistogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            atomicHistogram.recordValue(testValueLevel);
            atomicHistogram.recordValue(testValueLevel * 10);
            atomicHistogram.recordValueWithExpectedInterval(atomicHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copyInto for AtomicHistogram:");
            atomicHistogram.copyInto(targetAtomicHistogram);
            assertEqual(atomicHistogram, targetAtomicHistogram);

            atomicHistogram.recordValue(testValueLevel * 20);

            atomicHistogram.copyInto(targetAtomicHistogram);
            assertEqual(atomicHistogram, targetAtomicHistogram);

            SynchronizedHistogram syncHistogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            SynchronizedHistogram targetSyncHistogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
            syncHistogram.recordValue(testValueLevel);
            syncHistogram.recordValue(testValueLevel * 10);
            syncHistogram.recordValueWithExpectedInterval(syncHistogram.getHighestTrackableValue() - 1, 31000); // Should this really be 31, if it is the test takes 1min!!!);

            Console.WriteLine("Testing copyInto for SynchronizedHistogram:");
            syncHistogram.copyInto(targetSyncHistogram);
            assertEqual(syncHistogram, targetSyncHistogram);

            syncHistogram.recordValue(testValueLevel * 20);

            syncHistogram.copyInto(targetSyncHistogram);
            assertEqual(syncHistogram, targetSyncHistogram);
        }

        [Test]
        public void testScaledCopyInto()  
        {
            Histogram histogram = new Histogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            Histogram targetHistogram = new Histogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            histogram.recordValue(testValueLevel);
            histogram.recordValue(testValueLevel * 10);
            histogram.recordValueWithExpectedInterval(histogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copyInto for scaled Histogram:");
            histogram.copyInto(targetHistogram);
            assertEqual(histogram, targetHistogram);

            histogram.recordValue(testValueLevel * 20);

            histogram.copyInto(targetHistogram);
            assertEqual(histogram, targetHistogram);

            IntHistogram intHistogram = new IntHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            IntHistogram targetIntHistogram = new IntHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            intHistogram.recordValue(testValueLevel);
            intHistogram.recordValue(testValueLevel * 10);
            intHistogram.recordValueWithExpectedInterval(intHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copyInto for scaled IntHistogram:");
            intHistogram.copyInto(targetIntHistogram);
            assertEqual(intHistogram, targetIntHistogram);

            intHistogram.recordValue(testValueLevel * 20);

            intHistogram.copyInto(targetIntHistogram);
            assertEqual(intHistogram, targetIntHistogram);

            ShortHistogram shortHistogram = new ShortHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            ShortHistogram targetShortHistogram = new ShortHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            shortHistogram.recordValue(testValueLevel);
            shortHistogram.recordValue(testValueLevel * 10);
            shortHistogram.recordValueWithExpectedInterval(shortHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copyInto for scaled ShortHistogram:");
            shortHistogram.copyInto(targetShortHistogram);
            assertEqual(shortHistogram, targetShortHistogram);

            shortHistogram.recordValue(testValueLevel * 20);

            shortHistogram.copyInto(targetShortHistogram);
            assertEqual(shortHistogram, targetShortHistogram);

            AtomicHistogram atomicHistogram = new AtomicHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            AtomicHistogram targetAtomicHistogram = new AtomicHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            atomicHistogram.recordValue(testValueLevel);
            atomicHistogram.recordValue(testValueLevel * 10);
            atomicHistogram.recordValueWithExpectedInterval(atomicHistogram.getHighestTrackableValue() - 1, 31000);

            atomicHistogram.copyInto(targetAtomicHistogram);
            assertEqual(atomicHistogram, targetAtomicHistogram);

            atomicHistogram.recordValue(testValueLevel * 20);

            Console.WriteLine("Testing copyInto for scaled AtomicHistogram:");
            atomicHistogram.copyInto(targetAtomicHistogram);
            assertEqual(atomicHistogram, targetAtomicHistogram);

            SynchronizedHistogram syncHistogram = new SynchronizedHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            SynchronizedHistogram targetSyncHistogram = new SynchronizedHistogram(1000, highestTrackableValue, numberOfSignificantValueDigits);
            syncHistogram.recordValue(testValueLevel);
            syncHistogram.recordValue(testValueLevel * 10);
            syncHistogram.recordValueWithExpectedInterval(syncHistogram.getHighestTrackableValue() - 1, 31000);

            Console.WriteLine("Testing copyInto for scaled SynchronizedHistogram:");
            syncHistogram.copyInto(targetSyncHistogram);
            assertEqual(syncHistogram, targetSyncHistogram);

            syncHistogram.recordValue(testValueLevel * 20);

            syncHistogram.copyInto(targetSyncHistogram);
            assertEqual(syncHistogram, targetSyncHistogram);
        }
    }
}
