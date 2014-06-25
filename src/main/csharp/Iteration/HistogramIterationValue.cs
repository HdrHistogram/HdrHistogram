/*
 * Written by Matt Warren, and released to the public domain,
 * as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This is a .NET port of the original Java version, which was written by
 * Gil Tene as described in
 * https://github.com/HdrHistogram/HdrHistogram
 */

namespace HdrHistogram.NET.Iteration
{
    /**
     * Represents a value point iterated through in a Histogram, with associated stats.
     * <ul>
     * <li><b><code>valueIteratedTo</code></b> :<br> The actual value level that was iterated to by the iterator</li>
     * <li><b><code>prevValueIteratedTo</code></b> :<br> The actual value level that was iterated from by the iterator</li>
     * <li><b><code>countAtValueIteratedTo</code></b> :<br> The count of recorded values in the histogram that
     * exactly match this [lowestEquivalentValue(valueIteratedTo)...highestEquivalentValue(valueIteratedTo)] value
     * range.</li>
     * <li><b><code>countAddedInThisIterationStep</code></b> :<br> The count of recorded values in the histogram that
     * were added to the totalCountToThisValue (below) as a result on this iteration step. Since multiple iteration
     * steps may occur with overlapping equivalent value ranges, the count may be lower than the count found at
     * the value (e.g. multiple linear steps or percentile levels can occur within a single equivalent value range)</li>
     * <li><b><code>totalCountToThisValue</code></b> :<br> The total count of all recorded values in the histogram at
     * values equal or smaller than valueIteratedTo.</li>
     * <li><b><code>totalValueToThisValue</code></b> :<br> The sum of all recorded values in the histogram at values
     * equal or smaller than valueIteratedTo.</li>
     * <li><b><code>percentile</code></b> :<br> The percentile of recorded values in the histogram at values equal
     * or smaller than valueIteratedTo.</li>
     * <li><b><code>percentileLevelIteratedTo</code></b> :<br> The percentile level that the iterator returning this
     * HistogramIterationValue had iterated to. Generally, percentileLevelIteratedTo will be equal to or smaller than
     * percentile, but the same value point can contain multiple iteration levels for some iterators. E.g. a
     * PercentileIterator can stop multiple times in the exact same value point (if the count at that value covers a
     * range of multiple percentiles in the requested percentile iteration points).</li>
     * </ul>
     */
    public class HistogramIterationValue 
    {
        long valueIteratedTo;
        long valueIteratedFrom;
        long countAtValueIteratedTo;
        long countAddedInThisIterationStep;
        long totalCountToThisValue;
        long totalValueToThisValue;
        double percentile;
        double percentileLevelIteratedTo;

        // Set is all-or-nothing to avoid the potential for accidental omission of some values...
        internal void set(/*final*/ long valueIteratedTo, 
                            /*final*/ long valueIteratedFrom, 
                            /*final*/ long countAtValueIteratedTo,
                            /*final*/ long countInThisIterationStep, 
                            /*final*/ long totalCountToThisValue, 
                            /*final*/ long totalValueToThisValue,
                            /*final*/ double percentile, 
                            /*final*/ double percentileLevelIteratedTo) 
        {
            this.valueIteratedTo = valueIteratedTo;
            this.valueIteratedFrom = valueIteratedFrom;
            this.countAtValueIteratedTo = countAtValueIteratedTo;
            this.countAddedInThisIterationStep = countInThisIterationStep;
            this.totalCountToThisValue = totalCountToThisValue;
            this.totalValueToThisValue = totalValueToThisValue;
            this.percentile = percentile;
            this.percentileLevelIteratedTo = percentileLevelIteratedTo;
        }

        internal void reset() 
        {
            this.valueIteratedTo = 0;
            this.valueIteratedFrom = 0;
            this.countAtValueIteratedTo = 0;
            this.countAddedInThisIterationStep = 0;
            this.totalCountToThisValue = 0;
            this.totalValueToThisValue = 0;
            this.percentile = 0.0;
            this.percentileLevelIteratedTo = 0.0;
        }

        public HistogramIterationValue() 
        {
        }

        public override string  ToString()
        {
            return  "valueIteratedTo:" + this.valueIteratedTo +
                    ", prevValueIteratedTo:" + this.valueIteratedFrom +
                    ", countAtValueIteratedTo:" + this.countAtValueIteratedTo +
                    ", countAddedInThisIterationStep:" + this.countAddedInThisIterationStep +
                    ", totalCountToThisValue:" + this.totalCountToThisValue +
                    ", totalValueToThisValue:" + this.totalValueToThisValue +
                    ", percentile:" + this.percentile +
                    ", percentileLevelIteratedTo:" + this.percentileLevelIteratedTo;
        }

        public long getValueIteratedTo() 
        {
            return this.valueIteratedTo;
        }

        public long getValueIteratedFrom() 
        {
            return this.valueIteratedFrom;
        }

        public long getCountAtValueIteratedTo() 
        {
            return this.countAtValueIteratedTo;
        }

        public long getCountAddedInThisIterationStep() 
        {
            return this.countAddedInThisIterationStep;
        }

        public long getTotalCountToThisValue() 
        {
            return this.totalCountToThisValue;
        }

        public long getTotalValueToThisValue() 
        {
            return this.totalValueToThisValue;
        }

        public double getPercentile() 
        {
            return this.percentile;
        }

        public double getPercentileLevelIteratedTo() 
        {
            return this.percentileLevelIteratedTo;
        }
    }
}
