/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Records floating point values, and provides stable interval {@link DoubleHistogram} samples from live recorded data
 * without interrupting or stalling active recording of values. Each interval histogram provided contains all
 * value counts accumulated since the previous interval histogram was taken.
 * <p>
 * This pattern is commonly used in logging interval histogram information while recording is ongoing.
 * <p>
 * {@link SingleWriterDoubleRecorder} expects only a single thread (the "single writer") to
 * call {@link SingleWriterDoubleRecorder#recordValue} or
 * {@link SingleWriterDoubleRecorder#recordValueWithExpectedInterval} at any point in time.
 * It DOES NOT support concurrent recording calls.
 * Recording calls are wait-free on architectures that support atomic increment operations, and
 * are lock-free on architectures that do not.
 * <p>
 * A common pattern for using a {@link SingleWriterDoubleRecorder} looks like this:
 * <br><pre><code>
 * SingleWriterDoubleRecorder recorder = new SingleWriterDoubleRecorder(2); // Two decimal point accuracy
 * DoubleHistogram intervalHistogram = null;
 * ...
 * [start of some loop construct that periodically wants to grab an interval histogram]
 *   ...
 *   // Get interval histogram, recycling previous interval histogram:
 *   intervalHistogram = recorder.getIntervalHistogram(intervalHistogram);
 *   histogramLogWriter.outputIntervalHistogram(intervalHistogram);
 *   ...
 * [end of loop construct]
 * </code></pre>
 */

public class SingleWriterDoubleRecorder implements DoubleValueRecorder, IntervalHistogramProvider<DoubleHistogram> {
    private static AtomicLong instanceIdSequencer = new AtomicLong(1);
    private final long instanceId = instanceIdSequencer.getAndIncrement();

    private final WriterReaderPhaser recordingPhaser = new WriterReaderPhaser();

    private volatile DoubleHistogram activeHistogram;
    private DoubleHistogram inactiveHistogram;

    /**
     * Construct an auto-resizing {@link SingleWriterDoubleRecorder} using a precision stated as a
     * number of significant decimal digits.
     * <p>
     * Depending on the valuer of the <b><code>packed</code></b> parameter {@link SingleWriterDoubleRecorder} can
     * be configured to track value counts in a packed internal representation optimized for typical histogram
     * recoded values are sparse in the value range and tend to be incremented in small unit counts. This packed
     * representation tends to require significantly smaller amounts of storage when compared to unpacked
     * representations, but can incur additional recording cost due to resizing and repacking operations that may
     * occur as previously unrecorded values are encountered.
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     * @param packed Specifies whether the recorder will uses a packed internal representation or not.
     */
    public SingleWriterDoubleRecorder(final int numberOfSignificantValueDigits, final boolean packed) {
        activeHistogram = packed ?
                new PackedInternalDoubleHistogram(instanceId, numberOfSignificantValueDigits) :
                new InternalDoubleHistogram(instanceId, numberOfSignificantValueDigits);
        inactiveHistogram = null;
        activeHistogram.setStartTimeStamp(System.currentTimeMillis());
    }

    /**
     * Construct an auto-resizing {@link SingleWriterDoubleRecorder} using a precision stated as a
     * number of significant decimal digits.
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SingleWriterDoubleRecorder(final int numberOfSignificantValueDigits) {
        this(numberOfSignificantValueDigits, false);
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
        inactiveHistogram = null;
        activeHistogram.setStartTimeStamp(System.currentTimeMillis());
    }

    /**
     * Record a value
     * @param value the value to record
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValue(final double value) {
        long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
        try {
            activeHistogram.recordValue(value);
        } finally {
            recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    /**
     * Record a value in the histogram (adding to the value's current count)
     *
     * @param value The value to be recorded
     * @param count The number of occurrences of this value to record
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValueWithCount(final double value, final long count) throws ArrayIndexOutOfBoundsException {
        long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
        try {
            activeHistogram.recordValueWithCount(value, count);
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
     * for more explanations about coordinated omission and expected interval correction.
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

    @Override
    public synchronized DoubleHistogram getIntervalHistogram() {
        return getIntervalHistogram(null);
    }

    @Override
    public synchronized DoubleHistogram getIntervalHistogram(DoubleHistogram histogramToRecycle) {
        return getIntervalHistogram(histogramToRecycle, true);
    }

    @Override
    public synchronized DoubleHistogram getIntervalHistogram(DoubleHistogram histogramToRecycle,
                                                             boolean enforceContainingInstance) {
        // Verify that replacement histogram can validly be used as an inactive histogram replacement:
        validateFitAsReplacementHistogram(histogramToRecycle, enforceContainingInstance);
        inactiveHistogram = histogramToRecycle;
        performIntervalSample();
        DoubleHistogram sampledHistogram = inactiveHistogram;
        inactiveHistogram = null; // Once we expose the sample, we can't reuse it internally until it is recycled
        return sampledHistogram;
    }

    @Override
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
        try {
            recordingPhaser.readerLock();

            // Make sure we have an inactive version to flip in:
            if (inactiveHistogram == null) {
                if (activeHistogram instanceof InternalDoubleHistogram) {
                    inactiveHistogram = new InternalDoubleHistogram((InternalDoubleHistogram) activeHistogram);
                } else if (activeHistogram instanceof PackedInternalDoubleHistogram) {
                    inactiveHistogram = new PackedInternalDoubleHistogram(
                            instanceId, activeHistogram.getNumberOfSignificantValueDigits());
                } else {
                    throw new IllegalStateException("Unexpected internal histogram type for activeHistogram");
                }
            }

            inactiveHistogram.reset();

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

    private class PackedInternalDoubleHistogram extends PackedDoubleHistogram {
        private final long containingInstanceId;

        private PackedInternalDoubleHistogram(long id, int numberOfSignificantValueDigits) {
            super(numberOfSignificantValueDigits);
            this.containingInstanceId = id;
        }
    }

    private void validateFitAsReplacementHistogram(DoubleHistogram replacementHistogram,
                                                   boolean enforceContainingInstance) {
        boolean bad = true;
        if (replacementHistogram == null) {
            bad = false;
        } else if ((replacementHistogram instanceof InternalDoubleHistogram)
                &&
                ((!enforceContainingInstance) ||
                        (((InternalDoubleHistogram) replacementHistogram).containingInstanceId ==
                                ((InternalDoubleHistogram)activeHistogram).containingInstanceId)
                )) {
            bad = false;
        } else if ((replacementHistogram instanceof PackedInternalDoubleHistogram)
                &&
                ((!enforceContainingInstance) ||
                        (((PackedInternalDoubleHistogram) replacementHistogram).containingInstanceId ==
                                ((PackedInternalDoubleHistogram)activeHistogram).containingInstanceId)
                )) {
            bad = false;
        }

        if (bad) {
            throw new IllegalArgumentException("replacement histogram must have been obtained via a previous " +
                    "getIntervalHistogram() call from this " + this.getClass().getName() +" instance");
        }
    }
}
