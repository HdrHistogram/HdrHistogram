/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Records integer values, and provides stable interval {@link Histogram} samples from
 * live recorded data without interrupting or stalling active recording of values. Each interval
 * histogram provided contains all value counts accumulated since the previous interval histogram
 * was taken.
 * <p>
 * This pattern is commonly used in logging interval histogram information while recording is ongoing.
 * <p>
 * {@link Recorder} supports concurrent
 * {@link Recorder#recordValue} or
 * {@link Recorder#recordValueWithExpectedInterval} calls.
 * Recording calls are wait-free on architectures that support atomic increment operations, and
 * are lock-free on architectures that do not.
 * <p>
 * A common pattern for using a {@link Recorder} looks like this:
 * <br><pre><code>
 * Recorder recorder = new Recorder(2); // Two decimal point accuracy
 * Histogram intervalHistogram = null;
 * ...
 * [start of some loop construct that periodically wants to grab an interval histogram]
 *   ...
 *   // Get interval histogram, recycling previous interval histogram:
 *   intervalHistogram = recorder.getIntervalHistogram(intervalHistogram);
 *   histogramLogWriter.outputIntervalHistogram(intervalHistogram);
 *   ...
 * [end of loop construct]
 * </code></pre>
 *
 */

public class Recorder implements ValueRecorder, IntervalHistogramProvider<Histogram> {
    private static AtomicLong instanceIdSequencer = new AtomicLong(1);
    private final long instanceId = instanceIdSequencer.getAndIncrement();

    private final WriterReaderPhaser recordingPhaser = new WriterReaderPhaser();

    private volatile Histogram activeHistogram;
    private Histogram inactiveHistogram;

    /**
     * Construct an auto-resizing {@link Recorder} with a lowest discernible value of
     * 1 and an auto-adjusting highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE / 2).
     * <p>
     * Depending on the valuer of the <b><code>packed</code></b> parameter {@link Recorder} can be configured to
     * track value counts in a packed internal representation optimized for typical histogram recoded values are
     * sparse in the value range and tend to be incremented in small unit counts. This packed representation tends
     * to require significantly smaller amounts of storage when compared to unpacked representations, but can incur
     * additional recording cost due to resizing and repacking operations that may
     * occur as previously unrecorded values are encountered.
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     * @param packed Specifies whether the recorder will uses a packed internal representation or not.
     */
    public Recorder(final int numberOfSignificantValueDigits, boolean packed) {
        activeHistogram = packed ?
                new InternalPackedConcurrentHistogram(instanceId, numberOfSignificantValueDigits) :
                new InternalConcurrentHistogram(instanceId, numberOfSignificantValueDigits);
        inactiveHistogram = null;
        activeHistogram.setStartTimeStamp(System.currentTimeMillis());
    }

    /**
     * Construct an auto-resizing {@link Recorder} with a lowest discernible value of
     * 1 and an auto-adjusting highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE / 2).
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public Recorder(final int numberOfSignificantValueDigits) {
        this(numberOfSignificantValueDigits, false);
    }

    /**
     * Construct a {@link Recorder} given the highest value to be tracked and a number of significant
     * decimal digits. The histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} 2.
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public Recorder(final long highestTrackableValue,
                    final int numberOfSignificantValueDigits) {
        this(1, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Construct a {@link Recorder} given the Lowest and highest values to be tracked and a number
     * of significant decimal digits. Providing a lowestDiscernibleValue is useful is situations where the units used
     * for the histogram's values are much smaller that the minimal accuracy required. E.g. when tracking
     * time values stated in nanosecond units, where the minimal accuracy required is a microsecond, the
     * proper value for lowestDiscernibleValue would be 1000.
     *
     * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by the histogram.
     *                               Must be a positive integer that is {@literal >=} 1. May be internally rounded
     *                               down to nearest power of 2.
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} (2 * lowestDiscernibleValue).
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public Recorder(final long lowestDiscernibleValue,
                    final long highestTrackableValue,
                    final int numberOfSignificantValueDigits) {
        activeHistogram = new InternalAtomicHistogram(
                instanceId, lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
        inactiveHistogram = null;
        activeHistogram.setStartTimeStamp(System.currentTimeMillis());
    }

    /**
     * Record a value
     * @param value the value to record
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    @Override
    public void recordValue(final long value) throws ArrayIndexOutOfBoundsException {
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
    @Override
    public void recordValueWithCount(final long value, final long count) throws ArrayIndexOutOfBoundsException {
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
     * See related notes {@link AbstractHistogram#recordValueWithExpectedInterval(long, long)}
     * for more explanations about coordinated omission and expected interval correction.
     *      *
     * @param value The value to record
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    @Override
    public void recordValueWithExpectedInterval(final long value, final long expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
        try {
            activeHistogram.recordValueWithExpectedInterval(value, expectedIntervalBetweenValueSamples);
        } finally {
            recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    @Override
    public synchronized Histogram getIntervalHistogram() {
        return getIntervalHistogram(null);
    }

    @Override
    public synchronized Histogram getIntervalHistogram(Histogram histogramToRecycle) {
        return getIntervalHistogram(histogramToRecycle, true);
    }

    @Override
    public synchronized Histogram getIntervalHistogram(Histogram histogramToRecycle,
                                                       boolean enforceContainingInstance) {
        // Verify that replacement histogram can validly be used as an inactive histogram replacement:
        validateFitAsReplacementHistogram(histogramToRecycle, enforceContainingInstance);
        inactiveHistogram = histogramToRecycle;
        performIntervalSample();
        Histogram sampledHistogram = inactiveHistogram;
        inactiveHistogram = null; // Once we expose the sample, we can't reuse it internally until it is recycled
        return sampledHistogram;
    }

    @Override
    public synchronized void getIntervalHistogramInto(Histogram targetHistogram) {
        performIntervalSample();
        inactiveHistogram.copyInto(targetHistogram);
    }

    /**
     * Reset any value counts accumulated thus far.
     */
    @Override
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
                if (activeHistogram instanceof InternalAtomicHistogram) {
                    inactiveHistogram = new InternalAtomicHistogram(
                            instanceId,
                            activeHistogram.getLowestDiscernibleValue(),
                            activeHistogram.getHighestTrackableValue(),
                            activeHistogram.getNumberOfSignificantValueDigits());
                } else if (activeHistogram instanceof InternalConcurrentHistogram) {
                    inactiveHistogram = new InternalConcurrentHistogram(
                            instanceId,
                            activeHistogram.getNumberOfSignificantValueDigits());
                } else if (activeHistogram instanceof InternalPackedConcurrentHistogram) {
                    inactiveHistogram = new InternalPackedConcurrentHistogram(
                            instanceId,
                            activeHistogram.getNumberOfSignificantValueDigits());
                } else {
                    throw new IllegalStateException("Unexpected internal histogram type for activeHistogram");
                }
            }

            inactiveHistogram.reset();

            // Swap active and inactive histograms:
            final Histogram tempHistogram = inactiveHistogram;
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

    private static class InternalAtomicHistogram extends AtomicHistogram {
        private final long containingInstanceId;

        private InternalAtomicHistogram(long id,
                                        long lowestDiscernibleValue,
                                        long highestTrackableValue,
                                        int numberOfSignificantValueDigits) {
            super(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
            this.containingInstanceId = id;
        }
    }

    private static class InternalConcurrentHistogram extends ConcurrentHistogram {
        private final long containingInstanceId;

        private InternalConcurrentHistogram(long id, int numberOfSignificantValueDigits) {
            super(numberOfSignificantValueDigits);
            this.containingInstanceId = id;
        }
    }

    private static class InternalPackedConcurrentHistogram extends PackedConcurrentHistogram {
        private final long containingInstanceId;

        private InternalPackedConcurrentHistogram(long id, int numberOfSignificantValueDigits) {
            super(numberOfSignificantValueDigits);
            this.containingInstanceId = id;
        }
    }

    private void validateFitAsReplacementHistogram(Histogram replacementHistogram,
                                                   boolean enforceContainingInstance) {
        boolean bad = true;
        if (replacementHistogram == null) {
            bad = false;
        } else if (replacementHistogram instanceof InternalAtomicHistogram) {
            if ((activeHistogram instanceof InternalAtomicHistogram)
                    &&
                    ((!enforceContainingInstance) ||
                            (((InternalAtomicHistogram)replacementHistogram).containingInstanceId ==
                                    ((InternalAtomicHistogram)activeHistogram).containingInstanceId)
                    )) {
                bad = false;
            }
        } else if (replacementHistogram instanceof InternalConcurrentHistogram) {
            if ((activeHistogram instanceof InternalConcurrentHistogram)
                    &&
                    ((!enforceContainingInstance) ||
                            (((InternalConcurrentHistogram)replacementHistogram).containingInstanceId ==
                                    ((InternalConcurrentHistogram)activeHistogram).containingInstanceId)
                    )) {
                bad = false;
            }
        } else if (replacementHistogram instanceof InternalPackedConcurrentHistogram) {
            if ((activeHistogram instanceof InternalPackedConcurrentHistogram)
                    &&
                    ((!enforceContainingInstance) ||
                            (((InternalPackedConcurrentHistogram)replacementHistogram).containingInstanceId ==
                                    ((InternalPackedConcurrentHistogram)activeHistogram).containingInstanceId)
                    )) {
                bad = false;
            }
        }
        if (bad) {
            throw new IllegalArgumentException("replacement histogram must have been obtained via a previous" +
                    " getIntervalHistogram() call from this " + this.getClass().getName() +
                    (enforceContainingInstance ? " instance" : " class"));
        }
    }
}
