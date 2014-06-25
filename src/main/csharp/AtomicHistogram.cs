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
using CSharp.Atomic;
using HdrHistogram.NET.Utilities;

namespace HdrHistogram.NET
{
    /**
     * <h3>A High Dynamic Range (HDR) Histogram using atomic <b><code>long</code></b> count type </h3>
     * <p>
     * See package description for {@link org.HdrHistogram} for details.
     */
    public class AtomicHistogram : AbstractHistogram
    {
        //static AtomicLongFieldUpdater<AtomicHistogram> totalCountUpdater =
        //        AtomicLongFieldUpdater.newUpdater(AtomicHistogram.class, "totalCount");
        //volatile long totalCount;

        // TODO Revisit this, consider porting AtomicLongFieldUpdater, maybe gives a perf boost (creates less memory/objects)
        // The difference with this method is that we now have 1 AtomicLong per instance of AtomicHistogram, 
        // Using AtomicLongFieldUpdate<T>, you have 1 static instance shared by all instances (of AtomicHistogram), so less overhead?!?!?
        // See http://stackoverflow.com/questions/17239568/real-life-use-and-explanation-of-the-atomiclongfieldupdate-class
        // and http://javamex.com/tutorials/synchronization_concurrency_7_atomic_updaters.shtml
        // and http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/atomic/AtomicLongFieldUpdater.html#incrementAndGet(T) for more info
        AtomicLong totalCountUpdater = new AtomicLong();
        
        AtomicLongArray counts;

        public override long getCountAtIndex(int index) 
        {
            return counts.get(index);
        }

        public override void incrementCountAtIndex(int index) 
        {
            counts.incrementAndGet(index);
        }

        public override void addToCountAtIndex(int index, long value) 
        {
            counts.addAndGet(index, value);
        }

        public override void clearCounts() 
        {
            for (int i = 0; i < counts.length(); i++)
                counts.lazySet(i, 0);
            totalCountUpdater.Set(0);
        }

        public override /*AtomicHistogram*/ AbstractHistogram copy() 
        {
            AtomicHistogram copy = new AtomicHistogram(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits);
            copy.add(this);
            return copy;
        }

        public override /*AtomicHistogram*/ AbstractHistogram copyCorrectedForCoordinatedOmission(long expectedIntervalBetweenValueSamples) 
        {
            AtomicHistogram toHistogram = new AtomicHistogram(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits);
            toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
            return toHistogram;
        }

        public override long getTotalCount() 
        {
            return totalCountUpdater.Get();
        }

        public override void setTotalCount(long totalCount) 
        {
            totalCountUpdater.Set(totalCount);
        }

        public override void incrementTotalCount() 
        {
            totalCountUpdater.Increment();
        }

        public override void addToTotalCount(long value) 
        {
            totalCountUpdater.AddAndGet(value);
        }

        public override int _getEstimatedFootprintInBytes() 
        {
            return (512 + (8 * counts.length()));
        }

        /**
         * Construct a AtomicHistogram given the Highest value to be tracked and a number of significant decimal digits. The
         * histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
         *
         * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
         *                              integer that is {@literal >=} 2.
         * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
         *                                       maintain value resolution and separation. Must be a non-negative
         *                                       integer between 0 and 5.
         */
        public AtomicHistogram(long highestTrackableValue, int numberOfSignificantValueDigits)
            : this(1, highestTrackableValue, numberOfSignificantValueDigits)
        {
        }

        /**
         * Construct a AtomicHistogram given the Lowest and Highest values to be tracked and a number of significant
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
        public AtomicHistogram(long lowestTrackableValue, long highestTrackableValue, int numberOfSignificantValueDigits)
            : base(lowestTrackableValue, highestTrackableValue, numberOfSignificantValueDigits)
        {
            counts = new AtomicLongArray(countsArrayLength);
            wordSizeInBytes = 8;
        }

        /**
         * Construct a new histogram by decoding it from a ByteBuffer.
         * @param buffer The buffer to decode from
         * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
         * @return The newly constructed histogram
         */
        public static AtomicHistogram decodeFromByteBuffer(ByteBuffer buffer,
                                                           long minBarForHighestTrackableValue) 
        {
            return (AtomicHistogram)decodeFromByteBuffer(buffer, typeof(AtomicHistogram), minBarForHighestTrackableValue);
        }

        /**
         * Construct a new histogram by decoding it from a compressed form in a ByteBuffer.
         * @param buffer The buffer to encode into
         * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
         * @return The newly constructed histogram
         * @throws DataFormatException on error parsing/decompressing the buffer
         */
        public static AtomicHistogram decodeFromCompressedByteBuffer(ByteBuffer buffer,
                                                                     long minBarForHighestTrackableValue) //throws DataFormatException 
        {
            return (AtomicHistogram)decodeFromCompressedByteBuffer(buffer, typeof(AtomicHistogram), minBarForHighestTrackableValue);
        }

        //private void readObject(ObjectInputStream o)
        //        throws IOException, ClassNotFoundException {
        //    o.defaultReadObject();
        //}

        public override void fillCountsArrayFromBuffer(ByteBuffer buffer, int length) 
        {
            lock (updateLock)
            {
                WrappedBuffer<long> logbuffer = buffer.asLongBuffer();
                for (int i = 0; i < length; i++)
                {
                    counts.lazySet(i, logbuffer.get());
                }
            }
        }

        // We try to cache the LongBuffer used in output cases, as repeated
        // output form the same histogram using the same buffer is likely:
        private WrappedBuffer<long> cachedDstLongBuffer = null;
        private ByteBuffer cachedDstByteBuffer = null;
        private int cachedDstByteBufferPosition = 0;

        public override void fillBufferFromCountsArray(ByteBuffer buffer, int length)
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
                for (int i = 0; i < length; i++)
                {
                    cachedDstLongBuffer.put(counts.get(i));
                }
            }
        }
    }
}
