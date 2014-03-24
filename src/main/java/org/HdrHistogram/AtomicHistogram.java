/**
 * AtomicHistogram.java
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
import java.util.concurrent.atomic.*;
import java.util.zip.DataFormatException;

/**
 * <h3>A High Dynamic Range (HDR) Histogram using atomic <b><code>long</code></b> count type </h3>
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class AtomicHistogram extends AbstractHistogram {
    static final AtomicLongFieldUpdater<AtomicHistogram> totalCountUpdater =
            AtomicLongFieldUpdater.newUpdater(AtomicHistogram.class, "totalCount");
    volatile long totalCount;
    final AtomicLongArray counts;

    @Override
    long getCountAtIndex(final int index) {
        return counts.get(index);
    }

    @Override
    void incrementCountAtIndex(final int index) {
        counts.incrementAndGet(index);
    }

    @Override
    void addToCountAtIndex(final int index, final long value) {
        counts.addAndGet(index, value);
    }

    @Override
    void clearCounts() {
        for (int i = 0; i < counts.length(); i++)
            counts.lazySet(i, 0);
        totalCountUpdater.set(this, 0);
    }

    /**
     * @inheritDoc
     */
    @Override
    public AtomicHistogram copy() {
      AtomicHistogram copy = new AtomicHistogram(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits);
      copy.add(this);
      return copy;
    }

    /**
     * @inheritDoc
     */
    @Override
    public AtomicHistogram copyCorrectedForCoordinatedOmission(final long expectedIntervalBetweenValueSamples) {
        AtomicHistogram toHistogram = new AtomicHistogram(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits);
        toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        return toHistogram;
    }

    @Override
    long getTotalCount() {
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
        return (512 + (8 * counts.length()));
    }

    /**
     * Construct a AtomicHistogram given the Highest value to be tracked and a number of significant decimal digits. The
     * histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is >= 2.
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public AtomicHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        this(1, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Construct a AtomicHistogram given the Lowest and Highest values to be tracked and a number of significant
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
    public AtomicHistogram(final long lowestTrackableValue, final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits);
        counts = new AtomicLongArray(countsArrayLength);
    }

    /**
     * Construct a new histogram by decoding it from a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     */
    public static AtomicHistogram decodeFromByteBuffer(final ByteBuffer buffer, long minBarForHighestTrackableValue) {
        return (AtomicHistogram) decodeFromByteBuffer(buffer, AtomicHistogram.class,
                minBarForHighestTrackableValue, encodingCookie);
    }

    /**
     * Construct a new histogram by decoding it from a compressed form in a ByteBuffer.
     * @param buffer The buffer to encode into
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     * @throws DataFormatException
     */
    public static AtomicHistogram decodeFromCompressedByteBuffer(final ByteBuffer buffer,
                                                                 long minBarForHighestTrackableValue) throws DataFormatException {
        return (AtomicHistogram) decodeFromCompressedByteBuffer(buffer, AtomicHistogram.class,
                minBarForHighestTrackableValue, encodingCookie, compressedEncodingCookie);
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }

    private static final int encodingCookie = 0x1c849388;
    private static final int compressedEncodingCookie = 0x1c849389;

    @Override
    int getEncodingCookie() {
        return encodingCookie;
    }

    @Override
    int getCompressedEncodingCookie() {
        return compressedEncodingCookie;
    }


    @Override
    int getNeededByteBufferCapacity(final int relevantLength) {
        return (relevantLength * 8) + 32;
    }

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
        for (int i = 0; i < length; i++) {
            cachedDstLongBuffer.put(counts.get(i));
        }
    }

    private LongBuffer cachedSrcLongBuffer = null;
    private ByteBuffer cachedSrcByteBuffer = null;
    private int cachedSrcByteBufferPosition = 0;

    @Override
    synchronized void fillCountsArrayFromBuffer(final ByteBuffer buffer, final int length) {
        if ((cachedSrcLongBuffer == null) ||
                (buffer != cachedSrcByteBuffer)||
                (buffer.position() != cachedSrcByteBufferPosition)) {
            cachedSrcByteBuffer = buffer;
            cachedSrcByteBufferPosition = buffer.position();
            cachedSrcLongBuffer = buffer.asLongBuffer();
        }
        cachedSrcLongBuffer.rewind();
        for (int i = 0; i < length; i++) {
            counts.lazySet(i, cachedSrcLongBuffer.get());
        }
    }
}