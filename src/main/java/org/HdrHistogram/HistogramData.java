/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Locale;

/**
 * The HistogramData class has been deprecated with HdrHistogram version 1.2. Use direct
 * calls to equivalent methods in @link AbstractHistogram} and it's derived classes instead.
 *
 * Provides iteration capabilities for a histogram's data set, as well as access to common
 * statistics information.
 *
 */
@Deprecated
public class HistogramData {
    final AbstractHistogram histogram;

    HistogramData(final AbstractHistogram histogram) {
        this.histogram = histogram;
    }

    /**
     * The the total count of recorded values in the histogram data
     *
     * @return the total recorded value count in the (corrected) histogram data
     */
    public long getTotalCount() {
        return histogram.getTotalCount();
    }

    /**
     * Get the lowest recorded value level in the histogram
     *
     * @return the Min value recorded in the histogram
     */
    public long getMinValue() {
        return histogram.getMinValue();
    }

    /**
     * Get the highest recorded value level in the histogram
     *
     * @return the Max value recorded in the histogram
     */
    public long getMaxValue() {
        return histogram.getMaxValue();
    }

    /**
     * Get the computed mean value of all recorded values in the histogram
     *
     * @return the mean value (in value units) of the histogram data
     */
    public double getMean() {
        return histogram.getMean();
    }

    /**
     * Get the computed standard deviation of all recorded values in the histogram
     *
     * @return the standard deviation (in value units) of the histogram data
     */
    public double getStdDeviation() {
        return histogram.getStdDeviation();
    }

    /**
     * Get the value at a given percentile
     *
     * @param percentile    The percentile for which the return the associated value
     * @return The value below which a given percentage of the overall recorded value entries in the
     * histogram all fall.
     */
    public long getValueAtPercentile(final double percentile) {
        return histogram.getValueAtPercentile(percentile);
    }

    /**
     * Get the percentile at a given value
     *
     * @param value    The value for which the return the associated percentile
     * @return The percentile of values recorded at or below the given percentage in the
     * histogram all fall.
     */
    public double getPercentileAtOrBelowValue(final long value) {
        return histogram.getPercentileAtOrBelowValue(value);
    }

    /**
     * Get the count of recorded values within a range of value levels. (inclusive to within the histogram's resolution)
     *
     * @param lowValue  The lower value bound on the range for which
     *                  to provide the recorded count. Will be rounded down with
     *                  {@link Histogram#lowestEquivalentValue lowestEquivalentValue}.
     * @param highValue  The higher value bound on the range for which to provide the recorded count.
     *                   Will be rounded up with {@link Histogram#highestEquivalentValue highestEquivalentValue}.
     * @return the total count of values recorded in the histogram within the value range that is
     * >= lowestEquivalentValue(<i>lowValue</i>) and <= highestEquivalentValue(<i>highValue</i>)
     * @throws ArrayIndexOutOfBoundsException
     */
    public long getCountBetweenValues(final long lowValue, final long highValue) throws ArrayIndexOutOfBoundsException {
        return histogram.getCountBetweenValues(lowValue, highValue);
    }

    /**
     * Get the count of recorded values at a specific value
     *
     * @param value The value for which to provide the recorded count
     * @return The total count of values recorded in the histogram at the given value (to within
     * the histogram resolution at the value level).
     * @throws ArrayIndexOutOfBoundsException
     */
    public long getCountAtValue(final long value) throws ArrayIndexOutOfBoundsException {
        return histogram.getCountAtValue(value);
    }

    /**
     * Produce textual representation of the value distribution of histogram data by percentile. The distribution is
     * output with exponentially increasing resolution, with each exponentially decreasing half-distance containing
     * five (5) percentile reporting tick points.
     *
     * @param printStream    Stream into which the distribution will be output
     * <p>
     * @param outputValueUnitScalingRatio    The scaling factor by which to divide histogram recorded values units in
     *                                     output
     */
    public void outputPercentileDistribution(final PrintStream printStream,
                                             final Double outputValueUnitScalingRatio) {
        histogram.outputPercentileDistribution(printStream, outputValueUnitScalingRatio);
    }

    /**
     * Produce textual representation of the value distribution of histogram data by percentile. The distribution is
     * output with exponentially increasing resolution, with each exponentially decreasing half-distance containing
     * <i>dumpTicksPerHalf</i> percentile reporting tick points.
     *
     * @param printStream    Stream into which the distribution will be output
     * <p>
     * @param percentileTicksPerHalfDistance  The number of reporting points per exponentially decreasing half-distance
     * <p>
     * @param outputValueUnitScalingRatio    The scaling factor by which to divide histogram recorded values units in
     *                                     output
     */
    public void outputPercentileDistribution(final PrintStream printStream,
                                             final int percentileTicksPerHalfDistance,
                                             final Double outputValueUnitScalingRatio) {
        histogram.outputPercentileDistribution(printStream, percentileTicksPerHalfDistance, outputValueUnitScalingRatio);
    }

    /**
     * Produce textual representation of the value distribution of histogram data by percentile. The distribution is
     * output with exponentially increasing resolution, with each exponentially decreasing half-distance containing
     * <i>dumpTicksPerHalf</i> percentile reporting tick points.
     *
     * @param printStream    Stream into which the distribution will be output
     * <p>
     * @param percentileTicksPerHalfDistance  The number of reporting points per exponentially decreasing half-distance
     * <p>
     * @param outputValueUnitScalingRatio    The scaling factor by which to divide histogram recorded values units in
     *                                     output
     * @param useCsvFormat  Output in CSV format if true. Otherwise use plain text form.
     */
    public void outputPercentileDistribution(final PrintStream printStream,
                                             final int percentileTicksPerHalfDistance,
                                             final Double outputValueUnitScalingRatio,
                                             boolean useCsvFormat) {
        histogram.outputPercentileDistribution(printStream, percentileTicksPerHalfDistance,
                outputValueUnitScalingRatio, useCsvFormat);
    }

