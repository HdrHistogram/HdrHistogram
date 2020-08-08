/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import org.HdrHistogram.packedarray.ConcurrentPackedLongArray;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;

/**
 * <h3>An integer values High Dynamic Range (HDR) Histogram that uses a packed internal representation
 * and supports safe concurrent recording operations.</h3>
 * A {@link PackedConcurrentHistogram} guarantees lossless recording of values into the histogram even when the
 * histogram is updated by multiple threads, and supports auto-resize and shift operations that may
 * result from or occur concurrently with other recording operations.
 * <p>
 * {@link PackedConcurrentHistogram} tracks value counts in a packed internal representation optimized
 * for typical histogram recoded values are sparse in the value range and tend to be incremented in small unit counts.
 * This packed representation tends to require significantly smaller amounts of storage when compared to unpacked
 * representations, but can incur additional recording cost due to resizing and repacking operations that may
 * occur as previously unrecorded values are encountered.
 * <p>
 * It is important to note that concurrent recording, auto-sizing, and value shifting are the only thread-safe
 * behaviors provided by {@link PackedConcurrentHistogram}, and that it is not otherwise synchronized. Specifically,
 * {@link PackedConcurrentHistogram} provides no implicit synchronization that would prevent the contents of the
 * histogram from changing during queries, iterations, copies, or addition operations on the histogram. Callers
 * wishing to make potentially concurrent, multi-threaded updates that would safely work in the presence of
 * queries, copies, or additions of histogram objects should either take care to externally synchronize and/or
 * order their access, use {@link Recorder} or {@link SingleWriterRecorder} which are intended for
 * this purpose.
 * <p>
 * Auto-resizing: When constructed with no specified value range range (or when auto-resize is turned on with {@link
 * Histogram#setAutoResize}) a {@link PackedConcurrentHistogram} will auto-resize its dynamic range to include recorded
 * values as they are encountered. Note that recording calls that cause auto-resizing may take longer to execute, as
 * resizing incurs allocation and copying of internal data structures.
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class PackedConcurrentHistogram extends ConcurrentHistogram {

    @Override
    ConcurrentArrayWithNormalizingOffset allocateArray(int length, int normalizingIndexOffset) {
        return new ConcurrentPackedArrayWithNormalizingOffset(length, normalizingIndexOffset);
    }

    @Override
    void clearCounts() {
        try {
            wrp.readerLock();
            assert (countsArrayLength == activeCounts.length());
            assert (countsArrayLength == inactiveCounts.length());
            for (int i = 0; i < activeCounts.length(); i++) {
                activeCounts.lazySet(i, 0);
                inactiveCounts.lazySet(i, 0);
            }
            totalCountUpdater.set(this, 0);
        } finally {
            wrp.readerUnlock();
        }
    }

    @Override
    public PackedConcurrentHistogram copy() {
        PackedConcurrentHistogram copy = new PackedConcurrentHistogram(this);
        copy.add(this);
        return copy;
    }

    @Override
    public PackedConcurrentHistogram copyCorrectedForCoordinatedOmission(final long expectedIntervalBetweenValueSamples) {
        PackedConcurrentHistogram toHistogram = new PackedConcurrentHistogram(this);
        toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        return toHistogram;
    }

    @Override
    public long getTotalCount() {
        return totalCountUpdater.get(this);
    }

    @Override
    void setTotalCount(final long totalCount) {
        totalCountUpdater.set(this, totalCount);
    }

    @Override
    void incrementTotalCount() {
        totalCountUpdater.incrementAndGet(this);
    }

    @Override
    void addToTotalCount(final long value) {
        totalCountUpdater.addAndGet(this, value);
    }


    @Override
    int _getEstimatedFootprintInBytes() {
        try {
            wrp.readerLock();
            return 128 + activeCounts.getEstimatedFootprintInBytes() + inactiveCounts.getEstimatedFootprintInBytes();
        } finally {
            wrp.readerUnlock();
        }
    }

    /**
     * Construct an auto-resizing ConcurrentHistogram with a lowest discernible value of 1 and an auto-adjusting
     * highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE / 2).
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public PackedConcurrentHistogram(final int numberOfSignificantValueDigits) {
        this(1, 2, numberOfSignificantValueDigits);
        setAutoResize(true);
    }

    /**
     * Construct a ConcurrentHistogram given the Highest value to be tracked and a number of significant decimal
     * digits. The histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} 2.
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public PackedConcurrentHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        this(1, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Construct a ConcurrentHistogram given the Lowest and Highest values to be tracked and a number of significant
     * decimal digits. Providing a lowestDiscernibleValue is useful is situations where the units used
     * for the histogram's values are much smaller that the minimal accuracy required. E.g. when tracking
     * time values stated in nanosecond units, where the minimal accuracy required is a microsecond, the
     * proper value for lowestDiscernibleValue would be 1000.
     *
     * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by the histogram.
     *                               Must be a positive integer that is {@literal >=} 1. May be internally rounded
     *                               down to nearest power of 2.
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} (2 * lowestDiscernibleValue).
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public PackedConcurrentHistogram(final long lowestDiscernibleValue, final long highestTrackableValue,
                                     final int numberOfSignificantValueDigits) {
        this(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits,
                true);
    }

    /**
     * Construct a histogram with the same range settings as a given source histogram,
     * duplicating the source's start/end timestamps (but NOT it's contents)
     * @param source The source histogram to duplicate
     */
    public PackedConcurrentHistogram(final AbstractHistogram source) {
        this(source, true);
    }


    PackedConcurrentHistogram(final AbstractHistogram source, boolean allocateCountsArray) {
        super(source,false);
        if (allocateCountsArray) {
            activeCounts = new ConcurrentPackedArrayWithNormalizingOffset(countsArrayLength, 0);
            inactiveCounts = new ConcurrentPackedArrayWithNormalizingOffset(countsArrayLength, 0);
        }
        wordSizeInBytes = 8;
    }

    PackedConcurrentHistogram(final long lowestDiscernibleValue, final long highestTrackableValue,
                              final int numberOfSignificantValueDigits, boolean allocateCountsArray) {
        super(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits,
                false);
        if (allocateCountsArray) {
            activeCounts = new ConcurrentPackedArrayWithNormalizingOffset(countsArrayLength, 0);
            inactiveCounts = new ConcurrentPackedArrayWithNormalizingOffset(countsArrayLength, 0);
        }
        wordSizeInBytes = 8;
    }

    /**
     * Construct a new histogram by decoding it from a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     */
    public static PackedConcurrentHistogram decodeFromByteBuffer(final ByteBuffer buffer,
                                                                 final long minBarForHighestTrackableValue) {
        return decodeFromByteBuffer(buffer, PackedConcurrentHistogram.class, minBarForHighestTrackableValue);
    }

    /**
     * Construct a new histogram by decoding it from a compressed form in a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     * @throws DataFormatException on error parsing/decompressing the buffer
     */
    public static PackedConcurrentHistogram decodeFromCompressedByteBuffer(final ByteBuffer buffer,
                                                                           final long minBarForHighestTrackableValue)
            throws DataFormatException {
        return decodeFromCompressedByteBuffer(buffer, PackedConcurrentHistogram.class, minBarForHighestTrackableValue);
    }

    /**
     * Construct a new ConcurrentHistogram by decoding it from a String containing a base64 encoded
     * compressed histogram representation.
     *
     * @param base64CompressedHistogramString A string containing a base64 encoding of a compressed histogram
     * @return A ConcurrentHistogram decoded from the string
     * @throws DataFormatException on error parsing/decompressing the input
     */
    public static PackedConcurrentHistogram fromString(final String base64CompressedHistogramString)
            throws DataFormatException {
        return decodeFromCompressedByteBuffer(
                ByteBuffer.wrap(Base64Helper.parseBase64Binary(base64CompressedHistogramString)),
                0);
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
        wrp = new WriterReaderPhaser();
    }

    @Override
    synchronized void fillBufferFromCountsArray(final ByteBuffer buffer) {
        try {
            wrp.readerLock();
            super.fillBufferFromCountsArray(buffer);
        } finally {
            wrp.readerUnlock();
        }
    }

    static class ConcurrentPackedArrayWithNormalizingOffset
            implements ConcurrentArrayWithNormalizingOffset, Serializable {

        private ConcurrentPackedLongArray packedCounts;

        private int normalizingIndexOffset;
        private double doubleToIntegerValueConversionRatio;

        ConcurrentPackedArrayWithNormalizingOffset(int length, int normalizingIndexOffset) {
            packedCounts = new ConcurrentPackedLongArray(length);
            this.normalizingIndexOffset = normalizingIndexOffset;
        }

        public int getNormalizingIndexOffset() {
            return normalizingIndexOffset;
        }

        public void setNormalizingIndexOffset(int normalizingIndexOffset) {
            this.normalizingIndexOffset = normalizingIndexOffset;
        }

        public double getDoubleToIntegerValueConversionRatio() {
            return doubleToIntegerValueConversionRatio;
        }

        public void setDoubleToIntegerValueConversionRatio(double doubleToIntegerValueConversionRatio) {
            this.doubleToIntegerValueConversionRatio = doubleToIntegerValueConversionRatio;
        }

        @Override
        public long get(int index) {
            return packedCounts.get(index);
        }

        @Override
        public void atomicIncrement(int index) {
            packedCounts.increment(index);
        }

        @Override
        public void atomicAdd(int index, long valueToAdd) {
            packedCounts.add(index, valueToAdd);
        }

        @Override
        public void lazySet(int index, long newValue) {
            packedCounts.set(index, newValue);
        }

        @Override
        public int length() {
            return packedCounts.length();
        }

        @Override
        public int getEstimatedFootprintInBytes() {
            return 128 + (8 * packedCounts.getPhysicalLength());
        }
    }
}
