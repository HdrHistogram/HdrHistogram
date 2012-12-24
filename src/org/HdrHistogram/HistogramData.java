/**
 * HistogramData.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
 */

package org.HdrHistogram;


import java.io.PrintStream;
import java.util.Iterator;
import java.util.Locale;

/**
 * Provides iteration capabilities for a histogram's data set, as well as access to common
 * statistics information.
 *
 */
public class HistogramData {
    final AbstractHistogram histogram;
    // Create instances of the needed iterators once. We will reset them on each use to avoid allocation:
    final PercentileIterator percentileIterator;
    final RecordedValuesIterator recordedValuesIterator;
    final int bucketCount;
    final int subBucketCount;

    HistogramData(AbstractHistogram histogram) {
        this.histogram = histogram;
        this.percentileIterator = new PercentileIterator(histogram, 1);
        this.recordedValuesIterator = new RecordedValuesIterator(histogram);
        this.bucketCount = histogram.bucketCount;
        this.subBucketCount = histogram.subBucketCount;
    }

    /**
     * The the total count of recorded values in the histogram data
     *
     * @return the total recorded value count in the (corrected) histogram data
     */
    public long getTotalCount() {
        return histogram.totalCount;
    }

    /**
     * Get the lowest recorded value level in the histogram
     *
     * @return the Min value recorded in the histogram
     */
    public long getMinValue() {
        recordedValuesIterator.reset();
        long min = 0;
        if (recordedValuesIterator.hasNext()) {
            HistogramIterationValue iterationValue = recordedValuesIterator.next();
            min = iterationValue.getValueIteratedTo();
        }
        return histogram.lowestEquivalentValue(min);
    }
    /**
     * Get the highest recorded value level in the histogram
     *
     * @return the Max value recorded in the histogram
     */
    public long getMaxValue() {
        recordedValuesIterator.reset();
        long max = 0;
        while (recordedValuesIterator.hasNext()) {
            HistogramIterationValue iterationValue = recordedValuesIterator.next();
            max = iterationValue.getValueIteratedTo();
        }
        return histogram.lowestEquivalentValue(max);
    }

    /**
     * Get the computed mean value of all recorded values in the histogram
     *
     * @return the mean value (in value units) of the histogram data
     */
    public double getMean() {
        recordedValuesIterator.reset();
        long totalValue = 0;
        while (recordedValuesIterator.hasNext()) {
            HistogramIterationValue iterationValue = recordedValuesIterator.next();
            totalValue = iterationValue.getTotalValueToThisValue();
        }
        return (totalValue * 1.0) / getTotalCount();
    }

    /**
     * Get the computed standard deviation of all recorded values in the histogram
     *
     * @return the standard deviation (in value units) of the histogram data
     */
    public double getStdDeviation() {
        double mean =  getMean();
        double geometric_deviation_total = 0.0;
        recordedValuesIterator.reset();
        while (recordedValuesIterator.hasNext()) {
            HistogramIterationValue iterationValue = recordedValuesIterator.next();
            Double deviation = (histogram.medianEquivalentValue(iterationValue.getValueIteratedTo()) * 1.0) - mean;
            geometric_deviation_total += (deviation * deviation) * iterationValue.getCountAddedInThisIterationStep();
        }
        double std_deviation = Math.sqrt(geometric_deviation_total / getTotalCount());
        return std_deviation;
    }

    /**
     * Get the value at a given percentile
     *
     * @param percentile    The percentile for which the return the associated value
     * @return The value below which a given percentage of the overall recorded value entries in the
     * histogram all fall.
     */
    public long getValueAtPercentile(final double percentile) {
        double requestedPercentile = Math.min(percentile, 100.0); // Truncate down to 100%
        long countAtPercentile = (long)(((requestedPercentile / 100.0) * getTotalCount()) + 0.5); // round to nearest
        countAtPercentile = Math.max(countAtPercentile, 1); // Make sure we at least reach the first recorded entry
        long totalToCurrentIJ = 0;
        for (int i = 0; i < bucketCount; i++) {
            int j = (i == 0) ? 0 : (subBucketCount / 2);
            for (; j < subBucketCount; j++) {
                totalToCurrentIJ += histogram.getCountAt(i, j);
                if (totalToCurrentIJ >= countAtPercentile) {
                    long valueAtIndex = j << i;
                    return valueAtIndex;
                }
            }
        }
        throw new ArrayIndexOutOfBoundsException("percentile value not found in range"); // should not reach here.
    }

