/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

/**
 * {@link org.HdrHistogram.IntervalDoubleHistogramRecorder} records values, and provides stable
 * interval histograms from live recorded data without interrupting or stalling active recording
 * of values. Each interval histogram provided contains all value counts accumulated since the
 * previous interval histogram was taken.
 * <p>
 * This pattern is commonly used in logging interval histogram information while recoding is ongoing.
 * <p>
 *     NOTE: IntervalDoubleHistogramRecorder uses DoubleHistograms with AtomicHistograms for
 *     internal count tracking. These DoubleHistograms maintain safe and wait-free atomic
 *     recording qualities ONLY as long as recoded values fall within an already recorded
 *     value range. But when values outside of the already recorded range appear, their recording
 *     operation may not be atomic. In single writer use cases of
 *     {@link org.HdrHistogram.IntervalDoubleHistogramRecorder} this is not a concern. But
 *     in multi-writer situations, it is useful to pre-establish a range of values for which
 *     atomic operations remain safe. This can be done using the
 *     {@link org.HdrHistogram.IntervalDoubleHistogramRecorder#forceValueIntoRange} method.
 * <p>
 * {@link org.HdrHistogram.IntervalDoubleHistogramRecorder} supports concurrent
 * {@link org.HdrHistogram.IntervalDoubleHistogramRecorder#recordValue} or
 * {@link org.HdrHistogram.IntervalDoubleHistogramRecorder#recordValueWithExpectedInterval} calls.
 * Recording calls are wait-free on architectures that support atomic increment operations, and
 * are lock-free on architectures that do no.
 *
 */

public class IntervalDoubleHistogramRecorder {

    private final WriterReaderPhaser recordingPhaser = new WriterReaderPhaser();

    private volatile DoubleHistogram activeHistogram;
    private DoubleHistogram inactiveHistogram;

    /**
     * Construct a DoubleBufferedHistogram dynamic range of values to cover and a number of significant
     * decimal digits. The histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestToLowestValueRatio specifies the dynamic range to use (as a ratio)
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public IntervalDoubleHistogramRecorder(final long highestToLowestValueRatio,
                                           final int numberOfSignificantValueDigits) {
        activeHistogram = new DoubleHistogram(
                highestToLowestValueRatio, numberOfSignificantValueDigits, AtomicHistogram.class);
        inactiveHistogram = new DoubleHistogram(
                highestToLowestValueRatio, numberOfSignificantValueDigits, AtomicHistogram.class);
    }

    /**
     * force value into the histogram's covered range. Will overflow (with an AIOOBE) if value
     * cannot be covered within the bounds of dynamic range and already recorded values.
     * <p>
     * Note the value 0.0 is always in range, and will not affect or force any range changes.
     *
     * @param value The value to force into range.
     */
    public void forceValueIntoRange(double value) {
        long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
        try {
            activeHistogram.recordValueWithCount(value, 0);
        } finally {
            recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    /**
     * Record a value
     * @param value the value to record
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValue(double value) {
        long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
        try {
            activeHistogram.recordValue(value);
        } finally {
            recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    /**
     * Record a value
     * <p>
     * To compensate for the loss of sampled values when a recorded value is larger than the expected
     * interval between value samples, Histogram will auto-generate an additional series of decreasingly-smaller
     * (down to the expectedIntervalBetweenValueSamples) value records.
     * <p>
     * See related notes {@link org.HdrHistogram.DoubleHistogram#recordValueWithExpectedInterval(double, double)}
     * for more explanations about coordinated opmissionand expetced interval correction.
     *      *
     * @param value The value to record
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValueWithExpectedInterval(final double value, final double expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
        try {
            activeHistogram.recordValueWithExpectedInterval(value, expectedIntervalBetweenValueSamples);
        } finally {
            recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    /**
     * Get a new interval histogram, which will include a stable, consistent view of all value counts
     * accumulated since the last interval histogram was taken.
     * <p>
     * Calling {@link org.HdrHistogram.IntervalDoubleHistogramRecorder#getIntervalHistogram}() will reset
     * the value counts, and start accumulating value counts for the next interval.
     *
     * @return a histogram containing the value counts accumulated since the last interval histogram was taken.
     */
    public synchronized DoubleHistogram getIntervalHistogram() {
        DoubleHistogram intervalHistogram = new DoubleHistogram(inactiveHistogram);
        getIntervalHistogramInto(intervalHistogram);
        return intervalHistogram;
    }

    /**
     * Place a copy of the value counts accumulated since accumulated (since the last interval histogram
     * was taken) into {@code targetHistogram}.
     *
     * Calling {@link org.HdrHistogram.IntervalDoubleHistogramRecorder#getIntervalHistogramInto}() will reset
     * the value counts, and start accumulating value counts for the next interval.
     *
     * @param targetHistogram the histogram into which the interval histogram's data should be copied
     */
    public synchronized void getIntervalHistogramInto(DoubleHistogram targetHistogram) {
        performIntervalSample();
        inactiveHistogram.copyInto(targetHistogram);
    }

    public synchronized void reset() {
        // the currently inactive histogram is reset each time we flip. So flipping twice resets both:
        performIntervalSample();
        performIntervalSample();
    }

    private void performIntervalSample() {
        inactiveHistogram.reset();
        try {
            recordingPhaser.readerLock();

            // Swap active and inactive histograms:
            final DoubleHistogram tempHistogram = inactiveHistogram;
            inactiveHistogram = activeHistogram;
            activeHistogram = tempHistogram;

            // Mark end time of previous interval and start time of new one:
            long now = System.currentTimeMillis();
            activeHistogram.setStartTimeStamp(now);
            inactiveHistogram.setEndTimeStamp(now);

            // Make sure we are not in the middle of recording a value on the previously active histogram:

            // Flip phase to make sure no recordings that were in flight pre-flip are still active:
            recordingPhaser.flipPhase(500000L /* yield in 0.5 msec units if needed */);
        } finally {
            recordingPhaser.readerUnlock();
        }
    }
}
