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
 * <h3>An internally synchronized High Dynamic Range (HDR) Histogram using a <b><code>long</code></b> count type </h3>
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
    void incrementCountAtIndex(final int index) {
        synchronized (this) {
            counts[index]++;
        }
    }

    @Override
    void addToCountAtIndex(final int index, final long value) {
        synchronized (this) {
            counts[index] += value;
        }
    }

    @Override
    void clearCounts() {
        synchronized (this) {
            java.util.Arrays.fill(counts, 0);
            totalCount = 0;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void add(final AbstractHistogram other) {
        // Synchronize add(). Avoid deadlocks by synchronizing in order of construction identity count.
        if (identity < other.identity) {
            synchronized (this) {
                synchronized (other) {
                    super.add(other);
                }
            }
        } else {
            synchronized (other) {
                synchronized (this) {
                    super.add(other);
                }
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public SynchronizedHistogram copy() {
        SynchronizedHistogram copy = new SynchronizedHistogram(this);
        copy.add(this);
        return copy;
    }

    /**
     * @inheritDoc
     */
    @Override
    public SynchronizedHistogram copyCorrectedForCoordinatedOmission(final long expectedIntervalBetweenValueSamples) {
        SynchronizedHistogram toHistogram = new SynchronizedHistogram(this);
        toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        return toHistogram;
    }

    @Override
    long getTotalCount() {
        return totalCount;
    }

    @Override
    void setTotalCount(final long totalCount) {
        synchronized (this) {
           this.totalCount = totalCount;
        }
    }

    @Override
    void incrementTotalCount() {
        synchronized (this) {
            totalCount++;
        }
    }

    @Override
    void addToTotalCount(long value) {
        synchronized (this) {
            totalCount += value;
        }
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
     *                              integer that is >= 2.
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public SynchronizedHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        this(1, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Construct a SynchronizedHistogram given the Lowest and Highest values to be tracked and a number of significant
     * decimal digits. Providing a lowestTrackableValue is useful is situations where the units used
     * for the histogram's values are much smaller that the minimal accuracy required. E.g. when tracking
     * time values stated in nanosecond units, where the minimal accuracy required is a microsecond, the
     * proper value for lowestTrackableValue would be 1000.
     *
     * @param lowestTrackableValue The lowest value that can be tracked (distinguished from 0) by the histogram.
     *                             Must be a positive integer that is >= 1. May be internally rounded down to nearest
     *                             power of 2.
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is >= (2 * lowestTrackableValue).
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public SynchronizedHistogram(final long lowestTrackableValue, final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits);
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
     * @throws DataFormatException
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