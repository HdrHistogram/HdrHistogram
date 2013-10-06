/**
 * IntHistogram.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * <h3>A High Dynamic Range (HDR) Histogram using an <b><code>int</code></b> count type </h3>
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class IntHistogram extends AbstractHistogram {
    long totalCount;
    final int[] counts;

    @Override
    long getCountAtIndex(final int index) {
        return counts[index];
    }

    @Override
    void incrementCountAtIndex(final int index) {
        counts[index]++;
    }

    @Override
    void addToCountAtIndex(final int index, final long value) {
        counts[index] += value;
    }

    @Override
    void clearCounts() {
        java.util.Arrays.fill(counts, 0);
        totalCount = 0;
    }

    /**
     * @inheritDoc
     */
    @Override
    public IntHistogram copy() {
      IntHistogram copy = new IntHistogram(highestTrackableValue, numberOfSignificantValueDigits);
      copy.add(this);
      return copy;
    }

    /**
     * @inheritDoc
     */
    @Override
    public IntHistogram copyCorrectedForCoordinatedOmission(final long expectedIntervalBetweenValueSamples) {
        IntHistogram toHistogram = new IntHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        return toHistogram;
    }

    @Override
    long getTotalCount() {
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

    /**
     * Provide a (conservatively high) estimate of the Histogram's total footprint in bytes
     *
     * @return a (conservatively high) estimate of the Histogram's total footprint in bytes
     */
    @Override
    public int getEstimatedFootprintInBytes() {
        return (512 + (4 * counts.length));
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
    public IntHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(highestTrackableValue, numberOfSignificantValueDigits);
        counts = new int[countsArrayLength];
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }
}