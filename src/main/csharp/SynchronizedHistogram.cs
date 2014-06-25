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
using HdrHistogram.NET.Utilities;

namespace HdrHistogram.NET
{
    /**
     * <h3>An internally synchronized High Dynamic Range (HDR) Histogram using a <b><code>long</code></b> count type </h3>
     * <p>
     * See package description for {@link org.HdrHistogram} for details.
     */
    public class SynchronizedHistogram : AbstractHistogram 
    {
        long totalCount;
        readonly long[] counts;

        public override long getCountAtIndex(/*final*/ int index) 
        {
            return counts[index];
        }

        public override void incrementCountAtIndex(/*final*/ int index) 
        {
            lock (updateLock) 
            {
                counts[index]++;
            }
        }

        public override void addToCountAtIndex(/*final*/ int index, /*final*/ long value) 
        {
            lock (updateLock) 
            {
                counts[index] += value;
            }
        }

        public override void clearCounts() 
        {
            lock (updateLock) 
            {
                Array.Clear(counts, 0, counts.Length);
                totalCount = 0;
            }
        }

        public new void add(/*final*/ AbstractHistogram other) 
        {
            // Synchronize add(). Avoid deadlocks by synchronizing in order of construction identity count.
            if (identity < other.identity) 
            {
                lock (updateLock)
                {
                    lock (other)
                    {
                        base.add(other);
                    }
                }
            } 
            else 
            {
                lock(other) 
                {
                    lock (updateLock) 
                    {
                        base.add(other);
                    }
                }
            }
        }

        public override /*SynchronizedHistogram*/ AbstractHistogram copy() 
        {
            SynchronizedHistogram copy = new SynchronizedHistogram(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits);
            copy.add(this);
            return copy;
        }

        public override /*SynchronizedHistogram*/ AbstractHistogram copyCorrectedForCoordinatedOmission(/*final*/ long expectedIntervalBetweenValueSamples) 
        {
            SynchronizedHistogram toHistogram = new SynchronizedHistogram(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits);
            toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
            return toHistogram;
        }

        public override long getTotalCount() 
        {
            return totalCount;
        }

        public override void setTotalCount(/*final*/ long totalCount) 
        {
            lock (updateLock) 
            {
               this.totalCount = totalCount;
            }
        }

        public override void incrementTotalCount() 
        {
            lock (updateLock) 
            {
                totalCount++;
            }
        }

        public override void addToTotalCount(long value) 
        {
            lock (updateLock) 
            {
                totalCount += value;
            }
        }

        public override int _getEstimatedFootprintInBytes() 
        {
            return (512 + (8 * counts.Length));
        }

        /**
         * Construct a SynchronizedHistogram given the Highest value to be tracked and a number of significant decimal digits. The
         * histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
         *
         * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
         *                              integer that is {@literal >=} 2.
         * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
         *                                       maintain value resolution and separation. Must be a non-negative
         *                                       integer between 0 and 5.
         */
        public SynchronizedHistogram(/*final*/ long highestTrackableValue, /*final*/ int numberOfSignificantValueDigits)
            : this(1, highestTrackableValue, numberOfSignificantValueDigits)
        {
        }

        /**
         * Construct a SynchronizedHistogram given the Lowest and Highest values to be tracked and a number of significant
         * decimal digits. Providing a lowestTrackableValue is useful is situations where the units used
         * for the histogram's values are much smaller that the minimal accuracy required. E.g. when tracking
         * time values stated in nanosecond units, where the minimal accuracy required is a microsecond, the
         * proper value for lowestTrackableValue would be 1000.
         *
         * @param lowestTrackableValue The lowest value that can be tracked (distinguished from 0) by the histogram.
         *                             Must be a positive integer that is {@literal >=} 1. May be internally rounded down to nearest
         *                             power of 2.
         * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
         *                              integer that is {@literal >=} (2 * lowestTrackableValue).
         * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
         *                                       maintain value resolution and separation. Must be a non-negative
         *                                       integer between 0 and 5.
         */
        public SynchronizedHistogram(/*final*/ long lowestTrackableValue, /*final*/ long highestTrackableValue, /*final*/ int numberOfSignificantValueDigits)
            : base(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits)
        {
            counts = new long[countsArrayLength];
            wordSizeInBytes = 8;
        }

        /**
         * Construct a new histogram by decoding it from a ByteBuffer.
         * @param buffer The buffer to decode from
         * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
         * @return The newly constructed histogram
         */
        public static SynchronizedHistogram decodeFromByteBuffer(/*final*/ ByteBuffer buffer,
                                                                 /*final*/ long minBarForHighestTrackableValue) 
        {
            return (SynchronizedHistogram)decodeFromByteBuffer(buffer, typeof(SynchronizedHistogram), minBarForHighestTrackableValue);
        }

        /**
         * Construct a new histogram by decoding it from a compressed form in a ByteBuffer.
         * @param buffer The buffer to encode into
         * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
         * @return The newly constructed histogram
         * @throws DataFormatException on error parsing/decompressing the buffer
         */
        public static SynchronizedHistogram decodeFromCompressedByteBuffer(/*final*/ ByteBuffer buffer,
                                                                           /*final*/ long minBarForHighestTrackableValue) //throws DataFormatException 
        {
            return (SynchronizedHistogram)decodeFromCompressedByteBuffer(buffer, typeof(SynchronizedHistogram), minBarForHighestTrackableValue);
        }

        //private void readObject(/*final*/ ObjectInputStream o)
        //        throws IOException, ClassNotFoundException {
        //    o.defaultReadObject();
        //}

        public override void fillCountsArrayFromBuffer(/*final*/ ByteBuffer buffer, /*final*/ int length) 
        {
            lock (updateLock)
            {
                buffer.asLongBuffer().get(counts, 0, length);
            }
        }

        // We try to cache the LongBuffer used in output cases, as repeated
        // output form the same histogram using the same buffer is likely:
        private WrappedBuffer<long> cachedDstLongBuffer = null;
        private ByteBuffer cachedDstByteBuffer = null;
        private int cachedDstByteBufferPosition = 0;

        public override void fillBufferFromCountsArray(/*final*/ ByteBuffer buffer, /*final*/ int length) 
        {
            lock (updateLock)
            {
                if ((cachedDstLongBuffer == null) ||
                    (buffer != cachedDstByteBuffer) ||
                    (buffer.position() != cachedDstByteBufferPosition))
                {
                    cachedDstByteBuffer = buffer;
                    cachedDstByteBufferPosition = buffer.position();
                    cachedDstLongBuffer = buffer.asLongBuffer();
                }
                cachedDstLongBuffer.rewind();
                cachedDstLongBuffer.put(counts, 0, length);
            }
        }
    }
}
