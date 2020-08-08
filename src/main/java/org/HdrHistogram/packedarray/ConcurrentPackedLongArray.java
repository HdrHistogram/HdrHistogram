package org.HdrHistogram.packedarray;

import org.HdrHistogram.WriterReaderPhaser;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * A Packed array of signed 64 bit values that supports {@link #get get()}, {@link #set set()}, {@link #add add()} and
 * {@link #increment increment()} operations the logical contents of the array.
 * <p>
 * {@link ConcurrentPackedLongArray} supports concurrent accumulation, with the {@link #add add()} and {@link #increment
 * increment()} methods providing lossless atomic accumulation in the presence of multiple writers. However, it is
 * important to note that {@link #add add()} and {@link #increment increment()} are the *only* safe concurrent
 * operations, and that all other operations, including {@link #get get()}, {@link #set set()} and {@link #clear()} may
 * produce "surprising" results if used on an array that is not at rest.
 * <p>
 * While the {@link #add add()} and {@link #increment increment()} methods are not quite wait-free, they come "close"
 * that behavior in the sense that a given thread will incur a total of no more than a capped fixed number (e.g. 74 in a
 * current implementation) of non-wait-free add or increment operations during the lifetime of an array, regardless of
 * the number of operations done.
 * </p>
 */
public class ConcurrentPackedLongArray extends PackedLongArray {

    public ConcurrentPackedLongArray(final int virtualLength) {
        this(virtualLength, AbstractPackedArrayContext.MINIMUM_INITIAL_PACKED_ARRAY_CAPACITY);
    }

    public ConcurrentPackedLongArray(final int virtualLength, final int initialPhysicalLength) {
        super();
        setArrayContext(new ConcurrentPackedArrayContext(virtualLength, initialPhysicalLength));
    }

    transient WriterReaderPhaser wrp = new WriterReaderPhaser();

    @Override
    void resizeStorageArray(final int newPhysicalLengthInLongs) {
        AbstractPackedArrayContext inactiveArrayContext;
        try {
            wrp.readerLock();

            // Create a new array context, mimicking the structure of the currently active
            // context, but without actually populating any values.
            ConcurrentPackedArrayContext newArrayContext =
                    new ConcurrentPackedArrayContext(
                            getArrayContext().getVirtualLength(),
                            getArrayContext(), newPhysicalLengthInLongs
                    );

            // Flip the current live array context and the newly created one:
            inactiveArrayContext = getArrayContext();
            setArrayContext(newArrayContext);

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
        AbstractPackedArrayContext inactiveArrayContext;
        try {
            wrp.readerLock();
            AbstractPackedArrayContext currentArrayContext = getArrayContext();
            if (currentArrayContext.isPacked() &&
                    (currentArrayContext.determineTopLevelShiftForVirtualLength(newVirtualArrayLength) ==
                            currentArrayContext.getTopLevelShift())) {
                // No changes to the array context contents is needed. Just change the virtual length.
                currentArrayContext.setVirtualLength(newVirtualArrayLength);
                return;
            }
            inactiveArrayContext = currentArrayContext;
            setArrayContext(
                    new ConcurrentPackedArrayContext(
                            newVirtualArrayLength,
                            inactiveArrayContext,
                            inactiveArrayContext.length()
                    ));

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
    public ConcurrentPackedLongArray copy() {
        ConcurrentPackedLongArray copy = new ConcurrentPackedLongArray(this.length(), this.getPhysicalLength());
        copy.add(this);
        return copy;
    }

    @Override
    void clearContents() {
        try {
            wrp.readerLock();
            getArrayContext().clearContents();
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
