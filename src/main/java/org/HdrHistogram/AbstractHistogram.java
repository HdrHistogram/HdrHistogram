/**
 * Histogram.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.1.2
 */

package org.HdrHistogram;

import java.io.*;

/**
 * <h3>A High Dynamic Range (HDR) Histogram</h3>
 * <p>
 * AbstractHistogram supports the recording and analyzing sampled data value counts across a configurable integer value
 * range with configurable value precision within the range. Value precision is expressed as the number of significant
 * digits in the value recording, and provides control over value quantization behavior across the value range and the
 * subsequent value resolution at any given level.
 * <p>
 * For example, a Histogram could be configured to track the counts of observed integer values between 0 and
 * 3,600,000,000 while maintaining a value precision of 3 significant digits across that range. Value quantization
 * within the range will thus be no larger than 1/1,000th (or 0.1%) of any value. This example Histogram could
 * be used to track and analyze the counts of observed response times ranging between 1 microsecond and 1 hour
 * in magnitude, while maintaining a value resolution of 1 microsecond up to 1 millisecond, a resolution of
 * 1 millisecond (or better) up to one second, and a resolution of 1 second (or better) up to 1,000 seconds. At it's
 * maximum tracked value (1 hour), it would still maintain a resolution of 3.6 seconds (or better).
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 *
 */
abstract class AbstractHistogramColdFields{
    // "Cold" accessed fields. Not used in the recording code path:
    long highestTrackableValue;
    int numberOfSignificantValueDigits;

    int bucketCount;
    int subBucketCount;
    int countsArrayLength;

    HistogramData histogramData;
}
public abstract class AbstractHistogram extends AbstractHistogramColdFields implements Serializable {
    // Bunch "Hot" accessed fields (used in the the value recording code path) here, near the end, so
    // that they will have a good chance of ending up in the same cache line as the counts array reference
    // field that subclass implementations will add.
    int subBucketHalfCountMagnitude;
    int subBucketHalfCount;
    long subBucketMask;
    // long totalCount;


    // Abstract, counts-type dependent methods to be provided by subclass implementations:

    abstract long getCountAtIndex(int index);

    abstract void incrementCountAtIndex(int index);

    abstract void addToCountAtIndex(int index, long value);

    abstract long getTotalCount();

    abstract void setTotalCount(long totalCount);

    abstract void incrementTotalCount();

    abstract void clearCounts();

    /**
     * Provide a (conservatively high) estimate of the Histogram's total footprint in bytes
     *
     * @return a (conservatively high) estimate of the Histogram's total footprint in bytes
     */
    abstract public int getEstimatedFootprintInBytes();

    /**
     * Construct a Histogram given the Highest value to be tracked and a number of significant decimal digits
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is >= 2.
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public AbstractHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        // Verify argument validity
        if (highestTrackableValue < 2)
            throw new IllegalArgumentException("highestTrackableValue must be >= 2");
        if ((numberOfSignificantValueDigits < 0) || (numberOfSignificantValueDigits > 5))
            throw new IllegalArgumentException("numberOfSignificantValueDigits must be between 0 and 6");
        init(highestTrackableValue, numberOfSignificantValueDigits, 0);
    }

    private void init(final long highestTrackableValue, final int numberOfSignificantValueDigits, long totalCount) {
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;

        final long largestValueWithSingleUnitResolution = 2 * (long) Math.pow(10, numberOfSignificantValueDigits);

        // We need to maintain power-of-two subBucketCount (for clean direct indexing) that is large enough to
        // provide unit resolution to at least largestValueWithSingleUnitResolution. So figure out
        // largestValueWithSingleUnitResolution's nearest power-of-two (rounded up), and use that:
        int subBucketCountMagnitude = (int) Math.ceil(Math.log(largestValueWithSingleUnitResolution)/Math.log(2));
        subBucketHalfCountMagnitude = ((subBucketCountMagnitude > 1) ? subBucketCountMagnitude : 1) - 1;
        subBucketCount = (int) Math.pow(2, (subBucketHalfCountMagnitude + 1));
        subBucketHalfCount = subBucketCount / 2;
        subBucketMask = subBucketCount - 1;

        // determine exponent range needed to support the trackable value with no overflow:
        long trackableValue = subBucketCount - 1;
        int bucketsNeeded = 1;
        while (trackableValue < highestTrackableValue) {
            trackableValue <<= 1;
            bucketsNeeded++;
        }
        this.bucketCount = bucketsNeeded;

        countsArrayLength = (bucketCount + 1) * (subBucketCount / 2);

        setTotalCount(totalCount);

        histogramData = new HistogramData(this);
    }

    /**
     * get the configured numberOfSignificantValueDigits
     * @return numberOfSignificantValueDigits
     */
    public int getNumberOfSignificantValueDigits() {
        return numberOfSignificantValueDigits;
    }

