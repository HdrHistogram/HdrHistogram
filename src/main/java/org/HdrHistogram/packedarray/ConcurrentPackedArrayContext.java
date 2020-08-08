package org.HdrHistogram.packedarray;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongArray;


class ConcurrentPackedArrayContext extends PackedArrayContext {

    ConcurrentPackedArrayContext(final int virtualLength,
                                 final int initialPhysicalLength,
                                 final boolean allocateArray) {
        super(virtualLength, initialPhysicalLength, false);
        if (allocateArray) {
            array = new AtomicLongArray(getPhysicalLength());
            init(virtualLength);
        }
    }

    ConcurrentPackedArrayContext(final int virtualLength,
                                 final int initialPhysicalLength) {
        this(virtualLength, initialPhysicalLength, true);
    }

    ConcurrentPackedArrayContext(final int newVirtualCountsArraySize,
                                 final AbstractPackedArrayContext from,
                                 final int arrayLength) {
        this(newVirtualCountsArraySize, arrayLength);
        if (isPacked()) {
            populateEquivalentEntriesWithZerosFromOther(from);
        }
    }

    private AtomicLongArray array;
    private volatile int populatedShortLength;

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
    long getAtLongIndex(final int longIndex) {
        return array.get(longIndex);
    }

    @Override
    boolean casAtLongIndex(final int longIndex, final long expectedValue, final long newValue) {
        return array.compareAndSet(longIndex, expectedValue, newValue);
    }

    @Override
    void lazySetAtLongIndex(final int longIndex, final long newValue) {
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
    void resizeArray(final int newLength) {
        final AtomicLongArray newArray = new AtomicLongArray(newLength);
        int copyLength = Math.min(array.length(), newLength);
        for (int i = 0; i < copyLength; i++) {
            newArray.lazySet(i, array.get(i));
        }
        array = newArray;
    }

    @Override
    long getAtUnpackedIndex(final int index) {
        return array.get(index);
    }

    @Override
    void setAtUnpackedIndex(final int index, final long newValue) {
        array.set(index, newValue);
    }

    @Override
    void lazySetAtUnpackedIndex(final int index, final long newValue) {
        array.lazySet(index, newValue);
    }

    @Override
    long incrementAndGetAtUnpackedIndex(final int index) {
        return array.incrementAndGet(index);
    }

    @Override
    long addAndGetAtUnpackedIndex(final int index, final long valueToAdd) {
        return array.addAndGet(index, valueToAdd);
    }

    @Override
    String unpackedToString() {
        return array.toString();
    }
}
