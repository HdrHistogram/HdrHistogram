/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

/**
 * {@link IntervalHistogramRecorder} records values, and provides stable interval histograms from
 * live recorded data without interrupting or stalling active recording of values. Each interval
 * histogram provided contains all value counts accumulated since the previous interval histogram
 * was taken.
 *
 * This pattern is commonly used in logging interval histogram information while recoding is ongoing.
 */

public class IntervalHistogramRecorder {

    private final long lowestDiscernibleValue;
    private final long highestTrackableValue;
    private final int numberOfSignificantValueDigits;

    private final CriticalSectionPhaser recordingPhaser = new CriticalSectionPhaser();

    private volatile AtomicHistogram activeHistogram;
    private AtomicHistogram inactiveHistogram;

    /**
     * Construct a DoubleBufferedHistogram given the highest value to be tracked and a number of significant
     * decimal digits. The histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} 2.
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public IntervalHistogramRecorder(final long highestTrackableValue,
                                     final int numberOfSignificantValueDigits) {
        this(1, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Construct a DoubleBufferedHistogram given the Lowest and highest values to be tracked and a number
     * of significant decimal digits. Providing a lowestDiscernibleValue is useful is situations where the units used
     * for the histogram's values are much smaller that the minimal accuracy required. E.g. when tracking
     * time values stated in nanosecond units, where the minimal accuracy required is a microsecond, the
     * proper value for lowestDiscernibleValue would be 1000.
     *
     * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by the histogram.
     *                             Must be a positive integer that is {@literal >=} 1. May be internally rounded down to nearest
     *                             power of 2.
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} (2 * lowestDiscernibleValue).
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public IntervalHistogramRecorder(final long lowestDiscernibleValue,
                                     final long highestTrackableValue,
                                     final int numberOfSignificantValueDigits) {
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;

        activeHistogram = new AtomicHistogram(
                lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
        inactiveHistogram = new AtomicHistogram(
                lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Record a value
     * @param value the value to record
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValue(long value) {
        long criticalValueAtEnter = recordingPhaser.enteringCriticalSection();
        try {
            activeHistogram.recordValue(value);
        } finally {
            recordingPhaser.exitingCriticalSection(criticalValueAtEnter);
        }
    }

    /**
     * Record a value
     * <p>
     * To compensate for the loss of sampled values when a recorded value is larger than the expected
     * interval between value samples, Histogram will auto-generate an additional series of decreasingly-smaller
     * (down to the expectedIntervalBetweenValueSamples) value records.
     * <p>
     * See related notes {@link AbstractHistogram#recordValueWithExpectedInterval(long, long)}
     * for more explanations about coordinated opmissionand expetced interval correction.
     *      *
     * @param value The value to record
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValueWithExpectedInterval(final long value, final long expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        long criticalValueAtEnter = recordingPhaser.enteringCriticalSection();
        try {
            activeHistogram.recordValueWithExpectedInterval(value, expectedIntervalBetweenValueSamples);
        } finally {
            recordingPhaser.exitingCriticalSection(criticalValueAtEnter);
        }
    }

    /**
     * Get a new interval histogram, which will include a stable, consistent view of all value counts
     * accumulated since the last interval histobram was taken.
     * <p>
     * Calling {@link org.HdrHistogram.IntervalHistogramRecorder#getIntervalHistogram}() will reset
     * the value counts, and start accumulating value counts for the next interval.
     *
     * @return a histogram containing the value counts accumulated since the last interval histogram was taken.
     */
    public synchronized Histogram getIntervalHistogram() {
        Histogram intervalHistogram =
                new Histogram(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
        getIntervalHistogramInto(intervalHistogram);
        return intervalHistogram;
    }

    /**
     * Place a copy of the value counts accumulated since accumulated (since the last interval histogram
     * was taken) into {@param targetHistogram}.
     *
     * Calling {@link org.HdrHistogram.IntervalHistogramRecorder#getIntervalHistogramInto}() will reset
     * the value counts, and start accumulating value counts for the next interval.
     *
     * @param targetHistogram the histogram into which the interval histogram's data should be copied
     */
    public synchronized void getIntervalHistogramInto(AbstractHistogram targetHistogram) {
        performIntervalSample();
        inactiveHistogram.copyInto(targetHistogram);
    }

    private void performIntervalSample() {
        inactiveHistogram.reset();

        // Swap active and inactive histograms:
        final AtomicHistogram tempHistogram = inactiveHistogram;
        inactiveHistogram = activeHistogram;
        activeHistogram = tempHistogram;

        // Mark end time of previous interval and start time of new one:
        long now = System.currentTimeMillis();
        activeHistogram.setStartTimeStamp(now);
        inactiveHistogram.setEndTimeStamp(now);

        // Make sure we are not in the middle of recording a value on the previously current recording histogram:

        // Flip phase on epochs to make sure no in-flight recordings are active on pre-flip phase:
        recordingPhaser.flipPhase();
    }
}
