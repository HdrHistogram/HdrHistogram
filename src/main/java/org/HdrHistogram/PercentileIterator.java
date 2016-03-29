/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.util.Iterator;

/**
 * Used for iterating through histogram values according to percentile levels. The iteration is
 * performed in steps that start at 0% and reduce their distance to 100% according to the
 * <i>percentileTicksPerHalfDistance</i> parameter, ultimately reaching 100% when all recorded histogram
 * values are exhausted.
*/
public class PercentileIterator extends AbstractHistogramIterator implements Iterator<HistogramIterationValue> {
    int percentileTicksPerHalfDistance;
    double percentileLevelToIterateTo;
    double percentileLevelToIterateFrom;
    boolean reachedLastRecordedValue;

    /**
     * Reset iterator for re-use in a fresh iteration over the same histogram data set.
     *
     * @param percentileTicksPerHalfDistance The number of iteration steps per half-distance to 100%.
     */
    public void reset(final int percentileTicksPerHalfDistance) {
        reset(histogram, percentileTicksPerHalfDistance);
    }

    private void reset(final AbstractHistogram histogram, final int percentileTicksPerHalfDistance) {
        super.resetIterator(histogram);
        this.percentileTicksPerHalfDistance = percentileTicksPerHalfDistance;
        this.percentileLevelToIterateTo = 0.0;
        this.percentileLevelToIterateFrom = 0.0;
        this.reachedLastRecordedValue = false;
    }

    /**
     * @param histogram The histogram this iterator will operate on
     * @param percentileTicksPerHalfDistance The number of iteration steps per half-distance to 100%.
     */
    public PercentileIterator(final AbstractHistogram histogram, final int percentileTicksPerHalfDistance) {
        reset(histogram, percentileTicksPerHalfDistance);
    }

    @Override
    public boolean hasNext() {
        if (super.hasNext())
            return true;
        // We want one additional last step to 100%
        if (!reachedLastRecordedValue && (arrayTotalCount > 0)) {
            percentileLevelToIterateTo = 100.0;
            reachedLastRecordedValue = true;
            return true;
        }
        return false;
    }

    @Override
    void incrementIterationLevel() {
        percentileLevelToIterateFrom = percentileLevelToIterateTo;

        // To calculate the delta to add on at the current iteration, we want to know how many
        // iterations at the current scale it would take to go from 0 to 100.
        // By definition, it should take percentileTicksPerHalfDistance to go half the
        // remaining distance, so the ticks to go 0-100 is
        // 2 * percentileTicksPerHalfDistance * (multiples of remaining distance that fit in 0-100).
        //
        // However, this will give a "natural" half-distance behavior, where each iteration is
        // slightly smaller than the one preceding it. To make it easier for users to reason about,
        // it would be nice if the iteration size would stay the same throughout the entire first
        // half, then divide in half and stay the same for the next quarter, etc. To do this,
        // instead of using simply the number of times that the remaining distance will fit, we use
        // the greatest power of 2 smaller than that. This will exhibit the desired behavior of
        // "the same every time until it can double".

        long percentileReportingTicks =
                percentileTicksPerHalfDistance *
                        (long) Math.pow(2,
                                (long) (Math.log(100.0 / (100.0 - (percentileLevelToIterateTo))) / Math.log(2)) + 1);
        percentileLevelToIterateTo += 100.0 / percentileReportingTicks;
    }

    @Override
    boolean reachedIterationLevel() {
        if (countAtThisValue == 0)
            return false;
        double currentPercentile = (100.0 * (double) totalCountToCurrentIndex) / arrayTotalCount;
        return (currentPercentile >= percentileLevelToIterateTo);
    }

    @Override
    double getPercentileIteratedTo() {
        return percentileLevelToIterateTo;
    }

    @Override
    double getPercentileIteratedFrom() {
        return percentileLevelToIterateFrom;
    }
}
