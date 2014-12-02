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
import java.util.Arrays;
import java.util.zip.DataFormatException;

/**
 * <h3>A High Dynamic Range (HDR) Histogram of recorded counts at floating point (double) values </h3>
 * <p/>
 * A {@link SynchronizedDoubleHistogram} is a variant of {@link org.HdrHistogram.DoubleHistogram} that is
 * synchronized as a whole, such that queries, copying, and addition operations are atomic with relation to
 * modification on the {@link SynchronizedDoubleHistogram}, nd such that external accessors (e.g. iterations on the
 * histogram data) that synchronize on the {@link SynchronizedDoubleHistogram} instance can safely assume that no
 * modifications to the histogram data occur within their synchronized block.
 * <p>
 * It is important to note that synchrionization can result in blocking recoding calls. If non-blocking recoding
 * operations are required, consider using {@link org.HdrHistogram.ConcurrentDoubleHistogram}, or (recommended)
 * {@link IntervalDoubleHistogramRecorder} which were intended for concurrent operations.
 * <p/>
 * {@link SynchronizedDoubleHistogram} supports the recording and analyzing sampled data value counts across a
 * configurable dynamic range of floating point (double) values, with configurable value precision within the range.
 * Dynamic range is expressed as a ratio between the hightes and lowest non-zero values trackable within the histogram
 * at any given time. Value precision is expressed as the number of significant [decimal] digits in the value recording,
 * and provides control over value quantization behavior across the value range and the subsequent value resolution at
 * any given level.
 * <p/>
 * Auto-ranging: Unlike integer value based histograms, the specific value range tracked by a {@link
 * SynchronizedDoubleHistogram} is not specified upfront. Only the dynamic range of values that the histogram can
 * cover is (optionally) specified. E.g. When a {@link ConcurrentDoubleHistogram} is created to track a dynamic range of
 * 3600000000000 (enoygh to track values from a nanosecond to an hour), values could be recorded into into it in any
 * consistent unit of time as long as the ratio between the highest and lowest non-zero values stays within the
 * specified dynamic range, so recording in units of nanoseconds (1.0 thru 3600000000000.0), milliseconds (0.000001
 * thru 3600000.0) seconds (0.000000001 thru 3600.0), hours (1/3.6E12 thru 1.0) will all work just as well.
 * <p/>
 * Auto-resizing: When constructed with no specified dynamic range (or when auto-resize is turned on with {@link
 * SynchronizedDoubleHistogram#setAutoResize}) a {@link SynchronizedDoubleHistogram} will auto-resize its dynamic
 * range to include recorded values as they are encountered. Note that recording calls that cause auto-resizing may
 * take longer to execute, as resizing incurrs allocation and copying of internal data structures.
 * <p/>
 * Attempts to record non-zero values that range outside of the specified dynamic range (or exceed the limits of
 * of dynamic range when auto-resizing) may results in {@link ArrayIndexOutOfBoundsException} exceptions, either
 * due to overflow or underflow conditions. These exceptions will only be thrown if recording the value would have
 * resulted in discarding or losing the required value precision of values already recorded in the histogram.
 * <p/>
 * See package description for {@link org.HdrHistogram} for details.
 */


public class SynchronizedHistogram extends AbstractHistogram {
    long totalCount;
    long[] counts;
    int normalizingIndexOffset;

    @Override
    synchronized long getCountAtIndex(final int index) {
        return counts[normalizeIndex(index, normalizingIndexOffset)];
    }

    @Override
    synchronized long getCountAtNormalizedIndex(final int index) {
        return counts[index];
    }

    @Override
    synchronized void incrementCountAtIndex(final int index) {
        counts[normalizeIndex(index, normalizingIndexOffset)]++;
    }

    @Override
    synchronized void addToCountAtIndex(final int index, final long value) {
        counts[normalizeIndex(index, normalizingIndexOffset)] += value;
    }

    @Override
    synchronized void setCountAtIndex(int index, long value) {
        counts[normalizeIndex(index, normalizingIndexOffset)] = value;
    }

    @Override
    synchronized void setCountAtNormalizedIndex(int index, long value) {
        counts[index] = value;
    }

    @Override
    synchronized int getNormalizingIndexOffset() {
        return normalizingIndexOffset;
    }

    @Override
    synchronized void setNormalizingIndexOffset(int normalizingIndexOffset) {
        this.normalizingIndexOffset = normalizingIndexOffset;
    }

    @Override
    synchronized void shiftNormalizingIndexByOffset(int offsetToAdd, boolean lowestHalfBucketPopulated) {
        nonConcurrentNormalizingIndexShift(offsetToAdd, lowestHalfBucketPopulated);
    }

    @Override
    synchronized void clearCounts() {
            java.util.Arrays.fill(counts, 0);
            totalCount = 0;
    }

    @Override
    public void add(final AbstractHistogram otherHistogram) {
        // Synchronize add(). Avoid deadlocks by synchronizing in order of construction identity count.
        if (identity < otherHistogram.identity) {
            synchronized (this) {
                synchronized (otherHistogram) {
                    super.add(otherHistogram);
                }
            }
        } else {
            synchronized (otherHistogram) {
                synchronized (this) {
                    super.add(otherHistogram);
                }
            }
        }
    }

