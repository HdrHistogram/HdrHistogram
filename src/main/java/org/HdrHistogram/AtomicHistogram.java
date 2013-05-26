/**
 * AtomicHistogram.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.5
 */

package org.HdrHistogram;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.atomic.*;

/**
 * <h3>A High Dynamic Range (HDR) Histogram using atomic <b><code>long</code></b> count type </h3>
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class AtomicHistogram extends AbstractHistogram {
    static final AtomicLongFieldUpdater<AtomicHistogram> totalCountUpdater =
            AtomicLongFieldUpdater.newUpdater(AtomicHistogram.class, "totalCount");
    volatile long totalCount;
    final AtomicLongArray counts;

    long getCountAtIndex(int index) {
        return counts.get(index);
    }

    void incrementCountAtIndex(int index) {
        counts.incrementAndGet(index);
    }

    void addToCountAtIndex(int index, long value) {
        counts.addAndGet(index, value);
    }

    void clearCounts() {
        for (int i = 0; i < counts.length(); i++)
            counts.lazySet(i, 0);
        totalCountUpdater.set(this, 0);
    }
    
    public AtomicHistogram copy() {
      AtomicHistogram copy = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
      copy.add(this);
      return copy;
    }

    long getTotalCount() {
        return totalCount;
    }

    void setTotalCount(long totalCount) {
        totalCountUpdater.set(this, totalCount);
    }

    void incrementTotalCount() {
        totalCountUpdater.incrementAndGet(this);
    }

    /**
     * Provide a (conservatively high) estimate of the Histogram's total footprint in bytes
     *
     * @return a (conservatively high) estimate of the Histogram's total footprint in bytes
     */
    public int getEstimatedFootprintInBytes() {
        return (512 + (8 * counts.length()));
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
    public AtomicHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(highestTrackableValue, numberOfSignificantValueDigits);
        counts = new AtomicLongArray(countsArrayLength);
    }

    private void readObject(ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }
}