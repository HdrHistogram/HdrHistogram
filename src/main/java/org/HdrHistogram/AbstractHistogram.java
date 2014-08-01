/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.IllegalFormatException;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * This non-public AbstractHistogramBase super-class separation is meant to bunch "cold" fields
 * separately from "hot" fields, in an attempt to force the JVM to place the (hot) fields
 * commonly used in the value recording code paths close together.
 * Subclass boundaries tend to be strongly control memory layout decisions in most practical
 * JVM implementations, making this an effective method for control filed grouping layout.
 */

abstract class AbstractHistogramBase {
    static AtomicLong constructionIdentityCount = new AtomicLong(0);

    // "Cold" accessed fields. Not used in the recording code path:
    long identity;

    long highestTrackableValue;
    long lowestDiscernibleValue;
    int numberOfSignificantValueDigits;

    int bucketCount;
    int subBucketCount;
    int countsArrayLength;
    int wordSizeInBytes;

    long startTimeStampMsec;
    long endTimeStampMsec;

    double integerToDoubleValueConversionRatio = 1.0;

    PercentileIterator percentileIterator;
    RecordedValuesIterator recordedValuesIterator;

    @SuppressWarnings("deprecation")
    HistogramData histogramData; // Deprecated, but we'll keep it around for compatibility.

    ByteBuffer intermediateUncompressedByteBuffer = null;

    public double getIntegerToDoubleValueConversionRatio() {
        return integerToDoubleValueConversionRatio;
    }

    public void setIntegerToDoubleValueConversionRatio(double integerToDoubleValueConversionRatio) {
        this.integerToDoubleValueConversionRatio = integerToDoubleValueConversionRatio;
    }
}

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

public abstract class AbstractHistogram extends AbstractHistogramBase implements EncodableHistogram, Serializable{

    // "Hot" accessed fields (used in the the value recording code path) are bunched here, such
    // that they will have a good chance of ending up in the same cache line as the totalCounts and
    // counts array reference fields that subclass implementations will typically add.
    int subBucketHalfCountMagnitude;
    int unitMagnitude;
    int subBucketHalfCount;
    long subBucketMask;
    long maxValue;

    // Sub-classes will typically add a totalCount field and a counts array field, which will likely be laid out
    // right around here due to the subclass layout rules in most practical JVM implementations.

    //
    //
    //
    // Abstract, counts-type dependent methods to be provided by subclass implementations:
    //
    //
    //

    abstract long getCountAtIndex(int index);

    abstract void incrementCountAtIndex(int index);

    abstract void addToCountAtIndex(int index, long value);

    abstract void setCountAtIndex(int index, long value);

    abstract void setTotalCount(long totalCount);

    abstract void incrementTotalCount();

    abstract void addToTotalCount(long value);

    abstract void clearCounts();

    abstract int _getEstimatedFootprintInBytes();

    /**
     * Get the total count of all recorded values in the histogram
     * @return the total count of all recorded values in the histogram
     */
    abstract public long getTotalCount();

    void setMaxValue(final long maxValue) {
        this.maxValue = maxValue;
    }

    //
    //
    //
    // Construction:
    //
    //
    //

    /**
     * Construct a histogram given the Lowest and Highest values to be tracked and a number of significant
     * decimal digits. Providing a lowestDiscernibleValue is useful is situations where the units used
     * for the histogram's values are much smaller that the minimal accuracy required. E.g. when tracking
     * time values stated in nanosecond units, where the minimal accuracy required is a microsecond, the
     * proper value for lowestDiscernibleValue would be 1000.
     *
     * @param lowestDiscernibleValue The lowest value that can be discerned (distinguished from 0) by the histogram.
     *                               Must be a positive integer that is {@literal >=} 1. May be internally rounded
     *                               down to nearest power of 2.
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} (2 * lowestDiscernibleValue).
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    public AbstractHistogram(final long lowestDiscernibleValue, final long highestTrackableValue,
                             final int numberOfSignificantValueDigits) {
        // Verify argument validity
        if (lowestDiscernibleValue < 1) {
            throw new IllegalArgumentException("lowestDiscernibleValue must be >= 1");
        }
        if (highestTrackableValue < 2 * lowestDiscernibleValue) {
            throw new IllegalArgumentException("highestTrackableValue must be >= 2 * lowestDiscernibleValue");
        }
        if ((numberOfSignificantValueDigits < 0) || (numberOfSignificantValueDigits > 5)) {
            throw new IllegalArgumentException("numberOfSignificantValueDigits must be between 0 and 6");
        }
        identity = constructionIdentityCount.getAndIncrement();

        init(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits, 1.0, 0, 0);
    }

    /**
     * Construct a histogram with the same range settings as a given source histogram,
     * duplicating the source's start/end timestamps (but NOT it's contents)
     * @param source The source histogram to duplicate
     */
    AbstractHistogram(final AbstractHistogram source) {
        this(source.getLowestDiscernibleValue(), source.getHighestTrackableValue(),
                source.getNumberOfSignificantValueDigits());
        this.setStartTimeStamp(source.getStartTimeStamp());
        this.setEndTimeStamp(source.getEndTimeStamp());
    }

    @SuppressWarnings("deprecation")
    private void init(final long lowestDiscernibleValue, final long highestTrackableValue,
                      final int numberOfSignificantValueDigits, final double integerToDoubleValueConversionRatio,
                      final long totalCount, final long maxValue) {
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
        this.integerToDoubleValueConversionRatio = integerToDoubleValueConversionRatio;

        final long largestValueWithSingleUnitResolution = 2 * (long) Math.pow(10, numberOfSignificantValueDigits);

        unitMagnitude = (int) Math.floor(Math.log(lowestDiscernibleValue)/Math.log(2));

        // We need to maintain power-of-two subBucketCount (for clean direct indexing) that is large enough to
        // provide unit resolution to at least largestValueWithSingleUnitResolution. So figure out
        // largestValueWithSingleUnitResolution's nearest power-of-two (rounded up), and use that:
        int subBucketCountMagnitude = (int) Math.ceil(Math.log(largestValueWithSingleUnitResolution)/Math.log(2));
        subBucketHalfCountMagnitude = ((subBucketCountMagnitude > 1) ? subBucketCountMagnitude : 1) - 1;
        subBucketCount = (int) Math.pow(2, (subBucketHalfCountMagnitude + 1));
        subBucketHalfCount = subBucketCount / 2;
        subBucketMask = (subBucketCount - 1) << unitMagnitude;


        // determine exponent range needed to support the trackable value with no overflow:

        bucketCount = getBucketsNeededToCoverValue(highestTrackableValue);

        countsArrayLength = getLengthForNumberOfBuckets(bucketCount);

        setTotalCount(totalCount);

        setMaxValue(maxValue);

        histogramData = new HistogramData(this);

        percentileIterator = new PercentileIterator(this, 1);
        recordedValuesIterator = new RecordedValuesIterator(this);
    }

    //
    //
    //
    // Value recording support:
    //
    //
    //

