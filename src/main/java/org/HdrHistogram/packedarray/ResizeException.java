package org.HdrHistogram.packedarray;

class ResizeException extends Exception {
    private final int newSize;

    ResizeException(final int newSize) {
        this.newSize = newSize;
    }

    int getNewSize() {
        return newSize;
    }
}
