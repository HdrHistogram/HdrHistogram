/**
 * Histogram.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
 */

package org.HdrHistogram;

/**
 * <h3>A High Dynamic Range (HDR) Histogram</h3>
 * <p>
 * AbstractHistogram supports the recording and analyzing sampled data value counts across a configurable integer value
 * range with configurable value precision within the range. Value precision is expressed as the number of significant
 * digits in the value recording, and provides control over value quantization behavior across the value range and the
 * subsequent value resolution at any given level.
 * <p>
 * For example, a Histogram could be configured to track the counts of observed integer values between 0 and
 * 3,600,000,000 while maintaining a value precision of 3 significant digits across that range. Value quantization
 * within the range will thus be no larger than 1/1,000th (or 0.1%) of any value. This example Histogram could
 * be used to track and analyze the counts of observed response times ranging between 1 microsecond and 1 hour
 * in magnitude, while maintaining a value resolution of 1 microsecond up to 1 millisecond, a resolution of
 * 1 millisecond (or better) up to one second, and a resolution of 1 second (or better) up to 1,000 seconds. At it's
 * maximum tracked value (1 hour), it would still maintain a resolution of 3.6 seconds (or better).
 * <p>
 * The HdrHistogram package includes the {@link org.HdrHistogram.Histogram} implementation, which tracks value counts
 * in <b><code>long</code></b> fields, and is expected to be the commonly used Histogram form.
 * {@link org.HdrHistogram.IntHistogram} and {@link org.HdrHistogram.ShortHistogram}, which track value counts
 * in <b><code>int</code></b> and
 * <b><code>short</code></b> fields respectively, are provided for use cases where smaller count ranges are practical
 * and smaller overall storage is beneficial.
 * <p>
 * HDR Histogram is designed for recoding histograms of value measurements in latency and performance
 * sensitive applications. Measurements show value recording times as low as 3-6 nanoseconds on modern
 * (circa 2012) Intel CPUs. AbstractHistogram maintains a fixed cost in both space and time. A Histogram's memory
 * footprint is constant, with no allocation operations involved in recording data values or in iterating through them.
 * The memory footprint is fixed regardless of the number of data value samples recorded, and depends solely on
 * the dynamic range and precision chosen. The amount of work involved in recording a sample is constant, and
 * directly computes storage index locations such that no iteration or searching is ever involved in recording
 * data values.
 * <p>
 * A combination of high dynamic range and precision is useful for collection and accurate post-recording
 * analysis of sampled value data distribution in various forms. Whether it's calculating or
 * plotting arbitrary percentiles, iterating through and summarizing values in various ways, or deriving mean and
 * standard deviation values, the fact that the recorded data information is kept in high
 * resolution allows for accurate post-recording analysis with low [and ultimately configurable] loss in
 * accuracy when compared to performing the same analysis directly on the potentially infinite series of sourced
 * data values samples.
 * <p>
 * Internally, AbstractHistogram data is maintained using a concept somewhat similar to that of floating
 * point number representation: Using a an exponent a (non-normalized) mantissa to
 * support a wide dynamic range at a high but varying (by exponent value) resolution.
 * AbstractHistogram uses exponentially increasing bucket value ranges (the parallel of
 * the exponent portion of a floating point number) with each bucket containing
 * a fixed number (per bucket) set of linear sub-buckets (the parallel of a non-normalized mantissa portion
 * of a floating point number).
 * Both dynamic range and resolution are configurable, with <b><code>highestTrackableValue</code></b>
 * controlling dynamic range, and <b><code>numberOfSignificantValueDigits</code></b> controlling
 * resolution.
 * <p>
 * An common use example of an HDR Histogram would be to record response times in units of
 * microseconds across a dynamic range stretching from 1 usec to over an hour, with a good enough resolution
 * to support later performing post-recording analysis on the collected data. Analysis can including computing,
 * examining, and reporting of distribution by percentiles, linear or logarithmic value buckets, mean and standard
 * deviation, or by any other means that can can be easily added by using the various iteration techniques supported
 * by the Histogram.
 * In order to facilitate the accuracy needed for various post-recording analysis techniques, this
 * example can maintain where a resolution of ~1 usec or better for times ranging to ~2 msec in magnitude, while at the
 * same time maintaining a resolution of ~1 msec or better for times ranging to ~2 sec, and a resolution
 * of ~1 second or better for values up to 2,000 seconds. This sort of example resolution can be thought of as
 * "always accurate to 3 decimal points." Such an example Histogram would simply be created with a
 * <b><code>highestTrackableValue</code></b> of 3,600,000,000, and a
 * <b><code>numberOfSignificantValueDigits</code></b> of 3, and would occupy a fixed,
 * unchanging memory footprint of around 185KB (see "Footprint estimation" below).
 * <p>
 * <h3>Synchronization and concurrent access</h3>
 * In the interest of keeping value recording cost to a minimum, AbstractHistogram and it's direct implementations
 * are NOT internally synchronized, and do NOT use atomic variables. Callers wishing to make potentially concurrent,
 * multi-threaded updates or queries against Histogram objects should take care to externally synchronize and/or order
 * their access.
 * <p>
 * <h3>Iteration</h3>
 * Histograms supports multiple convenient forms of iterating through the histogram data set, including linear,
 * logarithmic, and percentile iteration mechanisms, as well as means for iterating through each recorded value or
 * each possible value level. The iteration mechanisms are accessible through the {@link org.HdrHistogram.HistogramData}
 * available through  {@link #getHistogramData()}.
 * Iteration mechanisms all provide {@link HistogramIterationValue} data points along the
 * histogram's iterated data set, and are available for the default (corrected) histogram data set
 * via the following {@link org.HdrHistogram.HistogramData} methods:
 * <ul>
 *     <li>{@link HistogramData#percentiles percentiles} : An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the
 *     histogram using a {@link PercentileIterator} </li>
 *     <li>{@link HistogramData#linearBucketValues linearBucketValues} : An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through
 *     the histogram using a {@link LinearIterator} </li>
 *     <li>{@link HistogramData#logarithmicBucketValues logarithmicBucketValues} : An {@link java.lang.Iterable}<{@link HistogramIterationValue}>
 *     through the histogram using a {@link LogarithmicIterator} </li>
 *     <li>{@link HistogramData#recordedValues recordedValues} : An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through
 *     the histogram using a {@link RecordedValuesIterator} </li>
 *     <li>{@link HistogramData#allValues allValues} : An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through
 *     the histogram using a {@link AllValuesIterator} </li>
 * </ul>
 * <p>
 * Iteration is typically done with a for-each loop statement. E.g.:
 * <br><code>
 * <pre>
 * for (HistogramIterationValue v : histogram.getHistogramData().percentiles(<i>percentileTicksPerHalfDistance</i>)) {
 *     ...
 * }
 * </pre>
 * </code>
 * or
 * <br><code>
 * <pre>
 * for (HistogramIterationValue v : histogram.getRawHistogramData().linearBucketValues(<i>valueUnitsPerBucket</i>)) {
 *     ...
 * }
 * </pre>
 * </code>
 * The iterators associated with each iteration method are resettable, such that a caller that would like to avoid
 * allocating a new iterator object for each iteration loop can re-use an iterator to repeatedly iterate through the
 * histogram. This iterator re-use usually takes the form of a traditional for loop using the Iterator's
 * <b><code>hasNext()</code></b> and <b><code>next()</code></b> methods:
 *
 * to avoid allocating a new iterator object for each iteration loop:
 * <br>
 * <code>
 * <pre>
 * PercentileIterator iter = histogram.getHistogramData().percentiles().iterator(<i>percentileTicksPerHalfDistance</i>);
 * ...
 * iter.reset(<i>percentileTicksPerHalfDistance</i>);
 * for (iter.hasNext() {
 *     HistogramIterationValue v = iter.next();
 *     ...
 * }
 * </pre>
 * </code>
 * <p>
 * <h3>Equivalent Values and value ranges</h3>
 * <p>
 * Due to the finite (and configurable) resolution of the histogram, multiple adjacent integer data values can
 * be "equivalent". Two values are considered "equivalent" if samples recorded for both are always counted in a
 * common total count due to the histogram's resolution level. Histogram provides methods for determining the
 * lowest and highest equivalent values for any given value, as we as determining whether two values are equivalent,
 * and for finding the next non-equivalent value for a given value (useful when looping through values, in order
 * to avoid double-counting count).
 * <p>
 * <h3>Corrected vs. Raw <b><code>recordValue()</code></b> calls</h3>
 * <p>
 * In order to support a common use case needed when histogram values are used to track response time distribution,
 * Histogram provides for the recording of corrected histogram value by supporting an optional
 * <b><code>expectedIntervalBetweenValueSamples</code></b> parameter to the {@link #recordValue(long, long) recordValue}
 * method. This form is useful in [common load generator] scenarios where response times may exceed the expected interval
 * between issuing requests, leading to "dropped" response time measurements that would typically correlate with
 * "bad" results.
 * <p>
 * When a value recorded in the histogram exceeds the
 * <b><code>expectedIntervalBetweenValueSamples</code></b> parameter (when provided), recorded histogram data will
 * reflect an appropriate number of additional values, linearly decreasing in steps of
 * <b><code>expectedIntervalBetweenValueSamples</code></b>, down to the last value
 * that would still be higher than <b><code>expectedIntervalBetweenValueSamples</code></b>).
 * <p>
 * To illustrate why this corrective behavior is critically needed in order to accurately represent value
 * distribution when large value measurements may lead to missed samples, imagine a system for which response
 * times samples are taken once every 10 msec to characterize response time distribution.
 * The hypothetical system behaves "perfectly" for 100 seconds (10,000 recorded samples), with each sample
 * showing a 1msec response time value. At each sample for 100 seconds (10,000 logged samples
 * at 1msec each). The hypothetical system then encounters a 100 sec pause during which only a single sample is
 * recorded (with a 100 second value).
 * The raw data histogram collected for such a hypothetical system (over the 200 second scenario above) would show
 * ~99.99% of results at 1msec or below, which is obviously "not right". The same histogram, corrected with the
 * knowledge of an expectedIntervalBetweenValueSamples of 10msec will correctly represent the response time
 * distribution. Only ~50% of results will be at 1msec or below, with the remaining 50% coming from the
 * auto-generated value records covering the missing increments spread between 10msec and 100 sec.
 * <p>
 * The raw and default (corrected) data sets will differ only if at least one value recorded with the
 * <b><code>recordValue</code></b> method was greater than it's associated
 * <b><code>expectedIntervalBetweenValueSamples</code></b> parameter. The raw and default (corrected) data set
 * will be identical in contents if all values recorded via
 * the <b><code>recordValue</code></b> were smaller than their associated (and optional)
 * <b><code>expectedIntervalBetweenValueSamples</code></b> parameters.
 * <p>
 * While both the raw and corrected histogram data are tracked and accessible, it is the
 * (default) corrected numbers that would typically be consulted and reported. When used for response time
 * characterization, the default (corrected) data set will tend to much more accurately reflect the response time
 * distribution that a random, uncoordinated request would have experienced.
 * <p>
 * <h3>Footprint estimation</h3>
 * Due to it's dynamic range representation, Histogram is relatively efficient in memory space requirements given
 * the accuracy and dynamic range it covers. Still, it is useful to be able to estimate the memory footprint involved
 * for a given <b><code>highestTrackableValue</code></b> and <b><code>largestValueWithSingleUnitResolution</code></b>
 * combination. Beyond a relatively small fixed-size footprint used for internal fields and stats (which can be
 * estimated as "fixed at well less than 1KB"), the bulk of a Histogram's storage is taken up by it's data value
 * recording counts array. The total footprint can be conservatively estimated by:
 * <pre><code>
 *     largestValueWithSingleUnitResolution = 2 * (10 ^ numberOfSignificantValueDigits);
 *     subBucketSize = roundedUpToNearestPowerOf2(largestValueWithSingleUnitResolution);

 *     expectedHistogramFootprintInBytes = 512 +
 *          ({primitive type size} / 2) *
 *          (log2RoundedUp((highestTrackableValue) / subBucketSize) + 2) *
 *          subBucketSize
 *
 * </pre></code>
 * A conservative (high) estimate of a Histogram's footprint in bytes is available via the
 * {@link #getEstimatedFootprintInBytes()} method.
 */