    /**
     * Get the percentile at a given value
     *
     * @param value    The value for which the return the associated percentile
     * @return The percentile of values recorded at or below the given percentage in the
     * histogram all fall.
     */
    public double getPercentileAtOrBelowValue(final long value) {
        long totalToCurrentIJ = 0;

        int targetBucketIndex = histogram.getBucketIndex(value);
        int targetSubBucketIndex = histogram.getSubBucketIndex(value, targetBucketIndex);

        if (targetBucketIndex >= bucketCount)
            return 100.0;

        for (int i = 0; i <= targetBucketIndex; i++) {
            int j = (i == 0) ? 0 : (subBucketCount / 2);
            int subBucketCap = (i == targetBucketIndex) ? (targetSubBucketIndex + 1): subBucketCount;
            for (; j < subBucketCap; j++) {
                totalToCurrentIJ += histogram.getCountAt(i, j);
            }
        }

        return (100.0 * totalToCurrentIJ) / getTotalCount();    }

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
        long count = 0;

        // Compute the sub-bucket-rounded values for low and high:
        int lowBucketIndex = histogram.getBucketIndex(lowValue);
        int lowSubBucketIndex = histogram.getSubBucketIndex(lowValue, lowBucketIndex);
        long valueAtlowValue = lowSubBucketIndex << lowBucketIndex;
        int highBucketIndex = histogram.getBucketIndex(highValue);
        int highSubBucketIndex = histogram.getSubBucketIndex(highValue, highBucketIndex);
        long valueAtHighValue = highSubBucketIndex << highBucketIndex;

        if ((lowBucketIndex >= bucketCount) || (highBucketIndex >= bucketCount))
            throw new ArrayIndexOutOfBoundsException();

        for (int i = lowBucketIndex; i <= highBucketIndex; i++) {
            int j = (i == 0) ? 0 : (subBucketCount / 2);
            for (; j < subBucketCount; j++) {
                long valueAtIndex = j << i;
                if (valueAtIndex > valueAtHighValue)
                    return count;
                if (valueAtIndex >= valueAtlowValue)
                    count += histogram.getCountAt(i, j);
            }
        }
        return count;
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
        int bucketIndex = histogram.getBucketIndex(value);
        int subBucketIndex = histogram.getSubBucketIndex(value, bucketIndex);
        // May throw ArrayIndexOutOfBoundsException:
        return histogram.getCountAt(bucketIndex, subBucketIndex);
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
    public LogarithmicBucketValues logarithmicBucketValues(final int valueUnitsInFirstBucket, final int logBase) {
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
        printStream.println("Value, Percentile, TotalCountIncludingThisValue\n");

        PercentileIterator iterator = percentileIterator;
        iterator.reset(percentileTicksPerHalfDistance);
        String percentileFormatString = "%12." + histogram.numberOfSignificantValueDigits + "f %2.12f %10d\n";
        while (iterator.hasNext()) {
            HistogramIterationValue iterationValue = iterator.next();
            printStream.format(Locale.US, percentileFormatString,
                    iterationValue.getValueIteratedTo() / outputValueUnitScalingRatio, iterationValue.getPercentileLevelIteratedTo()/100.0,
                    iterationValue.getTotalCountToThisValue());
        }

        // Calculate and output mean and std. deviation.
        // Note: mean/std. deviation numbers are very often completely irrelevant when
        // data is extremely non-normal in distribution (e.g. in cases of strong multi-modal
        // response time distribution associated with GC pauses). However, reporting these numbers
        // can be very useful for contrasting with the detailed percentile distribution
        // reported by outputPercentileDistribution(). It is not at all surprising to find
        // percentile distributions where results fall many tens or even hundreds of standard
        // deviations away from the mean - such results simply indicate that the data sampled
        // exhibits a very non-normal distribution, highlighting situations for which the std.
        // deviation metric is a useless indicator.
        //

        double mean =  getMean() / outputValueUnitScalingRatio;
        double std_deviation = getStdDeviation() / outputValueUnitScalingRatio;
        printStream.format(Locale.US,
                "#[Mean    = %12." + histogram.numberOfSignificantValueDigits + "f, StdDeviation   = %12." +
                        histogram.numberOfSignificantValueDigits +"f]\n",
                mean, std_deviation);
        printStream.format(Locale.US,
                "#[Max     = %12." + histogram.numberOfSignificantValueDigits + "f, Total count    = %12d]\n",
                getMaxValue() / outputValueUnitScalingRatio, getTotalCount());
        printStream.format(Locale.US, "#[Buckets = %12d, SubBuckets     = %12d]\n",
                histogram.bucketCount, histogram.subBucketCount);
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
        final int logBase;

        private LogarithmicBucketValues(final AbstractHistogram histogram,
                                        final int valueUnitsInFirstBucket, final int logBase) {
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
