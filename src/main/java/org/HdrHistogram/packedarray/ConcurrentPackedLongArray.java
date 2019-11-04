package org.HdrHistogram.packedarray;

import org.HdrHistogram.WriterReaderPhaser;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * A Packed array of signed 64 bit values, and supports {@link #get get()}, {@link #set set()},
 * {@link #add add()} and {@link #increment increment()} operations on the logical contents of the array.
 * <p>
 * {@link ConcurrentPackedLongArray} supports concurrent accumulation, with the {@link #add add()}
 * and {@link #increment increment()} methods providing lossless atomic accumulation in the presence of
 * multiple writers. However, it is impotant to note that {@link #add add()} and {@link #increment increment()}
 * are the *only* safe concurrent operation operations, and that all other operations, including
 * {@link #get get()}, {@link #set set()} and {@link #clear()} may produce suprising results if used on an
 * array that is not at rest.
 * </p>
 */
public class ConcurrentPackedLongArray extends AbstractPackedLongArray {

    public ConcurrentPackedLongArray(final int virtualLength) {
        this(virtualLength, AbstractPackedArrayContext.MINIMUM_INITIAL_PACKED_ARRAY_CAPACITY);
    }

    public ConcurrentPackedLongArray(final int virtualLength, final int initialPhysicalLength) {
        arrayContext = new ConcurrentPackedArrayContext(virtualLength, initialPhysicalLength);
    }

    transient WriterReaderPhaser wrp = new WriterReaderPhaser();

    private ConcurrentPackedArrayContext arrayContext;

    @Override
    ConcurrentPackedArrayContext getStorageArrayContext() {
        return arrayContext;
    }

    @Override
    void resizeStorageArray(int newPhysicalLengthInLongs) {
        ConcurrentPackedArrayContext inactiveArrayContext;
        try {
            wrp.readerLock();

            ConcurrentPackedArrayContext newArrayContext =
                    new ConcurrentPackedArrayContext(arrayContext.getVirtualLength(), arrayContext, newPhysicalLengthInLongs);

            // Flip the current live array context and the newly created one:
            inactiveArrayContext = arrayContext;
            arrayContext = newArrayContext;

            wrp.flipPhase();

            // The now inactive array context is stable, and the new array context is active.
            // We don't want to try to record values from the inactive into the new array context
            // here (under the wrp reader lock) because we could deadlock if resizing is needed.
            // Instead, value recording will be done after we release the read lock.

        } finally {
            wrp.readerUnlock();
        }

        // Record all contents from the now inactive array to new live one:
        for (IterationValue v : inactiveArrayContext.nonZeroValues()) {
            add(v.getIndex(), v.getValue());
        }

        // inactive array contents is fully committed into the newly resized live array. It can now die in peace.

    }

    @Override
    public void setVirtualLength(final int newVirtualArrayLength) {
        if (newVirtualArrayLength < length()) {
            throw new IllegalArgumentException(
                    "Cannot set virtual length, as requested length " + newVirtualArrayLength +
                            " is smaller than the current virtual length " + length());
        }
        ConcurrentPackedArrayContext inactiveArrayContext;
        try {
            wrp.readerLock();
            if (arrayContext.isPacked() &&
                    (arrayContext.determineTopLevelShiftForVirtualLength(newVirtualArrayLength) ==
                    arrayContext.getTopLevelShift())) {
                // No changes to the array context contents is needed. Just change the virtual length.
                arrayContext.setVirtualLength(newVirtualArrayLength);
                return;
            }
            inactiveArrayContext = arrayContext;
            arrayContext = new ConcurrentPackedArrayContext(
                    newVirtualArrayLength, inactiveArrayContext, inactiveArrayContext.length());

            wrp.flipPhase();

            // The now inactive array context is stable, and the new array context is active.
            // We don't want to try to record values from the inactive into the new array context
            // here (under the wrp reader lock) because we could deadlock if resizing is needed.
            // Instead, value recording will be done after we release the read lock.

        } finally {
            wrp.readerUnlock();
        }

        for (IterationValue v : inactiveArrayContext.nonZeroValues()) {
            add(v.getIndex(), v.getValue());
        }
    }

    @Override
    void clearContents() {
        try {
            wrp.readerLock();
            arrayContext.clearContents();
        } finally {
            wrp.readerUnlock();
        }
    }

    @Override
    long criticalSectionEnter() {
        return wrp.writerCriticalSectionEnter();
    }

    @Override
    void criticalSectionExit(long criticalValueAtEnter) {
        wrp.writerCriticalSectionExit(criticalValueAtEnter);
    }

    /////

    @Override
    public String toString() {
        try {
            wrp.readerLock();
            return super.toString();
        } finally {
            wrp.readerUnlock();
        }
    }

    @Override
    public void clear() {
        try {
            wrp.readerLock();
            super.clear();
        } finally {
            wrp.readerUnlock();
        }
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
        wrp = new WriterReaderPhaser();
    }
}
