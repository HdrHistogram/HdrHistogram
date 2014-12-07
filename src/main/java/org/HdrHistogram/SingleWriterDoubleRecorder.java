/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Records floting point values, and provides stable interval {@link DoubleHistogram} samples from live recorded data
 * without interrupting or stalling active recording of values. Each interval histogram provided contains all
 * value counts accumulated since the previous interval histogram was taken.
 * <p>
 * This pattern is commonly used in logging interval histogram information while recoding is ongoing.
 * <p>
 * {@link SingleWriterDoubleRecorder} expects only a single thread (the "single writer") to
 * call {@link SingleWriterDoubleRecorder#recordValue} or
 * {@link SingleWriterDoubleRecorder#recordValueWithExpectedInterval} at any point in time.
 * It DOES NOT support concurrent recording calls.
 *
 */

public class SingleWriterDoubleRecorder {
    private static AtomicLong instanceIdSequencer = new AtomicLong(1);
    private final long instanceId = instanceIdSequencer.getAndIncrement();

    private final WriterReaderPhaser recordingPhaser = new WriterReaderPhaser();

    private volatile InternalDoubleHistogram activeHistogram;
    private InternalDoubleHistogram inactiveHistogram;

    /**
     * Construct an auto-resizing {@link SingleWriterDoubleRecorder} using a precision stated as a
     * number of significant decimal digits.
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SingleWriterDoubleRecorder(final int numberOfSignificantValueDigits) {
        activeHistogram = new InternalDoubleHistogram(instanceId, numberOfSignificantValueDigits);
        inactiveHistogram = new InternalDoubleHistogram(instanceId, numberOfSignificantValueDigits);
    }

    /**
     * Construct a {@link SingleWriterDoubleRecorder} dynamic range of values to cover and a number
     * of significant decimal digits.
     *
     * @param highestToLowestValueRatio specifies the dynamic range to use (as a ratio)
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SingleWriterDoubleRecorder(final long highestToLowestValueRatio,
                                      final int numberOfSignificantValueDigits) {
        activeHistogram = new InternalDoubleHistogram(
                instanceId, highestToLowestValueRatio, numberOfSignificantValueDigits);
        inactiveHistogram = new InternalDoubleHistogram(
                instanceId, highestToLowestValueRatio, numberOfSignificantValueDigits);
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
     * Get a new instance of an interval histogram, which will include a stable, consistent view of all value
     * counts accumulated since the last interval histogram was taken.
     * <p>
     * Calling {@link SingleWriterDoubleRecorder#getIntervalHistogram()} will reset
     * the value counts, and start accumulating value counts for the next interval.
     *
     * @return a histogram containing the value counts accumulated since the last interval histogram was taken.
     */
    public synchronized DoubleHistogram getIntervalHistogram() {
        return getIntervalHistogram(null);
    }

    /**
     * Get an interval histogram, which will include a stable, consistent view of all value counts
     * accumulated since the last interval histogram was taken.
     * <p>
     * {@link SingleWriterDoubleRecorder#getIntervalHistogram(DoubleHistogram histogramToRecycle)
     * getIntervalHistogram(histogramToRecycle)}
     * accepts a previously returned interval histogram that can be recycled internally to avoid allocation
     * and content copying operations, and is therefore siginificantly more efficient for repeated use than
     * {@link SingleWriterDoubleRecorder#getIntervalHistogram()} and
     * {@link SingleWriterDoubleRecorder#getIntervalHistogramInto getIntervalHistogramInto()}. The
     * provided {@code histogramToRecycle} must
     * be either be null or an interval histogram returned by a previous call to
     * {@link SingleWriterDoubleRecorder#getIntervalHistogram(DoubleHistogram histogramToRecycle)
     * getIntervalHistogram(histogramToRecycle)} or
     * {@link SingleWriterDoubleRecorder#getIntervalHistogram()}.
     * <p>
     * NOTE: The caller is responsible for not recycling the same returned interval histogram more than once. If
     * the same interval histogram instance is recycled more than once, behavior is undefined.
     * <p>
     * Calling
     * {@link SingleWriterDoubleRecorder#getIntervalHistogram(DoubleHistogram histogramToRecycle)
     * getIntervalHistogram(histogramToRecycle)} will reset the value counts, and start accumulating value
     * counts for the next interval
     *
     * @param histogramToRecycle a previously returned interval histogram that may be recycled to avoid allocation and
     *                           copy operations.
     * @return a histogram containing the value counts accumulated since the last interval histogram was taken.
     */
    public synchronized DoubleHistogram getIntervalHistogram(DoubleHistogram histogramToRecycle) {
        if (histogramToRecycle == null) {
            histogramToRecycle = new InternalDoubleHistogram(inactiveHistogram);
        }
        // Verify that replacement histogram can validly be used as an inactiuve histogram replacement:
        validateFitAsReplacementHistogram(histogramToRecycle);
        try {
            recordingPhaser.readerLock();
            inactiveHistogram = (InternalDoubleHistogram) histogramToRecycle;
            performIntervalSample();
            return inactiveHistogram;
        } finally {
            recordingPhaser.readerUnlock();
        }
    }

    /**
     * Place a copy of the value counts accumulated since accumulated (since the last interval histogram
     * was taken) into {@code targetHistogram}.
     *
     * Calling {@link SingleWriterDoubleRecorder#getIntervalHistogramInto}() will
     * reset the value counts, and start accumulating value counts for the next interval.
     *
     * @param targetHistogram the histogram into which the interval histogram's data should be copied
     */
    public synchronized void getIntervalHistogramInto(DoubleHistogram targetHistogram) {
        performIntervalSample();
        inactiveHistogram.copyInto(targetHistogram);
    }

    /**
     * Reset any value counts accumulated thus far.
     */
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
            final InternalDoubleHistogram tempHistogram = inactiveHistogram;
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

    private class InternalDoubleHistogram extends DoubleHistogram {
        private final long containingInstanceId;

        private InternalDoubleHistogram(long id, int numberOfSignificantValueDigits) {
            super(numberOfSignificantValueDigits);
            this.containingInstanceId = id;
        }

        private InternalDoubleHistogram(long id,
                                        long highestToLowestValueRatio,
                                        int numberOfSignificantValueDigits) {
            super(highestToLowestValueRatio, numberOfSignificantValueDigits);
            this.containingInstanceId = id;
        }

        private InternalDoubleHistogram(InternalDoubleHistogram source) {
            super(source);
            this.containingInstanceId = source.containingInstanceId;
        }
    }

    void validateFitAsReplacementHistogram(DoubleHistogram replacementHistogram) {
        boolean bad = true;
        if ((replacementHistogram instanceof InternalDoubleHistogram)
                &&
                (((InternalDoubleHistogram) replacementHistogram).containingInstanceId ==
                        activeHistogram.containingInstanceId)
                ) {
            bad = false;
        }

        if (bad) {
            throw new IllegalArgumentException("replacement histogram must have been obtained via a previous" +
                    "getIntervalHistogram() call from this " + this.getClass().getName() +" instance");
        }
    }
}
