package org.HdrHistogram.packedarray;

import java.util.Arrays;

/**
 * A non-concurrent array context. No atomics used.
 */
class PackedArrayContext extends AbstractPackedArrayContext {

    PackedArrayContext(final int virtualLength, final int initialPhysicalLength) {
        int physicalLength = Math.max(initialPhysicalLength, MINIMUM_INITIAL_PACKED_ARRAY_CAPACITY);
        array = new long[physicalLength];
        isPacked = (physicalLength <= AbstractPackedArrayContext.MAX_SUPPORTED_PACKED_COUNTS_ARRAY_LENGTH);
        init(virtualLength);
    }

    PackedArrayContext(final int virtualLength,
                       final PackedArrayContext from,
                       final int newPhysicalArrayLength) {
        this(virtualLength, newPhysicalArrayLength);
        if (isPacked) {
            populateEquivalentEntriesWithZerosFromOther(from);
        }
    }

    private long[] array;
    private int populatedShortLength = 0;
    private final boolean isPacked;

    @Override
    int length() {
        return array.length;
    }

    @Override
    int getPopulatedShortLength() {
        return populatedShortLength;
    }

    @Override
    boolean casPopulatedShortLength(final int expectedPopulatedShortLength, final int newPopulatedShortLength) {
        if (this.populatedShortLength != expectedPopulatedShortLength) return false;
        this.populatedShortLength = newPopulatedShortLength;
        return true;
    }

    @Override
    boolean casPopulatedLongLength(final int expectedPopulatedLongLength, final int newPopulatedLongLength) {
        if (getPopulatedLongLength() != expectedPopulatedLongLength) return false;
        return casPopulatedShortLength(populatedShortLength, newPopulatedLongLength << 2);
    }

    @Override
    long getAtLongIndex(final int longIndex) {
        return array[longIndex];
    }

    @Override
    boolean casAtLongIndex(final int longIndex, long expectedValue, long newValue) {
        if (array[longIndex] != expectedValue) return false;
        array[longIndex] = newValue;
        return true;
    }

    @Override
    void lazySetAtLongIndex(int longIndex, long newValue) {
        array[longIndex] = newValue;
    }

    @Override
    void clearContents() {
        java.util.Arrays.fill(array, 0);
        init(getVirtualLength());
    }

    @Override
    void resizeArray(int newLength) {
        array = Arrays.copyOf(array, newLength);
    }

    @Override
    boolean isPacked() {
        return isPacked;
    }

    @Override
    long getAtUnpackedIndex(int index) {
        return array[index];
    }

    @Override
    void setAtUnpackedIndex(int index, long newValue) {
        array[index] = newValue;
    }

    @Override
    void lazysetAtUnpackedIndex(int index, long newValue) {
        array[index] = newValue;
    }

    @Override
    long incrementAndGetAtUnpackedIndex(int index) {
        array[index]++;
        return array[index];
    }

    @Override
    long addAndGetAtUnpackedIndex(int index, long valueToAdd) {
        array[index] += valueToAdd;
        return array[index];        
    }

    @Override
    String unpackedToString() {
        return Arrays.toString(array);
    }
}
