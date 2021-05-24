package org.HdrHistogram;

public interface IntervalHistogramProvider<T extends EncodableHistogram> {

   /**
    * Get a new instance of an interval histogram, which will include a stable, consistent view of all value
    * counts accumulated since the last interval histogram was taken.
    * <p>
    * Calling this will reset the value counts, and start accumulating value counts for the next interval.
    *
    * @return a histogram containing the value counts accumulated since the last interval histogram was taken.
    */
   T getIntervalHistogram();

   /**
    * Get an interval histogram, which will include a stable, consistent view of all value counts
    * accumulated since the last interval histogram was taken.
    * <p>
    * {@link #getIntervalHistogram(EncodableHistogram histogramToRecycle)
    * getIntervalHistogram(histogramToRecycle)}
    * accepts a previously returned interval histogram that can be recycled internally to avoid allocation
    * and content copying operations, and is therefore significantly more efficient for repeated use than
    * {@link #getIntervalHistogram()} and
    * {@link #getIntervalHistogramInto getIntervalHistogramInto()}. The provided
    * {@code histogramToRecycle} must
    * be either be null or an interval histogram returned by a previous call to
    * {@link #getIntervalHistogram(EncodableHistogram histogramToRecycle)
    * getIntervalHistogram(histogramToRecycle)} or
    * {@link #getIntervalHistogram()}.
    * <p>
    * NOTE: The caller is responsible for not recycling the same returned interval histogram more than once. If
    * the same interval histogram instance is recycled more than once, behavior is undefined.
    * <p>
    * Calling {@link #getIntervalHistogram(EncodableHistogram histogramToRecycle)
    * getIntervalHistogram(histogramToRecycle)} will reset the value counts, and start accumulating value
    * counts for the next interval
    *
    * @param histogramToRecycle a previously returned interval histogram that may be recycled to avoid allocation and
    *                           copy operations.
    * @return a histogram containing the value counts accumulated since the last interval histogram was taken.
    */
   T getIntervalHistogram(T histogramToRecycle);

   /**
    * Get an interval histogram, which will include a stable, consistent view of all value counts
    * accumulated since the last interval histogram was taken.
    * <p>
    * {@link #getIntervalHistogram(EncodableHistogram histogramToRecycle)
    * getIntervalHistogram(histogramToRecycle)}
    * accepts a previously returned interval histogram that can be recycled internally to avoid allocation
    * and content copying operations, and is therefore significantly more efficient for repeated use than
    * {@link #getIntervalHistogram()} and
    * {@link #getIntervalHistogramInto getIntervalHistogramInto()}. The provided
    * {@code histogramToRecycle} must
    * be either be null or an interval histogram returned by a previous call to
    * {@link #getIntervalHistogram(EncodableHistogram histogramToRecycle)
    * getIntervalHistogram(histogramToRecycle)} or
    * {@link #getIntervalHistogram()}.
    * <p>
    * NOTE: The caller is responsible for not recycling the same returned interval histogram more than once. If
    * the same interval histogram instance is recycled more than once, behavior is undefined.
    * <p>
    * Calling {@link #getIntervalHistogram(EncodableHistogram histogramToRecycle)
    * getIntervalHistogram(histogramToRecycle)} will reset the value counts, and start accumulating value
    * counts for the next interval
    *
    * @param histogramToRecycle        a previously returned interval histogram that may be recycled to avoid allocation and
    *                                  copy operations.
    * @param enforceContainingInstance if true, will only allow recycling of histograms previously returned from this
    *                                  instance of {@link IntervalHistogramProvider}. If false, will allow recycling histograms
    *                                  previously returned by other instances of {@link IntervalHistogramProvider}.
    * @return a histogram containing the value counts accumulated since the last interval histogram was taken.
    */
   T getIntervalHistogram(T histogramToRecycle, boolean enforceContainingInstance);

   /**
    * Place a copy of the value counts accumulated since accumulated (since the last interval histogram
    * was taken) into {@code targetHistogram}.
    * <p>
    * Calling {@link #getIntervalHistogramInto getIntervalHistogramInto()} will reset
    * the value counts, and start accumulating value counts for the next interval.
    *
    * @param targetHistogram the histogram into which the interval histogram's data should be copied
    */
   void getIntervalHistogramInto(T targetHistogram);
}
