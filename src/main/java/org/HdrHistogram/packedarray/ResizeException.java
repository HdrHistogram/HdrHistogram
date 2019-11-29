package org.HdrHistogram.packedarray;

class ResizeException extends Exception {
    private int newSize;

    ResizeException(final int newSize) {
        this.newSize = newSize;
    }

    int getNewSize() {
        return newSize;
    }
}
