/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;

import static org.HdrHistogram.HistogramTestUtils.constructHistogram;
import static org.HdrHistogram.HistogramTestUtils.constructDoubleHistogram;
import static org.HdrHistogram.HistogramTestUtils.decodeFromCompressedByteBuffer;
import static org.HdrHistogram.HistogramTestUtils.decodeDoubleHistogramFromCompressedByteBuffer;

/**
 * JUnit test for {@link org.HdrHistogram.Histogram}
 */
@RunWith(Theories.class)
public class HistogramEncodingTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units

    @Test
    public void testHistogramEncoding_ByteBufferHasCorrectPositionSetAfterEncoding() throws Exception {
        Histogram histogram = new Histogram(highestTrackableValue, 3);
        int size = histogram.getNeededByteBufferCapacity();
        ByteBuffer buffer = ByteBuffer.allocate(size);

        int bytesWritten = histogram.encodeIntoCompressedByteBuffer(buffer);
        Assert.assertEquals(bytesWritten, buffer.position());
        buffer.rewind();

        bytesWritten = histogram.encodeIntoByteBuffer(buffer);
        Assert.assertEquals(bytesWritten, buffer.position());
    }

    public enum BufferAllocator {
        DIRECT {
            @Override
            public ByteBuffer allocate(final int size) {
                return ByteBuffer.allocateDirect(size);
            }
        },
        HEAP {
            @Override
            public ByteBuffer allocate(final int size) {
                return ByteBuffer.allocate(size);
            }
        };

        public abstract ByteBuffer allocate(int size);
    }

    @DataPoints
    public static BufferAllocator[] ALLOCATORS = new BufferAllocator[] { BufferAllocator.DIRECT, BufferAllocator.HEAP };

    @Theory
    public void testHistogramEncoding(BufferAllocator allocator) throws Exception {

        ShortCountsHistogram shortCountsHistogram = new ShortCountsHistogram(highestTrackableValue, 3);
        IntCountsHistogram intCountsHistogram = new IntCountsHistogram(highestTrackableValue, 3);
        Histogram histogram = new Histogram(highestTrackableValue, 3);
        PackedHistogram packedHistogram = new PackedHistogram(highestTrackableValue, 3);
        PackedConcurrentHistogram packedConcurrentHistogram = new PackedConcurrentHistogram(highestTrackableValue, 3);
        AtomicHistogram atomicHistogram = new AtomicHistogram(highestTrackableValue, 3);
        ConcurrentHistogram concurrentHistogram = new ConcurrentHistogram(highestTrackableValue, 3);
        SynchronizedHistogram synchronizedHistogram = new SynchronizedHistogram(highestTrackableValue, 3);
        DoubleHistogram doubleHistogram = new DoubleHistogram(highestTrackableValue * 1000, 3);
        PackedDoubleHistogram packedDoubleHistogram = new PackedDoubleHistogram(highestTrackableValue * 1000, 3);
        DoubleHistogram concurrentDoubleHistogram = new ConcurrentDoubleHistogram(highestTrackableValue * 1000, 3);
        PackedConcurrentDoubleHistogram packedConcurrentDoubleHistogram = new PackedConcurrentDoubleHistogram(highestTrackableValue * 1000, 3);

        for (int i = 0; i < 10000; i++) {
            shortCountsHistogram.recordValue(1000 * i);
            intCountsHistogram.recordValue(2000 * i);
            histogram.recordValue(3000 * i);
            packedHistogram.recordValue(3000 * i);
            packedConcurrentHistogram.recordValue(3000 * i);
            atomicHistogram.recordValue(4000 * i);
            concurrentHistogram.recordValue(4000 * i);
            synchronizedHistogram.recordValue(5000 * i);
            doubleHistogram.recordValue(5000 * i);
            doubleHistogram.recordValue(0.001); // Makes some internal shifts happen.
            packedDoubleHistogram.recordValue(5000 * i);
            packedDoubleHistogram.recordValue(0.001); // Makes some internal shifts happen.
            concurrentDoubleHistogram.recordValue(5000 * i);
            concurrentDoubleHistogram.recordValue(0.001); // Makes some internal shifts happen.
            packedConcurrentDoubleHistogram.recordValue(5000 * i);
            packedConcurrentDoubleHistogram.recordValue(0.001); // Makes some internal shifts happen.
        }

        System.out.println("Testing encoding of a ShortHistogram:");
        ByteBuffer targetBuffer = allocator.allocate(shortCountsHistogram.getNeededByteBufferCapacity());
        shortCountsHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        ShortCountsHistogram shortCountsHistogram2 = ShortCountsHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(shortCountsHistogram, shortCountsHistogram2);

        ByteBuffer targetCompressedBuffer = allocator.allocate(shortCountsHistogram.getNeededByteBufferCapacity());
        shortCountsHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        ShortCountsHistogram shortCountsHistogram3 = ShortCountsHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(shortCountsHistogram, shortCountsHistogram3);

        System.out.println("Testing encoding of a IntHistogram:");
        targetBuffer = allocator.allocate(intCountsHistogram.getNeededByteBufferCapacity());
        intCountsHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        IntCountsHistogram intCountsHistogram2 = IntCountsHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(intCountsHistogram, intCountsHistogram2);

        targetCompressedBuffer = allocator.allocate(intCountsHistogram.getNeededByteBufferCapacity());
        intCountsHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        IntCountsHistogram intCountsHistogram3 = IntCountsHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(intCountsHistogram, intCountsHistogram3);

        System.out.println("Testing encoding of a Histogram:");
        targetBuffer = allocator.allocate(histogram.getNeededByteBufferCapacity());
        histogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        Histogram histogram2 = Histogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(histogram, histogram2);

        targetCompressedBuffer = allocator.allocate(histogram.getNeededByteBufferCapacity());
        histogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        Histogram histogram3 = Histogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(histogram, histogram3);

        System.out.println("Testing encoding of a PackedHistogram:");
        targetBuffer = allocator.allocate(packedHistogram.getNeededByteBufferCapacity());
        packedHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        PackedHistogram packedHistogram2 = PackedHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(packedHistogram, packedHistogram2);

        targetCompressedBuffer = allocator.allocate(packedHistogram.getNeededByteBufferCapacity());
        packedHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        PackedHistogram packedHistogram3 = PackedHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(packedHistogram, packedHistogram3);

        System.out.println("Testing encoding of a PackedConcurrentHistogram:");
        targetBuffer = allocator.allocate(packedConcurrentHistogram.getNeededByteBufferCapacity());
        packedConcurrentHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        PackedConcurrentHistogram packedConcurrentHistogram2 = PackedConcurrentHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(packedConcurrentHistogram, packedConcurrentHistogram2);

        targetCompressedBuffer = allocator.allocate(packedConcurrentHistogram.getNeededByteBufferCapacity());
        packedConcurrentHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        PackedConcurrentHistogram packedConcurrentHistogram3 = PackedConcurrentHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(packedConcurrentHistogram, packedConcurrentHistogram3);

        System.out.println("Testing encoding of a AtomicHistogram:");
        targetBuffer = allocator.allocate(atomicHistogram.getNeededByteBufferCapacity());
        atomicHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        AtomicHistogram atomicHistogram2 = AtomicHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(atomicHistogram, atomicHistogram2);

        targetCompressedBuffer = allocator.allocate(atomicHistogram.getNeededByteBufferCapacity());
        atomicHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        AtomicHistogram atomicHistogram3 = AtomicHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(atomicHistogram, atomicHistogram3);

        System.out.println("Testing encoding of a ConcurrentHistogram:");
        targetBuffer = allocator.allocate(concurrentHistogram.getNeededByteBufferCapacity());
        concurrentHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        ConcurrentHistogram concurrentHistogram2 = ConcurrentHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(concurrentHistogram, concurrentHistogram2);

        targetCompressedBuffer = allocator.allocate(concurrentHistogram.getNeededByteBufferCapacity());
        concurrentHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        ConcurrentHistogram concurrentHistogram3 = ConcurrentHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(concurrentHistogram, concurrentHistogram3);

        System.out.println("Testing encoding of a SynchronizedHistogram:");
        targetBuffer = allocator.allocate(synchronizedHistogram.getNeededByteBufferCapacity());
        synchronizedHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        SynchronizedHistogram synchronizedHistogram2 = SynchronizedHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(synchronizedHistogram, synchronizedHistogram2);

        synchronizedHistogram.setIntegerToDoubleValueConversionRatio(5.0);

        targetCompressedBuffer = allocator.allocate(synchronizedHistogram.getNeededByteBufferCapacity());
        synchronizedHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        SynchronizedHistogram synchronizedHistogram3 = SynchronizedHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(synchronizedHistogram, synchronizedHistogram3);

        System.out.println("Testing encoding of a DoubleHistogram:");
        targetBuffer = allocator.allocate(doubleHistogram.getNeededByteBufferCapacity());
        doubleHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        DoubleHistogram doubleHistogram2 = DoubleHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(doubleHistogram, doubleHistogram2);

        targetCompressedBuffer = allocator.allocate(doubleHistogram.getNeededByteBufferCapacity());
        doubleHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        DoubleHistogram doubleHistogram3 = DoubleHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(doubleHistogram, doubleHistogram3);

        System.out.println("Testing encoding of a PackedDoubleHistogram:");
        targetBuffer = allocator.allocate(packedDoubleHistogram.getNeededByteBufferCapacity());
        packedDoubleHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        PackedDoubleHistogram packedDoubleHistogram2 = PackedDoubleHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(packedDoubleHistogram, packedDoubleHistogram2);

        targetCompressedBuffer = allocator.allocate(packedDoubleHistogram.getNeededByteBufferCapacity());
        packedDoubleHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        PackedDoubleHistogram packedDoubleHistogram3 = PackedDoubleHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(packedDoubleHistogram, packedDoubleHistogram3);

        System.out.println("Testing encoding of a ConcurrentDoubleHistogram:");
        targetBuffer = allocator.allocate(concurrentDoubleHistogram.getNeededByteBufferCapacity());
        concurrentDoubleHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        ConcurrentDoubleHistogram concurrentDoubleHistogram2 = ConcurrentDoubleHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(concurrentDoubleHistogram, concurrentDoubleHistogram2);

        targetCompressedBuffer = allocator.allocate(concurrentDoubleHistogram.getNeededByteBufferCapacity());
        concurrentDoubleHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        ConcurrentDoubleHistogram concurrentDoubleHistogram3 = ConcurrentDoubleHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(concurrentDoubleHistogram, concurrentDoubleHistogram3);

        System.out.println("Testing encoding of a PackedConcurrentDoubleHistogram:");
        targetBuffer = allocator.allocate(packedConcurrentDoubleHistogram.getNeededByteBufferCapacity());
        packedConcurrentDoubleHistogram.encodeIntoByteBuffer(targetBuffer);
        targetBuffer.rewind();

        PackedConcurrentDoubleHistogram packedConcurrentDoubleHistogram2 = PackedConcurrentDoubleHistogram.decodeFromByteBuffer(targetBuffer, 0);
        Assert.assertEquals(packedConcurrentDoubleHistogram, packedConcurrentDoubleHistogram2);

        targetCompressedBuffer = allocator.allocate(packedConcurrentDoubleHistogram.getNeededByteBufferCapacity());
        packedConcurrentDoubleHistogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        PackedConcurrentDoubleHistogram packedConcurrentDoubleHistogram3 = PackedConcurrentDoubleHistogram.decodeFromCompressedByteBuffer(targetCompressedBuffer, 0);
        Assert.assertEquals(packedConcurrentDoubleHistogram, packedConcurrentDoubleHistogram3);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            Histogram.class,
            AtomicHistogram.class,
            ConcurrentHistogram.class,
            SynchronizedHistogram.class,
            PackedHistogram.class,
            PackedConcurrentHistogram.class,
            IntCountsHistogram.class,
            ShortCountsHistogram.class,
    })
    public void testSimpleIntegerHistogramEncoding(final Class histoClass) throws Exception {
        AbstractHistogram histogram = constructHistogram(histoClass, 274877906943L, 3);
        histogram.recordValue(6147);
        histogram.recordValue(1024);
        histogram.recordValue(0);

        ByteBuffer targetBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());

        histogram.encodeIntoCompressedByteBuffer(targetBuffer);
        targetBuffer.rewind();
        AbstractHistogram decodedHistogram = decodeFromCompressedByteBuffer(histoClass, targetBuffer, 0);
        Assert.assertEquals(histogram, decodedHistogram);

        histogram.recordValueWithCount(100, 1L << 4); // Make total count > 2^4

        targetBuffer.clear();
        histogram.encodeIntoCompressedByteBuffer(targetBuffer);
        targetBuffer.rewind();
        decodedHistogram = decodeFromCompressedByteBuffer(histoClass, targetBuffer, 0);
        Assert.assertEquals(histogram, decodedHistogram);

        if (histoClass.equals(ShortCountsHistogram.class)) {
            return; // Going farther will overflow short counts histogram
        }
        histogram.recordValueWithCount(200, 1L << 16); // Make total count > 2^16

        targetBuffer.clear();
        histogram.encodeIntoCompressedByteBuffer(targetBuffer);
        targetBuffer.rewind();
        decodedHistogram = decodeFromCompressedByteBuffer(histoClass, targetBuffer, 0);
        Assert.assertEquals(histogram, decodedHistogram);

        histogram.recordValueWithCount(300, 1L << 20); // Make total count > 2^20

        targetBuffer.clear();
        histogram.encodeIntoCompressedByteBuffer(targetBuffer);
        targetBuffer.rewind();
        decodedHistogram = decodeFromCompressedByteBuffer(histoClass, targetBuffer, 0);
        Assert.assertEquals(histogram, decodedHistogram);

        if (histoClass.equals(IntCountsHistogram.class)) {
            return; // Going farther will overflow int counts histogram
        }
        histogram.recordValueWithCount(400, 1L << 32); // Make total count > 2^32

        targetBuffer.clear();
        histogram.encodeIntoCompressedByteBuffer(targetBuffer);
        targetBuffer.rewind();
        decodedHistogram = decodeFromCompressedByteBuffer(histoClass, targetBuffer, 0);
         Assert.assertEquals(histogram, decodedHistogram);

        histogram.recordValueWithCount(500, 1L << 52); // Make total count > 2^52

        targetBuffer.clear();
        histogram.encodeIntoCompressedByteBuffer(targetBuffer);
        targetBuffer.rewind();
        decodedHistogram = decodeFromCompressedByteBuffer(histoClass, targetBuffer, 0);
        Assert.assertEquals(histogram, decodedHistogram);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            DoubleHistogram.class,
            SynchronizedDoubleHistogram.class,
            ConcurrentDoubleHistogram.class,
            PackedDoubleHistogram.class,
            PackedConcurrentDoubleHistogram.class,
    })
    public void testSimpleDoubleHistogramEncoding(final Class histoClass) throws Exception {
        DoubleHistogram histogram = constructDoubleHistogram(histoClass, 100000000L, 3);
        histogram.recordValue(6.0);
        histogram.recordValue(1.0);
        histogram.recordValue(0.0);

        ByteBuffer targetBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        histogram.encodeIntoCompressedByteBuffer(targetBuffer);
        targetBuffer.rewind();

        DoubleHistogram decodedHistogram = decodeDoubleHistogramFromCompressedByteBuffer(histoClass, targetBuffer, 0);

        Assert.assertEquals(histogram, decodedHistogram);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            Histogram.class,
            ConcurrentHistogram.class,
            SynchronizedHistogram.class,
            PackedHistogram.class,
            PackedConcurrentHistogram.class,
            IntCountsHistogram.class,
            ShortCountsHistogram.class,
    })
    public void testResizingHistogramBetweenCompressedEncodings(final Class histoClass) throws Exception {
        AbstractHistogram histogram = constructHistogram(histoClass, 3);

        histogram.recordValue(1);

        ByteBuffer targetCompressedBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        histogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);

        histogram.recordValue(10000);

        targetCompressedBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        histogram.encodeIntoCompressedByteBuffer(targetCompressedBuffer);
        targetCompressedBuffer.rewind();

        AbstractHistogram histogram2 = decodeFromCompressedByteBuffer(histoClass, targetCompressedBuffer, 0);
        Assert.assertEquals(histogram, histogram2);
    }
}
