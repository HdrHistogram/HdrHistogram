/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import org.junit.Assert;
import org.junit.*;

import java.nio.ByteBuffer;

/**
 * JUnit test for {@link org.HdrHistogram.Histogram}
 */
public class HistogramEncodingTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units

    @Test
    public void testHistogramEncoding() throws Exception {

        ShortCountsHistogram shortCountsHistogram = new ShortCountsHistogram(highestTrackableValue, 3);
        IntCountsHistogram intCountsHistogram = new IntCountsHistogram(highestTrackableValue, 3);
        Histogram histogram = new Histogram(highestTrackableValue, 3);
        AtomicHistogram atomicHistogram = new AtomicHistogram(highestTrackableValue, 3);
        SynchronizedHistogram synchronizedHistogram = new SynchronizedHistogram(highestTrackableValue, 3);
        DoubleHistogram doubleHistogram = new DoubleHistogram(highestTrackableValue * 1000, 3);

        for (int i = 0; i < 10000; i++) {
            shortCountsHistogram.recordValueWithExpectedInterval(1000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intCountsHistogram.recordValueWithExpectedInterval(2000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            histogram.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            atomicHistogram.recordValueWithExpectedInterval(4000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            synchronizedHistogram.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            doubleHistogram.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            doubleHistogram.recordValue(0.001); // Makes some internal shifts happen.
        }

        System.out.println("\nTesting encoding of a ShortHistogram:");
        ByteBuffer targetBuffer = ByteBuffer.allocate(shortCountsHistogram.getNeededByteBufferCapacity());
        shortCountsHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        ShortCountsHistogram shortCountsHistogram2 = ShortCountsHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(shortCountsHistogram, shortCountsHistogram2);

        ByteBuffer targetCompressedBuffer = ByteBuffer.allocate(shortCountsHistogram.getNeededByteBufferCapacity());
        shortCountsHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        ShortCountsHistogram shortCountsHistogram3 = ShortCountsHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(shortCountsHistogram, shortCountsHistogram3);

        System.out.println("\nTesting encoding of a IntHistogram:");
        targetBuffer = ByteBuffer.allocate(intCountsHistogram.getNeededByteBufferCapacity());
        intCountsHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        IntCountsHistogram intCountsHistogram2 = IntCountsHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(intCountsHistogram, intCountsHistogram2);

        targetCompressedBuffer = ByteBuffer.allocate(intCountsHistogram.getNeededByteBufferCapacity());
        intCountsHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        IntCountsHistogram intCountsHistogram3 = IntCountsHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(intCountsHistogram, intCountsHistogram3);

        System.out.println("\nTesting encoding of a Histogram:");
        targetBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        histogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        Histogram histogram2 = Histogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(histogram, histogram2);

        targetCompressedBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        histogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        Histogram histogram3 = Histogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(histogram, histogram3);

        System.out.println("\nTesting encoding of a AtomicHistogram:");
        targetBuffer = ByteBuffer.allocate(atomicHistogram.getNeededByteBufferCapacity());
        atomicHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        AtomicHistogram atomicHistogram2 = AtomicHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(atomicHistogram, atomicHistogram2);

        targetCompressedBuffer = ByteBuffer.allocate(atomicHistogram.getNeededByteBufferCapacity());
        atomicHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        AtomicHistogram atomicHistogram3 = AtomicHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(atomicHistogram, atomicHistogram3);

        System.out.println("\nTesting encoding of a SynchronizedHistogram:");
        targetBuffer = ByteBuffer.allocate(synchronizedHistogram.getNeededByteBufferCapacity());
        synchronizedHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        SynchronizedHistogram synchronizedHistogram2 = SynchronizedHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(synchronizedHistogram, synchronizedHistogram2);

        synchronizedHistogram.setIntegerToDoubleValueConversionRatio(5.0);

        targetCompressedBuffer = ByteBuffer.allocate(synchronizedHistogram.getNeededByteBufferCapacity());
        synchronizedHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        SynchronizedHistogram synchronizedHistogram3 = SynchronizedHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(synchronizedHistogram, synchronizedHistogram3);

        System.out.println("\nTesting encoding of a DoubleHistogram:");
        targetBuffer = ByteBuffer.allocate(doubleHistogram.getNeededByteBufferCapacity());
        doubleHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        DoubleHistogram doubleHistogram2 = DoubleHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(doubleHistogram, doubleHistogram2);

        targetCompressedBuffer = ByteBuffer.allocate(doubleHistogram.getNeededByteBufferCapacity());
        doubleHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        DoubleHistogram doubleHistogram3 = DoubleHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(doubleHistogram, doubleHistogram3);
    }
}
