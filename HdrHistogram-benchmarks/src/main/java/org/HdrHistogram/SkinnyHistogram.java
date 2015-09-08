/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.zip.Deflater;

/**
 * This is a Java port of the Scala SkinnyHistogram encdoding logic from the Khronus
 * project on github. The port only covers the encoding side, since it is (currently) mainly used
 * to gauage the compression volume (and not for correctness).
 */
public class SkinnyHistogram extends Histogram {

    private static int encodingCompressedCookieBase = 130;
    private static int defaultCompressionLevel = Deflater.DEFAULT_COMPRESSION;

    public SkinnyHistogram(long max, int digits) {
        super(max, digits);
    }

    public SkinnyHistogram(int digits) {
        super(digits);
    }


    synchronized public int encodeIntoCompressedByteBuffer(final ByteBuffer targetBuffer) {

        ByteBuffer intermediateUncompressedByteBuffer = ByteBuffer.allocate(this.getNeededByteBufferCapacity());
        int uncompressedLength = this.encodeIntoByteBuffer(intermediateUncompressedByteBuffer);

        targetBuffer.putInt(SkinnyHistogram.encodingCompressedCookieBase);
        targetBuffer.putInt(0);
        targetBuffer.putInt(uncompressedLength);

        Deflater compressor = new Deflater(defaultCompressionLevel);
        compressor.setInput(intermediateUncompressedByteBuffer.array(), 0, uncompressedLength);
        compressor.finish();

        byte[] targetArray = targetBuffer.array();
        int compressedDataLength = compressor.deflate(targetArray, 12, targetArray.length - 12);
        compressor.reset();

        targetBuffer.putInt(4, compressedDataLength);
        return compressedDataLength + 12;
    }

    synchronized public int encodeIntoByteBuffer(final ByteBuffer buffer) {
        // val output = new Output(buffer.array())

        long maxValue = getMaxValue();

        int initialPosition = buffer.position();

        buffer.putInt(normalizingIndexOffset);
        buffer.putInt(getNumberOfSignificantValueDigits());
        buffer.putLong(getLowestDiscernibleValue());
        buffer.putLong(getHighestTrackableValue());
        buffer.putDouble(integerToDoubleValueConversionRatio);
        buffer.putLong(getTotalCount());

        int seqPairBufferPosition = buffer.position();
        buffer.putInt(0);
        int seqPairLength = writeCountsDiffs(buffer);
        buffer.putInt(seqPairBufferPosition, seqPairLength);

        return (buffer.position() - initialPosition);
    }

    private int writeCountsDiffs(ByteBuffer buffer) {
        long lastValue = 0;
        int lastIdx = 0;
        int seqLength = 0;
        for (int i = 0; i < counts.length; i++) {
            long value = counts[i];
            if (value > 0) {
                ZigZagEncoding.putInt(buffer, i - lastIdx);
                ZigZagEncoding.putLong(buffer, value - lastValue);
                lastIdx = i;
                lastValue = value;
                seqLength++;
            }
        }
        return seqLength;
    }

}
