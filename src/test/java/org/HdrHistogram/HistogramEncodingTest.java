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
    static final int numberOfSignificantValueDigits = 3;
    // static final long testValueLevel = 12340;
    static final long testValueLevel = 4;

    @Test
    public void testHistogramEncoding() throws Exception {


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

        System.out.println("\nTesting encoding of a ShortHistogram:");
        ByteBuffer targetBuffer = ByteBuffer.allocate(shortHistogram.getNeededByteBufferCapacity());
        shortHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        ShortHistogram shortHistogram2 = ShortHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(shortHistogram, shortHistogram2);

        ByteBuffer targetCompressedBuffer = ByteBuffer.allocate(shortHistogram.getNeededByteBufferCapacity());
        shortHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        ShortHistogram shortHistogram3 = ShortHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(shortHistogram, shortHistogram3);

        System.out.println("\nTesting encoding of a IntHistogram:");
        targetBuffer = ByteBuffer.allocate(intHistogram.getNeededByteBufferCapacity());
        intHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        IntHistogram intHistogram2 = IntHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(intHistogram, intHistogram2);

        targetCompressedBuffer = ByteBuffer.allocate(intHistogram.getNeededByteBufferCapacity());
        intHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        IntHistogram intHistogram3 = IntHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(intHistogram, intHistogram3);

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

        targetCompressedBuffer = ByteBuffer.allocate(synchronizedHistogram.getNeededByteBufferCapacity());
        synchronizedHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        SynchronizedHistogram synchronizedHistogram3 = SynchronizedHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(synchronizedHistogram, synchronizedHistogram3);
    }
}
