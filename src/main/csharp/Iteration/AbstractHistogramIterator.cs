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

namespace HdrHistogram.NET.Iteration
{
    public abstract class AbstractHistogramIterator : IEnumerator<HistogramIterationValue>
    {
        internal AbstractHistogram histogram;

        internal long savedHistogramTotalRawCount;

        internal int currentBucketIndex;

        internal int currentSubBucketIndex;

        internal long currentValueAtIndex;

        internal int nextBucketIndex;

        internal int nextSubBucketIndex;

        internal long nextValueAtIndex;

        internal long prevValueIteratedTo;

        internal long totalCountToPrevIndex;

        internal long totalCountToCurrentIndex;

        internal long totalValueToCurrentIndex;

        internal long arrayTotalCount;

        internal long countAtThisValue;

        private bool freshSubBucket;

        private HistogramIterationValue currentIterationValue;

        public void Dispose()
        {
            //throw new NotImplementedException();
        }

        public bool MoveNext()
        {
            var canMove = this.hasNext();
            if (canMove)
            {
                this.Current = this.next();
            }
            return canMove;
        }

        public void Reset()
        {
            this.resetIterator(this.histogram);
        }

        public HistogramIterationValue Current { get; private set; }

        object IEnumerator.Current
        {
            get { return this.Current; }
        }

        /**
         * Returns true if the iteration has more elements. (In other words, returns true if next would return an
         * element rather than throwing an exception.)
         *
         * @return true if the iterator has more elements.
         */
        public virtual bool hasNext()
        {
            if (this.histogram.getTotalCount() != this.savedHistogramTotalRawCount)
            {
                throw new InvalidOperationException();
            }
            return (this.totalCountToCurrentIndex < this.arrayTotalCount);
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the {@link HistogramIterationValue} associated with the next element in the iteration.
         */
        public HistogramIterationValue next()
        {
            // Move through the sub buckets and buckets until we hit the next reporting level:
            while (!this.exhaustedSubBuckets())
            {
                this.countAtThisValue = this.histogram.getCountAt(this.currentBucketIndex, this.currentSubBucketIndex);
                if (this.freshSubBucket)
                {
                    // Don't add unless we've incremented since last bucket...
                    this.totalCountToCurrentIndex += this.countAtThisValue;
                    this.totalValueToCurrentIndex += this.countAtThisValue * this.histogram.medianEquivalentValue(this.currentValueAtIndex);
                    this.freshSubBucket = false;
                }
                if (this.reachedIterationLevel())
                {
                    long valueIteratedTo = this.getValueIteratedTo();
                    this.currentIterationValue.set(
                        valueIteratedTo,
                        this.prevValueIteratedTo,
                        this.countAtThisValue,
                        (this.totalCountToCurrentIndex - this.totalCountToPrevIndex),
                        this.totalCountToCurrentIndex,
                        this.totalValueToCurrentIndex,
                        ((100.0 * this.totalCountToCurrentIndex) / this.arrayTotalCount),
                        this.getPercentileIteratedTo());
                    this.prevValueIteratedTo = valueIteratedTo;
                    this.totalCountToPrevIndex = this.totalCountToCurrentIndex;
                    // move the next iteration level forward:
                    this.incrementIterationLevel();
                    if (this.histogram.getTotalCount() != this.savedHistogramTotalRawCount)
                    {
                        throw new InvalidOperationException();
                    }
                    return this.currentIterationValue;
                }
                this.incrementSubBucket();
            }
            // Should not reach here. But possible for overflowed histograms under certain conditions
            throw new ArgumentOutOfRangeException();
        }

        protected void resetIterator(/*final*/ AbstractHistogram histogram)
        {
            this.histogram = histogram;
            this.savedHistogramTotalRawCount = histogram.getTotalCount();
            this.arrayTotalCount = histogram.getTotalCount();
            this.currentBucketIndex = 0;
            this.currentSubBucketIndex = 0;
            this.currentValueAtIndex = 0;
            this.nextBucketIndex = 0;
            this.nextSubBucketIndex = 1;
            this.nextValueAtIndex = 1;
            this.prevValueIteratedTo = 0;
            this.totalCountToPrevIndex = 0;
            this.totalCountToCurrentIndex = 0;
            this.totalValueToCurrentIndex = 0;
            this.countAtThisValue = 0;
            this.freshSubBucket = true;
            if (this.currentIterationValue == null)
                this.currentIterationValue = new HistogramIterationValue();
            this.currentIterationValue.reset();
        }

        /**
         * Not supported. Will throw an {@link UnsupportedOperationException}.
         */
        public void remove()
        {
            throw new InvalidOperationException();
        }

        /*private*/ protected abstract void incrementIterationLevel();

        /*private*/ protected abstract bool reachedIterationLevel();

        protected virtual double getPercentileIteratedTo()
        {
            return (100.0 * (double)this.totalCountToCurrentIndex) / this.arrayTotalCount;
        }

        protected virtual double getPercentileIteratedFrom()
        {
            return (100.0 * (double)this.totalCountToPrevIndex) / this.arrayTotalCount;
        }

        protected virtual long getValueIteratedTo()
        {
            return this.histogram.highestEquivalentValue(this.currentValueAtIndex);
        }

        private bool exhaustedSubBuckets()
        {
            return (this.currentBucketIndex >= this.histogram.bucketCount);
        }

        private void incrementSubBucket()
        {
            this.freshSubBucket = true;
            // Take on the next index:
            this.currentBucketIndex = this.nextBucketIndex;
            this.currentSubBucketIndex = this.nextSubBucketIndex;
            this.currentValueAtIndex = this.nextValueAtIndex;
            // Figure out the next next index:
            this.nextSubBucketIndex++;
            if (this.nextSubBucketIndex >= this.histogram.subBucketCount)
            {
                this.nextSubBucketIndex = this.histogram.subBucketHalfCount;
                this.nextBucketIndex++;
            }
            this.nextValueAtIndex = this.histogram.valueFromIndex(this.nextBucketIndex, this.nextSubBucketIndex);
        }
    }
}