    /**
     * Record a value in the histogram
     *
     * @param value The value to be recorded
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValue(final long value) throws ArrayIndexOutOfBoundsException {
        recordSingleValue(value);
    }

    /**
     * Record a value in the histogram (adding to the value's current count)
     *
     * @param value The value to be recorded
     * @param count The number of occurrences of this value to record
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValueWithCount(final long value, final long count) throws ArrayIndexOutOfBoundsException {
        recordCountAtValue(count, value);
    }

    /**
     * Record a value in the histogram.
     * <p>
     * To compensate for the loss of sampled values when a recorded value is larger than the expected
     * interval between value samples, Histogram will auto-generate an additional series of decreasingly-smaller
     * (down to the expectedIntervalBetweenValueSamples) value records.
     * <p>
     * Note: This is a at-recording correction method, as opposed to the post-recording correction method provided
     * by {@link #copyCorrectedForCoordinatedOmission(long) getHistogramCorrectedForCoordinatedOmission}.
     * The two methods are mutually exclusive, and only one of the two should be be used on a given data set to correct
     * for the same coordinated omission issue.
     * <p>
     * See notes in the description of the Histogram calls for an illustration of why this corrective behavior is
     * important.
     *
     * @param value The value to record
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValueWithExpectedInterval(final long value, final long expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        recordValueWithCountAndExpectedInterval(value, 1, expectedIntervalBetweenValueSamples);
    }

    /**
     * @deprecated
     *
     * Record a value in the histogram. This deprecated method has identical behavior to
     * <b><code>recordValueWithExpectedInterval()</code></b>. It was renamed to avoid ambiguity.
     *
     * @param value The value to record
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValue(final long value, final long expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        recordValueWithExpectedInterval(value, expectedIntervalBetweenValueSamples);
    }

    private void recordCountAtValue(final long count, final long value)
            throws ArrayIndexOutOfBoundsException {
        int countsIndex = countsArrayIndex(value);
        addToCountAtIndex(countsIndex, count);
        if (value > maxValue) {
            maxValue = value;
        }
        addToTotalCount(count);
    }

    private void recordSingleValue(final long value) throws ArrayIndexOutOfBoundsException {
        int countsIndex = countsArrayIndex(value);
        incrementCountAtIndex(countsIndex);
        if (value > maxValue) {
            maxValue = value;
        }
        incrementTotalCount();
    }


    private void recordValueWithCountAndExpectedInterval(final long value, final long count,
                                                         final long expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        recordCountAtValue(count, value);
        if (expectedIntervalBetweenValueSamples <=0)
            return;
        for (long missingValue = value - expectedIntervalBetweenValueSamples;
             missingValue >= expectedIntervalBetweenValueSamples;
             missingValue -= expectedIntervalBetweenValueSamples) {
            recordCountAtValue(count, missingValue);
        }
    }

    //
    //
    //
    // Clearing support:
    //
    //
    //

    /**
     * Reset the contents and stats of this histogram
     */
    public void reset() {
        clearCounts();
        setMaxValue(0);
    }

    //
    //
    //
    // Copy support:
    //
    //
    //

    /**
     * Create a copy of this histogram, complete with data and everything.
     *
     * @return A distinct copy of this histogram.
     */
    abstract public AbstractHistogram copy();

    /**
     * Get a copy of this histogram, corrected for coordinated omission.
     * <p>
     * To compensate for the loss of sampled values when a recorded value is larger than the expected
     * interval between value samples, the new histogram will include an auto-generated additional series of
     * decreasingly-smaller (down to the expectedIntervalBetweenValueSamples) value records for each count found
     * in the current histogram that is larger than the expectedIntervalBetweenValueSamples.
     *
     * Note: This is a post-correction method, as opposed to the at-recording correction method provided
     * by {@link #recordValueWithExpectedInterval(long, long) recordValueWithExpectedInterval}. The two
     * methods are mutually exclusive, and only one of the two should be be used on a given data set to correct
     * for the same coordinated omission issue.
     * by
     * <p>
     * See notes in the description of the Histogram calls for an illustration of why this corrective behavior is
     * important.
     *
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     * @return a copy of this histogram, corrected for coordinated omission.
     */
    abstract public AbstractHistogram copyCorrectedForCoordinatedOmission(long expectedIntervalBetweenValueSamples);

    /**
     * Copy this histogram into the target histogram, overwriting it's contents.
     *
     * @param targetHistogram the histogram to copy into
     */
    public void copyInto(final AbstractHistogram targetHistogram) {
        targetHistogram.reset();
        targetHistogram.add(this);
        targetHistogram.setStartTimeStamp(this.startTimeStampMsec);
        targetHistogram.setEndTimeStamp(this.endTimeStampMsec);
    }

    /**
     * Copy this histogram, corrected for coordinated omission, into the target histogram, overwriting it's contents.
     * (see {@link #copyCorrectedForCoordinatedOmission} for more detailed explanation about how correction is applied)
     *
     * @param targetHistogram the histogram to copy into
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     */
    public void copyIntoCorrectedForCoordinatedOmission(final AbstractHistogram targetHistogram,
                                                        final long expectedIntervalBetweenValueSamples) {
        targetHistogram.reset();
        targetHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        targetHistogram.setStartTimeStamp(this.startTimeStampMsec);
        targetHistogram.setEndTimeStamp(this.endTimeStampMsec);
    }

    //
    //
    //
    // Add support:
    //
    //
    //

    /**
     * Add the contents of another histogram to this one.
     *
     * @param otherHistogram The other histogram.
     * @throws ArrayIndexOutOfBoundsException (may throw) if values in fromHistogram's are
     * higher than highestTrackableValue.
     */
    public void add(final AbstractHistogram otherHistogram) throws ArrayIndexOutOfBoundsException {
        if (this.highestTrackableValue < otherHistogram.highestTrackableValue) {
            throw new ArrayIndexOutOfBoundsException("The other histogram covers a wider range than this one.");
        }
        if ((bucketCount == otherHistogram.bucketCount) &&
                (subBucketCount == otherHistogram.subBucketCount) &&
                (unitMagnitude == otherHistogram.unitMagnitude)) {
            // Counts arrays are of the same length and meaning, so we can just iterate and add directly:
            long observedOtherTotalCount = 0;
            for (int i = 0; i < otherHistogram.countsArrayLength; i++) {
                long otherCount = otherHistogram.getCountAtIndex(i);
                addToCountAtIndex(i, otherCount);
                observedOtherTotalCount += otherCount;
            }
            setTotalCount(getTotalCount() + observedOtherTotalCount);
            setMaxValue(Math.max(this.maxValue, otherHistogram.maxValue));
        } else {
            // Arrays are not a direct match, so we can't just stream through and add them.
            // Instead, go through the array and add each non-zero value found at it's proper value:
            for (int i = 0; i < otherHistogram.countsArrayLength; i++) {
                long count = otherHistogram.getCountAtIndex(i);
                if (count > 0) {
                    recordValueWithCount(otherHistogram.valueFromIndex(i), count);
                }
            }
        }
    }

    /**
     * Subtract the contents of another histogram from this one.
     *
     * @param otherHistogram The other histogram.
     * @throws ArrayIndexOutOfBoundsException (may throw) if values in otherHistogram's are higher than highestTrackableValue.
     *
     */
    public void subtract(final AbstractHistogram otherHistogram)
            throws ArrayIndexOutOfBoundsException, IllegalArgumentException {
        if (this.highestTrackableValue < otherHistogram.highestTrackableValue) {
            throw new ArrayIndexOutOfBoundsException("The other histogram covers a wider range than this one.");
        }
        if ((bucketCount == otherHistogram.bucketCount) &&
                (subBucketCount == otherHistogram.subBucketCount) &&
                (unitMagnitude == otherHistogram.unitMagnitude)) {
            // Counts arrays are of the same length and meaning, so we can just iterate and add directly:
            for (int i = 0; i < otherHistogram.countsArrayLength; i++) {
                long otherCount = otherHistogram.getCountAtIndex(i);
                if (getCountAtIndex(i) < otherCount) {
                    throw new IllegalArgumentException("otherHistogram count (" + otherCount + ") at value " +
                            valueFromIndex(i) + " is larger than this one's (" + getCountAtIndex(i) + ")");
                }
                addToCountAtIndex(i, -otherCount);
            }
            setTotalCount(getTotalCount() + otherHistogram.getTotalCount());
            setMaxValue(Math.max(this.maxValue, otherHistogram.maxValue));
        } else {
            // Arrays are not a direct match, so we can't just stream through and add them.
            // Instead, go through the array and add each non-zero value found at it's proper value:
            for (int i = 0; i < otherHistogram.countsArrayLength; i++) {
                long otherCount = otherHistogram.getCountAtIndex(i);
                long otherValue = otherHistogram.valueFromIndex(i);
                if (getCountAtValue(otherValue) < otherCount) {
                    throw new IllegalArgumentException("otherHistogram count (" + otherCount + ") at value " +
                            otherValue + " is larger than this one's (" + getCountAtValue(otherValue) + ")");
                }
                if (otherCount > 0) {
                    recordValueWithCount(otherHistogram.valueFromIndex(i), -otherCount);
                }
            }
        }
    }

