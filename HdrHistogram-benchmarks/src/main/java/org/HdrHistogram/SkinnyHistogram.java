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
 * to gauage the compression volume (and not for correctness). It includes ZigZag putLong and putInt
 * implementations to avoid dependencies on other libs (including HdrHistogram), so that benchmarking
 * against older versions will be possible.
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
                putInt(buffer, i - lastIdx);
                putLong(buffer, value - lastValue);
                lastIdx = i;
                lastValue = value;
                seqLength++;
            }
        }
        return seqLength;
    }

    /**
     * Writes a long value to the given buffer in LEB128 ZigZag encoded format
     * @param buffer the buffer to write to
     * @param value  the value to write to the buffer
     */
    static void putLong(ByteBuffer buffer, long value) {
        value = (value << 1) ^ (value >> 63);
        if (value >>> 7 == 0) {
            buffer.put((byte) value);
        } else {
            buffer.put((byte) ((value & 0x7F) | 0x80));
            if (value >>> 14 == 0) {
                buffer.put((byte) (value >>> 7));
            } else {
                buffer.put((byte) (value >>> 7 | 0x80));
                if (value >>> 21 == 0) {
                    buffer.put((byte) (value >>> 14));
                } else {
                    buffer.put((byte) (value >>> 14 | 0x80));
                    if (value >>> 28 == 0) {
                        buffer.put((byte) (value >>> 21));
                    } else {
                        buffer.put((byte) (value >>> 21 | 0x80));
                        if (value >>> 35 == 0) {
                            buffer.put((byte) (value >>> 28));
                        } else {
                            buffer.put((byte) (value >>> 28 | 0x80));
                            if (value >>> 42 == 0) {
                                buffer.put((byte) (value >>> 35));
                            } else {
                                buffer.put((byte) (value >>> 35 | 0x80));
                                if (value >>> 49 == 0) {
                                    buffer.put((byte) (value >>> 42));
                                } else {
                                    buffer.put((byte) (value >>> 42 | 0x80));
                                    if (value >>> 56 == 0) {
                                        buffer.put((byte) (value >>> 49));
                                    } else {
                                        buffer.put((byte) (value >>> 49 | 0x80));
                                        buffer.put((byte) (value >>> 56));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Writes an int value to the given buffer in LEB128 ZigZag encoded format
     * @param buffer the buffer to write to
     * @param value  the value to write to the buffer
     */
    static void putInt(ByteBuffer buffer, int value) {
        value = (value << 1) ^ (value >> 31);
        if (value >>> 7 == 0) {
            buffer.put((byte) value);
        } else {
            buffer.put((byte) ((value & 0x7F) | 0x80));
            if (value >>> 14 == 0) {
                buffer.put((byte) (value >>> 7));
            } else {
                buffer.put((byte) (value >>> 7 | 0x80));
                if (value >>> 21 == 0) {
                    buffer.put((byte) (value >>> 14));
                } else {
                    buffer.put((byte) (value >>> 14 | 0x80));
                    if (value >>> 28 == 0) {
                        buffer.put((byte) (value >>> 21));
                    } else {
                        buffer.put((byte) (value >>> 21 | 0x80));
                        buffer.put((byte) (value >>> 28));
                    }
                }
            }
        }
    }
}