    @Override
    public synchronized void shiftValuesLeft(final int numberOfBinaryOrdersOfMagnitude) {
        super.shiftValuesLeft(numberOfBinaryOrdersOfMagnitude);
    }

    @Override
    public synchronized void shiftValuesRight(final int numberOfBinaryOrdersOfMagnitude) {
        super.shiftValuesRight(numberOfBinaryOrdersOfMagnitude);
    }

    @Override
    public SynchronizedHistogram copy() {
        SynchronizedHistogram copy;
        synchronized (this) {
            copy = new SynchronizedHistogram(this);
        }
        copy.add(this);
        return copy;
    }

    @Override
    public SynchronizedHistogram copyCorrectedForCoordinatedOmission(final long expectedIntervalBetweenValueSamples) {
        synchronized (this) {
            SynchronizedHistogram toHistogram = new SynchronizedHistogram(this);
            toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
            return toHistogram;
        }
    }

    @Override
    public long getTotalCount() {
        return totalCount;
    }

    @Override
    synchronized void setTotalCount(final long totalCount) {
           this.totalCount = totalCount;
    }

    @Override
    synchronized void incrementTotalCount() {
            totalCount++;
    }

    @Override
    synchronized void addToTotalCount(long value) {
            totalCount += value;
    }

    @Override
    synchronized void updatedMaxValue(long maxValue) {
        if (maxValue > getMaxValue()) {
            super.updatedMaxValue(maxValue);
        }
    }

    @Override
    synchronized void updateMinNonZeroValue(long minNonZeroValue) {
        if (minNonZeroValue < getMinNonZeroValue()) {
            super.updateMinNonZeroValue(minNonZeroValue);
        }
    }

    @Override
    synchronized int _getEstimatedFootprintInBytes() {
        return (512 + (8 * counts.length));
    }

    @Override
    synchronized void resize(long newHighestTrackableValue) {
        int oldNormalizedZeroIndex = normalizeIndex(0, normalizingIndexOffset);

        establishSize(newHighestTrackableValue);

        int countsDelta = countsArrayLength - counts.length;

        counts = Arrays.copyOf(counts, countsArrayLength);

        if (oldNormalizedZeroIndex != 0) {
            // We need to shift the stuff from the zero index and up to the end of the array:
            int newNormalizedZeroIndex = oldNormalizedZeroIndex + countsDelta;
            int lengthToCopy = (countsArrayLength - countsDelta) - oldNormalizedZeroIndex;
            System.arraycopy(counts, oldNormalizedZeroIndex, counts, newNormalizedZeroIndex, lengthToCopy);
        }
    }

    /**
     * Construct an auto-resizing SynchronizedHistogram with a lowest discernible value of 1 and an auto-adjusting
     * highestTrackableValue. Can auto-reize up to track values up to (Long.MAX_VALUE / 2).
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SynchronizedHistogram(final int numberOfSignificantValueDigits) {
        this(1, 2, numberOfSignificantValueDigits);
        setAutoResize(true);
    }

    /**
     * Construct a SynchronizedHistogram given the Highest value to be tracked and a number of significant decimal digits. The
     * histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} 2.
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SynchronizedHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        this(1, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Construct a SynchronizedHistogram given the Lowest and Highest values to be tracked and a number of significant
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
    public SynchronizedHistogram(final long lowestDiscernibleValue, final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
        counts = new long[countsArrayLength];
        wordSizeInBytes = 8;
    }

    /**
     * Construct a histogram with the same range settings as a given source histogram,
     * duplicating the source's start/end timestamps (but NOT it's contents)
     * @param source The source histogram to duplicate
     */
    public SynchronizedHistogram(final AbstractHistogram source) {
        super(source);
        counts = new long[countsArrayLength];
        wordSizeInBytes = 8;
    }

    /**
     * Construct a new histogram by decoding it from a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     */
    public static SynchronizedHistogram decodeFromByteBuffer(final ByteBuffer buffer,
                                                             final long minBarForHighestTrackableValue) {
        return (SynchronizedHistogram) decodeFromByteBuffer(buffer, SynchronizedHistogram.class,
                minBarForHighestTrackableValue);
    }

    /**
     * Construct a new histogram by decoding it from a compressed form in a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     * @throws DataFormatException on error parsing/decompressing the buffer
     */
    public static SynchronizedHistogram decodeFromCompressedByteBuffer(final ByteBuffer buffer,
                                                                       final long minBarForHighestTrackableValue) throws DataFormatException {
        return (SynchronizedHistogram) decodeFromCompressedByteBuffer(buffer, SynchronizedHistogram.class,
                minBarForHighestTrackableValue);
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }

    @Override
    synchronized void fillCountsArrayFromBuffer(final ByteBuffer buffer, final int length) {
        buffer.asLongBuffer().get(counts, 0, length);
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
        cachedDstLongBuffer.put(counts, 0, length);
    }
}