    /**
     * Add the contents of another histogram to this one, while correcting the incoming data for coordinated omission.
     * <p>
     * To compensate for the loss of sampled values when a recorded value is larger than the expected
     * interval between value samples, the values added will include an auto-generated additional series of
     * decreasingly-smaller (down to the expectedIntervalBetweenValueSamples) value records for each count found
     * in the current histogram that is larger than the expectedIntervalBetweenValueSamples.
     *
     * Note: This is a post-recording correction method, as opposed to the at-recording correction method provided
     * by {@link #recordValueWithExpectedInterval(long, long) recordValueWithExpectedInterval}. The two
     * methods are mutually exclusive, and only one of the two should be be used on a given data set to correct
     * for the same coordinated omission issue.
     * by
     * <p>
     * See notes in the description of the Histogram calls for an illustration of why this corrective behavior is
     * important.
     *
     * @param otherHistogram The other histogram. highestTrackableValue and largestValueWithSingleUnitResolution must match.
     * @param expectedIntervalBetweenValueSamples If expectedIntervalBetweenValueSamples is larger than 0, add
     *                                           auto-generated value records as appropriate if value is larger
     *                                           than expectedIntervalBetweenValueSamples
     * @throws ArrayIndexOutOfBoundsException (may throw) if values exceed highestTrackableValue
     */
    public void addWhileCorrectingForCoordinatedOmission(final AbstractHistogram otherHistogram,
                                                         final long expectedIntervalBetweenValueSamples) {
        final AbstractHistogram toHistogram = this;

        for (HistogramIterationValue v : otherHistogram.recordedValues()) {
            toHistogram.recordValueWithCountAndExpectedInterval(v.getValueIteratedTo(),
                    v.getCountAtValueIteratedTo(), expectedIntervalBetweenValueSamples);
        }
    }

    //
    //
    //
    // Shifting and scaling support:
    //
    //
    //

    /**
     * Shift the recorded values in the histogram "to the left" by a given number of binary orders
     * of magnitude, and scale the lowestDiscernibleValue and highestTrackableValue values accodingly.
     * This operation is equivalent to performing a shift left on the magnitude of all values recorded
     * so far in the histogram. The count attibuted to each value X will become attributed to value
     * X << numberOfBinaryOrdersOfMagnitude).
     *
     * @param numberOfBinaryOrdersOfMagnitude
     */
    void shiftLeftAndScaleLimits(final int numberOfBinaryOrdersOfMagnitude) {
        // Change magnitude while keeping all data in the same place:
        unitMagnitude += numberOfBinaryOrdersOfMagnitude;
        lowestDiscernibleValue <<= numberOfBinaryOrdersOfMagnitude;
        highestTrackableValue <<= numberOfBinaryOrdersOfMagnitude;
        maxValue <<= numberOfBinaryOrdersOfMagnitude;
    }

    /**
     * Shift the recorded values in the histogram "to the right" by a given number of binary orders
     * of magnitude, and scale the lowestDiscernibleValue and highestTrackableValue values accodingly.
     * This operation is equivalent to performing a shift right on the magnitude of all values recorded
     * so far in the histogram. The count attibuted to each value X will become attributed to value
     * X >> numberOfBinaryOrdersOfMagnitude).
     *
     * If the new value limits cannot be tracked (lowestDiscernibleValue would drop below 1) as a
     * result of this shift operation, an ArrayIndexOutOfBoundsException exception will be thrown and
     * the shift will not occur.
     *
     * @param numberOfBinaryOrdersOfMagnitude
     * @throws ArrayIndexOutOfBoundsException if new limits cannot be tracked
     * (lowestDiscernibleValue would drop below 1)
     */
    public void shiftRightAndScaleLimits(final int numberOfBinaryOrdersOfMagnitude)
            throws ArrayIndexOutOfBoundsException {
        if (unitMagnitude <= numberOfBinaryOrdersOfMagnitude) {
            throw new ArrayIndexOutOfBoundsException(
                    "Operation would undeflow, cannot scale lowestDiscernibleValue below 1");
        }
        // Change magnitude while keeping all data in the same place:
        unitMagnitude -= numberOfBinaryOrdersOfMagnitude;
        lowestDiscernibleValue >>= numberOfBinaryOrdersOfMagnitude;
        highestTrackableValue >>= numberOfBinaryOrdersOfMagnitude;
        maxValue >>= numberOfBinaryOrdersOfMagnitude;
    }

    /**
     * Shift the recorded values in the histogram "to the left" by a given number of binary orders
     * of magnitude, keeping lowestDiscernibleValue and highestTrackableValue values at their same level.
     * This operation is equivalent to performing a shift left on the magnitude of all values recorded so
     * far in the histogram. The count attibuted to each value X will become attributed to value
     * X << numberOfBinaryOrdersOfMagnitude).
     *
     * If any non-zero counts exist on the top-most range of the histogram that will be discarded as
     * result of this shift, an ArrayIndexOutOfBoundsException exception will be thrown and the shift
     * will not occur.
     *
     * @param numberOfBinaryOrdersOfMagnitude number of "bits" to shift values to the left by
     * @throws ArrayIndexOutOfBoundsException if non-zero counts would be discarded by this operation.
     */
    public void shiftLeft(final int numberOfBinaryOrdersOfMagnitude) throws ArrayIndexOutOfBoundsException {
        final int shiftAmount = subBucketHalfCount * numberOfBinaryOrdersOfMagnitude;

        if (getTotalCount() == 0) {
            return;
        }

        // Detect overflow:
        boolean overflowDetected = false;
        if (shiftAmount > countsArrayLength) {
            overflowDetected = true;
        } else {
            // Scan discarded range for non-zeros:
            // [going backwards to get the prefetchers working on the next loop...]
            for (int i = countsArrayLength - 1; i >= countsArrayLength - shiftAmount; i--) {
                if (getCountAtIndex(i) != 0) {
                    overflowDetected = true;
                }
            }
        }
        if (overflowDetected) {
            throw new ArrayIndexOutOfBoundsException(
                    "Operation would overflow, would discard recorded value counts");
        }

        // Shift from buckets [1...] by moving contents of upper halves (they don't have lower halves):
        // [careful, order matters...]
        for (int i = countsArrayLength - shiftAmount - 1; i >= subBucketCount; i--) {
            setCountAtIndex(i + shiftAmount, getCountAtIndex(i));
        }

        // Shift from bucket 0:
        for (int magnitudes = numberOfBinaryOrdersOfMagnitude - 1; magnitudes >= 0; magnitudes--) {
            // Shift upper half by copying into upper buckets:
            int nextBucketIndexOffset = magnitudes * subBucketHalfCount;
            for (int i = subBucketCount - 1; i >= subBucketHalfCount; i--) {
                setCountAtIndex(i + subBucketHalfCount + nextBucketIndexOffset, getCountAtIndex(i));
            }

            // Shift lower half, by expanding and filling gaps with zeros:
            for (int fromIndex = subBucketHalfCount - 1; fromIndex >= 0; fromIndex--) {
                int toIndex = fromIndex << 1;
                setCountAtIndex(toIndex + 1, 0);
                setCountAtIndex(toIndex, getCountAtIndex(fromIndex));
            }
        }

        maxValue <<= numberOfBinaryOrdersOfMagnitude;
    }

