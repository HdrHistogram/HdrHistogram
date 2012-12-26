/**
 * Histogram.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
 */

package org.HdrHistogram;

/**
 * <h3>A High Dynamic Range (HDR) Histogram using an <b><code>int</code></b> count type </h3>
 * <p>
 * See description in {@link org.HdrHistogram.AbstractHistogram} for details.
 */

public class IntHistogram extends AbstractHistogram {
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
}