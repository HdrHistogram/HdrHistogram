/**
 * LinearIterator.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
 */

package org.HdrHistogram;

import java.util.Iterator;

/**
 * Used for iterating through histogram values in linear steps. The iteration is
 * performed in steps of <i>valueUnitsPerBucket</i> in size, terminating when all recorded histogram
 * values are exhausted. Note that each iteration "bucket" includes values up to and including
 * the next bucket boundary value.
 */
public class LinearIterator extends AbstractHistogramIterator implements Iterator<HistogramIterationValue> {
    long valueUnitsPerBucket;
    long nextValueReportingLevel;
    long nextValueReportingLevelLowestEquivalent;

    /**
     * Reset iterator for re-use in a fresh iteration over the same histogram data set.
     * @param valueUnitsPerBucket The size (in value units) of each bucket iteration.
     */
    public void reset(final int valueUnitsPerBucket) {
        reset(histogram, valueUnitsPerBucket);
    }

    private void reset(final AbstractHistogram histogram, final long valueUnitsPerBucket) {
        super.resetIterator(histogram);
        this.valueUnitsPerBucket = valueUnitsPerBucket;
        this.nextValueReportingLevel = valueUnitsPerBucket;
        this.nextValueReportingLevelLowestEquivalent = histogram.lowestEquivalentValue(nextValueReportingLevel);
    }

    LinearIterator(final AbstractHistogram histogram, final int valueUnitsPerBucket) {
        reset(histogram, valueUnitsPerBucket);
    }

    @Override
    public boolean hasNext() {
        return (super.hasNext() || (countAtThisValue != 0));
    }

    void incrementIterationLevel() {
        nextValueReportingLevel += valueUnitsPerBucket;
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
