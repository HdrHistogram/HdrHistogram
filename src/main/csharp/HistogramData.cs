/*
 * Written by Matt Warren, and released to the public domain,
 * as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This is a .NET port of the original Java version, which was written by
 * Gil Tene as described in
 * https://github.com/HdrHistogram/HdrHistogram
 */

using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using HdrHistogram.NET.Iteration;

namespace HdrHistogram.NET
{
    public class HistogramData 
    {
        readonly AbstractHistogram histogram;
        // Create instances of the needed iterators once. We will reset them on each use to avoid allocation:
        readonly PercentileIterator percentileIterator;
        readonly RecordedValuesIterator recordedValuesIterator;
        readonly int bucketCount;
        readonly int subBucketCount;

        internal HistogramData(/*final*/ AbstractHistogram histogram) 
        {
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
        public long getTotalCount() 
        {
            return histogram.getTotalCount();
        }

        /**
         * Get the lowest recorded value level in the histogram
         *
         * @return the Min value recorded in the histogram
         */
        public long getMinValue() 
        {
            recordedValuesIterator.reset();
            long min = 0;
            if (recordedValuesIterator.hasNext()) 
            {
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
        public long getMaxValue() 
        {
            recordedValuesIterator.reset();
            long max = 0;
            while (recordedValuesIterator.hasNext()) 
            {
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
        public double getMean() 
        {
            recordedValuesIterator.reset();
            long totalValue = 0;
            while (recordedValuesIterator.hasNext()) 
            {
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
        public double getStdDeviation() 
        {
            double mean =  getMean();
            double geometric_deviation_total = 0.0;
            recordedValuesIterator.reset();
            while (recordedValuesIterator.hasNext()) 
            {
                HistogramIterationValue iterationValue = recordedValuesIterator.next();
                Double deviation = (histogram.medianEquivalentValue(iterationValue.getValueIteratedTo()) * 1.0) - mean;
                geometric_deviation_total += (deviation * deviation) * iterationValue.getCountAddedInThisIterationStep();
            }
            double std_deviation = Math.Sqrt(geometric_deviation_total / getTotalCount());
            return std_deviation;
        }

        /**
         * Get the value at a given percentile
         *
         * @param percentile    The percentile for which the return the associated value
         * @return The value below which a given percentage of the overall recorded value entries in the
         * histogram all fall.
         */
        public long getValueAtPercentile(/*final*/ double percentile) 
        {
            double requestedPercentile = Math.Min(percentile, 100.0); // Truncate down to 100%
            long countAtPercentile = (long)(((requestedPercentile / 100.0) * getTotalCount()) + 0.5); // round to nearest
            countAtPercentile = Math.Max(countAtPercentile, 1); // Make sure we at least reach the first recorded entry
            long totalToCurrentIJ = 0;
            for (int i = 0; i < bucketCount; i++) 
            {
                int j = (i == 0) ? 0 : (subBucketCount / 2);
                for (; j < subBucketCount; j++) 
                {
                    totalToCurrentIJ += histogram.getCountAt(i, j);
                    if (totalToCurrentIJ >= countAtPercentile) 
                    {
                        long valueAtIndex = histogram.valueFromIndex(i, j);
                        return valueAtIndex;
                    }
                }
            }
            throw new ArgumentOutOfRangeException("percentile", "percentile value not found in range"); // should not reach here.
        }

        /**
         * Get the percentile at a given value
         *
         * @param value    The value for which the return the associated percentile
         * @return The percentile of values recorded at or below the given percentage in the
         * histogram all fall.
         */
        public double getPercentileAtOrBelowValue(/*final*/ long value) 
        {
            long totalToCurrentIJ = 0;

            int targetBucketIndex = histogram.getBucketIndex(value);
            int targetSubBucketIndex = histogram.getSubBucketIndex(value, targetBucketIndex);

            if (targetBucketIndex >= bucketCount)
                return 100.0;

            for (int i = 0; i <= targetBucketIndex; i++) 
            {
                int j = (i == 0) ? 0 : (subBucketCount / 2);
                int subBucketCap = (i == targetBucketIndex) ? (targetSubBucketIndex + 1): subBucketCount;
                for (; j < subBucketCap; j++) 
                {
                    totalToCurrentIJ += histogram.getCountAt(i, j);
                }
            }

            return (100.0 * totalToCurrentIJ) / getTotalCount();
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
        public long getCountBetweenValues(/*final*/ long lowValue, /*final*/ long highValue) //throws ArrayIndexOutOfBoundsException 
        {
            long count = 0;

            // Compute the sub-bucket-rounded values for low and high:
            int lowBucketIndex = histogram.getBucketIndex(lowValue);
            int lowSubBucketIndex = histogram.getSubBucketIndex(lowValue, lowBucketIndex);
            long valueAtlowValue = histogram.valueFromIndex(lowBucketIndex, lowSubBucketIndex);
            int highBucketIndex = histogram.getBucketIndex(highValue);
            int highSubBucketIndex = histogram.getSubBucketIndex(highValue, highBucketIndex);
            long valueAtHighValue = histogram.valueFromIndex(highBucketIndex, highSubBucketIndex);

            if ((lowBucketIndex >= bucketCount) || (highBucketIndex >= bucketCount))
                throw new ArgumentOutOfRangeException();

            for (int i = lowBucketIndex; i <= highBucketIndex; i++) 
            {
                int j = (i == 0) ? 0 : (subBucketCount / 2);
                for (; j < subBucketCount; j++) 
                {
                    long valueAtIndex = histogram.valueFromIndex(i, j);
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
        public long getCountAtValue(/*final*/ long value) //throws ArrayIndexOutOfBoundsException 
        {
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
        public Percentiles percentiles(/*final*/ int percentileTicksPerHalfDistance) 
        {
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
        public LinearBucketValues linearBucketValues(/*final*/ int valueUnitsPerBucket) 
        {
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
        public LogarithmicBucketValues logarithmicBucketValues(/*final*/ int valueUnitsInFirstBucket, /*final*/ double logBase) 
        {
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
        public RecordedValues recordedValues() 
        {
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
        public AllValues allValues() 
        {
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
         * @param useCsvFormat  Output in CSV format if true. Otherwise use plain text form.
         */
        public void outputPercentileDistribution(/*final*/ TextWriter /*PrintStream*/ printStream,
                                                 /*final*/ int percentileTicksPerHalfDistance = 5,
                                                 /*final*/ Double outputValueUnitScalingRatio = 1000.0,
                                                 bool useCsvFormat = false) 
        {
            if (useCsvFormat)
            {
                printStream.Write("\"Value\",\"Percentile\",\"TotalCount\",\"1/(1-Percentile)\"\n");
            }
            else 
            {
                printStream.Write("{0,12} {1,14} {2,10} {3,14}\n\n", "Value", "Percentile", "TotalCount", "1/(1-Percentile)");
            }

            PercentileIterator iterator = percentileIterator;
            iterator.reset(percentileTicksPerHalfDistance);

            String percentileFormatString;
            String lastLinePercentileFormatString;
            if (useCsvFormat) 
            {
                percentileFormatString = "{0:F" + histogram.numberOfSignificantValueDigits + "},{1:F12},{2},{3:F2}\n";
                lastLinePercentileFormatString = "{0:F" + histogram.numberOfSignificantValueDigits + "},{1:F12},{2},Infinity\n";
            }
            else
            {
                percentileFormatString = "{0,12:F" + histogram.numberOfSignificantValueDigits + "}" + " {1,2:F12} {2,10} {3,14:F2}\n";
                lastLinePercentileFormatString = "{0,12:F" + histogram.numberOfSignificantValueDigits + "} {1,2:F12} {2,10}\n";
            }

            try 
            {
                while (iterator.hasNext())
                {
                    HistogramIterationValue iterationValue = iterator.next();
                    if (iterationValue.getPercentileLevelIteratedTo() != 100.0D) 
                    {
                        printStream.Write(percentileFormatString,
                                iterationValue.getValueIteratedTo() / outputValueUnitScalingRatio, 
                                iterationValue.getPercentileLevelIteratedTo() / 100.0D,
                                iterationValue.getTotalCountToThisValue(),
                                1 / (1.0D - (iterationValue.getPercentileLevelIteratedTo() / 100.0D)));
                    } 
                    else
                    {
                        printStream.Write(lastLinePercentileFormatString,
                                iterationValue.getValueIteratedTo() / outputValueUnitScalingRatio, 
                                iterationValue.getPercentileLevelIteratedTo() / 100.0D,
                                iterationValue.getTotalCountToThisValue());
                    }
                }

                if (!useCsvFormat) 
                {
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

                    double mean =  getMean() / outputValueUnitScalingRatio;
                    double std_deviation = getStdDeviation() / outputValueUnitScalingRatio;
                    printStream.Write("#[Mean    = {0,12:F" + histogram.numberOfSignificantValueDigits + "}, " +
                                       "StdDeviation   = {1,12:F" + histogram.numberOfSignificantValueDigits + "}]\n", mean, std_deviation);
                    printStream.Write("#[Max     = {0,12:F" + histogram.numberOfSignificantValueDigits + "}, Total count    = {1,12}]\n",
                                        getMaxValue() / outputValueUnitScalingRatio, getTotalCount());
                    printStream.Write("#[Buckets = {0,12}, SubBuckets     = {1,12}]\n",
                                        histogram.bucketCount, histogram.subBucketCount);
                }
            }
            catch (ArgumentOutOfRangeException e) 
            {
                // Overflow conditions on histograms can lead to ArrayIndexOutOfBoundsException on iterations:
                if (histogram.hasOverflowed()) 
                {
                    //printStream.format(Locale.US, "# Histogram counts indicate OVERFLOW values");
                    printStream.Write("# Histogram counts indicate OVERFLOW values");
                } 
                else 
                {
                    // Re-throw if reason is not a known overflow:
                    throw e;
                }
            }
        }

        // Percentile iterator support:

        /**
         * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link PercentileIterator}
         */
        public class Percentiles : IEnumerable<HistogramIterationValue> 
        {
            readonly AbstractHistogram histogram;
            readonly int percentileTicksPerHalfDistance;

            public /*private*/ Percentiles(/*final*/ AbstractHistogram histogram, /*final*/ int percentileTicksPerHalfDistance) 
            {
                this.histogram = histogram;
                this.percentileTicksPerHalfDistance = percentileTicksPerHalfDistance;
            }

            /**
             * @return A {@link PercentileIterator}<{@link HistogramIterationValue}>
             */
            public IEnumerator<HistogramIterationValue> GetEnumerator() 
            {
                return new PercentileIterator(histogram, percentileTicksPerHalfDistance);
            }

            IEnumerator IEnumerable.GetEnumerator()
            {
                return this.GetEnumerator();
            }
        }

        // Linear iterator support:

        /**
         * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link LinearIterator}
         */
        public class LinearBucketValues : IEnumerable<HistogramIterationValue> 
        {
            readonly AbstractHistogram histogram;
            readonly int valueUnitsPerBucket;

            public /*private*/ LinearBucketValues(/*final*/ AbstractHistogram histogram, /*final*/ int valueUnitsPerBucket) 
            {
                this.histogram = histogram;
                this.valueUnitsPerBucket = valueUnitsPerBucket;
            }

            /**
             * @return A {@link LinearIterator}<{@link HistogramIterationValue}>
             */
            public IEnumerator<HistogramIterationValue> GetEnumerator() 
            {
                return new LinearIterator(histogram, valueUnitsPerBucket);
            }

            IEnumerator IEnumerable.GetEnumerator()
            {
                return this.GetEnumerator();
            }
        }

        // Logarithmic iterator support:

        /**
         * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link LogarithmicIterator}
         */
        public class LogarithmicBucketValues : IEnumerable<HistogramIterationValue> 
        {
            readonly AbstractHistogram histogram;
            readonly int valueUnitsInFirstBucket;
            readonly double logBase;

            public /*private*/ LogarithmicBucketValues(/*final*/ AbstractHistogram histogram,
                                            /*final*/ int valueUnitsInFirstBucket,/*final*/ double logBase) 
            {
                this.histogram = histogram;
                this.valueUnitsInFirstBucket = valueUnitsInFirstBucket;
                this.logBase = logBase;
            }

            /**
             * @return A {@link LogarithmicIterator}<{@link HistogramIterationValue}>
             */
            public IEnumerator<HistogramIterationValue> GetEnumerator() 
            {
                return new LogarithmicIterator(histogram, valueUnitsInFirstBucket, logBase);
            }

            IEnumerator IEnumerable.GetEnumerator()
            {
                return this.GetEnumerator();
            }
        }

        // Recorded value iterator support:

        /**
         * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link RecordedValuesIterator}
         */
        public class RecordedValues : IEnumerable<HistogramIterationValue> 
        {
            readonly AbstractHistogram histogram;

            public /*private*/ RecordedValues(/*final*/ AbstractHistogram histogram) 
            {
                this.histogram = histogram;
            }

            /**
             * @return A {@link RecordedValuesIterator}<{@link HistogramIterationValue}>
             */
            public IEnumerator<HistogramIterationValue> GetEnumerator() 
            {
                return new RecordedValuesIterator(histogram);
            }

            IEnumerator IEnumerable.GetEnumerator()
            {
                return this.GetEnumerator();
            }
        }

        // AllValues iterator support:

        /**
         * An {@link java.lang.Iterable}<{@link HistogramIterationValue}> through the histogram using a {@link AllValuesIterator}
         */
        public class AllValues : IEnumerable<HistogramIterationValue> 
        {
            readonly AbstractHistogram histogram;

            public /*private*/ AllValues(/*final*/ AbstractHistogram histogram) 
            {
                this.histogram = histogram;
            }

            /**
             * @return A {@link AllValuesIterator}<{@link HistogramIterationValue}>
             */
            public IEnumerator<HistogramIterationValue> GetEnumerator() 
            {
                return new AllValuesIterator(histogram);
            }

            IEnumerator IEnumerable.GetEnumerator()
            {
                return this.GetEnumerator();
            }
        }
    }
}