    /**
     * Shift the recorded values in the histogram "to the right" by a given number of binary orders
     * of magnitude, keeping lowestDiscernibleValue and highestTrackableValue values at their same level.
     * This operation is equivalent to performing a shift right on the magnitude of all values recorded
     * so far in the histogram. The count attibuted to each value X will become attributed to value
     * X >> numberOfBinaryOrdersOfMagnitude).
     *
     * @param numberOfBinaryOrdersOfMagnitude number of "bits" to shift values to the right by
     */
    public void shiftRight(final int numberOfBinaryOrdersOfMagnitude) {
        shiftRight(numberOfBinaryOrdersOfMagnitude, false);
    }

    /**
     * Shift the recorded values in the histogram "to the right" by a given number of binary orders
     * of magnitude, keeping lowestDiscernibleValue and highestTrackableValue values at their same level.
     * This operation is equivalent to performing a shift right on the magnitude of all values recorded
     * so far in the histogram. The count attibuted to each value X will become attributed to value
     * X >> numberOfBinaryOrdersOfMagnitude).
     *
     * If, as result of this shift, any non-zero counts exist on the lower-most range of the histogram
     * that will shifted into ranges that (e.g. if shifted back to the left) may not retain the accuracy
     * required by the histogram's settings, an ArrayIndexOutOfBoundsException exception will be thrown
     * and the shift will not occur.
     *
     * @param numberOfBinaryOrdersOfMagnitude number of "bits" to shift values to the right by
     */
    public void shiftRightWithUndeflowProtection(final int numberOfBinaryOrdersOfMagnitude) {
        shiftRight(numberOfBinaryOrdersOfMagnitude, true);
    }


    private void shiftRight(final int numberOfBinaryOrdersOfMagnitude, final boolean protectFromUndeflow) {
        final int shiftAmount = subBucketHalfCount * numberOfBinaryOrdersOfMagnitude;

        if (getTotalCount() == 0) {
            return;
        }

        if (protectFromUndeflow) {
            // Detect underflow:
            boolean underflowDetected = false;
            if (shiftAmount > countsArrayLength) {
                underflowDetected = true;
            } else {
                // There will be no shifts allowed into the lower half of bucket 0:
                for (int i = subBucketHalfCount; i < subBucketHalfCount + shiftAmount; i++) {
                    if (getCountAtIndex(i) != 0) {
                        underflowDetected = true;
                    }
                }
            }
            if (underflowDetected) {
                throw new ArrayIndexOutOfBoundsException(
                        "Operation would undeflow, losing accuracy on already recorded value counts");
            }
        } else {
            // Shift into bucket 0 by compacting & copying into it:
            for (int magnitudes = 0; magnitudes < numberOfBinaryOrdersOfMagnitude; magnitudes++) {
                // Shift into lower half of bucket 0 by compacting adjacent sub-bucket pairs into one sub-bucket:
                for (int i = 0; i < subBucketHalfCount; i++) {
                    int toIndex = i;
                    int fromIndex = toIndex << 1;
                    setCountAtIndex(toIndex, getCountAtIndex(fromIndex) + getCountAtIndex(fromIndex + 1));
                }

                // Shift into upper half of bucket 0 by copying from next bucket:
                int nextBucketIndexOffset = magnitudes * subBucketHalfCount;
                if (subBucketCount + nextBucketIndexOffset < countsArrayLength) {
                    // The source order of magnitude is inside the counts array
                    for (int i = subBucketHalfCount; i < subBucketCount; i++) {
                        setCountAtIndex(i, getCountAtIndex(i + subBucketHalfCount + nextBucketIndexOffset));
                    }
                } else {
                    // The source order of magnitude is outside of the counts array, use 0s:
                    for (int i = subBucketHalfCount; i < subBucketCount; i++) {
                        setCountAtIndex(i, 0);
                    }
                }
            }
        }

        if (shiftAmount > countsArrayLength - subBucketCount) {
            // Deal with shifts larger than the count array: just fill with 0s and get out:
            for (int i = subBucketHalfCount; i < countsArrayLength; i++) {
                setCountAtIndex(i, 0);
            }
        } else {
            // Shift into buckets [1...] by copying contents their upper halves (they don't have lower halves):
            for (int i = subBucketCount; i < countsArrayLength - shiftAmount; i++) {
                setCountAtIndex(i, getCountAtIndex(i + shiftAmount));
            }

            // Fill newly expanded upper range with zeros:
            for (int i = countsArrayLength - shiftAmount; i < countsArrayLength; i++) {
                setCountAtIndex(i, 0);
            }
        }
        maxValue >>= numberOfBinaryOrdersOfMagnitude;
    }

    //
    //
    //
    // Comparison support:
    //
    //
    //