    /**
     * get the configured highestTrackableValue
     * @return highestTrackableValue
     */
    public long getHighestTrackableValue() {
        return highestTrackableValue;
    }

    private int countsArrayIndex(final int bucketIndex, final int subBucketIndex) {
        assert(subBucketIndex < subBucketCount);
        assert(bucketIndex < bucketCount);
        assert(bucketIndex == 0 || (subBucketIndex >= subBucketHalfCount));
        // Calculate the index for the first entry in the bucket:
        // (The following is the equivalent of ((bucketIndex + 1) * subBucketHalfCount) ):
        int bucketBaseIndex = (bucketIndex + 1) << subBucketHalfCountMagnitude;
        // Calculate the offset in the bucket:
        int offsetInBucket = subBucketIndex - subBucketHalfCount;
        // The following is the equivalent of ((subBucketIndex  - subBucketHalfCount) + bucketBaseIndex;
        return bucketBaseIndex + offsetInBucket;
    }

    long getCountAt(final int bucketIndex, final int subBucketIndex) {
        return getCountAtIndex(countsArrayIndex(bucketIndex, subBucketIndex));
    }

    private static void arrayAdd(AbstractHistogram toHistogram, AbstractHistogram fromHistogram) {
        if (fromHistogram.countsArrayLength != toHistogram.countsArrayLength) throw new IndexOutOfBoundsException();
        for (int i = 0; i < fromHistogram.countsArrayLength; i++)
            toHistogram.addToCountAtIndex(i, fromHistogram.getCountAtIndex(i));
    }

    int getBucketIndex(long value) {
        int pow2ceiling = 64 - Long.numberOfLeadingZeros(value | subBucketMask); // smallest power of 2 containing value
        return  pow2ceiling - (subBucketHalfCountMagnitude + 1);
    }

    int getSubBucketIndex(long value, int bucketIndex) {
        return  (int)(value >> bucketIndex);
    }

    private void recordSingleValue(final long value) throws ArrayIndexOutOfBoundsException {
        // Dissect the value into bucket and sub-bucket parts, and derive index into counts array:
        int bucketIndex = getBucketIndex(value);
        int subBucketIndex = getSubBucketIndex(value, bucketIndex);
        int countsIndex = countsArrayIndex(bucketIndex, subBucketIndex);
        incrementCountAtIndex(countsIndex);
        incrementTotalCount();
    }

    /**
     * Record a value in the histogram.
     * <p>
     * To compensate for the loss of sampled values when a recorded value is larger than the expected
     * interval between value samples, Histogram will auto-generate an additional series of decreasingly-smaller
     * (down to the expectedIntervalBetweenValueSamples) value records. In addition to the default, "corrected"
     * histogram representation containing potential auto-generated value records, the Histogram keeps track of
     * "raw" histogram data containing no such corrections. This data set is available via the
     * <b><code>getRawData</code></b> method.
     * <p>
     * See notes in the description of the Histogram calls for an illustration of why this corrective behavior is
     * important.
     *
     * @param value The value to record
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     * @throws ArrayIndexOutOfBoundsException
     */
    public void recordValue(final long value, final long expectedIntervalBetweenValueSamples) throws ArrayIndexOutOfBoundsException {
        recordSingleValue(value);
        if (expectedIntervalBetweenValueSamples <=0)
            return;
        for (long missingValue = value - expectedIntervalBetweenValueSamples;
             missingValue >= expectedIntervalBetweenValueSamples;
             missingValue -= expectedIntervalBetweenValueSamples) {
            recordSingleValue(missingValue);
        }
    }

    /**
     * Record a value in the histogram
     *
     * @param value The value to be recorded
     * @throws ArrayIndexOutOfBoundsException
     */
    public void recordValue(final long value) throws ArrayIndexOutOfBoundsException {
        recordSingleValue(value);
    }

    /**
     * Reset the contents and stats of this histogram
     */
    public void reset() {
        clearCounts();
    }

    /**
     * Add the contents of another histogram to this one
     *
     * @param other The other histogram. highestTrackableValue and largestValueWithSingleUnitResolution must match.
     */
    public void add(final AbstractHistogram other) {
        if ((highestTrackableValue != other.highestTrackableValue) ||
                (numberOfSignificantValueDigits != other.numberOfSignificantValueDigits) ||
                (bucketCount != other.bucketCount) ||
                (subBucketCount != other.subBucketCount))
            throw new IllegalArgumentException("Cannot add histograms with incompatible ranges");
        arrayAdd(this, other);
        setTotalCount(getTotalCount() + other.getTotalCount());
    }

