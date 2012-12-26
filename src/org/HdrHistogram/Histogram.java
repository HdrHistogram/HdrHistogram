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
 * <h3>A High Dynamic Range (HDR) Histogram</h3>
 * <p>
 * Histogram supports the recording and analyzing sampled data value counts across a configurable integer value
 * range with configurable value precision within the range. Value precision is expressed as the number of significant
 * digits in the value recording, and provides control over value quantization behavior across the value range and the
 * subsequent value resolution at any given level.
 * <p>
 * For example, a Histogram could be configured to track the counts of observed integer values between 0 and
 * 3,600,000,000 while maintaining a value precision of 3 significant digits across that range. Value quantization
 * within the range will thus be no larger than 1/1,000th (or 0.1%) of any value. This example Histogram could
 * be used to track and analyze the counts of observed response times ranging between 1 microsecond and 1 hour
 * in magnitude, while maintaining a value resolution of 1 microsecond up to 1 millisecond, a resolution of
 * 1 millisecond (or better) up to one second, and a resolution of 1 second (or better) up to 1,000 seconds. At it's
 * maximum tracked value (1 hour), it would still maintain a resolution of 3.6 seconds (or better).
 * <p>
 * Histogram tracks value counts in <b><code>long</code></b> fields. Smaller field types are available in the
 * {@link org.HdrHistogram.IntHistogram} and {@link org.HdrHistogram.ShortHistogram} implementations of
 * {@link org.HdrHistogram.AbstractHistogram}.
 * <p>
 * See description in {@link org.HdrHistogram.AbstractHistogram} for details.
 */

public class Histogram extends AbstractHistogram {
    final long[] counts;

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
        return (512 + (8 * counts.length));
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
    public Histogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(highestTrackableValue, numberOfSignificantValueDigits);
        counts = new long[countsArrayLength];
    }
}