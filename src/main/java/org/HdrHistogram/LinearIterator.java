/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
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

    /**
     * @param histogram The histogram this iterator will operate on
     * @param valueUnitsPerBucket The size (in value units) of each bucket iteration.
     */
    public LinearIterator(final AbstractHistogram histogram, final int valueUnitsPerBucket) {
        reset(histogram, valueUnitsPerBucket);
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
        nextValueReportingLevel += valueUnitsPerBucket;
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
