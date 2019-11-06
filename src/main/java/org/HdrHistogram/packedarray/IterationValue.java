package org.HdrHistogram.packedarray;

/**
 * An iteration value representing the index iterated to, and the value found at that index
 */
public class IterationValue {
    IterationValue() {
    }

    void set(final int index, final long value) {
        this.index = index;
        this.value = value;
    }

    /**
     * The index iterated to
     * @return the index iterated to
     */
    public int getIndex() {
        return index;
    }

    /**
     * The value at the index iterated to
     * @return the value at the index iterated to
     */
    public long getValue() {
        return value;
    }

    private int index;
    private long value;
}