public abstract class AbstractHistogram {
    // "Cold" accessed fields. Not used in the recording code path:
    final long highestTrackableValue;
    final int numberOfSignificantValueDigits;

    final int bucketCount;
    final int subBucketCount;
    final int countsArrayLength;

    final HistogramData histogramData;

    // Bunch "Hot" accessed fields (used in the the value recording code path) here, near the end, so
    // that they will have a good chance of ending up in the same cache line as the counts array reference
    // field that subclass implementations will add.
    final int subBucketHalfCountMagnitude;
    final int subBucketHalfCount;
    final long subBucketMask;
    long totalCount;


    // Abstract, counts-type dependent methods to be provided by subclass implementations:

    abstract long getCountAtIndex(int index);

    abstract void incrementCountAtIndex(int index);

    abstract void addToCountAtIndex(int index, long value);

    abstract void clearCounts();

    /**
     * Provide a (conservatively high) estimate of the Histogram's total footprint in bytes
     *
     * @return a (conservatively high) estimate of the Histogram's total footprint in bytes
     */
    abstract public int getEstimatedFootprintInBytes();

    /**
     * Construct a Histogram given the Highest value to be tracked and a number of significant decimal digits
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is >= 2.
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public AbstractHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {

        // Verify argument validity
        if (highestTrackableValue < 2)
            throw new IllegalArgumentException("highestTrackableValue must be >= 2");
        if ((numberOfSignificantValueDigits < 0) || (numberOfSignificantValueDigits > 5))
            throw new IllegalArgumentException("numberOfSignificantValueDigits must be between 0 and 6");
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;

        final long largestValueWithSingleUnitResolution = 2 * (long) Math.pow(10, numberOfSignificantValueDigits);

        // We need to maintain power-of-two subBucketCount (for clean direct indexing) that is large enough to
        // provide unit resolution to at least largestValueWithSingleUnitResolution. So figure out
        // largestValueWithSingleUnitResolution's nearest power-of-two (rounded up), and use that:
        int subBucketCountMagnitude = (int) Math.ceil(Math.log(largestValueWithSingleUnitResolution)/Math.log(2));
        subBucketHalfCountMagnitude = ((subBucketCountMagnitude > 1) ? subBucketCountMagnitude : 1) - 1;
        subBucketCount = (int) Math.pow(2, (subBucketHalfCountMagnitude + 1));
        subBucketHalfCount = subBucketCount / 2;
        subBucketMask = subBucketCount - 1;

        // determine exponent range needed to support the trackable value with no overflow:
        long trackableValue = subBucketCount - 1;
        int bucketsNeeded = 1;
        while (trackableValue < highestTrackableValue) {
            trackableValue <<= 1;
            bucketsNeeded++;
        }
        this.bucketCount = bucketsNeeded;

        countsArrayLength = (bucketCount + 1) * (subBucketCount / 2);

        totalCount = 0;

        histogramData = new HistogramData(this);
    }

    /**
     * get the configured numberOfSignificantValueDigits
     * @return numberOfSignificantValueDigits
     */
    public int getNumberOfSignificantValueDigits() {
        return numberOfSignificantValueDigits;
    }

