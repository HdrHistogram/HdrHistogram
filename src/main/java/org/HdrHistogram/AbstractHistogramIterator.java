/**
 * AbstractHistogramIterator.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
 */

package org.HdrHistogram;

import java.util.ConcurrentModificationException;
import java.util.Iterator;

/**
 * Used for iterating through histogram values.
 */
abstract class AbstractHistogramIterator implements Iterator<HistogramIterationValue> {
    AbstractHistogram histogram;
    long savedHistogramTotalRawCount;

    int currentBucketIndex;
    int currentSubBucketIndex;
    long currentValueAtIndex;

    int nextBucketIndex;
    int nextSubBucketIndex;
    long nextValueAtIndex;

    long prevValueIteratedTo;
    long totalCountToPrevIndex;

    long totalCountToCurrentIndex;
    long totalValueToCurrentIndex;

    long arrayTotalCount;
    long countAtThisValue;

    private boolean freshSubBucket;
    HistogramIterationValue currentIterationValue;

    void resetIterator(final AbstractHistogram histogram) {
        this.histogram = histogram;
        this.savedHistogramTotalRawCount = histogram.totalCount;
        this.arrayTotalCount = histogram.totalCount;
        this.currentBucketIndex = 0;
        this.currentSubBucketIndex = 0;
        this.currentValueAtIndex = 0;
        this.nextBucketIndex = 0;
        this.nextSubBucketIndex = 1;
        this.nextValueAtIndex = 1;
        this.prevValueIteratedTo = 0;
        this.totalCountToPrevIndex = 0;
        this.totalCountToCurrentIndex = 0;
        this.totalValueToCurrentIndex = 0;
        this.countAtThisValue = 0;
        this.freshSubBucket = true;
        if (this.currentIterationValue == null)
            this.currentIterationValue = new HistogramIterationValue();
        currentIterationValue.reset();
    }

    /**
     * Returns true if the iteration has more elements. (In other words, returns true if next would return an
     * element rather than throwing an exception.)
     *
     * @return true if the iterator has more elements.
     */
    public boolean hasNext() {
        if (histogram.totalCount != savedHistogramTotalRawCount) {
            throw new ConcurrentModificationException();
        }
        return (totalCountToCurrentIndex < arrayTotalCount);
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the {@link HistogramIterationValue} associated with the next element in the iteration.
     */
    public HistogramIterationValue next() {
        // Move through the sub buckets and buckets until we hit the next  reporting level:
        while (!exhaustedSubBuckets()) {
            countAtThisValue = histogram.getCountAt(currentBucketIndex, currentSubBucketIndex);
            if (freshSubBucket) { // Don't add unless we've incremented since last bucket...
                totalCountToCurrentIndex += countAtThisValue;
                totalValueToCurrentIndex += countAtThisValue * histogram.medianEquivalentValue(currentValueAtIndex);
                freshSubBucket = false;
            }
            if (reachedIterationLevel()) {
                long valueIteratedTo = getValueIteratedTo();
                currentIterationValue.set(valueIteratedTo, prevValueIteratedTo, countAtThisValue,
                        (totalCountToCurrentIndex - totalCountToPrevIndex), totalCountToCurrentIndex,
                        totalValueToCurrentIndex, ((100.0 * totalCountToCurrentIndex) / arrayTotalCount),
                        getPercentileIteratedTo());
                prevValueIteratedTo = valueIteratedTo;
                totalCountToPrevIndex = totalCountToCurrentIndex;
                // move the next percentile reporting level forward:
                incrementIterationLevel();
                if (histogram.totalCount != savedHistogramTotalRawCount) {
                    throw new ConcurrentModificationException();
                }
                return currentIterationValue;
            }
            incrementSubBucket();
        }
        // Should not reach here. But possible for overflowed histograms under certain conditions
        throw new ArrayIndexOutOfBoundsException();
    }

    /**
     * Not supported. Will throw an {@link UnsupportedOperationException}.
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

    abstract void incrementIterationLevel();

    abstract boolean reachedIterationLevel();


    double getPercentileIteratedTo() {
        return (100.0 * (double) totalCountToCurrentIndex) / arrayTotalCount;
    }

    double getPercentileIteratedFrom() {
        return (100.0 * (double) totalCountToPrevIndex) / arrayTotalCount;
    }

    long getValueIteratedTo() {
        return histogram.highestEquivalentValue(currentValueAtIndex);
    }

    private boolean exhaustedSubBuckets() {
        return (currentBucketIndex >= histogram.bucketCount);
    }

    void incrementSubBucket() {
        freshSubBucket = true;
        // Take on the next index:
        currentBucketIndex = nextBucketIndex;
        currentSubBucketIndex = nextSubBucketIndex;
        currentValueAtIndex = nextValueAtIndex;
        // Figure out the next next index:
        nextSubBucketIndex++;
        if (nextSubBucketIndex >= histogram.subBucketCount) {
            nextSubBucketIndex = histogram.subBucketHalfCount;
            nextBucketIndex++;
        }
        nextValueAtIndex = nextSubBucketIndex << nextBucketIndex;
    }
}
