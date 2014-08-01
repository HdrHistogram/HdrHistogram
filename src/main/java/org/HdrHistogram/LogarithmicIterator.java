/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.util.Iterator;

/**
 * Used for iterating through histogram values in logarithmically increasing levels. The iteration is
 * performed in steps that start at <i>valueUnitsInFirstBucket</i> and increase exponentially according to
 * <i>logBase</i>, terminating when all recorded histogram values are exhausted. Note that each iteration "bucket"
 * includes values up to and including the next bucket boundary value.
 */
public class LogarithmicIterator extends AbstractHistogramIterator implements Iterator<HistogramIterationValue> {
    long valueUnitsInFirstBucket;
    double logBase;
    long nextValueReportingLevel;
    long nextValueReportingLevelLowestEquivalent;

    /**
     * Reset iterator for re-use in a fresh iteration over the same histogram data set.
     * @param valueUnitsInFirstBucket the size (in value units) of the first value bucket step
     * @param logBase the multiplier by which the bucket size is expanded in each iteration step.
     */
    public void reset(final long valueUnitsInFirstBucket, final double logBase) {
        reset(histogram, valueUnitsInFirstBucket, logBase);
    }

    private void reset(final AbstractHistogram histogram, final long valueUnitsInFirstBucket, final double logBase) {
        super.resetIterator(histogram);
        this.logBase = logBase;
        this.valueUnitsInFirstBucket = valueUnitsInFirstBucket;
        this.nextValueReportingLevel = valueUnitsInFirstBucket;
        this.nextValueReportingLevelLowestEquivalent = histogram.lowestEquivalentValue(nextValueReportingLevel);
    }

    /**
     * @param histogram The histogram this iterator will operate on
     * @param valueUnitsInFirstBucket the size (in value units) of the first value bucket step
     * @param logBase the multiplier by which the bucket size is expanded in each iteration step.
     */
    public LogarithmicIterator(final AbstractHistogram histogram, final long valueUnitsInFirstBucket, final double logBase) {
        reset(histogram, valueUnitsInFirstBucket, logBase);
    }

    @Override
    public boolean hasNext() {
        if (super.hasNext()) {
            return true;
        }
        // If next iterate does not move to the next sub bucket index (which is empty if
        // if we reached this point), then we are not done iterating... Otherwise we're done.
        return (nextValueReportingLevelLowestEquivalent < nextValueAtIndex);
    }

    @Override
    void incrementIterationLevel() {
        nextValueReportingLevel *= logBase;
        nextValueReportingLevelLowestEquivalent = histogram.lowestEquivalentValue(nextValueReportingLevel);
    }

    @Override
    long getValueIteratedTo() {
        return nextValueReportingLevel;
    }

    @Override
    boolean reachedIterationLevel() {
        return (currentValueAtIndex >= nextValueReportingLevelLowestEquivalent);
    }
}
