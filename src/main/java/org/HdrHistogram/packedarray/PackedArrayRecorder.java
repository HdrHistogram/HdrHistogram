/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram.packedarray;

import org.HdrHistogram.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Records increments and adds of integer values at indexes of a logical array of 64 bit signed integer values, and
 * provides stable interval {@link PackedLongArray} samples from live recorded data without interrupting or stalling
 * active recording of values. Each interval array provided contains all values accumulated since the previous
 * interval array was taken.
 * <p>
 * This pattern is commonly used in logging interval accumulator information while recording is ongoing.
 * <p>
 * {@link PackedArrayRecorder} supports fully concurrent
 * {@link PackedArrayRecorder#increment(int)} and
 * {@link PackedArrayRecorder#add(int, long)} calls.
 * While the {@link #increment increment()} and {@link #add add()} methods are not quite wait-free, they
 * come "close" to that behavior in the sense that a given thread will incur a total of no more than a capped
 * fixed number (e.g. 74 in a current implementation) of non-wait-free add or increment operations during
 * the lifetime of an interval array (including across recycling of that array across intervals within the
 * same recorder), regardless of the number of operations done.
 * <p>
 * A common pattern for using a {@link PackedArrayRecorder} looks like this:
 * <br><pre><code>
 * PackedArrayRecorder recorder = new PackedArrayRecorder(); //
 * PackedLongArray intervalArray = null;
 * ...
 * [start of some loop construct that periodically wants to grab an interval array]
 *   ...
 *   // Get interval array, recycling previous interval array:
 *   intervalArray = recorder.getIntervalArray(intervalArray);
 *   // Process the interval array, which is nice and stable here:
 *   myLogWriter.logArrayContents(intervalArray);
 *   ...
 * [end of loop construct]
 * </code></pre>
 *
 */

public class PackedArrayRecorder {
    private static AtomicLong instanceIdSequencer = new AtomicLong(1);
    private final long instanceId = instanceIdSequencer.getAndIncrement();

    private final WriterReaderPhaser recordingPhaser = new WriterReaderPhaser();

    private volatile PackedLongArray activeArray;

    /**
     * Construct a {@link PackedArrayRecorder} with a given (virtual) array length.
     *
     * @param virtualLength The (virtual) array length
     */
    public PackedArrayRecorder(final int virtualLength) {
        activeArray = new InternalConcurrentPackedLongArray(instanceId, virtualLength);
        activeArray.setStartTimeStamp(System.currentTimeMillis());
    }

    /**
     * Construct a {@link PackedArrayRecorder} with a given (virtual) array length, starting with a given
     * initial physical backing store length
     *
     * @param virtualLength The (virtual) array length
     * @param initialPhysicalLength The initial physical backing store length
     */
    public PackedArrayRecorder(final int virtualLength, final int initialPhysicalLength) {
        activeArray = new InternalConcurrentPackedLongArray(instanceId, virtualLength, initialPhysicalLength);
        activeArray.setStartTimeStamp(System.currentTimeMillis());
    }

    /**
     * Returns the virtual length of the array represented by this recorder
     * @return The virtual length of the array represented by this recorder
     */
    public int length() {
        return activeArray.length();
    }

    /**
     * Change the (virtual) length of the array represented by the this recorder
     * @param newVirtualLength the new (virtual) length to use
     */
    public void setVirtualLength(int newVirtualLength) {
        try {
            recordingPhaser.readerLock();
            // We don't care about concurrent modifications to the array, as setVirtualLength() in the
            // ConcurrentPackedLongArray takes care of those. However, we must perform the change of virtual
            // length under the recorder's readerLock protection to prevent mid-change observations:
            activeArray.setVirtualLength(newVirtualLength);
        } finally {
            recordingPhaser.readerUnlock();
        }
    }

    /**
     * Increment a value at a given index in the array
     * @param index the index of the value to be incremented
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds length()
     */
    public void increment(final int index) throws ArrayIndexOutOfBoundsException {
        long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
        try {
            activeArray.increment(index);
        } finally {
            recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    /**
     * Add to a value at a given index in the array
     * @param index The index of value to add to
     * @param valueToAdd The amount to add to the value at the given index
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds length()
     */
    public void add(final int index, final long valueToAdd) throws ArrayIndexOutOfBoundsException {
        long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
        try {
            activeArray.add(index, valueToAdd);
        } finally {
            recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
        }
    }

    /**
     * Get an interval array, which will include a stable, consistent view of all values
     * accumulated since the last interval array was taken.
     * <p>
     * Calling this method is equivalent to calling {@code getIntervalArray(null)}. It is generally recommended
     * that the {@link PackedArrayRecorder#getIntervalArray(PackedLongArray arrayToRecycle)
     * getIntervalHistogram(arrayToRecycle)} orm be used for
     * regular interval array sampling, as that form accepts a previously returned interval array that can be
     * recycled internally to avoid allocation and content copying operations, and is therefore significantly
     * more efficient for repeated use than {@link PackedArrayRecorder#getIntervalArray()}.
     * <p>
     * Calling {@link PackedArrayRecorder#getIntervalArray()} will reset the values at
     * all indexes of the array tracked by the recorder, and start accumulating values for the next interval.
     *
     * @return an array containing the values accumulated since the last interval array was taken.
     */
    public synchronized PackedLongArray getIntervalArray() {
        return getIntervalArray(null);
    }

    /**
     * Get an interval array, which will include a stable, consistent view of all values
     * accumulated since the last interval array was taken.
     * <p>
     * {@link PackedArrayRecorder#getIntervalArray(PackedLongArray arrayToRecycle)
     * getIntervalArray(arrayToRecycle)}
     * accepts a previously returned interval array that can be recycled internally to avoid allocation
     * and content copying operations, and is therefore significantly more efficient for repeated use than
     * {@link PackedArrayRecorder#getIntervalArray()}. The provided {@code arrayToRecycle} must
     * be either be null or an interval array returned by a previous call to
     * {@link PackedArrayRecorder#getIntervalArray(PackedLongArray arrayToRecycle)
     * getIntervalArray(arrayToRecycle)} or
     * {@link PackedArrayRecorder#getIntervalArray()}.
     * <p>
     * NOTE: The caller is responsible for not recycling the same returned interval array more than once. If
     * the same interval array instance is recycled more than once, behavior is undefined.
     * <p>
     * Calling {@link PackedArrayRecorder#getIntervalArray(PackedLongArray arrayToRecycle)
     * getIntervalArray(arrayToRecycle)} will reset the values at all indexes of the array
     * tracked by the recorder, and start accumulating values for the next interval.
     *
     * @param arrayToRecycle a previously returned interval array (from this instance of
     *                           {@link PackedArrayRecorder}) that may be recycled to avoid allocation and
     *                           copy operations.
     * @return an array containing the values accumulated since the last interval array was taken.
     */
    public synchronized PackedLongArray getIntervalArray(final PackedLongArray arrayToRecycle) {
        return getIntervalArray(arrayToRecycle, true);
    }

    /**
     * Get an interval array, which will include a stable, consistent view of all values
     * accumulated since the last interval array was taken.
     * <p>
     * {@link PackedArrayRecorder#getIntervalArray(PackedLongArray arrayToRecycle)
     * getIntervalArray(arrayToRecycle)}
     * accepts a previously returned interval array that can be recycled internally to avoid allocation
     * and content copying operations, and is therefore significantly more efficient for repeated use than
     * {@link PackedArrayRecorder#getIntervalArray()}. The provided {@code arrayToRecycle} must
     * be either be null or an interval array returned by a previous call to
     * {@link PackedArrayRecorder#getIntervalArray(PackedLongArray arrayToRecycle)
     * getIntervalArray(arrayToRecycle)} or
     * {@link PackedArrayRecorder#getIntervalArray()}.
     * <p>
     * NOTE: The caller is responsible for not recycling the same returned interval array more than once. If
     * the same interval array instance is recycled more than once, behavior is undefined.
     * <p>
     * Calling {@link PackedArrayRecorder#getIntervalArray(PackedLongArray arrayToRecycle)
     * getIntervalArray(arrayToRecycle, enforeContainingInstance)} will reset the values at all indexes
     * of the array tracked by the recorder, and start accumulating values for the next interval.
     *
     * @param arrayToRecycle a previously returned interval array that may be recycled to avoid allocation and
     *                           copy operations.
     * @param enforceContainingInstance if true, will only allow recycling of arrays previously returned from this
     *                                 instance of {@link PackedArrayRecorder}. If false, will allow recycling arrays
     *                                 previously returned by other instances of {@link PackedArrayRecorder}.
     * @return an array containing the values accumulated since the last interval array was taken.
     */
    public synchronized PackedLongArray getIntervalArray(final PackedLongArray arrayToRecycle,
                                                         final boolean enforceContainingInstance) {
        // Verify that replacement array can validly be used as an inactive array replacement:
        validateFitAsReplacementArray(arrayToRecycle, enforceContainingInstance);
        PackedLongArray sampledArray = performIntervalSample(arrayToRecycle);
        return sampledArray;
    }

    /**
     * Reset the array contents to all zeros.
     */
    public synchronized void reset() {
        // the currently active array is reset each time we flip:
        performIntervalSample(null);
    }

    private PackedLongArray performIntervalSample(final PackedLongArray arrayToRecycle) {
        PackedLongArray inactiveArray = arrayToRecycle;
        try {
            recordingPhaser.readerLock();

            // Make sure we have an inactive version to flip in:
            if (inactiveArray == null) {
                if (activeArray instanceof InternalConcurrentPackedLongArray) {
                    inactiveArray = new InternalConcurrentPackedLongArray(instanceId, activeArray.length());
                } else {
                    throw new IllegalStateException("Unexpected internal array type for activeArray");
                }
            } else {
                inactiveArray.clear();
            }

            // Swap active and inactive arrays:
            final PackedLongArray tempArray = inactiveArray;
            inactiveArray = activeArray;
            activeArray = tempArray;

            // Mark end time of previous interval and start time of new one:
            long now = System.currentTimeMillis();
            activeArray.setStartTimeStamp(now);
            inactiveArray.setEndTimeStamp(now);

            // Make sure we are not in the middle of recording a value on the previously active array:

            // Flip phase to make sure no recordings that were in flight pre-flip are still active:
            recordingPhaser.flipPhase(500000L /* yield in 0.5 msec units if needed */);
        } finally {
            recordingPhaser.readerUnlock();
        }
        return inactiveArray;
    }

    private static class InternalConcurrentPackedLongArray extends ConcurrentPackedLongArray {
        private final long containingInstanceId;

        private InternalConcurrentPackedLongArray(final long id, int virtualLength, final int initialPhysicalLength) {
            super(virtualLength, initialPhysicalLength);
            this.containingInstanceId = id;
        }

        private InternalConcurrentPackedLongArray(final long id, final int virtualLength) {
            super(virtualLength);
            this.containingInstanceId = id;
        }
    }

    private void validateFitAsReplacementArray(final PackedLongArray replacementArray,
                                               final boolean enforceContainingInstance) {
        boolean bad = true;
        if (replacementArray == null) {
            bad = false;
        } else if (replacementArray instanceof InternalConcurrentPackedLongArray) {
            if ((activeArray instanceof InternalConcurrentPackedLongArray)
                    &&
                    ((!enforceContainingInstance) ||
                            (((InternalConcurrentPackedLongArray)replacementArray).containingInstanceId ==
                                    ((InternalConcurrentPackedLongArray) activeArray).containingInstanceId)
                    )) {
                bad = false;
            }
        }
        if (bad) {
            throw new IllegalArgumentException("replacement array must have been obtained via a previous" +
                    " getIntervalArray() call from this " + this.getClass().getName() +
                    (enforceContainingInstance ? " instance" : " class"));
        }
    }
}
