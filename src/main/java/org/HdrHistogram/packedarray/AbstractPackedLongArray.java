package org.HdrHistogram.packedarray;

import java.io.Serializable;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A Packed array of signed 64 bit values, and supports {@link #get get()}, {@link #set set()},
 * {@link #add add()} and {@link #increment increment()} operations on the logical contents of the array.
 */
abstract class AbstractPackedLongArray implements Iterable<Long>, Serializable {
    /**
     * An {@link AbstractPackedLongArray} Uses {@link AbstractPackedArrayContext} to track
     * the array's logical contents. Contexts may be switched when a context requires resizing
     * to complete logical array operations (get, set, add, increment). Contexts are
     * established and used within critical sections in order to facilitate concurrent
     * implementors.
     */

    private static final int NUMBER_OF_SETS = 8;

    /**
     * Set a new virtual length for the array.
     * @param newVirtualArrayLength the
     */
    abstract public void setVirtualLength(final int newVirtualArrayLength);

    abstract AbstractPackedArrayContext getStorageArrayContext();

    abstract void resizeStorageArray(int newPhysicalLengthInLongs);

    abstract void clearContents();

    abstract long criticalSectionEnter();

    abstract void criticalSectionExit(long criticalValueAtEnter);


    @Override
    public String toString() {
        String output = "PackedArray:\n";
        AbstractPackedArrayContext arrayContext = getStorageArrayContext();
        output += arrayContext.toString();
        return output;
    }

    /**
     * Get value at virtual index in the array
     * @param index the virtual array index
     * @return the array value at the virtual index given
     */
    public long get(final int index) {
        long value = 0;
        for (int byteNum = 0; byteNum < NUMBER_OF_SETS; byteNum ++) {
            int packedIndex = 0;
            long byteValueAtPackedIndex = 0;
            do {
                int newArraySize = 0;
                long criticalValue = criticalSectionEnter();
                try {
                    // Establish context within: critical section
                    AbstractPackedArrayContext arrayContext = getStorageArrayContext();
                    // Deal with unpacked context:
                    if (!arrayContext.isPacked()) {
                        return arrayContext.getAtUnpackedIndex(index);
                    }
                    // Context is packed:
                    packedIndex = arrayContext.getPackedIndex(byteNum, index, false);
                    if (packedIndex < 0) {
                        return value;
                    }
                    byteValueAtPackedIndex =
                            (((long)arrayContext.getAtByteIndex(packedIndex)) & 0xff) << (byteNum << 3);
                } catch (ResizeException ex) {
                    newArraySize = ex.getNewSize(); // Resize outside of critical section
                } finally {
                    criticalSectionExit(criticalValue);
                    if (newArraySize != 0) {
                        resizeStorageArray(newArraySize);
                    }
                }
            } while (packedIndex == 0);

            value += byteValueAtPackedIndex;
        }
        return value;
    }

    /**
     * Increment value at a virrual index in the array
     * @param index virtual index of value to increment
     */
    public void increment(final int index) {
        add(index, 1);
    }

    /**
     * Add to a value at a virtual index in the array
     * @param index the virtual index of the value to be added to
     * @param value the value to add
     */
    public void add(final int index, final long value) {
        if (value == 0) {
            return;
        }
        long remainingValueToAdd = value;

        do {
            try {
                long byteMask = 0xff;
                for (int byteNum = 0, byteShift = 0;
                     byteNum < NUMBER_OF_SETS;
                     byteNum++, byteShift += 8, byteMask <<= 8) {
                    final long criticalValue = criticalSectionEnter();
                    try {
                        // Establish context within: critical section
                        AbstractPackedArrayContext arrayContext = getStorageArrayContext();
                        // Deal with unpacked context:
                        if (!arrayContext.isPacked()) {
                            arrayContext.addAndGetAtUnpackedIndex(index, remainingValueToAdd);
                            return;
                        }
                        // Context is packed:
                        int packedIndex = arrayContext.getPackedIndex(byteNum, index, true);

                        long amountToAddAtSet = remainingValueToAdd & byteMask;
                        byte byteToAdd = (byte) (amountToAddAtSet >> byteShift);
                        long afterAddByteValue = arrayContext.addAtByteIndex(packedIndex, byteToAdd);

                        // Reduce remaining value to add by amount just added:
                        remainingValueToAdd -= amountToAddAtSet;

                        // Account for carry:
                        long carryAmount = afterAddByteValue & 0x100;
                        remainingValueToAdd += carryAmount << byteShift;

                        if (remainingValueToAdd == 0) {
                            return; // nothing to add to higher magnitudes
                        }
                    } finally {
                        criticalSectionExit(criticalValue);

                    }
                }
                return;
            } catch (ResizeException ex){
                resizeStorageArray(ex.getNewSize()); // Resize outside of critical section
            }
        } while (true);
    }

    /**
     * Set the value at a virtual index in the array
     * @param index the virtual index of the value to set
     * @param value the value to set
     */
    public void set(final int index, final long value) {
        int bytesAlreadySet = 0;
        do {
            long valueForNextLevels = value;
            try {
                for (int byteNum = 0; byteNum < NUMBER_OF_SETS; byteNum++) {
                    long criticalValue = criticalSectionEnter();
                    try {
                        // Establish context within: critical section
                        AbstractPackedArrayContext arrayContext = getStorageArrayContext();
                        // Deal with unpacked context:
                        if (!arrayContext.isPacked()) {
                            arrayContext.setAtUnpackedIndex(index, value);
                        }
                        // Context is packed:
                        if (valueForNextLevels == 0) {
                            // Special-case zeros to avoid inflating packed array for no reason
                            int packedIndex = arrayContext.getPackedIndex(byteNum, index, false);
                            if (packedIndex < 0) {
                                return; // no need to create entries for zero values if they don't already exist
                            }
                        }
                        // Make sure byte is populated:
                        int packedIndex = arrayContext.getPackedIndex(byteNum, index, true);

                        // Determine value to write, and prepare for next levels
                        byte byteToWrite = (byte) (valueForNextLevels & 0xff);
                        valueForNextLevels >>= 8;

                        if (byteNum < bytesAlreadySet) {
                            // We want to avoid writing to the same byte twice when not doing so for the
                            // entire 64 bit value atomically, as doing so opens a race with e.g. concurrent
                            // adders. So dobn't actually write the byte if has been written before.
                            continue;
                        }
                        arrayContext.setAtByteIndex(packedIndex, byteToWrite);
                        bytesAlreadySet++;
                    } finally {
                        criticalSectionExit(criticalValue);
                    }
                }
                return;
            } catch (ResizeException ex) {
                resizeStorageArray(ex.getNewSize()); // Resize outside of critical section
            }
        } while (true);
    }

    /**
     * Clear the array contents
     */
    public void clear() {
        clearContents();
    }

    /**
     * Get the current physical length (in longs) of the array's backing storage
     * @return the current physical length (in longs) of the array's current backing storage
     */
    public int getPhysicalLength() {
        return getStorageArrayContext().length();
    }

    /**
     * Get the (virtual) length of the array
     * @return the (virtual) length of the array
     */
    public int length() {
        return getStorageArrayContext().getVirtualLength();
    }

    // Regular array iteration (iterates over all virtrual indexes, zero-value or not:

    class AllValuesIterator implements Iterator<Long> {

        int nextVirtrualIndex = 0;

        @Override
        public Long next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return get(nextVirtrualIndex++);
        }

        @Override
        public boolean hasNext() {
            return ((nextVirtrualIndex >= 0) &&
                    (nextVirtrualIndex < length()));
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * An Iterator over all values in the array
     * @return an Iterator over all values in the array
     */
    public Iterator<Long> iterator() {
        return new AllValuesIterator();
    }

    /**
     * An Iterator over all non-Zero values in the array
     * @return an Iterator over all non-Zero values in the array
     */
    public Iterable<IterationValue> nonZeroValues() {
        return getStorageArrayContext().nonZeroValues();
    }

}
