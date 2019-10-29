/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;

/**
 * <h3>A floating point values High Dynamic Range (HDR) Histogram that supports safe concurrent recording
 * operations.</h3>
 * <p>
 * A {@link ConcurrentDoubleHistogram} is a variant of {@link org.HdrHistogram.DoubleHistogram} that guarantees
 * lossless recording of values into the histogram even when the histogram is updated by multiple threads, and
 * supports auto-resize and auto-ranging operations that may occur concurrently as a result of recording operations.
 * <p>
 * It is important to note that concurrent recording, auto-sizing, and value shifting are the only thread-safe behaviors
 * provided by {@link ConcurrentDoubleHistogram}, and that it is not otherwise synchronized. Specifically, {@link
 * ConcurrentDoubleHistogram} provides no implicit synchronization that would prevent the contents of the histogram
 * from changing during queries, iterations, copies, or addition operations on the histogram. Callers wishing to make
 * potentially concurrent, multi-threaded updates that would safely work in the presence of queries, copies, or
 * additions of histogram objects should either take care to externally synchronize and/or order their access,
 * use the {@link SynchronizedDoubleHistogram} variant, or (recommended) use the {@link DoubleRecorder}
 * or {@link SingleWriterDoubleRecorder} which are intended for this purpose.
 * <p>
 * {@link ConcurrentDoubleHistogram} supports the recording and analyzing sampled data value counts across a
 * configurable dynamic range of floating point (double) values, with configurable value precision within the range.
 * Dynamic range is expressed as a ratio between the highest and lowest non-zero values trackable within the histogram
 * at any given time. Value precision is expressed as the number of significant [decimal] digits in the value recording,
 * and provides control over value quantization behavior across the value range and the subsequent value resolution at
 * any given level.
 * <p>
 * Auto-ranging: Unlike integer value based histograms, the specific value range tracked by a {@link
 * ConcurrentDoubleHistogram} is not specified upfront. Only the dynamic range of values that the histogram can cover is
 * (optionally) specified. E.g. When a {@link ConcurrentDoubleHistogram} is created to track a dynamic range of
 * 3600000000000 (enough to track values from a nanosecond to an hour), values could be recorded into into it in any
 * consistent unit of time as long as the ratio between the highest and lowest non-zero values stays within the
 * specified dynamic range, so recording in units of nanoseconds (1.0 thru 3600000000000.0), milliseconds (0.000001
 * thru 3600000.0) seconds (0.000000001 thru 3600.0), hours (1/3.6E12 thru 1.0) will all work just as well.
 * <p>
 * Auto-resizing: When constructed with no specified dynamic range (or when auto-resize is turned on with {@link
 * ConcurrentDoubleHistogram#setAutoResize}) a {@link ConcurrentDoubleHistogram} will auto-resize its dynamic range to
 * include recorded values as they are encountered. Note that recording calls that cause auto-resizing may take
 * longer to execute, as resizing incurs allocation and copying of internal data structures.
 * <p>
 * Attempts to record non-zero values that range outside of the specified dynamic range (or exceed the limits of
 * of dynamic range when auto-resizing) may results in {@link ArrayIndexOutOfBoundsException} exceptions, either
 * due to overflow or underflow conditions. These exceptions will only be thrown if recording the value would have
 * resulted in discarding or losing the required value precision of values already recorded in the histogram.
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class ConcurrentDoubleHistogram extends DoubleHistogram {

    /**
     * Construct a new auto-resizing DoubleHistogram using a precision stated as a number of significant decimal
     * digits.
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant decimal
     *                                       digits to which the histogram will maintain value resolution and
     *                                       separation. Must be a non-negative integer between 0 and 5.
     */
    public ConcurrentDoubleHistogram(final int numberOfSignificantValueDigits) {
        this(2, numberOfSignificantValueDigits);
        setAutoResize(true);
    }

    /**
     * Construct a new DoubleHistogram with the specified dynamic range (provided in {@code highestToLowestValueRatio})
     * and using a precision stated as a number of significant decimal digits.
     *
     * @param highestToLowestValueRatio      specifies the dynamic range to use
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant decimal
     *                                       digits to which the histogram will maintain value resolution and
     *                                       separation. Must be a non-negative integer between 0 and 5.
     */
    public ConcurrentDoubleHistogram(final long highestToLowestValueRatio, final int numberOfSignificantValueDigits) {
        this(highestToLowestValueRatio, numberOfSignificantValueDigits, ConcurrentHistogram.class);
    }

    /**
     * Construct a {@link ConcurrentDoubleHistogram} with the same range settings as a given source,
     * duplicating the source's start/end timestamps (but NOT it's contents)
     * @param source The source histogram to duplicate
     */
    public ConcurrentDoubleHistogram(final DoubleHistogram source) {
        super(source);
    }

    ConcurrentDoubleHistogram(final long highestToLowestValueRatio,
                              final int numberOfSignificantValueDigits,
                              final Class<? extends AbstractHistogram> internalCountsHistogramClass) {
        super(highestToLowestValueRatio, numberOfSignificantValueDigits, internalCountsHistogramClass);
    }

    ConcurrentDoubleHistogram(final long highestToLowestValueRatio,
                    final int numberOfSignificantValueDigits,
                    final Class<? extends AbstractHistogram> internalCountsHistogramClass,
                    AbstractHistogram internalCountsHistogram) {
        super(
                highestToLowestValueRatio,
                numberOfSignificantValueDigits,
                internalCountsHistogramClass,
                internalCountsHistogram
        );
    }

    /**
     * Construct a new ConcurrentDoubleHistogram by decoding it from a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestToLowestValueRatio Force highestTrackableValue to be set at least this high
     * @return The newly constructed ConcurrentDoubleHistogram
     */
    public static ConcurrentDoubleHistogram decodeFromByteBuffer(
            final ByteBuffer buffer,
            final long minBarForHighestToLowestValueRatio) {
        try {
            int cookie = buffer.getInt();
            if (!isNonCompressedDoubleHistogramCookie(cookie)) {
                throw new IllegalArgumentException("The buffer does not contain a DoubleHistogram");
            }
            ConcurrentDoubleHistogram histogram = constructHistogramFromBuffer(cookie, buffer,
                    ConcurrentDoubleHistogram.class, ConcurrentHistogram.class,
                    minBarForHighestToLowestValueRatio);
            return histogram;
        } catch (DataFormatException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Construct a new ConcurrentDoubleHistogram by decoding it from a compressed form in a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestToLowestValueRatio Force highestTrackableValue to be set at least this high
     * @return The newly constructed ConcurrentDoubleHistogram
     * @throws DataFormatException on error parsing/decompressing the buffer
     */
    public static ConcurrentDoubleHistogram decodeFromCompressedByteBuffer(
            final ByteBuffer buffer,
            final long minBarForHighestToLowestValueRatio) throws DataFormatException {
        int cookie = buffer.getInt();
        if (!isCompressedDoubleHistogramCookie(cookie)) {
            throw new IllegalArgumentException("The buffer does not contain a compressed DoubleHistogram");
        }
        ConcurrentDoubleHistogram histogram = constructHistogramFromBuffer(cookie, buffer,
                ConcurrentDoubleHistogram.class, ConcurrentHistogram.class,
                minBarForHighestToLowestValueRatio);
        return histogram;
    }
}
