/*
 * Written by Matt Warren, and released to the public domain,
 * as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This is a .NET port of the original Java version, which was written by
 * Gil Tene as described in
 * https://github.com/HdrHistogram/HdrHistogram
 */

using HdrHistogram.NET.Utilities;
using NUnit.Framework;
using System;
using System.Text;
using Assert = HdrHistogram.NET.Test.AssertEx;

namespace HdrHistogram.NET.Test
{
    /**
     * JUnit test for {@link org.HdrHistogram.Histogram}
     */
    public class HistogramEncodingTest 
    {
        static readonly long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
        static readonly int numberOfSignificantValueDigits = 3;
        // static readonly long testValueLevel = 12340;
        static readonly long testValueLevel = 4;

        [Test]
        public void testHistogramEncoding() //throws Exception 
        {
            ShortHistogram shortHistogram = new ShortHistogram(highestTrackableValue, 3);
            IntHistogram intHistogram = new IntHistogram(highestTrackableValue, 3);
            Histogram histogram = new Histogram(highestTrackableValue, 3);
            AtomicHistogram atomicHistogram = new AtomicHistogram(highestTrackableValue, 3);
            SynchronizedHistogram synchronizedHistogram = new SynchronizedHistogram(highestTrackableValue, 3);

            for (int i = 0; i < 10000; i++) {
                shortHistogram.recordValueWithExpectedInterval(1000 /* 1 msec */, 10000 /* 10 msec expected interval */);
                intHistogram.recordValueWithExpectedInterval(2000 /* 1 msec */, 10000 /* 10 msec expected interval */);
                histogram.recordValueWithExpectedInterval(3000 /* 1 msec */, 10000 /* 10 msec expected interval */);
                atomicHistogram.recordValueWithExpectedInterval(4000 /* 1 msec */, 10000 /* 10 msec expected interval */);
                synchronizedHistogram.recordValueWithExpectedInterval(5000 /* 1 msec */, 10000 /* 10 msec expected interval */);
            }

            Console.WriteLine("\n\nTesting encoding of a ShortHistogram:");
            ByteBuffer targetBuffer = ByteBuffer.allocate(shortHistogram.getNeededByteBufferCapacity());
            shortHistogram.encodeIntoByteBuffer(targetBuffer);
            //Console.WriteLine("After ENCODING TargetBuffer length = {0} (position {1}), shortHistogram size = {2}",
            //                targetBuffer.capacity(), targetBuffer.position(), shortHistogram.getTotalCount());
            targetBuffer.rewind();

            ShortHistogram shortHistogram2 = ShortHistogram.decodeFromByteBuffer(targetBuffer, 0);
            Assert.assertEquals(shortHistogram, shortHistogram2);

            ByteBuffer targetCompressedBuffer = ByteBuffer.allocate(shortHistogram.getNeededByteBufferCapacity());
            shortHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
            targetCompressedBuffer.rewind();

            ShortHistogram shortHistogram3 = ShortHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
            Assert.assertEquals(shortHistogram, shortHistogram3);

            Console.WriteLine("\n\nTesting encoding of a IntHistogram:");
            targetBuffer = ByteBuffer.allocate(intHistogram.getNeededByteBufferCapacity());
            intHistogram.encodeIntoByteBuffer(targetBuffer);
            //Console.WriteLine("After ENCODING TargetBuffer length = {0} (position = {1}), intHistogram size = {2}", 
            //                targetBuffer.capacity(), targetBuffer.position(), intHistogram.getTotalCount());
            targetBuffer.rewind();

            IntHistogram intHistogram2 = IntHistogram.decodeFromByteBuffer(targetBuffer, 0);
            Assert.assertEquals(intHistogram, intHistogram2);

            targetCompressedBuffer = ByteBuffer.allocate(intHistogram.getNeededByteBufferCapacity());
            intHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
            targetCompressedBuffer.rewind();

            IntHistogram intHistogram3 = IntHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
            Assert.assertEquals(intHistogram, intHistogram3);

            Console.WriteLine("\n\nTesting encoding of a Histogram (long):");
            targetBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
            histogram.encodeIntoByteBuffer(targetBuffer);
            //Console.WriteLine("After ENCODING TargetBuffer length = {0} (position = {1}), histogram size = {2}",
            //                targetBuffer.capacity(), targetBuffer.position(), histogram.getTotalCount());
            targetBuffer.rewind();

            Histogram histogram2 = Histogram.decodeFromByteBuffer(targetBuffer, 0);
            Assert.assertEquals(histogram, histogram2);

            targetCompressedBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
            histogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
            targetCompressedBuffer.rewind();

            Histogram histogram3 = Histogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
            Assert.assertEquals(histogram, histogram3);

            Console.WriteLine("\n\nTesting encoding of a AtomicHistogram (long):");
            targetBuffer = ByteBuffer.allocate(atomicHistogram.getNeededByteBufferCapacity());
            atomicHistogram.encodeIntoByteBuffer(targetBuffer);
            //Console.WriteLine("After ENCODING TargetBuffer length = {0} (position {1}), atomicHistogram size = {2}",
            //                targetBuffer.capacity(), targetBuffer.position(), atomicHistogram.getTotalCount());
            targetBuffer.rewind();

            AtomicHistogram atomicHistogram2 = AtomicHistogram.decodeFromByteBuffer(targetBuffer, 0);
            Assert.assertEquals(atomicHistogram, atomicHistogram2);

            targetCompressedBuffer = ByteBuffer.allocate(atomicHistogram.getNeededByteBufferCapacity());
            atomicHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
            targetCompressedBuffer.rewind();

            AtomicHistogram atomicHistogram3 = AtomicHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
            Assert.assertEquals(atomicHistogram, atomicHistogram3);

            Console.WriteLine("\n\nTesting encoding of a SynchronizedHistogram:");
            targetBuffer = ByteBuffer.allocate(synchronizedHistogram.getNeededByteBufferCapacity());
            synchronizedHistogram.encodeIntoByteBuffer(targetBuffer);
            //Console.WriteLine("After ENCODING TargetBuffer length = {0} (position {1}), synchronizedHistogram size = {2}",
            //                targetBuffer.capacity(), targetBuffer.position(), synchronizedHistogram.getTotalCount());
            targetBuffer.rewind();

            SynchronizedHistogram synchronizedHistogram2 = SynchronizedHistogram.decodeFromByteBuffer(targetBuffer, 0);
            Assert.assertEquals(synchronizedHistogram, synchronizedHistogram2);

            targetCompressedBuffer = ByteBuffer.allocate(synchronizedHistogram.getNeededByteBufferCapacity());
            synchronizedHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
            targetCompressedBuffer.rewind();

            SynchronizedHistogram synchronizedHistogram3 = SynchronizedHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
            Assert.assertEquals(synchronizedHistogram, synchronizedHistogram3);
        }

        [Test]
        public void testHistogramEncodingFullRangeOfValues() //throws Exception 
        {
            Histogram histogram = new Histogram(highestTrackableValue, 3);

            for (long i = 0; i < highestTrackableValue; i += 100) 
            {
                histogram.recordValue(i);
            }
            histogram.recordValue(highestTrackableValue);

            Console.WriteLine("\n\nTesting encoding of a Histogram (long):");
            var targetBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
            histogram.encodeIntoByteBuffer(targetBuffer);
            targetBuffer.rewind();

            Histogram histogram2 = Histogram.decodeFromByteBuffer(targetBuffer, 0);
            Assert.assertEquals(histogram, histogram2);

            var targetCompressedBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
            histogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
            targetCompressedBuffer.rewind();

            Histogram histogram3 = Histogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
            Assert.assertEquals(histogram, histogram3);

            Console.WriteLine();
            histogram3.outputPercentileDistribution(Console.Out);
        }
    }
}
