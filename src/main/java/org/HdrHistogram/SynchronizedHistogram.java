/**
 * SynchronizedHistogram.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <h3>An internally synchronized High Dynamic Range (HDR) Histogram using a <b><code>long</code></b> count type </h3>
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class SynchronizedHistogram extends AbstractHistogram {
    long totalCount;
    final long[] counts;

    @Override
    long getCountAtIndex(final int index) {
        return counts[index];
    }

    @Override
    void incrementCountAtIndex(final int index) {
        synchronized (this) {
            counts[index]++;
        }
    }

    @Override
    void addToCountAtIndex(final int index, final long value) {
        synchronized (this) {
            counts[index] += value;
        }
    }

    @Override
    void clearCounts() {
        synchronized (this) {
            java.util.Arrays.fill(counts, 0);
            totalCount = 0;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public synchronized void add(final AbstractHistogram other) {
        // Synchronize add(). Avoid deadlocks by synchronizing in order of construction identity count.
        if (identityCount < other.identityCount) {
            synchronized (this) {
                synchronized (other) {
                    super.add(other);
                }
            }
        } else {
            synchronized (other) {
                synchronized (this) {
                    super.add(other);
                }
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public SynchronizedHistogram copy() {
        SynchronizedHistogram copy = new SynchronizedHistogram(
                highestTrackableValue, numberOfSignificantValueDigits);
        copy.add(this);
        return copy;
    }

    /**
     * @inheritDoc
     */
    @Override
    public SynchronizedHistogram copyCorrectedForCoordinatedOmission(final long expectedIntervalBetweenValueSamples) {
        SynchronizedHistogram toHistogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        return toHistogram;
    }

    @Override
    long getTotalCount() {
        return totalCount;
    }

    @Override
    void setTotalCount(final long totalCount) {
        synchronized (this) {
           this.totalCount = totalCount;
        }
    }

    @Override
    void incrementTotalCount() {
        synchronized (this) {
            totalCount++;
        }
    }

    @Override
    void addToTotalCount(long value) {
        synchronized (this) {
            totalCount += value;
        }
    }

    /**
     * Provide a (conservatively high) estimate of the Histogram's total footprint in bytes
     *
     * @return a (conservatively high) estimate of the Histogram's total footprint in bytes
     */
    @Override
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
    public SynchronizedHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(highestTrackableValue, numberOfSignificantValueDigits);
        counts = new long[countsArrayLength];
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }
}