    /**
     * Provide a means of iterating through histogram values according to percentile levels. The iteration is
     * performed in steps that start at 0% and reduce their distance to 100% according to the
     * <i>percentileTicksPerHalfDistance</i> parameter, ultimately reaching 100% when all recorded histogram
     * values are exhausted.
     * <p>
     * @param percentileTicksPerHalfDistance The number of iteration steps per half-distance to 100%.
     * @return An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a
     * {@link PercentileIterator}
     */
    public Percentiles percentiles(final int percentileTicksPerHalfDistance) {
        return new Percentiles(histogram, percentileTicksPerHalfDistance);
    }

    /**
     * Provide a means of iterating through histogram values using linear steps. The iteration is
     * performed in steps of <i>valueUnitsPerBucket</i> in size, terminating when all recorded histogram
     * values are exhausted.
     *
     * @param valueUnitsPerBucket  The size (in value units) of the linear buckets to use
     * @return An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a
     * {@link LinearIterator}
     */
    public LinearBucketValues linearBucketValues(final int valueUnitsPerBucket) {
        return new LinearBucketValues(histogram, valueUnitsPerBucket);
    }

    /**
     * Provide a means of iterating through histogram values at logarithmically increasing levels. The iteration is
     * performed in steps that start at <i>valueUnitsInFirstBucket</i> and increase exponentially according to
     * <i>logBase</i>, terminating when all recorded histogram values are exhausted.
     *
     * @param valueUnitsInFirstBucket The size (in value units) of the first bucket in the iteration
     * @param logBase The multiplier by which bucket sizes will grow in eahc iteration step
     * @return An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using
     * a {@link LogarithmicIterator}
     */
    public LogarithmicBucketValues logarithmicBucketValues(final int valueUnitsInFirstBucket, final double logBase) {
        return new LogarithmicBucketValues(histogram, valueUnitsInFirstBucket, logBase);
    }

    /**
     * Provide a means of iterating through all recorded histogram values using the finest granularity steps
     * supported by the underlying representation. The iteration steps through all non-zero recorded value counts,
     * and terminates when all recorded histogram values are exhausted.
     *
     * @return An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using
     * a {@link RecordedValuesIterator}
     */
    public RecordedValues recordedValues() {
        return new RecordedValues(histogram);
    }

    /**
     * Provide a means of iterating through all histogram values using the finest granularity steps supported by
     * the underlying representation. The iteration steps through all possible unit value levels, regardless of
     * whether or not there were recorded values for that value level, and terminates when all recorded histogram
     * values are exhausted.
     *
     * @return An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using
     * a {@link RecordedValuesIterator}
     */
    public AllValues allValues() {
        return new AllValues(histogram);
    }

    // Percentile iterator support:

    /**
     * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link PercentileIterator}
     */
    public class Percentiles implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;
        final int percentileTicksPerHalfDistance;

        private Percentiles(final AbstractHistogram histogram, final int percentileTicksPerHalfDistance) {
            this.histogram = histogram;
            this.percentileTicksPerHalfDistance = percentileTicksPerHalfDistance;
        }

        /**
         * @return A {@link PercentileIterator}<{@link HistogramIterationValue}>
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new PercentileIterator(histogram, percentileTicksPerHalfDistance);
        }
    }

    // Linear iterator support:

    /**
     * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link LinearIterator}
     */
    public class LinearBucketValues implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;
        final int valueUnitsPerBucket;

        private LinearBucketValues(final AbstractHistogram histogram, final int valueUnitsPerBucket) {
            this.histogram = histogram;
            this.valueUnitsPerBucket = valueUnitsPerBucket;
        }

        /**
         * @return A {@link LinearIterator}<{@link HistogramIterationValue}>
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new LinearIterator(histogram, valueUnitsPerBucket);
        }
    }

    // Logarithmic iterator support:

    /**
     * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link LogarithmicIterator}
     */
    public class LogarithmicBucketValues implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;
        final int valueUnitsInFirstBucket;
        final double logBase;

        private LogarithmicBucketValues(final AbstractHistogram histogram,
                                        final int valueUnitsInFirstBucket, final double logBase) {
            this.histogram = histogram;
            this.valueUnitsInFirstBucket = valueUnitsInFirstBucket;
            this.logBase = logBase;
        }

        /**
         * @return A {@link LogarithmicIterator}<{@link HistogramIterationValue}>
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new LogarithmicIterator(histogram, valueUnitsInFirstBucket, logBase);
        }
    }

    // Recorded value iterator support:

    /**
     * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link RecordedValuesIterator}
     */
    public class RecordedValues implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;

        private RecordedValues(final AbstractHistogram histogram) {
            this.histogram = histogram;
        }

        /**
         * @return A {@link RecordedValuesIterator}<{@link HistogramIterationValue}>
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new RecordedValuesIterator(histogram);
        }
    }

    // AllValues iterator support:

    /**
     * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link AllValuesIterator}
     */
    public class AllValues implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;

        private AllValues(final AbstractHistogram histogram) {
            this.histogram = histogram;
        }

        /**
         * @return A {@link AllValuesIterator}<{@link HistogramIterationValue}>
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new AllValuesIterator(histogram);
        }
    }
}