    /**
     * Determine if this histogram is equivalent to another.
     *
     * @param other the other histogram to compare to
     * @return True if this histogram are equivalent with the other.
     */
    public boolean equals(final Object other){
        if ( this == other ) {
            return true;
        }
        if ( !(other instanceof AbstractHistogram) ) {
            return false;
        }
        AbstractHistogram that = (AbstractHistogram)other;
        if ((lowestDiscernibleValue != that.lowestDiscernibleValue) ||
                (highestTrackableValue != that.highestTrackableValue) ||
                (numberOfSignificantValueDigits != that.numberOfSignificantValueDigits) ||
                (integerToDoubleValueConversionRatio != that.integerToDoubleValueConversionRatio)) {
            return false;
        }
        if (countsArrayLength != that.countsArrayLength) {
            return false;
        }
        if (getTotalCount() != that.getTotalCount()) {
            return false;
        }
        for (int i = 0; i < countsArrayLength; i++) {
            if (getCountAtIndex(i) != that.getCountAtIndex(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deprecated: All {@link HistogramData} has been replaced with direct method calls
     * in {@link AbstractHistogram}
     *
     * Provide access to the histogram's data set.
     * @return a {@link HistogramData} that can be used to query stats and iterate through the default (corrected)
     * data set.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public HistogramData getHistogramData() {
        return histogramData;
    }

    //
    //
    //
    // Histogram structure querying support:
    //
    //
    //

    /**
     * get the configured lowestDiscernibleValue
     * @return lowestDiscernibleValue
     */
    public long getLowestDiscernibleValue() {
        return lowestDiscernibleValue;
    }

    /**
     * get the configured highestTrackableValue
     * @return highestTrackableValue
     */
    public long getHighestTrackableValue() {
        return highestTrackableValue;
    }

    /**
     * get the configured numberOfSignificantValueDigits
     * @return numberOfSignificantValueDigits
     */
    public int getNumberOfSignificantValueDigits() {
        return numberOfSignificantValueDigits;
    }

    /**
     * Get the size (in value units) of the range of values that are equivalent to the given value within the
     * histogram's resolution. Where "equivalent" means that value samples recorded for any two
     * equivalent values are counted in a common total count.
     *
     * @param value The given value
     * @return The lowest value that is equivalent to the given value within the histogram's resolution.
     */
    public long sizeOfEquivalentValueRange(final long value) {
        final int bucketIndex = getBucketIndex(value);
        final int subBucketIndex = getSubBucketIndex(value, bucketIndex);
        long distanceToNextValue =
                (1 << ( unitMagnitude + ((subBucketIndex >= subBucketCount) ? (bucketIndex + 1) : bucketIndex)));
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
    public long lowestEquivalentValue(final long value) {
        final int bucketIndex = getBucketIndex(value);
        final int subBucketIndex = getSubBucketIndex(value, bucketIndex);
        long thisValueBaseLevel = valueFromIndex(bucketIndex, subBucketIndex);
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
    public long highestEquivalentValue(final long value) {
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
    public long medianEquivalentValue(final long value) {
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
    public long nextNonEquivalentValue(final long value) {
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
    public boolean valuesAreEquivalent(final long value1, final long value2) {
        return (lowestEquivalentValue(value1) == lowestEquivalentValue(value2));
    }

    /**
     * Provide a (conservatively high) estimate of the Histogram's total footprint in bytes
     *
     * @return a (conservatively high) estimate of the Histogram's total footprint in bytes
     */
    public int getEstimatedFootprintInBytes() {
        return _getEstimatedFootprintInBytes();
    }

    //
    //
    //
    // Timestamp support:
    //
    //
    //

    /**
     * get the start time stamp [optionally] stored with this histogram
     * @return the start time stamp [optionally] stored with this histogram
     */
    public long getStartTimeStamp() {
        return startTimeStampMsec;
    }

    /**
     * Set the start time stamp value associated with this histogram to a given value.
     * @param timeStampMsec the value to set the time stamp to, [by convention] in msec since the epoch.
     */
    public void setStartTimeStamp(final long timeStampMsec) {
        this.startTimeStampMsec = timeStampMsec;
    }

    /**
     * get the end time stamp [optionally] stored with this histogram
     * @return the end time stamp [optionally] stored with this histogram
     */
    public long getEndTimeStamp() {
        return endTimeStampMsec;
    }

    /**
     * Set the end time stamp value associated with this histogram to a given value.
     * @param timeStampMsec the value to set the time stamp to, [by convention] in msec since the epoch.
     */
    public void setEndTimeStamp(final long timeStampMsec) {
        this.endTimeStampMsec = timeStampMsec;
    }

    //
    //
    //
    // Histogram Data access support:
    //
    //
    //

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
        return lowestEquivalentValue(min);
    }

    /**
     * Get the highest recorded value level in the histogram
     *
     * @return the Max value recorded in the histogram
     */
    public long getMaxValue() {
        return (maxValue == 0) ? 0 : highestEquivalentValue(maxValue);
    }

    /**
     * Get the highest recorded value level in the histogram as a double
     *
     * @return the Max value recorded in the histogram
     */
    public double getMaxValueAsDouble() {
        return getMaxValue();
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
        final double mean =  getMean();
        double geometric_deviation_total = 0.0;
        recordedValuesIterator.reset();
        while (recordedValuesIterator.hasNext()) {
            HistogramIterationValue iterationValue = recordedValuesIterator.next();
            Double deviation = (medianEquivalentValue(iterationValue.getValueIteratedTo()) * 1.0) - mean;
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
        // TODO: implement binary search for percentile
        final double requestedPercentile = Math.min(percentile, 100.0); // Truncate down to 100%
        long countAtPercentile = (long)(((requestedPercentile / 100.0) * getTotalCount()) + 0.5); // round to nearest
        countAtPercentile = Math.max(countAtPercentile, 1); // Make sure we at least reach the first recorded entry
        long totalToCurrentIndex = 0;
        for (int i = 0; i < countsArrayLength; i++) {
            totalToCurrentIndex += getCountAtIndex(i);
            if (totalToCurrentIndex >= countAtPercentile) {
                long valueAtIndex = valueFromIndex(i);
                return highestEquivalentValue(valueAtIndex);
            }
        }
        return 0;
    }

    /**
     * Get the percentile at a given value
     *
     * @param value    The value for which the return the associated percentile
     * @return The percentile of values recorded at or below the given percentage in the
     * histogram all fall.
     */
    public double getPercentileAtOrBelowValue(final long value) {
        final int targetIndex = countsArrayIndex(value);
        long totalToCurrentIndex = 0;
        for (int i = 0; i <= targetIndex; i++) {
            totalToCurrentIndex += getCountAtIndex(i);
        }
        return (100.0 * totalToCurrentIndex) / getTotalCount();
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
     * {@literal >=} lowestEquivalentValue(<i>lowValue</i>) and {@literal <=} highestEquivalentValue(<i>highValue</i>)
     * @throws ArrayIndexOutOfBoundsException on values that are outside the tracked value range
     */
    public long getCountBetweenValues(final long lowValue, final long highValue) throws ArrayIndexOutOfBoundsException {
        final int lowIndex = countsArrayIndex(lowValue);
        final int highIndex = countsArrayIndex(highValue);
        long count = 0;
        for (int i = lowIndex ; i <= highIndex; i++) {
            count += getCountAtIndex(i);
        }
        return count;
    }

    /**
     * Get the count of recorded values at a specific value
     *
     * @param value The value for which to provide the recorded count
     * @return The total count of values recorded in the histogram at the given value (to within
     * the histogram resolution at the value level).
     * @throws ArrayIndexOutOfBoundsException On values that are outside the tracked value range
     */
    public long getCountAtValue(final long value) throws ArrayIndexOutOfBoundsException {
        final int bucketIndex = getBucketIndex(value);
        final int subBucketIndex = getSubBucketIndex(value, bucketIndex);
        // May throw ArrayIndexOutOfBoundsException:
        return getCountAt(bucketIndex, subBucketIndex);
    }

    /**
     * Provide a means of iterating through histogram values according to percentile levels. The iteration is
     * performed in steps that start at 0% and reduce their distance to 100% according to the
     * <i>percentileTicksPerHalfDistance</i> parameter, ultimately reaching 100% when all recorded histogram
     * values are exhausted.
     * <p>
     * @param percentileTicksPerHalfDistance The number of iteration steps per half-distance to 100%.
     * @return An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >}
     * through the histogram using a
     * {@link PercentileIterator}
     */
    public Percentiles percentiles(final int percentileTicksPerHalfDistance) {
        return new Percentiles(this, percentileTicksPerHalfDistance);
    }

    /**
     * Provide a means of iterating through histogram values using linear steps. The iteration is
     * performed in steps of <i>valueUnitsPerBucket</i> in size, terminating when all recorded histogram
     * values are exhausted.
     *
     * @param valueUnitsPerBucket  The size (in value units) of the linear buckets to use
     * @return An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >}
     * through the histogram using a
     * {@link LinearIterator}
     */
    public LinearBucketValues linearBucketValues(final long valueUnitsPerBucket) {
        return new LinearBucketValues(this, valueUnitsPerBucket);
    }

    /**
     * Provide a means of iterating through histogram values at logarithmically increasing levels. The iteration is
     * performed in steps that start at <i>valueUnitsInFirstBucket</i> and increase exponentially according to
     * <i>logBase</i>, terminating when all recorded histogram values are exhausted.
     *
     * @param valueUnitsInFirstBucket The size (in value units) of the first bucket in the iteration
     * @param logBase The multiplier by which bucket sizes will grow in eahc iteration step
     * @return An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >}
     * through the histogram using
     * a {@link LogarithmicIterator}
     */
    public LogarithmicBucketValues logarithmicBucketValues(final long valueUnitsInFirstBucket, final double logBase) {
        return new LogarithmicBucketValues(this, valueUnitsInFirstBucket, logBase);
    }

    /**
     * Provide a means of iterating through all recorded histogram values using the finest granularity steps
     * supported by the underlying representation. The iteration steps through all non-zero recorded value counts,
     * and terminates when all recorded histogram values are exhausted.
     *
     * @return An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >}
     * through the histogram using
     * a {@link RecordedValuesIterator}
     */
    public RecordedValues recordedValues() {
        return new RecordedValues(this);
    }

    /**
     * Provide a means of iterating through all histogram values using the finest granularity steps supported by
     * the underlying representation. The iteration steps through all possible unit value levels, regardless of
     * whether or not there were recorded values for that value level, and terminates when all recorded histogram
     * values are exhausted.
     *
     * @return An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >}
     * through the histogram using
     * a {@link RecordedValuesIterator}
     */
    public AllValues allValues() {
        return new AllValues(this);
    }

    // Percentile iterator support:

    /**
     * An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >} through
     * the histogram using a {@link PercentileIterator}
     */
    public class Percentiles implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;
        final int percentileTicksPerHalfDistance;

        private Percentiles(final AbstractHistogram histogram, final int percentileTicksPerHalfDistance) {
            this.histogram = histogram;
            this.percentileTicksPerHalfDistance = percentileTicksPerHalfDistance;
        }

        /**
         * @return A {@link PercentileIterator}{@literal <}{@link HistogramIterationValue}{@literal >}
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new PercentileIterator(histogram, percentileTicksPerHalfDistance);
        }
    }

    // Linear iterator support:

    /**
     * An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >} through
     * the histogram using a {@link LinearIterator}
     */
    public class LinearBucketValues implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;
        final long valueUnitsPerBucket;

        private LinearBucketValues(final AbstractHistogram histogram, final long valueUnitsPerBucket) {
            this.histogram = histogram;
            this.valueUnitsPerBucket = valueUnitsPerBucket;
        }

        /**
         * @return A {@link LinearIterator}{@literal <}{@link HistogramIterationValue}{@literal >}
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new LinearIterator(histogram, valueUnitsPerBucket);
        }
    }

    // Logarithmic iterator support:

    /**
     * An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >} through
     * the histogram using a {@link LogarithmicIterator}
     */
    public class LogarithmicBucketValues implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;
        final long valueUnitsInFirstBucket;
        final double logBase;

        private LogarithmicBucketValues(final AbstractHistogram histogram,
                                        final long valueUnitsInFirstBucket, final double logBase) {
            this.histogram = histogram;
            this.valueUnitsInFirstBucket = valueUnitsInFirstBucket;
            this.logBase = logBase;
        }

        /**
         * @return A {@link LogarithmicIterator}{@literal <}{@link HistogramIterationValue}{@literal >}
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new LogarithmicIterator(histogram, valueUnitsInFirstBucket, logBase);
        }
    }

    // Recorded value iterator support:

    /**
     * An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >} through
     * the histogram using a {@link RecordedValuesIterator}
     */
    public class RecordedValues implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;

        private RecordedValues(final AbstractHistogram histogram) {
            this.histogram = histogram;
        }

        /**
         * @return A {@link RecordedValuesIterator}{@literal <}{@link HistogramIterationValue}{@literal >}
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new RecordedValuesIterator(histogram);
        }
    }

    // AllValues iterator support:

    /**
     * An {@link java.lang.Iterable}{@literal <}{@link HistogramIterationValue}{@literal >} through
     * the histogram using a {@link AllValuesIterator}
     */
    public class AllValues implements Iterable<HistogramIterationValue> {
        final AbstractHistogram histogram;

        private AllValues(final AbstractHistogram histogram) {
            this.histogram = histogram;
        }

        /**
         * @return A {@link AllValuesIterator}{@literal <}{@link HistogramIterationValue}{@literal >}
         */
        public Iterator<HistogramIterationValue> iterator() {
            return new AllValuesIterator(histogram);
        }
    }


    /**
     * Produce textual representation of the value distribution of histogram data by percentile. The distribution is
     * output with exponentially increasing resolution, with each exponentially decreasing half-distance containing
     * five (5) percentile reporting tick points.
     *
     * @param printStream    Stream into which the distribution will be output
     * <p>
     * @param outputValueUnitScalingRatio    The scaling factor by which to divide histogram recorded values units in
     *                                     output
     */
    public void outputPercentileDistribution(final PrintStream printStream,
                                             final Double outputValueUnitScalingRatio) {
        outputPercentileDistribution(printStream, 5, outputValueUnitScalingRatio);
    }

    //
    //
    //
    // Textual percentile output support:
    //
    //
    //

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
        outputPercentileDistribution(printStream, percentileTicksPerHalfDistance, outputValueUnitScalingRatio, false);
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
    public void outputPercentileDistribution(final PrintStream printStream,
                                             final int percentileTicksPerHalfDistance,
                                             final Double outputValueUnitScalingRatio,
                                             final boolean useCsvFormat) {

        if (useCsvFormat) {
            printStream.format("\"Value\",\"Percentile\",\"TotalCount\",\"1/(1-Percentile)\"\n");
        } else {
            printStream.format("%12s %14s %10s %14s\n\n", "Value", "Percentile", "TotalCount", "1/(1-Percentile)");
        }

        PercentileIterator iterator = percentileIterator;
        iterator.reset(percentileTicksPerHalfDistance);

        String percentileFormatString;
        String lastLinePercentileFormatString;
        if (useCsvFormat) {
            percentileFormatString = "%." + numberOfSignificantValueDigits + "f,%.12f,%d,%.2f\n";
            lastLinePercentileFormatString = "%." + numberOfSignificantValueDigits + "f,%.12f,%d,Infinity\n";
        } else {
            percentileFormatString = "%12." + numberOfSignificantValueDigits + "f %2.12f %10d %14.2f\n";
            lastLinePercentileFormatString = "%12." + numberOfSignificantValueDigits + "f %2.12f %10d\n";
        }

        try {
            while (iterator.hasNext()) {
                HistogramIterationValue iterationValue = iterator.next();
                if (iterationValue.getPercentileLevelIteratedTo() != 100.0D) {
                    printStream.format(Locale.US, percentileFormatString,
                            iterationValue.getValueIteratedTo() / outputValueUnitScalingRatio,
                            iterationValue.getPercentileLevelIteratedTo()/100.0D,
                            iterationValue.getTotalCountToThisValue(),
                            1/(1.0D - (iterationValue.getPercentileLevelIteratedTo()/100.0D)) );
                } else {
                    printStream.format(Locale.US, lastLinePercentileFormatString,
                            iterationValue.getValueIteratedTo() / outputValueUnitScalingRatio,
                            iterationValue.getPercentileLevelIteratedTo()/100.0D,
                            iterationValue.getTotalCountToThisValue());
                }
            }

            if (!useCsvFormat) {
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
                        "#[Mean    = %12." + numberOfSignificantValueDigits + "f, StdDeviation   = %12." +
                                numberOfSignificantValueDigits +"f]\n",
                        mean, std_deviation);
                printStream.format(Locale.US,
                        "#[Max     = %12." + numberOfSignificantValueDigits + "f, Total count    = %12d]\n",
                        getMaxValue() / outputValueUnitScalingRatio, getTotalCount());
                printStream.format(Locale.US, "#[Buckets = %12d, SubBuckets     = %12d]\n",
                        bucketCount, subBucketCount);
            }

        } catch (ArrayIndexOutOfBoundsException e) {
            // Overflow conditions on histograms can lead to ArrayIndexOutOfBoundsException on iterations:
            if (hasOverflowed()) {
                printStream.format(Locale.US, "# Histogram counts indicate OVERFLOW values");
            } else {
                // Re-throw if reason is not a known overflow:
                throw e;
            }
        }
    }

    //
    //
    //
    // Serialization support:
    //
    //
    //

    private static final long serialVersionUID = 44L;

    private void writeObject(final ObjectOutputStream o)
            throws IOException
    {
        o.writeLong(lowestDiscernibleValue);
        o.writeLong(highestTrackableValue);
        o.writeInt(numberOfSignificantValueDigits);
        o.writeDouble(integerToDoubleValueConversionRatio);
        o.writeLong(getTotalCount()); // Needed because overflow situations may lead this to differ from counts totals
        // Max Value is added to the serialized form because establishing max via scanning is "harder" during
        // deserialization, as the counts array is not available at the subclass desrializing level, and we don't
        // really want to have each subclass establish max on it's own...
        o.writeLong(getMaxValue());
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        final long lowestDiscernibleValue = o.readLong();
        final long highestTrackableValue = o.readLong();
        final int numberOfSignificantValueDigits = o.readInt();
        final double integerToDoubleValueConversionRatio = o.readDouble();
        final long totalCount = o.readLong();
        final long maxValue = o.readLong();
        init(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits,
                integerToDoubleValueConversionRatio, totalCount, maxValue);
    }

    //
    //
    //
    // Encoding/Decoding support:
    //
    //
    //

    /**
     * Get the capacity needed to encode this histogram into a ByteBuffer
     * @return the capacity needed to encode this histogram into a ByteBuffer
     */
    public int getNeededByteBufferCapacity() {
        return getNeededByteBufferCapacity(countsArrayLength);
    }

    int getNeededByteBufferCapacity(final int relevantLength) {
        return (relevantLength * wordSizeInBytes) + 32;
    }

    abstract void fillCountsArrayFromBuffer(ByteBuffer buffer, int length);

    abstract void fillBufferFromCountsArray(ByteBuffer buffer, int length);

    private static final int encodingCookieBase = 0x1c849308;
    private static final int compressedEncodingCookieBase = 0x1c849309;

    private int getEncodingCookie() {
        return encodingCookieBase + (wordSizeInBytes << 4);
    }

    private int getCompressedEncodingCookie() {
        return compressedEncodingCookieBase + (wordSizeInBytes << 4);
    }

    private static int getCookieBase(final int cookie) {
        return (cookie & ~0xf0);
    }

    private static int getWordSizeInBytesFromCookie(final int cookie) {
        return (cookie & 0xf0) >> 4;
    }

    /**
     * Encode this histogram into a ByteBuffer
     * @param buffer The buffer to encode into
     * @return The number of bytes written to the buffer
     */
    synchronized public int encodeIntoByteBuffer(final ByteBuffer buffer) {
        final long maxValue = getMaxValue();
        final int relevantLength = getLengthForNumberOfBuckets(getBucketsNeededToCoverValue(maxValue));
        if (buffer.capacity() < getNeededByteBufferCapacity(relevantLength)) {
            throw new ArrayIndexOutOfBoundsException("buffer does not have capacity for" +
                    getNeededByteBufferCapacity(relevantLength) + " bytes");
        }
        buffer.putInt(getEncodingCookie());
        buffer.putInt(numberOfSignificantValueDigits);
        buffer.putLong(lowestDiscernibleValue);
        buffer.putLong(highestTrackableValue);
        buffer.putLong(getTotalCount()); // Needed because overflow situations may make this differ from counts totals

        fillBufferFromCountsArray(buffer, relevantLength);

        return getNeededByteBufferCapacity(relevantLength);
    }

    /**
     * Encode this histogram in compressed form into a byte array
     * @param targetBuffer The buffer to encode into
     * @param compressionLevel Compression level (for java.util.zip.Deflater).
     * @return The number of bytes written to the buffer
     */
    synchronized public int encodeIntoCompressedByteBuffer(final ByteBuffer targetBuffer,
                                                           final int compressionLevel) {
        if (intermediateUncompressedByteBuffer == null) {
            intermediateUncompressedByteBuffer = ByteBuffer.allocate(getNeededByteBufferCapacity(countsArrayLength));
        }
        intermediateUncompressedByteBuffer.clear();
        final int uncompressedLength = encodeIntoByteBuffer(intermediateUncompressedByteBuffer);

        targetBuffer.putInt(getCompressedEncodingCookie());
        targetBuffer.putInt(0); // Placeholder for compressed contents length
        Deflater compressor = new Deflater(compressionLevel);
        compressor.setInput(intermediateUncompressedByteBuffer.array(), 0, uncompressedLength);
        compressor.finish();
        byte[] targetArray = targetBuffer.array();
        int compressedDataLength = compressor.deflate(targetArray, 8, targetArray.length - 8);
        compressor.end();

        targetBuffer.putInt(4, compressedDataLength); // Record the compressed length
        return compressedDataLength + 8;
    }

    /**
     * Encode this histogram in compressed form into a byte array
     * @param targetBuffer The buffer to encode into
     * @return The number of bytes written to the array
     */
    public int encodeIntoCompressedByteBuffer(final ByteBuffer targetBuffer) {
        return encodeIntoCompressedByteBuffer(targetBuffer, Deflater.DEFAULT_COMPRESSION);
    }

    private static final Class[] constructorArgsTypes = {Long.TYPE, Long.TYPE, Integer.TYPE};

    void parseBufferHeader(final ByteBuffer buffer, final long minBarForHighestTrackableValue) {
        final int cookie = buffer.getInt();
        if (getCookieBase(cookie) != getCookieBase(getEncodingCookie())) {
            throw new IllegalArgumentException("The buffer does not contain a Histogram");
        }

        if (cookie != getEncodingCookie()) {
            throw new IllegalArgumentException(
                    "The buffer's encoded value byte size (" +
                            getWordSizeInBytesFromCookie(cookie) +
                            ") does not match the Histogram's (" +
                            wordSizeInBytes + ")");
        }

        final int numberOfSignificantValueDigits = buffer.getInt();
        final long lowestDiscernibleValue = buffer.getLong();
        long highestTrackableValue = buffer.getLong();
        final long totalCount = buffer.getLong();

        highestTrackableValue = Math.max(highestTrackableValue, minBarForHighestTrackableValue);

        if (getNumberOfSignificantValueDigits() != numberOfSignificantValueDigits) {
            throw new IllegalArgumentException("The encoded histogram's numberOfSignificantValueDigits (" +
                    numberOfSignificantValueDigits + ") != the target histogram's (" +
                    getNumberOfSignificantValueDigits() + ")");
        }
        if (getLowestDiscernibleValue() != lowestDiscernibleValue) {
            throw new IllegalArgumentException("The encoded histogram's lowestDiscernibleValue (" +
                    lowestDiscernibleValue + ") != the target histogram's (" +
                    getLowestDiscernibleValue() + ")");
        }
        if (getHighestTrackableValue() <= highestTrackableValue) {
            throw new IllegalArgumentException("The encoded histogram's highestTrackableValue (" +
                    lowestDiscernibleValue + ") does not fit in the target histogram's (" +
                    getHighestTrackableValue() + ")");
        }

        setTotalCount(totalCount); // Restore totalCount
    }

    static AbstractHistogram constructHistogramFromBufferHeader(final ByteBuffer buffer,
                                                                final Class histogramClass,
                                                                long minBarForHighestTrackableValue) {
        final int cookie = buffer.getInt();
        if (getCookieBase(cookie) != encodingCookieBase) {
            throw new IllegalArgumentException("The buffer does not contain a Histogram");
        }

        final int numberOfSignificantValueDigits = buffer.getInt();
        final long lowestTrackableUnitValue = buffer.getLong();
        long highestTrackableValue = buffer.getLong();
        final long totalCount = buffer.getLong();

        highestTrackableValue = Math.max(highestTrackableValue, minBarForHighestTrackableValue);

        try {
            @SuppressWarnings("unchecked")
            Constructor<AbstractHistogram> constructor = histogramClass.getConstructor(constructorArgsTypes);
            AbstractHistogram histogram =
                    constructor.newInstance(lowestTrackableUnitValue, highestTrackableValue,
                            numberOfSignificantValueDigits);
            histogram.setTotalCount(totalCount); // Restore totalCount
            if (cookie != histogram.getEncodingCookie()) {
                throw new IllegalArgumentException(
                        "The buffer's encoded value byte size (" +
                                getWordSizeInBytesFromCookie(cookie) +
                                ") does not match the Histogram's (" +
                                histogram.wordSizeInBytes + ")");
            }
            return histogram;
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException(ex);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException(ex);
        } catch (InstantiationException ex) {
            throw new IllegalArgumentException(ex);
        } catch (InvocationTargetException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    static AbstractHistogram decodeFromByteBuffer(final ByteBuffer buffer,
                                                  final Class histogramClass,
                                                  final long minBarForHighestTrackableValue) {
        AbstractHistogram histogram = constructHistogramFromBufferHeader(buffer, histogramClass,
                minBarForHighestTrackableValue);

        final int expectedCapacity = histogram.getNeededByteBufferCapacity(histogram.countsArrayLength);
        if (expectedCapacity > buffer.capacity()) {
            throw new IllegalArgumentException("The buffer does not contain the full Histogram");
        }

        histogram.fillCountsArrayFromBuffer(buffer, histogram.countsArrayLength);

        histogram.establishMaxValue();

        return histogram;
    }

    static AbstractHistogram decodeFromCompressedByteBuffer(final ByteBuffer buffer,
                                                            final Class histogramClass,
                                                            final long minBarForHighestTrackableValue)
            throws DataFormatException {
        final int cookie = buffer.getInt();
        return decodeFromCompressedByteBuffer(cookie, buffer, histogramClass, minBarForHighestTrackableValue);
    }

    static AbstractHistogram decodeFromCompressedByteBuffer(final int cookie,
                                                            final ByteBuffer buffer,
                                                            final Class histogramClass,
                                                            final long minBarForHighestTrackableValue)
            throws DataFormatException {
        if (getCookieBase(cookie) != compressedEncodingCookieBase) {
            throw new IllegalArgumentException("The buffer does not contain a compressed Histogram");
        }
        final int lengthOfCompressedContents = buffer.getInt();
        final Inflater decompressor = new Inflater();
        decompressor.setInput(buffer.array(), 8, lengthOfCompressedContents);

        final ByteBuffer headerBuffer = ByteBuffer.allocate(32);
        decompressor.inflate(headerBuffer.array());
        AbstractHistogram histogram = constructHistogramFromBufferHeader(headerBuffer, histogramClass,
                minBarForHighestTrackableValue);
        ByteBuffer countsBuffer = ByteBuffer.allocate(
                histogram.getNeededByteBufferCapacity(histogram.countsArrayLength) - 32);
        decompressor.inflate(countsBuffer.array());

        histogram.fillCountsArrayFromBuffer(countsBuffer, histogram.countsArrayLength);

        histogram.establishMaxValue();

        return histogram;
    }

    //
    //
    //
    // Support for overflow detection and re-establishing a proper totalCount:
    //
    //
    //

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
     * Reestablish the internal notion of totalCount by recalculating it from recorded values.
     *
     * Implementations of AbstractHistogram may maintain a separately tracked notion of totalCount,
     * which is useful for concurrent modification tracking, overflow detection, and speed of execution
     * in iteration. This separately tracked totalCount can get into a state that is inconsistent with
     * the currently recorded value counts under various concurrent modification and overflow conditions.
     *
     * Applying this method will override internal indications of potential overflows and concurrent
     * modification, and will reestablish a self-consistent representation of the histogram data
     * based purely on the current internal representation of recorded counts.
     * <p>
     * In cases of concurrent modifications such as during copying, or due to racy multi-threaded
     * updates on non-atomic or non-synchronized variants, which can result in potential loss
     * of counts and an inconsistent (indicating potential overflow) internal state, calling this
     * method on a histogram will reestablish a consistent internal state based on the potentially
     * lossy counts representations.
     * <p>
     * Note that this method is not synchronized against concurrent modification in any way,
     * and will only reliably reestablish consistent internal state when no concurrent modification
     * of the histogram is performed while it executes.
     * <p>
     * Note that in the cases of actual overflow conditions (which can result in negative counts)
     * this self consistent view may be very wrong, and not just slightly lossy.
     *
     */
    public void reestablishTotalCount() {
        // On overflow, the totalCount accumulated counter will (always) not match the total of counts
        long totalCounted = 0;
        for (int i = 0; i < countsArrayLength; i++) {
            totalCounted += getCountAtIndex(i);
        }
        setTotalCount(totalCounted);
    }

    //
    //
    //
    // Internal helper methods:
    //
    //
    //

    void establishMaxValue() {
        setMaxValue(0);
        int maxIndex = -1;
        for (int index = 0; index < countsArrayLength; index++) {
            if (getCountAtIndex(index) > 0) {
                maxIndex = index;
            }
        }
        if (maxIndex >= 0) {
            setMaxValue(highestEquivalentValue(valueFromIndex(maxIndex)));
        }
    }

    int getBucketsNeededToCoverValue(final long value) {
        long smallestUntrackableValue = subBucketCount << unitMagnitude;
        int bucketsNeeded = 1;
        while (smallestUntrackableValue < value) {
            smallestUntrackableValue <<= 1;
            bucketsNeeded++;
        }
        return bucketsNeeded;
    }

    int getLengthForNumberOfBuckets(final int numberOfBuckets) {
        final int lengthNeeded = (numberOfBuckets + 1) * (subBucketCount / 2);
        return lengthNeeded;
    }

    private int countsArrayIndex(final long value) {
        final int bucketIndex = getBucketIndex(value);
        final int subBucketIndex = getSubBucketIndex(value, bucketIndex);
        return countsArrayIndex(bucketIndex, subBucketIndex);
    }

    private int countsArrayIndex(final int bucketIndex, final int subBucketIndex) {
        assert(subBucketIndex < subBucketCount);
        assert(bucketIndex == 0 || (subBucketIndex >= subBucketHalfCount));
        // Calculate the index for the first entry in the bucket:
        // (The following is the equivalent of ((bucketIndex + 1) * subBucketHalfCount) ):
        final int bucketBaseIndex = (bucketIndex + 1) << subBucketHalfCountMagnitude;
        // Calculate the offset in the bucket:
        final int offsetInBucket = subBucketIndex - subBucketHalfCount;
        // The following is the equivalent of ((subBucketIndex  - subBucketHalfCount) + bucketBaseIndex;
        return bucketBaseIndex + offsetInBucket;
    }

    long getCountAt(final int bucketIndex, final int subBucketIndex) {
        return getCountAtIndex(countsArrayIndex(bucketIndex, subBucketIndex));
    }

    int getBucketIndex(final long value) {
        final int pow2ceiling = 64 - Long.numberOfLeadingZeros(value | subBucketMask); // smallest containing power of 2
        return  pow2ceiling - unitMagnitude - subBucketHalfCountMagnitude - 1;
    }

    int getSubBucketIndex(final long value, final int bucketIndex) {
        return  (int)(value >> (bucketIndex + unitMagnitude));
    }

    final long valueFromIndex(final int bucketIndex, final int subBucketIndex) {
        return ((long) subBucketIndex) << (bucketIndex + unitMagnitude);
    }

    final long valueFromIndex(final int index) {
        int bucketIndex = (index >> subBucketHalfCountMagnitude) - 1;
        int subBucketIndex = (index & (subBucketHalfCount - 1)) + subBucketHalfCount;
        if (bucketIndex < 0) {
            subBucketIndex -= subBucketHalfCount;
            bucketIndex = 0;
        }
        return valueFromIndex(bucketIndex, subBucketIndex);
    }

    static int numberOfSubbuckets(final int numberOfSignificantValueDigits) {
        final long largestValueWithSingleUnitResolution = 2 * (long) Math.pow(10, numberOfSignificantValueDigits);

        // We need to maintain power-of-two subBucketCount (for clean direct indexing) that is large enough to
        // provide unit resolution to at least largestValueWithSingleUnitResolution. So figure out
        // largestValueWithSingleUnitResolution's nearest power-of-two (rounded up), and use that:
        int subBucketCountMagnitude = (int) Math.ceil(Math.log(largestValueWithSingleUnitResolution)/Math.log(2));
        int subBucketCount = (int) Math.pow(2, subBucketCountMagnitude);
        return subBucketCount;
    }
}