    /**
     * get the configured highestTrackableValue
     * @return highestTrackableValue
     */
    public long getHighestTrackableValue() {
        return highestTrackableValue;
    }

    private int countsArrayIndex(final int bucketIndex, final int subBucketIndex) {
        assert(subBucketIndex < subBucketCount);
        assert(bucketIndex < bucketCount);
        assert(bucketIndex == 0 || (subBucketIndex >= subBucketHalfCount));
        // Calculate the index for the first entry in the bucket:
        // (The following is the equivalent of ((bucketIndex + 1) * subBucketHalfCount) ):
        int bucketBaseIndex = (bucketIndex + 1) << subBucketHalfCountMagnitude;
        // Calculate the offset in the bucket:
        int offsetInBucket = subBucketIndex - subBucketHalfCount;
        // The following is the equivalent of ((subBucketIndex  - subBucketHalfCount) + bucketBaseIndex;
        return bucketBaseIndex + offsetInBucket;
    }

    long getCountAt(final int bucketIndex, final int subBucketIndex) {
        return getCountAtIndex(countsArrayIndex(bucketIndex, subBucketIndex));
    }

    private static void arrayAdd(AbstractHistogram toHistogram, AbstractHistogram fromHistogram) {
        if (fromHistogram.countsArrayLength != toHistogram.countsArrayLength) throw new IndexOutOfBoundsException();
        for (int i = 0; i < fromHistogram.countsArrayLength; i++)
            toHistogram.addToCountAtIndex(i, fromHistogram.getCountAtIndex(i));
    }

