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
    public class RecordedValuesIterator : AbstractHistogramIterator
    {
        int visitedSubBucketIndex;
        int visitedBucketIndex;

        /**
         * Reset iterator for re-use in a fresh iteration over the same histogram data set.
         */
        public void reset() 
        {
            this.reset(this.histogram);
        }

        private void reset(/*final*/ AbstractHistogram histogram) 
        {
            base.resetIterator(histogram);
            this.visitedSubBucketIndex = -1;
            this.visitedBucketIndex = -1;
        }

        /**
         * @param histogram The histogram this iterator will operate on
         */
        public RecordedValuesIterator(/*final*/ AbstractHistogram histogram) 
        {
            this.reset(histogram);
        }

        protected override void incrementIterationLevel() 
        {
            this.visitedSubBucketIndex = this.currentSubBucketIndex;
            this.visitedBucketIndex = this.currentBucketIndex;
        }

        protected override bool reachedIterationLevel() 
        {
            long currentIJCount = this.histogram.getCountAt(this.currentBucketIndex, this.currentSubBucketIndex);
            return (currentIJCount != 0) &&
                    ((this.visitedSubBucketIndex != this.currentSubBucketIndex) || (this.visitedBucketIndex != this.currentBucketIndex));
        }
    }
}
