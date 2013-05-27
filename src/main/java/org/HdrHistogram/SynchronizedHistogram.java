/**
 * SynchronizedHistogram.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.5
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

    long getCountAtIndex(int index) {
        return counts[index];
    }

    void incrementCountAtIndex(int index) {
        synchronized (this) {
            counts[index]++;
        }
    }

    void addToCountAtIndex(int index, long value) {
        synchronized (this) {
            counts[index] += value;
        }
    }

    void clearCounts() {
        synchronized (this) {
            java.util.Arrays.fill(counts, 0);
            totalCount = 0;
        }
    }

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

    public SynchronizedHistogram copy() {
        SynchronizedHistogram copy = new SynchronizedHistogram(
                highestTrackableValue, numberOfSignificantValueDigits);
        copy.add(this);
        return copy;
    }

    long getTotalCount() {
        return totalCount;
    }

    void setTotalCount(long totalCount) {
        synchronized (this) {
           this.totalCount = totalCount;
        }
    }

    void incrementTotalCount() {
        synchronized (this) {
            totalCount++;
        }
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
    public SynchronizedHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(highestTrackableValue, numberOfSignificantValueDigits);
        counts = new long[countsArrayLength];
    }

    private void readObject(ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }
}