    int getBucketIndex(long value) {
        int pow2ceiling = 64 - Long.numberOfLeadingZeros(value | subBucketMask); // smallest power of 2 containing value
        return  pow2ceiling - (subBucketHalfCountMagnitude + 1);
    }

    int getSubBucketIndex(long value, int bucketIndex) {
        return  (int)(value >> bucketIndex);
    }

    private void recordSingleValue(final long value) throws ArrayIndexOutOfBoundsException {
        // Given our knowledge that subBucketCount is a power of two, we can directly dissect the value
        // into bucket and sub-bucket parts:

        int bucketIndex = getBucketIndex(value);
        int subBucketIndex = getSubBucketIndex(value, bucketIndex);

        int countsIndex = countsArrayIndex(bucketIndex, subBucketIndex);
        incrementCountAtIndex(countsIndex);
        totalCount++;
    }

    /**
     * Record a value in the histogram.
     * <p>
     * To compensate for the loss of sampled values when a recorded value is larger than the expected
     * interval between value samples, Histogram will auto-generate an additional series of decreasingly-smaller
     * (down to the expectedIntervalBetweenValueSamples) value records. In addition to the default, "corrected"
     * histogram representation containing potential auto-generated value records, the Histogram keeps track of
     * "raw" histogram data containing no such corrections. This data set is available via the
     * <b><code>getRawData</code></b> method.
     * <p>
     * See notes in the description of the Histogram calls for an illustration of why this corrective behavior is
     * important.
     *
     * @param value The value to record
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     * @throws ArrayIndexOutOfBoundsException
     */
    public void recordValue(final long value, final long expectedIntervalBetweenValueSamples) throws ArrayIndexOutOfBoundsException {
        recordSingleValue(value);
        if (expectedIntervalBetweenValueSamples <=0)
            return;
        for (long missingValue = value - expectedIntervalBetweenValueSamples;
             missingValue >= expectedIntervalBetweenValueSamples;
             missingValue -= expectedIntervalBetweenValueSamples) {
            recordSingleValue(missingValue);
        }
    }

