/**
 * LogarithmicIterator.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
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
    int valueUnitsInFirstBucket;
    int logBase;
    long nextValueReportingLevel;
    long nextValueReportingLevelLowestEquivalent;

    /**
     * Reset iterator for re-use in a fresh iteration over the same histogram data set.
     * @param valueUnitsInFirstBucket the size (in value units) of the first value bucket step
     * @param logBase the multiplier by which the bucket size is expanded in each iteration step.
     */
    public void reset(final int valueUnitsInFirstBucket, final int logBase) {
        reset(histogram, valueUnitsInFirstBucket, logBase);
    }

    private void reset(final AbstractHistogram histogram, final int valueUnitsInFirstBucket, final int logBase) {
        super.resetIterator(histogram);
        this.logBase = logBase;
        this.valueUnitsInFirstBucket = valueUnitsInFirstBucket;
        this.nextValueReportingLevel = valueUnitsInFirstBucket;
        this.nextValueReportingLevelLowestEquivalent = histogram.lowestEquivalentValue(nextValueReportingLevel);
    }

    LogarithmicIterator(final AbstractHistogram histogram, final int valueUnitsInFirstBucket, final int logBase) {
        reset(histogram, valueUnitsInFirstBucket, logBase);
    }

    @Override
    public boolean hasNext() {
        return (super.hasNext() || (countAtThisValue != 0));
    }

    void incrementIterationLevel() {
        nextValueReportingLevel *= logBase;
        nextValueReportingLevelLowestEquivalent = histogram.lowestEquivalentValue(nextValueReportingLevel);
    }

    @Override
    long getValueIteratedTo() {
        return nextValueReportingLevel;
    }

    boolean reachedIterationLevel() {
        return (currentValueAtIndex >= nextValueReportingLevelLowestEquivalent);
    }
}
