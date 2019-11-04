package org.HdrHistogram.packedarray;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongArray;


class ConcurrentPackedArrayContext extends AbstractPackedArrayContext {

    public ConcurrentPackedArrayContext(final int virtualLength, final int initialPhysicalLength) {
        int physicalLength = Math.max(initialPhysicalLength, MINIMUM_INITIAL_PACKED_ARRAY_CAPACITY);
        array = new AtomicLongArray(physicalLength);
        isPacked = (physicalLength <= AbstractPackedArrayContext.MAX_SUPPORTED_PACKED_COUNTS_ARRAY_LENGTH);
        init(virtualLength);
    }

    public ConcurrentPackedArrayContext(int newVirtualCountsArraySize,
                                        final ConcurrentPackedArrayContext from,
                                        final int arrayLength) {
        this(newVirtualCountsArraySize, arrayLength);
        if (isPacked) {
            populateEquivalentEntriesWithZerosFromOther(from);
        }
    }

    private AtomicLongArray array;
    private volatile int populatedShortLength;
    private final boolean isPacked;

    private static final AtomicIntegerFieldUpdater<ConcurrentPackedArrayContext> populatedShortLengthUpdater =
            AtomicIntegerFieldUpdater.newUpdater(ConcurrentPackedArrayContext.class, "populatedShortLength");

    @Override
    int length() {
        return array.length();
    }

    @Override
    int getPopulatedShortLength() {
        return populatedShortLength;
    }

    @Override
    boolean casPopulatedShortLength(final int expectedPopulatedShortLength, final int newPopulatedShortLength) {
        return populatedShortLengthUpdater.compareAndSet(this, expectedPopulatedShortLength, newPopulatedShortLength);
    }

    @Override
    boolean casPopulatedLongLength(final int expectedPopulatedLongLength, final int newPopulatedLongLength) {
        int existingShortLength = getPopulatedShortLength();
        int existingLongLength = (existingShortLength + 3) >> 2;
        if (existingLongLength != expectedPopulatedLongLength) return false;
        return casPopulatedShortLength(existingShortLength, newPopulatedLongLength << 2);
    }

    @Override
    long getAtLongIndex(int longIndex) {
        return array.get(longIndex);
    }

    @Override
    boolean casAtLongIndex(int longIndex, long expectedValue, long newValue) {
        return array.compareAndSet(longIndex, expectedValue, newValue);
    }

    @Override
    void lazySetAtLongIndex(int longIndex, long newValue) {
        array.lazySet(longIndex, newValue);
    }

    @Override
    void clearContents() {
        for (int i = 0; i < array.length(); i++) {
            array.lazySet(i, 0);
        }
        init(getVirtualLength());
    }

    @Override
    void resizeArray(int newLength) {
        final AtomicLongArray newArray = new AtomicLongArray(newLength);
        int copyLength = Math.min(array.length(), newLength);
        for (int i = 0; i < copyLength; i++) {
            newArray.lazySet(i, array.get(i));
        }
        array = newArray;
    }

    @Override
    boolean isPacked() {
        return isPacked;
    }

    @Override
    long getAtUnpackedIndex(int index) {
        return array.get(index);
    }

    @Override
    void setAtUnpackedIndex(int index, long newValue) {
        array.set(index, newValue);
    }

    @Override
    void lazysetAtUnpackedIndex(int index, long newValue) {
        array.lazySet(index, newValue);
    }

    @Override
    long incrementAndGetAtUnpackedIndex(int index) {
        return array.incrementAndGet(index);
    }

    @Override
    long addAndGetAtUnpackedIndex(int index, long valueToAdd) {
        return array.addAndGet(index, valueToAdd);
    }

    @Override
    String unpackedToString() {
        return array.toString();
    }
}
