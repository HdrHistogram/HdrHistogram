/**
 * AtomicHistogram.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.5
 */

package org.HdrHistogram;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.atomic.*;

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
      AtomicHistogram copy = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
      copy.add(this);
      return copy;
    }

    /**
     * @inheritDoc
     */
    @Override
    public AtomicHistogram copyCorrectedForCoordinatedOmission(final long expectedIntervalBetweenValueSamples) {
        AtomicHistogram toHistogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        return toHistogram;
    }

    @Override
    long getTotalCount() {
        return totalCount;
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

    /**
     * Provide a (conservatively high) estimate of the Histogram's total footprint in bytes
     *
     * @return a (conservatively high) estimate of the Histogram's total footprint in bytes
     */
    @Override
    public int getEstimatedFootprintInBytes() {
        return (512 + (8 * counts.length()));
    }

    /**
     * Construct a Histogram given the Highest value to be tracked and a number of significant decimal digits
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is >= 2.
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public AtomicHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(highestTrackableValue, numberOfSignificantValueDigits);
        counts = new AtomicLongArray(countsArrayLength);
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }
}