    /**
     * Record a value in the histogram
     *
     * @param value The value to be recorded
     * @throws ArrayIndexOutOfBoundsException
     */
    public void recordValue(final long value) throws ArrayIndexOutOfBoundsException {
        recordSingleValue(value);
    }

    /**
     * Reset the contents and stats of this histogram
     */
    public void reset() {
        clearCounts();
        totalCount = 0;
    }

    /**
     * Add the contents of another histogram to this one
     *
     * @param other The other histogram. highestTrackableValue and largestValueWithSingleUnitResolution must match.
     */
    public void add(final AbstractHistogram other) {
        if ((highestTrackableValue != other.highestTrackableValue) ||
                (numberOfSignificantValueDigits != other.numberOfSignificantValueDigits) ||
                (bucketCount != other.bucketCount) ||
                (subBucketCount != other.subBucketCount))
            throw new IllegalArgumentException("Cannot add histograms with incompatible ranges");
        arrayAdd(this, other);
        totalCount += other.totalCount;
    }

    /**
     * Provide access to the histogram's data set.
     * @return a {@link HistogramData} that can be used to query stats and iterate through the default (corrected)
     * data set.
     */
    public HistogramData getHistogramData() {
        return histogramData;
    }

    /**
     * Get the size (in value units) of the range of values that are equivalent to the given value within the
     * histogram's resolution. Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The lowest value that is equivalent to the given value within the histogram's resolution.
     */
    public long sizeOfEquivalentValueRange(long value) {
        int bucketIndex = getBucketIndex(value);
        int subBucketIndex = getSubBucketIndex(value, bucketIndex);
        long distanceToNextValue =
                (1 << ((subBucketIndex >= subBucketCount) ? (bucketIndex + 1) : bucketIndex));
        return distanceToNextValue;
    }

    /**
     * Get the lowest value that is equivalent to the given value within the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The lowest value that is equivalent to the given value within the histogram's resolution.
     */
    public long lowestEquivalentValue(long value) {
        int bucketIndex = getBucketIndex(value);
        int subBucketIndex = getSubBucketIndex(value, bucketIndex);
        long thisValueBaseLevel = subBucketIndex << bucketIndex;
        return thisValueBaseLevel;
    }

    /**
     * Get the highest value that is equivalent to the given value within the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The highest value that is equivalent to the given value within the histogram's resolution.
     */
    public long highestEquivalentValue(long value) {
        return nextNonEquivalentValue(value) - 1;
    }

    /**
     * Get a value that lies in the middle (rounded up) of the range of values equivalent the given value.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The value lies in the middle (rounded up) of the range of values equivalent the given value.
     */
    public long medianEquivalentValue(long value) {
        return (lowestEquivalentValue(value) + (sizeOfEquivalentValueRange(value) >> 1));
    }

    /**
     * Get the next value that is not equivalent to the given value within the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The next value that is not equivalent to the given value within the histogram's resolution.
     */
    public long nextNonEquivalentValue(long value) {
        return lowestEquivalentValue(value) + sizeOfEquivalentValueRange(value);
    }

    /**
     * Determine if two values are equivalent with the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value1 first value to compare
     * @param value2 second value to compare
     * @return True if values are equivalent with the histogram's resolution.
     */
    public boolean valuesAreEquivalent(long value1, long value2) {
        return (lowestEquivalentValue(value1) == lowestEquivalentValue(value2));
    }
}