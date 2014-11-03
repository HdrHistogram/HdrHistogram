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
import java.nio.ShortBuffer;
import java.util.zip.DataFormatException;

/**
 * <h3>A High Dynamic Range (HDR) Histogram using a <b><code>short</code></b> count type </h3>
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class ShortCountsHistogram extends AbstractHistogram {
    long totalCount;
    final short[] counts;

    @Override
    long getCountAtNormalizedIndex(final int index) {
        return counts[index];
    }

    @Override
    void incrementCountAtNormalizedIndex(final int index) {
        short currentCount = counts[index];
        short newCount = (short) (currentCount + 1);
        if (newCount < 0) {
            throw new IllegalStateException("would overflow short integer count");
        }
        counts[index] = newCount;
    }

    @Override
    void addToCountAtNormalizedIndex(final int index, final long value) {
        short currentCount = counts[index];
        if ((value < 0) || (value > Short.MAX_VALUE)) {
            throw new IllegalArgumentException("would overflow short integer count");
        }
        short newCount = (short) (currentCount + value);
        if (newCount < 0) {
            throw new IllegalStateException("would overflow short integer count");
        }
        counts[index] = newCount;
    }

    @Override
    void setCountAtNormalizedIndex(int index, long value) {
        if ((value < 0) || (value > Short.MAX_VALUE)) {
            throw new IllegalArgumentException("would overflow short integer count");
        }
        counts[index] = (short) value;
    }

    @Override
    void clearCounts() {
        java.util.Arrays.fill(counts, (short) 0);
        totalCount = 0;
    }

    @Override    public ShortCountsHistogram copy() {
      ShortCountsHistogram copy = new ShortCountsHistogram(this);
      copy.add(this);
      return copy;
    }

    @Override
    public ShortCountsHistogram copyCorrectedForCoordinatedOmission(final long expectedIntervalBetweenValueSamples) {
        ShortCountsHistogram toHistogram = new ShortCountsHistogram(this);
        toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        return toHistogram;
    }

    @Override
    public long getTotalCount() {
        return totalCount;
    }

    @Override
    void setTotalCount(final long totalCount) {
        this.totalCount = totalCount;
    }

    @Override
    void incrementTotalCount() {
        totalCount++;
    }

    @Override
    void addToTotalCount(long value) {
        totalCount += value;
    }

    @Override
    int _getEstimatedFootprintInBytes() {
        return (512 + (2 * counts.length));
    }

    /**
     * Construct a ShortHistogram given the Highest value to be tracked and a number of significant decimal digits. The
     * histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} 2.
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public ShortCountsHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        this(1, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Construct a ShortHistogram given the Lowest and Highest values to be tracked and a number of significant
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
    public ShortCountsHistogram(final long lowestDiscernibleValue, final long highestTrackableValue,
                                final int numberOfSignificantValueDigits) {
        super(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
        counts = new short[countsArrayLength];
        wordSizeInBytes = 2;
    }

    private ShortCountsHistogram(final AbstractHistogram source) {
        super(source);
        counts = new short[countsArrayLength];
        wordSizeInBytes = 2;
    }

    /**
     * Construct a new histogram by decoding it from a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     */
    public static ShortCountsHistogram decodeFromByteBuffer(final ByteBuffer buffer,
                                                      final long minBarForHighestTrackableValue) {
        return (ShortCountsHistogram) decodeFromByteBuffer(buffer, ShortCountsHistogram.class,
                minBarForHighestTrackableValue);
    }

    /**
     * Construct a new histogram by decoding it from a compressed form in a ByteBuffer.
     * @param buffer The buffer to encode into
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     * @throws DataFormatException on error parsing/decompressing the buffer
     */
    public static ShortCountsHistogram decodeFromCompressedByteBuffer(final ByteBuffer buffer,
                                                                final long minBarForHighestTrackableValue) throws DataFormatException {
        return (ShortCountsHistogram) decodeFromCompressedByteBuffer(buffer, ShortCountsHistogram.class,
                minBarForHighestTrackableValue);
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }

    @Override
    synchronized void fillCountsArrayFromBuffer(final ByteBuffer buffer, final int length) {
        buffer.asShortBuffer().get(counts, 0, length);
    }

    // We try to cache the LongBuffer used in output cases, as repeated
    // output form the same histogram using the same buffer is likely:
    private ShortBuffer cachedDstShortBuffer = null;
    private ByteBuffer cachedDstByteBuffer = null;
    private int cachedDstByteBufferPosition = 0;

    @Override
    synchronized void fillBufferFromCountsArray(final ByteBuffer buffer, final int length) {
        if ((cachedDstShortBuffer == null) ||
                (buffer != cachedDstByteBuffer) ||
                (buffer.position() != cachedDstByteBufferPosition)) {
            cachedDstByteBuffer = buffer;
            cachedDstByteBufferPosition = buffer.position();
            cachedDstShortBuffer = buffer.asShortBuffer();
        }
        cachedDstShortBuffer.rewind();
        cachedDstShortBuffer.put(counts, 0, length);
    }
}