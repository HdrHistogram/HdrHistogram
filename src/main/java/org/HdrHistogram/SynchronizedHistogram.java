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
import java.util.zip.DataFormatException;

/**
 * <h3>An internally synchronized High Dynamic Range (HDR) Histogram using a <b><code>long</code></b> count type </h3>.
 * A SynchronizedHistogram is synchronized as a whole such that copying and addition operations are atomic with relation
 * to modification on the SynchronizedHistogram instance, and such that external accessors (e.g. iterations on the
 * histogram data) that synchronize on the SynchronizedHistogram instance can safely assume that no modifications
 * top the histogram data occur within their synchronized block.
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class SynchronizedHistogram extends AbstractHistogram {
    long totalCount;
    final long[] counts;

    @Override
    long getCountAtIndex(final int index) {
        return counts[index];
    }

    @Override
    synchronized void incrementCountAtIndex(final int index) {
            counts[index]++;
    }

    @Override
    synchronized void addToCountAtIndex(final int index, final long value) {
            counts[index] += value;
    }

    // setCountAtIndex is not synchronized, as it is only used within otherwise syncronized methods
    @Override
    void setCountAtIndex(int index, long value) {
            counts[index] = value;
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
    public synchronized void shiftLeftAndScaleLimits(int numberOfBinaryOrdersOfMagnitude) {
            super.shiftLeftAndScaleLimits(numberOfBinaryOrdersOfMagnitude);
    }

    @Override
    public synchronized void shiftRightAndScaleLimits(int numberOfBinaryOrdersOfMagnitude)
            throws ArrayIndexOutOfBoundsException {
            super.shiftRightAndScaleLimits(numberOfBinaryOrdersOfMagnitude);
    }

    @Override
    public synchronized void shiftLeft(int numberOfBinaryOrdersOfMagnitude) throws ArrayIndexOutOfBoundsException {
            super.shiftLeft(numberOfBinaryOrdersOfMagnitude);
    }

    @Override
    public synchronized void shiftRight(int numberOfBinaryOrdersOfMagnitude) {
            super.shiftRight(numberOfBinaryOrdersOfMagnitude);
    }

    @Override
    public synchronized void shiftRightWithUndeflowProtection(int numberOfBinaryOrdersOfMagnitude) {
        super.shiftRightWithUndeflowProtection(numberOfBinaryOrdersOfMagnitude);
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
    synchronized void setMaxValue(long maxValue) {
        super.setMaxValue(maxValue);
    }

    @Override
    int _getEstimatedFootprintInBytes() {
        return (512 + (8 * counts.length));
    }

    /**
     * Construct a SynchronizedHistogram given the Highest value to be tracked and a number of significant decimal digits. The
     * histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} 2.
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
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
     *                             Must be a positive integer that is {@literal >=} 1. May be internally rounded down to nearest
     *                             power of 2.
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} (2 * lowestDiscernibleValue).
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public SynchronizedHistogram(final long lowestDiscernibleValue, final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
        counts = new long[countsArrayLength];
        wordSizeInBytes = 8;
    }

    private SynchronizedHistogram(final AbstractHistogram source) {
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
     * @param buffer The buffer to encode into
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