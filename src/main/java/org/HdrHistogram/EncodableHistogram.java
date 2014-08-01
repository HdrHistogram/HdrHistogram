/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.nio.ByteBuffer;

public interface EncodableHistogram {

    public int getNeededByteBufferCapacity();

    public int encodeIntoCompressedByteBuffer(final ByteBuffer targetBuffer, int compressionLevel);

    public long getStartTimeStamp();

    public long getEndTimeStamp();

    public double getMaxValueAsDouble();

}