    /**
     * Determine if this histogram had any of it's value counts overflow.
     * Since counts are kept in fixed integer form with potentially limited range (e.g. int and short), a
     * specific value range count could potentially overflow, leading to an inaccurate and misleading histogram
     * representation. This method accurately determines whether or not an overflow condition has happened in an
     * IntHistogram or ShortHistogram.
     *
     * @return True if this histogram has had a count value overflow.
     */
    public boolean hasOverflowed() {
        // On overflow, the totalCount accumulated counter will (always) not match the total of counts
        long totalCounted = 0;
        for (int i = 0; i < countsArrayLength; i++) {
            totalCounted += getCountAtIndex(i);
        }
        return (totalCounted != getTotalCount());
    }

    /**
     * Determine if this histogram is equivalent to another.
     *
     * @param other the other histogram to compare to
     * @return True if this histogram are equivalent with the other.
     */
    public boolean equals(Object other){
        if ( this == other ) return true;
        if ( !(other instanceof AbstractHistogram) ) return false;
        AbstractHistogram that = (AbstractHistogram)other;
        if ((highestTrackableValue != that.highestTrackableValue) ||
                (numberOfSignificantValueDigits != that.numberOfSignificantValueDigits))
            return false;
        if (countsArrayLength != that.countsArrayLength)
            return false;
        if (getTotalCount() != that.getTotalCount())
            return false;
        return true;
    }

    /**
     * Provide access to the histogram's data set.
     * @return a {@link HistogramData} that can be used to query stats and iterate through the default (corrected)
     * data set.
     */
    public HistogramData getHistogramData() {
        return histogramData;
    }

    /**
     * Get the size (in value units) of the range of values that are equivalent to the given value within the
     * histogram's resolution. Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The lowest value that is equivalent to the given value within the histogram's resolution.
     */
    public long sizeOfEquivalentValueRange(long value) {
        int bucketIndex = getBucketIndex(value);
        int subBucketIndex = getSubBucketIndex(value, bucketIndex);
        long distanceToNextValue =
                (1 << ((subBucketIndex >= subBucketCount) ? (bucketIndex + 1) : bucketIndex));
        return distanceToNextValue;
    }

    /**
     * Get the lowest value that is equivalent to the given value within the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The lowest value that is equivalent to the given value within the histogram's resolution.
     */
    public long lowestEquivalentValue(long value) {
        int bucketIndex = getBucketIndex(value);
        int subBucketIndex = getSubBucketIndex(value, bucketIndex);
        long thisValueBaseLevel = subBucketIndex << bucketIndex;
        return thisValueBaseLevel;
    }

    /**
     * Get the highest value that is equivalent to the given value within the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The highest value that is equivalent to the given value within the histogram's resolution.
     */
    public long highestEquivalentValue(long value) {
        return nextNonEquivalentValue(value) - 1;
    }

    /**
     * Get a value that lies in the middle (rounded up) of the range of values equivalent the given value.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The value lies in the middle (rounded up) of the range of values equivalent the given value.
     */
    public long medianEquivalentValue(long value) {
        return (lowestEquivalentValue(value) + (sizeOfEquivalentValueRange(value) >> 1));
    }

    /**
     * Get the next value that is not equivalent to the given value within the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The next value that is not equivalent to the given value within the histogram's resolution.
     */
    public long nextNonEquivalentValue(long value) {
        return lowestEquivalentValue(value) + sizeOfEquivalentValueRange(value);
    }

    /**
     * Determine if two values are equivalent with the histogram's resolution.
     * Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value1 first value to compare
     * @param value2 second value to compare
     * @return True if values are equivalent with the histogram's resolution.
     */
    public boolean valuesAreEquivalent(long value1, long value2) {
        return (lowestEquivalentValue(value1) == lowestEquivalentValue(value2));
    }

    private static final long serialVersionUID = 42L;

    private void writeObject(ObjectOutputStream o)
            throws IOException
    {
        o.writeLong(highestTrackableValue);
        o.writeInt(numberOfSignificantValueDigits);
        o.writeLong(getTotalCount()); // Needed because overflow situations may lead this to differ from counts totals
    }

    private void readObject(ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        final long highestTrackableValue = o.readLong();
        final int numberOfSignificantValueDigits = o.readInt();
        final long totalCount = o.readLong();
        init(highestTrackableValue, numberOfSignificantValueDigits, totalCount);
    }
}