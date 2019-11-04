package org.HdrHistogram.packedarray;

/**
 * A Packed array of signed 64 bit values, and supports {@link #get get()}, {@link #set set()},
 * {@link #add add()} and {@link #increment increment()} operations on the logical contents of the array.
 */
public class PackedLongArray extends AbstractPackedLongArray {


    public PackedLongArray(final int virtualLength) {
        this(virtualLength, AbstractPackedArrayContext.MINIMUM_INITIAL_PACKED_ARRAY_CAPACITY);
    }

    public PackedLongArray(final int virtualLength, final int initialPhysicalLength) {
        arrayContext = new PackedArrayContext(virtualLength, initialPhysicalLength);
    }

    private PackedArrayContext arrayContext;

    @Override
    PackedArrayContext getStorageArrayContext() {
        return arrayContext;
    }

    @Override
    void resizeStorageArray(int newPhysicalLengthInLongs) {
        PackedArrayContext newArrayContext =
                new PackedArrayContext(arrayContext.getVirtualLength(), arrayContext, newPhysicalLengthInLongs);
        PackedArrayContext oldArrayContext = arrayContext;
        arrayContext = newArrayContext;
        for (IterationValue v : oldArrayContext.nonZeroValues()) {
            set(v.getIndex(), v.getValue());
        }
    }

    @Override
    public void setVirtualLength(final int newVirtualArrayLength) {
        if (newVirtualArrayLength < length()) {
            throw new IllegalArgumentException(
                    "Cannot set virtual length, as requested length " + newVirtualArrayLength +
                            " is smaller than the current virtual length " + length());
        }
        if (arrayContext.isPacked() &&
                (arrayContext.determineTopLevelShiftForVirtualLength(newVirtualArrayLength) ==
                arrayContext.getTopLevelShift())) {
            // No changes to the array context contents is needed. Just change the virtual length.
            arrayContext.setVirtualLength(newVirtualArrayLength);
            return;
        }
        PackedArrayContext oldArrayContext = arrayContext;
        arrayContext = new PackedArrayContext(newVirtualArrayLength, oldArrayContext, oldArrayContext.length());
        for (IterationValue v : oldArrayContext.nonZeroValues()) {
            set(v.getIndex(), v.getValue());
        }
    }

    @Override
    void clearContents() {
        arrayContext.clearContents();
    }

    @Override
    long criticalSectionEnter() {
        return 0;
    }

    @Override
    void criticalSectionExit(long criticalValueAtEnter) {
    }
}

