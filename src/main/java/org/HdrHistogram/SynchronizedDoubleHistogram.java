/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

/**
 * <h3>A floating piint values High Dynamic Range (HDR) Histogram that is synchronized as a whole</h3>
 * <p/>
 * A {@link SynchronizedDoubleHistogram} is a variant of {@link org.HdrHistogram.DoubleHistogram} that is
 * synchronized as a whole, such that queries, copying, and addition operations are atomic with relation to
 * modification on the {@link SynchronizedDoubleHistogram}, nd such that external accessors (e.g. iterations on the
 * histogram data) that synchronize on the {@link SynchronizedDoubleHistogram} instance can safely assume that no
 * modifications to the histogram data occur within their synchronized block.
 * <p>
 * It is important to note that synchrionization can result in blocking recoding calls. If non-blocking recoding
 * operations are required, consider using {@link org.HdrHistogram.ConcurrentDoubleHistogram}, or (recommended)
 * {@link DoubleRecorder} which were intended for concurrent operations.
 * <p/>
 * {@link SynchronizedDoubleHistogram} supports the recording and analyzing sampled data value counts across a
 * configurable dynamic range of floating point (double) values, with configurable value precision within the range.
 * Dynamic range is expressed as a ratio between the hightes and lowest non-zero values trackable within the histogram
 * at any given time. Value precision is expressed as the number of significant [decimal] digits in the value recording,
 * and provides control over value quantization behavior across the value range and the subsequent value resolution at
 * any given level.
 * <p/>
 * Auto-ranging: Unlike integer value based histograms, the specific value range tracked by a {@link
 * SynchronizedDoubleHistogram} is not specified upfront. Only the dynamic range of values that the histogram can
 * cover is (optionally) specified. E.g. When a {@link ConcurrentDoubleHistogram} is created to track a dynamic range of
 * 3600000000000 (enoygh to track values from a nanosecond to an hour), values could be recorded into into it in any
 * consistent unit of time as long as the ratio between the highest and lowest non-zero values stays within the
 * specified dynamic range, so recording in units of nanoseconds (1.0 thru 3600000000000.0), milliseconds (0.000001
 * thru 3600000.0) seconds (0.000000001 thru 3600.0), hours (1/3.6E12 thru 1.0) will all work just as well.
 * <p/>
 * Auto-resizing: When constructed with no specified dynamic range (or when auto-resize is turned on with {@link
 * SynchronizedDoubleHistogram#setAutoResize}) a {@link SynchronizedDoubleHistogram} will auto-resize its dynamic
 * range to include recorded values as they are encountered. Note that recording calls that cause auto-resizing may
 * take longer to execute, as resizing incurrs allocation and copying of internal data structures.
 * <p/>
 * Attempts to record non-zero values that range outside of the specified dynamic range (or exceed the limits of
 * of dynamic range when auto-resizing) may results in {@link ArrayIndexOutOfBoundsException} exceptions, either
 * due to overflow or underflow conditions. These exceptions will only be thrown if recording the value would have
 * resulted in discarding or losing the required value precision of values already recorded in the histogram.
 * <p/>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class SynchronizedDoubleHistogram extends DoubleHistogram {

    /**
     * Construct a new auto-resizing DoubleHistogram using a precision stated as a number of significant
     * decimal digits.
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SynchronizedDoubleHistogram(final int numberOfSignificantValueDigits) {
        this(2, numberOfSignificantValueDigits);
        setAutoResize(true);
    }

    /**
     * Construct a new DoubleHistogram with the specified dynamic range (provided in
     * {@code highestToLowestValueRatio}) and using a precision stated as a number of significant
     * decimal digits.
     *
     * @param highestToLowestValueRatio specifies the dynamic range to use
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SynchronizedDoubleHistogram(final long highestToLowestValueRatio, final int numberOfSignificantValueDigits) {
        super(highestToLowestValueRatio, numberOfSignificantValueDigits, SynchronizedHistogram.class);
    }

    /**
     * Construct a {@link SynchronizedDoubleHistogram} with the same range settings as a given source,
     * duplicating the source's start/end timestamps (but NOT it's contents)
     * @param source The source histogram to duplicate
     */
    public SynchronizedDoubleHistogram(final ConcurrentDoubleHistogram source) {
        super(source);
    }
}
