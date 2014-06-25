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
     * Used for iterating through histogram values in linear steps. The iteration is
     * performed in steps of <i>valueUnitsPerBucket</i> in size, terminating when all recorded histogram
     * values are exhausted. Note that each iteration "bucket" includes values up to and including
     * the next bucket boundary value.
     */
    public class LinearIterator : AbstractHistogramIterator
    {
        long valueUnitsPerBucket;
        long nextValueReportingLevel;
        long nextValueReportingLevelLowestEquivalent;

        /**
         * Reset iterator for re-use in a fresh iteration over the same histogram data set.
         * @param valueUnitsPerBucket The size (in value units) of each bucket iteration.
         */
        public void reset(/*final*/ int valueUnitsPerBucket) {
            this.reset(this.histogram, valueUnitsPerBucket);
        }

        private void reset(/*final*/ AbstractHistogram histogram, /*final*/ long valueUnitsPerBucket) {
            base.resetIterator(histogram);
            this.valueUnitsPerBucket = valueUnitsPerBucket;
            this.nextValueReportingLevel = valueUnitsPerBucket;
            this.nextValueReportingLevelLowestEquivalent = histogram.lowestEquivalentValue(this.nextValueReportingLevel);
        }

        /**
         * @param histogram The histogram this iterator will operate on
         * @param valueUnitsPerBucket The size (in value units) of each bucket iteration.
         */
        public LinearIterator(/*final*/ AbstractHistogram histogram, /*final*/ int valueUnitsPerBucket) {
            this.reset(histogram, valueUnitsPerBucket);
        }

        public override bool hasNext() 
        {
            if (base.hasNext()) 
            {
                return true;
            }
            // If next iterate does not move to the next sub bucket index (which is empty if
            // if we reached this point), then we are not done iterating... Otherwise we're done.
            return (this.nextValueReportingLevelLowestEquivalent < this.nextValueAtIndex);
        }

        protected override void incrementIterationLevel() 
        {
            this.nextValueReportingLevel += this.valueUnitsPerBucket;
            this.nextValueReportingLevelLowestEquivalent = this.histogram.lowestEquivalentValue(this.nextValueReportingLevel);
        }

        protected override long getValueIteratedTo() 
        {
            return this.nextValueReportingLevel;
        }

        protected override bool reachedIterationLevel() 
        {
            return (this.currentValueAtIndex >= this.nextValueReportingLevelLowestEquivalent);
        }
    }
}
