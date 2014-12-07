/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.zip.DataFormatException;

/**
 * <h3>An integer values High Dynamic Range (HDR) Histogram that supports safe concurrent recording operations.</h3>
 * A ConcurrentHistogram guarantees lossless recording of values into the hsitogram even when the
 * histogram is updated by mutliple threads, and supports auto-resize and shift operations that may
 * result from or occur concurrently with other recording operations.
 * <p>
 * It is important to note that concurrent recording, auto-sizing, and value shifting are the only thread-safe
 * behaviors provided by {@link ConcurrentHistogram}, and that it is not otherwise synchronized. Specifically, {@link
 * ConcurrentHistogram} provides no implicit synchronization that would prevent the contents of the histogram
 * from changing during queries, iterations, copies, or addition operations on the histogram. Callers wishing to make
 * potentially concurrent, multi-threaded updates that would safely work in the presence of queries, copies, or
 * additions of histogram objects should either take care to externally synchronize and/or order their access,
 * use the {@link SynchronizedHistogram} variant, or (recommended) use {@link Recorder} or
 * {@link SingleWriterRecorder} which are intended for this purpose.
 * <p>
 * Auto-resizing: When constructed with no specified value range range (or when auto-resize is turned on with {@link
 * Histogram#setAutoResize}) a {@link Histogram} will auto-resize its dynamic range to include recorded values as
 * they are encountered. Note that recording calls that cause auto-resizing may take longer to execute, as resizing
 * incurrs allocation and copying of internal data structures.
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class ConcurrentHistogram extends Histogram {

    static final AtomicLongFieldUpdater<ConcurrentHistogram> totalCountUpdater =
            AtomicLongFieldUpdater.newUpdater(ConcurrentHistogram.class, "totalCount");
    volatile long totalCount;

    volatile AtomicLongArrayWithNormalizingOffset activeCounts;
    volatile AtomicLongArrayWithNormalizingOffset inactiveCounts;
    WriterReaderPhaser wrp = new WriterReaderPhaser();

    @Override
    long getCountAtIndex(final int index) {
        try {
            wrp.readerLock();
            long activeCount = activeCounts.get(normalizeIndex(index, activeCounts.getNormalizingIndexOffset()));
            long inactiveCount = inactiveCounts.get(normalizeIndex(index, inactiveCounts.getNormalizingIndexOffset()));
            return activeCount + inactiveCount;
        } finally {
            wrp.readerUnlock();
        }
    }

    @Override
    long getCountAtNormalizedIndex(final int index) {
        try {
            wrp.readerLock();
            long activeCount = activeCounts.get(index);
            long inactiveCount = inactiveCounts.get(index);
            return activeCount + inactiveCount;
        } finally {
            wrp.readerUnlock();
        }
    }

    @Override
    void incrementCountAtIndex(final int index) {
        long criticalValue = wrp.writerCriticalSectionEnter();
        try {
            activeCounts.incrementAndGet(normalizeIndex(index, activeCounts.getNormalizingIndexOffset()));
        } finally {
            wrp.writerCriticalSectionExit(criticalValue);
        }
    }

    @Override
    void addToCountAtIndex(final int index, final long value) {
        long criticalValue = wrp.writerCriticalSectionEnter();
        try {
            activeCounts.addAndGet(normalizeIndex(index, activeCounts.getNormalizingIndexOffset()), value);
        } finally {
            wrp.writerCriticalSectionExit(criticalValue);
        }
    }

    @Override
    void setCountAtIndex(int index, long value) {
        try {
            wrp.readerLock();
            activeCounts.lazySet(normalizeIndex(index, activeCounts.getNormalizingIndexOffset()), value);
            inactiveCounts.lazySet(normalizeIndex(index, inactiveCounts.getNormalizingIndexOffset()), 0);
        } finally {
            wrp.readerUnlock();
        }
    }

    @Override
    void setCountAtNormalizedIndex(int index, long value) {
        try {
            wrp.readerLock();
            inactiveCounts.lazySet(index, value);
            activeCounts.lazySet(index, 0);
        } finally {
            wrp.readerUnlock();
        }
    }


    @Override
    int getNormalizingIndexOffset() {
        return activeCounts.getNormalizingIndexOffset();
    }

    @Override
    void setNormalizingIndexOffset(int normalizingIndexOffset) {
        setNormalizingIndexOffset(normalizingIndexOffset, 0, false);
    }

    private void setNormalizingIndexOffset(
            int normalizingIndexOffset,
            int shiftedAmount,
            boolean lowestHalfBucketPopulated) {
        try {
            wrp.readerLock();
            if (normalizingIndexOffset == activeCounts.getNormalizingIndexOffset()) {
                return; // Nothing to do.
            }

            // Save and clear the inactive 0 value count:
            long inactiveZeroValueCount =
                    inactiveCounts.get(normalizeIndex(0, inactiveCounts.getNormalizingIndexOffset()));
            inactiveCounts.lazySet(normalizeIndex(0, inactiveCounts.getNormalizingIndexOffset()), 0);

            // Change the normalizingIndexOffset on the current inactiveCounts:
            inactiveCounts.setNormalizingIndexOffset(normalizingIndexOffset);

            // Handle the inactive lowest half bucket:
            if ((shiftedAmount > 0) && lowestHalfBucketPopulated) {
                shiftLowestInactiveHalfBucketContentsLeft(shiftedAmount);
            }

            // Restore the inactive 0 value count:
            inactiveCounts.lazySet(
                    normalizeIndex(0, inactiveCounts.getNormalizingIndexOffset()),
                    inactiveZeroValueCount
            );

            // switch active and inactive:
            AtomicLongArrayWithNormalizingOffset tmp = activeCounts;
            activeCounts = inactiveCounts;
            inactiveCounts = tmp;

            wrp.flipPhase();

            // Save and clear the newly inactive 0 value count:
            inactiveZeroValueCount =
                    inactiveCounts.get(normalizeIndex(0, inactiveCounts.getNormalizingIndexOffset()));
            inactiveCounts.lazySet(normalizeIndex(0, inactiveCounts.getNormalizingIndexOffset()), 0);

            // Change the normalizingIndexOffset on the newly inactiveCounts:
            inactiveCounts.setNormalizingIndexOffset(normalizingIndexOffset);

            // Handle the newly inactive lowest half bucket:
            if ((shiftedAmount > 0) && lowestHalfBucketPopulated) {
                shiftLowestInactiveHalfBucketContentsLeft(shiftedAmount);
            }

            // Restore the newly inactive 0 value count:
            inactiveCounts.lazySet(
                    normalizeIndex(0, inactiveCounts.getNormalizingIndexOffset()),
                    inactiveZeroValueCount
            );

            // switch active and inactive again:
            tmp = activeCounts;
            activeCounts = inactiveCounts;
            inactiveCounts = tmp;

            wrp.flipPhase();

            // At this point, both active and inactive have normalizingIndexOffset safely set,
            // and the switch in each was done without any writers using the wrong value in flight.

        } finally {
            wrp.readerUnlock();
        }
    }

    private void shiftLowestInactiveHalfBucketContentsLeft(int shiftAmount) {
        final int numberOfBinaryOrdersOfMagnitude = shiftAmount >> subBucketHalfCountMagnitude;

        // The lowest inactive half-bucket (not including the 0 value) is special: unlike all other half
        // buckets, the lowest half bucket values cannot be scaled by simply changing the
        // normalizing offset. Instead, they must be individually re-recorded at the new
        // scale, and cleared from the current one.
        //
        // We know that all half buckets "below" the current lowest one are full of 0s, because
        // we would have overflowed otherwise. So we need to shift the values in the current
        // lowest half bucket into that range (including the current lowest half bucket itself).
        // Iterating up from the lowermost non-zero "from slot" and copying values to the newly
        // scaled "to slot" (and then zeroing the "from slot"), will work in a single pass,
        // because the scale "to slot" index will always be a lower index than its or any
        // preceding non-scaled "from slot" index:
        //
        // (Note that we specifically avoid slot 0, as it is directly handled in the outer case)

        for (int fromIndex = 1; fromIndex < subBucketHalfCount; fromIndex++) {
            long toValue = valueFromIndex(fromIndex) << numberOfBinaryOrdersOfMagnitude;
            int toIndex = countsArrayIndex(toValue);
            int normalizedToIndex = normalizeIndex(toIndex, inactiveCounts.getNormalizingIndexOffset());
            long countAtFromIndex = inactiveCounts.get(fromIndex);
            inactiveCounts.lazySet(normalizedToIndex, countAtFromIndex);
            inactiveCounts.lazySet(fromIndex, 0);
        }

        // Note that the above loop only creates O(N) work for histograms that have values in
        // the lowest half-bucket (excluding the 0 value). Histograms that never have values
        // there (e.g. all integer value histograms used as internal storage in DoubleHistograms)
        // will never loop, and their shifts will remain O(1).
    }

    @Override
    void shiftNormalizingIndexByOffset(int offsetToAdd, boolean lowestHalfBucketPopulated) {
        try {
            wrp.readerLock();
            int newNormalizingIndexOffset = inactiveCounts.getNormalizingIndexOffset() + offsetToAdd;
            setNormalizingIndexOffset(newNormalizingIndexOffset, offsetToAdd, lowestHalfBucketPopulated);
        } finally {
            wrp.readerUnlock();
        }
    }

    @Override
    void resize(long newHighestTrackableValue) {
        try {
            wrp.readerLock();

            int oldNormalizedZeroIndex = normalizeIndex(0, inactiveCounts.getNormalizingIndexOffset());
            establishSize(newHighestTrackableValue);
            int countsDelta = countsArrayLength - inactiveCounts.length();

            // Resize the current inactiveCounts:
            AtomicLongArray oldInactiveCounts = inactiveCounts;
            inactiveCounts =
                    new AtomicLongArrayWithNormalizingOffset(
                            countsArrayLength,
                            inactiveCounts.getNormalizingIndexOffset()
                    );
            // Copy inactive contents to newly sized inactiveCounts:
            for (int i = 0 ; i < oldInactiveCounts.length(); i++) {
                inactiveCounts.lazySet(i, oldInactiveCounts.get(i));
            }
            if (oldNormalizedZeroIndex != 0) {
                // We need to shift the stuff from the zero index and up to the end of the array:
                int newNormalizedZeroIndex = oldNormalizedZeroIndex + countsDelta;
                int lengthToCopy = (countsArrayLength - countsDelta) - oldNormalizedZeroIndex;
                int src, dst;
                for (src = oldNormalizedZeroIndex, dst =  newNormalizedZeroIndex;
                     src < oldNormalizedZeroIndex + lengthToCopy;
                     src++, dst++) {
                    inactiveCounts.lazySet(dst, oldInactiveCounts.get(src));
                }
            }

            // switch active and inactive:
            AtomicLongArrayWithNormalizingOffset tmp = activeCounts;
            activeCounts = inactiveCounts;
            inactiveCounts = tmp;

            wrp.flipPhase();

            // Resize the newly inactiveCounts:
            oldInactiveCounts = inactiveCounts;
            inactiveCounts =
                    new AtomicLongArrayWithNormalizingOffset(
                            countsArrayLength,
                            inactiveCounts.getNormalizingIndexOffset()
                    );
            // Copy inactive contents to newly sized inactiveCounts:
            for (int i = 0 ; i < oldInactiveCounts.length(); i++) {
                inactiveCounts.lazySet(i, oldInactiveCounts.get(i));
            }
            if (oldNormalizedZeroIndex != 0) {
                // We need to shift the stuff from the zero index and up to the end of the array:
                int newNormalizedZeroIndex = oldNormalizedZeroIndex + countsDelta;
                int lengthToCopy = (countsArrayLength - countsDelta) - oldNormalizedZeroIndex;
                int src, dst;
                for (src = oldNormalizedZeroIndex, dst =  newNormalizedZeroIndex;
                     src < oldNormalizedZeroIndex + lengthToCopy;
                     src++, dst++) {
                    inactiveCounts.lazySet(dst, oldInactiveCounts.get(src));
                }
            }

            // switch active and inactive again:
            tmp = activeCounts;
            activeCounts = inactiveCounts;
            inactiveCounts = tmp;

            wrp.flipPhase();

            // At this point, both active and inactive have been safely resized,
            // and the switch in each was done without any writers modifying it in flight.

        } finally {
            wrp.readerUnlock();
        }
    }

    @Override
    public void setAutoResize(boolean autoResize) {
        this.autoResize = true;
    }

    @Override
    void clearCounts() {
        try {
            wrp.readerLock();
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
    public ConcurrentHistogram copy() {
        ConcurrentHistogram copy = new ConcurrentHistogram(this);
        copy.add(this);
        return copy;
    }

    @Override
    public ConcurrentHistogram copyCorrectedForCoordinatedOmission(final long expectedIntervalBetweenValueSamples) {
        ConcurrentHistogram toHistogram = new ConcurrentHistogram(this);
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
        return (512 + (2 * 8 * activeCounts.length()));
    }

    /**
     * Construct an auto-resizing ConcurrentHistogram with a lowest discernible value of 1 and an auto-adjusting
     * highestTrackableValue. Can auto-reize up to track values up to (Long.MAX_VALUE / 2).
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public ConcurrentHistogram(final int numberOfSignificantValueDigits) {
        this(1, 2, numberOfSignificantValueDigits);
        setAutoResize(true);
    }

    /**
     * Construct a ConcurrentHistogram given the Highest value to be tracked and a number of significant decimal digits. The
     * histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} 2.
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public ConcurrentHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
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
    public ConcurrentHistogram(final long lowestDiscernibleValue, final long highestTrackableValue,
                               final int numberOfSignificantValueDigits) {
        super(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits, false);
        activeCounts = new AtomicLongArrayWithNormalizingOffset(countsArrayLength, 0);
        inactiveCounts = new AtomicLongArrayWithNormalizingOffset(countsArrayLength, 0);
        wordSizeInBytes = 8;
    }

    /**
     * Construct a histogram with the same range settings as a given source histogram,
     * duplicating the source's start/end timestamps (but NOT it's contents)
     * @param source The source histogram to duplicate
     */
    public ConcurrentHistogram(final AbstractHistogram source) {
        super(source, false);
        activeCounts = new AtomicLongArrayWithNormalizingOffset(countsArrayLength, 0);
        inactiveCounts = new AtomicLongArrayWithNormalizingOffset(countsArrayLength, 0);
        wordSizeInBytes = 8;
    }

    /**
     * Construct a new histogram by decoding it from a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     */
    public static ConcurrentHistogram decodeFromByteBuffer(final ByteBuffer buffer,
                                                           final long minBarForHighestTrackableValue) {
        return (ConcurrentHistogram) decodeFromByteBuffer(buffer, ConcurrentHistogram.class,
                minBarForHighestTrackableValue);
    }

    /**
     * Construct a new histogram by decoding it from a compressed form in a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     * @throws java.util.zip.DataFormatException on error parsing/decompressing the buffer
     */
    public static ConcurrentHistogram decodeFromCompressedByteBuffer(final ByteBuffer buffer,
                                                                     final long minBarForHighestTrackableValue) throws DataFormatException {
        return (ConcurrentHistogram) decodeFromCompressedByteBuffer(buffer, ConcurrentHistogram.class,
                minBarForHighestTrackableValue);
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }

    @Override
    synchronized void fillCountsArrayFromBuffer(final ByteBuffer buffer, final int length) {
        LongBuffer logbuffer = buffer.asLongBuffer();
        for (int i = 0; i < length; i++) {
            inactiveCounts.lazySet(i, logbuffer.get());
            activeCounts.lazySet(i, 0);
        }
    }

    // We try to cache the LongBuffer used in output cases, as repeated
    // output form the same histogram using the same buffer is likely:
    private LongBuffer cachedDstLongBuffer = null;
    private ByteBuffer cachedDstByteBuffer = null;
    private int cachedDstByteBufferPosition = 0;

    @Override
    synchronized void fillBufferFromCountsArray(final ByteBuffer buffer, final int length) {
        if ((cachedDstLongBuffer == null) ||
                (buffer != cachedDstByteBuffer) ||
                (buffer.position() != cachedDstByteBufferPosition)) {
            cachedDstByteBuffer = buffer;
            cachedDstByteBufferPosition = buffer.position();
            cachedDstLongBuffer = buffer.asLongBuffer();
        }
        cachedDstLongBuffer.rewind();
        try {
            wrp.readerLock();
            for (int i = 0; i < length; i++) {
                cachedDstLongBuffer.put(activeCounts.get(i) + inactiveCounts.get(i));
            }
        } finally {
            wrp.readerUnlock();
        }
    }

    static class AtomicLongArrayWithNormalizingOffset extends AtomicLongArray {

        private int normalizingIndexOffset;

        AtomicLongArrayWithNormalizingOffset(int length, int normalizingIndexOffset) {
            super(length);
            this.normalizingIndexOffset = normalizingIndexOffset;
        }

        public int getNormalizingIndexOffset() {
            return normalizingIndexOffset;
        }

        public void setNormalizingIndexOffset(int normalizingIndexOffset) {
            this.normalizingIndexOffset = normalizingIndexOffset;
        }
    }
}
