/*
 * package-info.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

/**
 * <h3>A High Dynamic Range (HDR) Histogram Package</h3>
 * <p>
 * An HdrHistogram histogram supports the recording and analyzing sampled data value counts across a configurable
 * integer value range with configurable value precision within the range. Value precision is expressed as the number
 * of significant digits in the value recording, and provides control over value quantization behavior across the
 * value range and the subsequent value resolution at any given level.
 * <p>
 * In contrast to traditional histograms that use linear, logarithmic, or arbitrary sized bins or buckets,
 * HdrHistograms use a fixed storage internal data representation that simultaneously supports an arbitrarily high
 * dynamic range and arbitrary precision throughout that dynamic range. This capability makes HdrHistograms extremely
 * useful for tracking and reporting on the distribution of percentile values with high resolution and across a wide
 * dynamic range -- a common need in latency behavior characterization.
 * <p>
 * The HdrHistogram package was specifically designed with latency and performance sensitive applications in mind.
 * Experimental u-benchmark measurements show value recording times as low as 3-6 nanoseconds on modern
 * (circa 2012) Intel CPUs. All Histogram variants maintain a fixed cost in both space and time. A Histogram's memory
 * footprint is constant, with no allocation operations involved in recording data values or in iterating through them.
 * The memory footprint is fixed regardless of the number of data value samples recorded, and depends solely on
 * the dynamic range and precision chosen. The amount of work involved in recording a sample is constant, and
 * directly computes storage index locations such that no iteration or searching is ever involved in recording
 * data values.
 * <p>
 * The combination of high dynamic range and precision is useful for collection and accurate post-recording
 * analysis of sampled value data distribution in various forms. Whether it's calculating or
 * plotting arbitrary percentiles, iterating through and summarizing values in various ways, or deriving mean and
 * standard deviation values, the fact that the recorded value count information is kept in high
 * resolution allows for accurate post-recording analysis with low [and ultimately configurable] loss in
 * accuracy when compared to performing the same analysis directly on the potentially infinite series of sourced
 * data values samples.
 * <p>
 * An HdrHistogram histogram is usually configured to maintain value count data with a resolution good enough
 * to support a desired precision in post-recording analysis and reporting on the collected data. Analysis can include
 * the computation and reporting of distribution by percentiles, linear or logarithmic arbitrary value buckets, mean
 * and standard deviation, as well as any other computations that can supported using the various iteration techniques
 * available on the collected value count data. In practice, a precision levels of 2 or 3 decimal points are most
 * commonly used, as they maintain a value accuracy of +/- ~1% or +/- ~0.1% respectively for derived distribution
 * statistics.
 * <p>
 * A good example of HdrHistogram use would be tracking of latencies across a wide dynamic range. E.g. from a
 * microsecond to an hour. A Histogram can be configured to track and later report on the counts of observed integer
 * usec-unit  latency values between 0 and 3,600,000,000 while maintaining a value precision of 3 significant digits
 * across that range. Such an example Histogram would simply be created with a
 * <b><code>highestTrackableValue</code></b> of 3,600,000,000, and a
 * <b><code>numberOfSignificantValueDigits</code></b> of 3, and would occupy a fixed, unchanging memory footprint
 * of around 185KB (see "Footprint estimation" below).
 * <br>
 * Code for this use example would include these basic elements:
 * <br>
 * <code>
 * <pre>
 * {@link org.HdrHistogram.Histogram} histogram = new {@link org.HdrHistogram.Histogram}(3600000000L, 3);
 * .
 * .
 * .
 * // Repeatedly record measured latencies:
 * histogram.{@link org.HdrHistogram.AbstractHistogram#recordValue(long) recordValue}(latency);
 * .
 * .
 * .
 * // Report histogram percentiles, expressed in msec units:
 * histogram.{@link org.HdrHistogram.AbstractHistogram#getHistogramData() getHistogramData}().{@link org.HdrHistogram.HistogramData#outputPercentileDistribution(java.io.PrintStream, Double) outputPercentileDistribution}(histogramLog, 1000.0);
 * </pre>
 * </code>
 *
 * Specifying 3 decimal points of precision in this example guarantees that value quantization within the value range
 * will be no larger than 1/1,000th (or 0.1%) of any recorded value. This example Histogram can be therefor used to
 * track, analyze and report the counts of observed latencies ranging between 1 microsecond and 1 hour in magnitude,
 * while maintaining a value resolution 1 microsecond (or better) up to 1 millisecond, a resolution of 1 millisecond
 * (or better) up to one second, and a resolution of 1 second (or better) up to 1,000 seconds. At it's maximum tracked
 * value (1 hour), it would still maintain a resolution of 3.6 seconds (or better).
 * <p>
 * <h3>Histogram variants and internal representation</h3>
 * The HdrHistogram package includes multiple implementations of the {@link org.HdrHistogram.AbstractHistogram} class:
 * <ul>
 *  <li> {@link org.HdrHistogram.Histogram}, which is the commonly used Histogram form and tracks value counts
 * in <b><code>long</code></b> fields. </li>
 *  <li>{@link org.HdrHistogram.IntHistogram} and {@link org.HdrHistogram.ShortHistogram}, which track value counts
 * in <b><code>int</code></b> and
 * <b><code>short</code></b> fields respectively, are provided for use cases where smaller count ranges are practical
 * and smaller overall storage is beneficial (e.g. systems where tens of thousands of in-memory histogram are
 * being tracked).</li>
 *  <li>{@link org.HdrHistogram.AtomicHistogram} and {@link org.HdrHistogram.SynchronizedHistogram}</li>
 * </ul>
 * <p>
 * Internally, data in HdrHistogram variants is maintained using a concept somewhat similar to that of floating
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
 * <h3>Synchronization and concurrent access</h3>
 * In the interest of keeping value recording cost to a minimum, the commonly used {@link org.HdrHistogram.Histogram}
 * class and it's {@link org.HdrHistogram.IntHistogram} and {@link org.HdrHistogram.ShortHistogram} variants are NOT
 * internally synchronized, and do NOT use atomic variables. Callers wishing to make potentially concurrent,
 * multi-threaded updates or queries against Histogram objects should either take care to externally synchronize and/or
 * order their access, or use the {@link org.HdrHistogram.SynchronizedHistogram} or
 * {@link org.HdrHistogram.AtomicHistogram} variants.
 * <p>
 * It's worth mentioning that since Histogram objects are additive, it is common practice to use per-thread,
 * non-synchronized histograms for the recording fast path, and "flipping" the actively recorded-to histogram
 * (usually with some non-locking variants on the fast path) and having a summary/reporting thread perform
 * histogram aggregation math across time and/or threads.
 * </p>
 * <p>
 * <h3>Iteration</h3>
 * Histograms supports multiple convenient forms of iterating through the histogram data set, including linear,
 * logarithmic, and percentile iteration mechanisms, as well as means for iterating through each recorded value or
 * each possible value level. The iteration mechanisms are accessible through the {@link org.HdrHistogram.HistogramData}
 * available through  {@link org.HdrHistogram.AbstractHistogram#getHistogramData()}.
 * Iteration mechanisms all provide {@link org.HdrHistogram.HistogramIterationValue} data points along the
 * histogram's iterated data set, and are available for the default (corrected) histogram data set
 * via the following {@link org.HdrHistogram.HistogramData} methods:
 * <ul>
 *     <li>{@link org.HdrHistogram.HistogramData#percentiles percentiles} :
 *     An {@link java.lang.Iterable}<{@link org.HdrHistogram.HistogramIterationValue}> through the
 *     histogram using a {@link org.HdrHistogram.PercentileIterator} </li>
 *     <li>{@link org.HdrHistogram.HistogramData#linearBucketValues linearBucketValues} :
 *     An {@link java.lang.Iterable}<{@link org.HdrHistogram.HistogramIterationValue}> through
 *     the histogram using a {@link org.HdrHistogram.LinearIterator} </li>
 *     <li>{@link org.HdrHistogram.HistogramData#logarithmicBucketValues logarithmicBucketValues} :
 *     An {@link java.lang.Iterable}<{@link org.HdrHistogram.HistogramIterationValue}>
 *     through the histogram using a {@link org.HdrHistogram.LogarithmicIterator} </li>
 *     <li>{@link org.HdrHistogram.HistogramData#recordedValues recordedValues} :
 *     An {@link java.lang.Iterable}<{@link org.HdrHistogram.HistogramIterationValue}> through
 *     the histogram using a {@link org.HdrHistogram.RecordedValuesIterator} </li>
 *     <li>{@link org.HdrHistogram.HistogramData#allValues allValues} :
 *     An {@link java.lang.Iterable}<{@link org.HdrHistogram.HistogramIterationValue}> through
 *     the histogram using a {@link org.HdrHistogram.AllValuesIterator} </li>
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
 * for (HistogramIterationValue v : histogram.getHistogramData().linearBucketValues(<i>valueUnitsPerBucket</i>)) {
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
 * <h3>Raw vs. corrected recording variants</h3>
 * <p>
 * Regular, raw value data recording into an HdrHistogram is achieved with the
 * {@link org.HdrHistogram.AbstractHistogram#recordValue(long) recordValue()} method.
 * <p>
 * Histogram variants also provide an auto-correcting
 * {@link org.HdrHistogram.AbstractHistogram#recordValueWithExpectedInterval(long, long) recordValueWithExpectedInterval()}
 * form in support of a common use case found when histogram values are used to track response time
 * distribution in the presence of Coordinated Omission - an extremely common phenomenon found in latency recording
 * systems.
 * This correcting form is useful in [e.g. load generator] scenarios where measured response times may exceed the
 * expected interval between issuing requests, leading to the "omission" of response time measurements that would
 * typically correlate with "bad" results. This coordinated (non random) omission of source data, if left uncorrected,
 * will then dramatically skew any overall latency stats computed on the recorded information, as the recorded data set
 * itself will be significantly skewed towards good results.
 * <p><
 * When a value recorded in the histogram exceeds the
 * <b><code>expectedIntervalBetweenValueSamples</code></b> parameter, recorded histogram data will
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
 * An normally recorded (uncorrected) data histogram collected for such a hypothetical system (over the 200 second
 * scenario above) would show ~99.99% of results at 1msec or below, which is obviously "not right". In contrast, a
 * histogram that records the same data using the auto-correcting
 * {@link org.HdrHistogram.AbstractHistogram#recordValueWithExpectedInterval(long, long) recordValueWithExpectedInterval()}
 * method with the knowledge of an expectedIntervalBetweenValueSamples of 10msec will correctly represent the
 * real world response time distribution of this hypothetical system. Only ~50% of results will be at 1msec or below,
 * with the remaining 50% coming from the auto-generated value records covering the missing increments spread between
 * 10msec and 100 sec.
 * <p>
 * Data sets recorded with and with
 * {@link org.HdrHistogram.AbstractHistogram#recordValue(long) recordValue()}
 * and with
 * {@link org.HdrHistogram.AbstractHistogram#recordValueWithExpectedInterval(long, long) recordValueWithExpectedInterval()}
 * will differ only if at least one value recorded was greater than it's
 * associated <b><code>expectedIntervalBetweenValueSamples</code></b> parameter.
 * Data sets recorded with
 * {@link org.HdrHistogram.AbstractHistogram#recordValueWithExpectedInterval(long, long) recordValueWithExpectedInterval()}
 * parameter will be identical to ones recorded with
 * {@link org.HdrHistogram.AbstractHistogram#recordValue(long) recordValue()}
 * it if all values recorded via the <b><code>recordValue</code></b> calls were smaller
 * than their associated <b><code>expectedIntervalBetweenValueSamples</code></b> parameters.
 * <p>
 * In addition to at-recording-time correction option, Histrogram variants also provide the post-recording correction
 * methods
 * {@link org.HdrHistogram.AbstractHistogram#copyCorrectedForCoordinatedOmission(long) copyCorrectedForCoordinatedOmission()}
 * and
 * {@link org.HdrHistogram.AbstractHistogram#addWhileCorrectingForCoordinatedOmission(AbstractHistogram, long) addWhileCorrectingForCoordinatedOmission()}.
 * These methods can be used for post-recording correction, and are useful when the
 * <b><code>expectedIntervalBetweenValueSamples</code></b> parameter is estimated to be the same for all recorded
 * values. However, for obvious reasons, it is important to note that only one correction method (during or post
 * recording) should be be used on a given histogram data set.
 * <p>
 * When used for response time characterization, the recording with the optional
 * </code></b>expectedIntervalBetweenValueSamples</code></b> parameter will tend to produce data sets that would
 * much more accurately reflect the response time distribution that a random, uncoordinated request would have
 * experienced.
 * <p>
 * <h3>Footprint estimation</h3>
 * Due to it's dynamic range representation, Histogram is relatively efficient in memory space requirements given
 * the accuracy and dynamic range it covers. Still, it is useful to be able to estimate the memory footprint involved
 * for a given <b><code>highestTrackableValue</code></b> and <b><code>numberOfSignificantValueDigits</code></b>
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
 * {@link org.HdrHistogram.AbstractHistogram#getEstimatedFootprintInBytes() getEstimatedFootprintInBytes()} method.
 */

package org.HdrHistogram;


