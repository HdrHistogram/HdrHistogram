/**
 * IntHistogram.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.5
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

    long getCountAtIndex(int index) {
        return counts[index];
    }

    void incrementCountAtIndex(int index) {
        counts[index]++;
    }

    void addToCountAtIndex(int index, long value) {
        counts[index] += value;
    }

    void clearCounts() {
        java.util.Arrays.fill(counts, 0);
        totalCount = 0;
    }
    
    public IntHistogram copy() {
      IntHistogram copy = new IntHistogram(highestTrackableValue, numberOfSignificantValueDigits);
      copy.add(this);
      return copy;
    }

    long getTotalCount() {
        return totalCount;
    }

    void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    void incrementTotalCount() {
        totalCount++;
    }

    /**
     * Provide a (conservatively high) estimate of the Histogram's total footprint in bytes
     *
     * @return a (conservatively high) estimate of the Histogram's total footprint in bytes
     */
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

    private void readObject(ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }
}