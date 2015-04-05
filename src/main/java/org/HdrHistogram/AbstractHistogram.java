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
import java.nio.*;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
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

abstract class AbstractHistogramBase extends EncodableHistogram {
    static AtomicLong constructionIdentityCount = new AtomicLong(0);

    // "Cold" accessed fields. Not used in the recording code path:
    long identity;
    volatile boolean autoResize = false;

    long highestTrackableValue;
    long lowestDiscernibleValue;
    int numberOfSignificantValueDigits;

    int bucketCount;
    int subBucketCount;
    int countsArrayLength;
    int wordSizeInBytes;

    long startTimeStampMsec = Long.MAX_VALUE;
    long endTimeStampMsec = 0;

    double integerToDoubleValueConversionRatio = 1.0;

    PercentileIterator percentileIterator;
    RecordedValuesIterator recordedValuesIterator;

    ByteBuffer intermediateUncompressedByteBuffer = null;

    double getIntegerToDoubleValueConversionRatio() {
        return integerToDoubleValueConversionRatio;
    }

    void setIntegerToDoubleValueConversionRatio(double integerToDoubleValueConversionRatio) {
        this.integerToDoubleValueConversionRatio = integerToDoubleValueConversionRatio;
    }
}

/**
 * <h3>An abstract base class for integer values High Dynamic Range (HDR) Histograms</h3>
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

public abstract class AbstractHistogram extends AbstractHistogramBase implements Serializable{

    // "Hot" accessed fields (used in the the value recording code path) are bunched here, such
    // that they will have a good chance of ending up in the same cache line as the totalCounts and
    // counts array reference fields that subclass implementations will typically add.
    int leadingZeroCountBase;
    int subBucketHalfCountMagnitude;
    int unitMagnitude;
    int subBucketHalfCount;
    long subBucketMask;
    volatile long maxValue = 0;
    volatile long minNonZeroValue = Long.MAX_VALUE;

    private static final AtomicLongFieldUpdater<AbstractHistogram> maxValueUpdater =
            AtomicLongFieldUpdater.newUpdater(AbstractHistogram.class, "maxValue");
    private static final AtomicLongFieldUpdater<AbstractHistogram> minNonZeroValueUpdater =
            AtomicLongFieldUpdater.newUpdater(AbstractHistogram.class, "minNonZeroValue");

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

    abstract long getCountAtNormalizedIndex(int index);

    abstract void incrementCountAtIndex(int index);

    abstract void addToCountAtIndex(int index, long value);

    abstract void setCountAtIndex(int index, long value);

    abstract void setCountAtNormalizedIndex(int index, long value);

    abstract int getNormalizingIndexOffset();

    abstract void setNormalizingIndexOffset(int normalizingIndexOffset);

    abstract void shiftNormalizingIndexByOffset(int offsetToAdd, boolean lowestHalfBucketPopulated);

    abstract void setTotalCount(long totalCount);

    abstract void incrementTotalCount();

    abstract void addToTotalCount(long value);

    abstract void clearCounts();

    abstract int _getEstimatedFootprintInBytes();

    abstract void resize(long newHighestTrackableValue);

    /**
     * Get the total count of all recorded values in the histogram
     * @return the total count of all recorded values in the histogram
     */
    abstract public long getTotalCount();

    /**
     * Set internally tracked maxValue to new value if new value is greater than current one.
     * May be overridden by subclasses for synchronization or atomicity purposes.
     * @param value new maxValue to set
     */
    void updatedMaxValue(final long value) {
        while (value > maxValue) {
            maxValueUpdater.compareAndSet(this, maxValue, value);
        }
    }

    final void resetMaxValue(final long maxValue) {
        this.maxValue = maxValue;
    }

    /**
     * Set internally tracked minNonZeroValue to new value if new value is smaller than current one.
     * May be overridden by subclasses for synchronization or atomicity purposes.
     * @param value new minNonZeroValue to set
     */
    void updateMinNonZeroValue(final long value) {
        while (value < minNonZeroValue) {
            minNonZeroValueUpdater.compareAndSet(this, minNonZeroValue, value);
        }
    }

    void resetMinNonZeroValue(final long minNonZeroValue) {
        this.minNonZeroValue = minNonZeroValue;
    }

    //
    //
    //
    // Construction:
    //
    //
    //

    /**
     * Construct an auto-resizing histogram with a lowest discernible value of 1 and an auto-adjusting
     * highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE / 2).
     *
     * @param numberOfSignificantValueDigits The number of significant decimal digits to which the histogram will
     *                                       maintain value resolution and separation. Must be a non-negative
     *                                       integer between 0 and 5.
     */
    protected AbstractHistogram(final int numberOfSignificantValueDigits) {
        this(1, 2, numberOfSignificantValueDigits);
        autoResize = true;
    }

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
    protected AbstractHistogram(final long lowestDiscernibleValue, final long highestTrackableValue,
                             final int numberOfSignificantValueDigits) {
        // Verify argument validity
        if (lowestDiscernibleValue < 1) {
            throw new IllegalArgumentException("lowestDiscernibleValue must be >= 1");
        }
        if (highestTrackableValue < 2L * lowestDiscernibleValue) {
            throw new IllegalArgumentException("highestTrackableValue must be >= 2 * lowestDiscernibleValue");
        }
        if ((numberOfSignificantValueDigits < 0) || (numberOfSignificantValueDigits > 5)) {
            throw new IllegalArgumentException("numberOfSignificantValueDigits must be between 0 and 5");
        }
        identity = constructionIdentityCount.getAndIncrement();

        init(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits, 1.0, 0);
    }

    /**
     * Construct a histogram with the same range settings as a given source histogram,
     * duplicating the source's start/end timestamps (but NOT it's contents)
     * @param source The source histogram to duplicate
     */
    protected AbstractHistogram(final AbstractHistogram source) {
        this(source.getLowestDiscernibleValue(), source.getHighestTrackableValue(),
                source.getNumberOfSignificantValueDigits());
        this.setStartTimeStamp(source.getStartTimeStamp());
        this.setEndTimeStamp(source.getEndTimeStamp());
        this.autoResize = source.autoResize;
    }

    @SuppressWarnings("deprecation")
    private void init(final long lowestDiscernibleValue,
                      final long highestTrackableValue,
                      final int numberOfSignificantValueDigits,
                      final double integerToDoubleValueConversionRatio,
                      final int normalizingIndexOffset) {
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
        this.integerToDoubleValueConversionRatio = integerToDoubleValueConversionRatio;
        if (normalizingIndexOffset != 0) {
            setNormalizingIndexOffset(normalizingIndexOffset);
        }

        final long largestValueWithSingleUnitResolution = 2 * (long) Math.pow(10, numberOfSignificantValueDigits);

        unitMagnitude = (int) Math.floor(Math.log(lowestDiscernibleValue)/Math.log(2));

        // We need to maintain power-of-two subBucketCount (for clean direct indexing) that is large enough to
        // provide unit resolution to at least largestValueWithSingleUnitResolution. So figure out
        // largestValueWithSingleUnitResolution's nearest power-of-two (rounded up), and use that:
        int subBucketCountMagnitude = (int) Math.ceil(Math.log(largestValueWithSingleUnitResolution)/Math.log(2));
        subBucketHalfCountMagnitude = ((subBucketCountMagnitude > 1) ? subBucketCountMagnitude : 1) - 1;
        subBucketCount = (int) Math.pow(2, (subBucketHalfCountMagnitude + 1));
        subBucketHalfCount = subBucketCount / 2;
        subBucketMask = ((long)subBucketCount - 1) << unitMagnitude;


        // determine exponent range needed to support the trackable value with no overflow:
        establishSize(highestTrackableValue);

        // Establish leadingZeroCountBase, used in getBucketIndex() fast path:
        leadingZeroCountBase = 64 - unitMagnitude - subBucketHalfCountMagnitude - 1;

        percentileIterator = new PercentileIterator(this, 1);
        recordedValuesIterator = new RecordedValuesIterator(this);
    }

    final void establishSize(long newHighestTrackableValue) {
        // establish counts array length:
        countsArrayLength = determineArrayLengthNeeded(newHighestTrackableValue);
        // establish exponent range needed to support the trackable value with no overflow:
        bucketCount = getBucketsNeededToCoverValue(newHighestTrackableValue);
        // establish the new highest trackable value:
        highestTrackableValue = newHighestTrackableValue;
    }

    final int determineArrayLengthNeeded(long highestTrackableValue) {
        if (highestTrackableValue < 2L * lowestDiscernibleValue) {
            throw new IllegalArgumentException("highestTrackableValue (" + highestTrackableValue +
                    ") cannot be < (2 * lowestDiscernibleValue)");
        }
        //determine counts array length needed:
        int countsArrayLength = getLengthForNumberOfBuckets(getBucketsNeededToCoverValue(highestTrackableValue));
        return countsArrayLength;
    }

    //
    //
    // Auto-resizing control:
    //
    //

    public boolean isAutoResize() {
        return autoResize;
    }

    public void setAutoResize(boolean autoResize) {
        this.autoResize = autoResize;
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
     * by {@link #copyCorrectedForCoordinatedOmission(long)}.
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
        recordSingleValueWithExpectedInterval(value, expectedIntervalBetweenValueSamples);
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

    private void updateMinAndMax(final long value) {
        if (value > maxValue) {
            updatedMaxValue(value);
        }
        if ((value < minNonZeroValue) && (value != 0)) {
            updateMinNonZeroValue(value);
        }
    }

    private void recordCountAtValue(final long count, final long value)
            throws ArrayIndexOutOfBoundsException {
        int countsIndex = countsArrayIndex(value);
        try {
            addToCountAtIndex(countsIndex, count);
        } catch (ArrayIndexOutOfBoundsException ex) {
            handleRecordException(count, value, ex);
        } catch (IndexOutOfBoundsException ex) {
            handleRecordException(count, value, ex);
        }
        updateMinAndMax(value);
        addToTotalCount(count);
    }

    private void recordSingleValue(final long value) throws ArrayIndexOutOfBoundsException {
        int countsIndex = countsArrayIndex(value);
        try {
            incrementCountAtIndex(countsIndex);
        } catch (ArrayIndexOutOfBoundsException ex) {
            handleRecordException(1, value, ex);
        } catch (IndexOutOfBoundsException ex) {
            handleRecordException(1, value, ex);
        }
        updateMinAndMax(value);
        incrementTotalCount();
    }

    private void handleRecordException(final long count, final long value, Exception ex) {
        if (!autoResize) {
            throw new ArrayIndexOutOfBoundsException("value outside of histogram covered range. Caused by: " + ex);
        }
        resize(value);
        int countsIndex = countsArrayIndex(value);
        addToCountAtIndex(countsIndex, count);
        this.highestTrackableValue = highestEquivalentValue(valueFromIndex(countsArrayLength - 1));
    }

    private void recordValueWithCountAndExpectedInterval(final long value, final long count,
                                                         final long expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        recordCountAtValue(count, value);
        if (expectedIntervalBetweenValueSamples <= 0)
            return;
        for (long missingValue = value - expectedIntervalBetweenValueSamples;
             missingValue >= expectedIntervalBetweenValueSamples;
             missingValue -= expectedIntervalBetweenValueSamples) {
            recordCountAtValue(count, missingValue);
        }
    }

    private void recordSingleValueWithExpectedInterval(final long value,
                                                       final long expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        recordSingleValue(value);
        if (expectedIntervalBetweenValueSamples <= 0)
            return;
        for (long missingValue = value - expectedIntervalBetweenValueSamples;
             missingValue >= expectedIntervalBetweenValueSamples;
             missingValue -= expectedIntervalBetweenValueSamples) {
            recordSingleValue(missingValue);
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
        resetMaxValue(0);
        resetMinNonZeroValue(Long.MAX_VALUE);
        setNormalizingIndexOffset(0);
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
     * <p>
     * As part of adding the contents, the start/end timestamp range of this histogram will be
     * extended to include the start/end timestamp range of the other histogram.
     *
     * @param otherHistogram The other histogram.
     * @throws ArrayIndexOutOfBoundsException (may throw) if values in fromHistogram's are
     * higher than highestTrackableValue.
     */
    public void add(final AbstractHistogram otherHistogram) throws ArrayIndexOutOfBoundsException {
        long highestRecordableValue = highestEquivalentValue(valueFromIndex(countsArrayLength - 1));
        if (highestRecordableValue < otherHistogram.getMaxValue()) {
            if (!isAutoResize()) {
                throw new ArrayIndexOutOfBoundsException(
                        "The other histogram includes values that do not fit in this histogram's range.");
            }
            resize(otherHistogram.getMaxValue());
        }
        if ((bucketCount == otherHistogram.bucketCount) &&
                (subBucketCount == otherHistogram.subBucketCount) &&
                (unitMagnitude == otherHistogram.unitMagnitude) &&
                (getNormalizingIndexOffset() == otherHistogram.getNormalizingIndexOffset())) {
            // Counts arrays are of the same length and meaning, so we can just iterate and add directly:
            long observedOtherTotalCount = 0;
            for (int i = 0; i < otherHistogram.countsArrayLength; i++) {
                long otherCount = otherHistogram.getCountAtIndex(i);
                if (otherCount > 0) {
                    addToCountAtIndex(i, otherCount);
                    observedOtherTotalCount += otherCount;
                }
            }
            setTotalCount(getTotalCount() + observedOtherTotalCount);
            updatedMaxValue(Math.max(getMaxValue(), otherHistogram.getMaxValue()));
            updateMinNonZeroValue(Math.min(getMinNonZeroValue(), otherHistogram.getMinNonZeroValue()));
        } else {
            // Arrays are not a direct match, so we can't just stream through and add them.
            // Instead, go through the array and add each non-zero value found at it's proper value:
            for (int i = 0; i < otherHistogram.countsArrayLength; i++) {
                long otherCount = otherHistogram.getCountAtIndex(i);
                if (otherCount > 0) {
                    recordValueWithCount(otherHistogram.valueFromIndex(i), otherCount);
                }
            }
        }
        setStartTimeStamp(Math.min(startTimeStampMsec, otherHistogram.startTimeStampMsec));
        setEndTimeStamp(Math.max(endTimeStampMsec, otherHistogram.endTimeStampMsec));
    }

    /**
     * Subtract the contents of another histogram from this one.
     * <p>
     * The start/end timestamps of this histogram will remain unchanged.
     *
     * @param otherHistogram The other histogram.
     * @throws ArrayIndexOutOfBoundsException (may throw) if values in otherHistogram's are higher than highestTrackableValue.
     *
     */
    public void subtract(final AbstractHistogram otherHistogram)
            throws ArrayIndexOutOfBoundsException, IllegalArgumentException {
        long highestRecordableValue = valueFromIndex(countsArrayLength - 1);
        if (highestRecordableValue < otherHistogram.getMaxValue()) {
            if (!isAutoResize()) {
                throw new ArrayIndexOutOfBoundsException(
                        "The other histogram includes values that do not fit in this histogram's range.");
            }
            resize(otherHistogram.getMaxValue());
        }
        if ((bucketCount == otherHistogram.bucketCount) &&
                (subBucketCount == otherHistogram.subBucketCount) &&
                (unitMagnitude == otherHistogram.unitMagnitude) &&
                (getNormalizingIndexOffset() == otherHistogram.getNormalizingIndexOffset())) {
            // Counts arrays are of the same length and meaning, so we can just iterate and add directly:
            long observedOtherTotalCount = 0;
            for (int i = 0; i < otherHistogram.countsArrayLength; i++) {
                long otherCount = otherHistogram.getCountAtIndex(i);
                if (otherCount > 0) {
                    if (getCountAtIndex(i) < otherCount) {
                        throw new IllegalArgumentException("otherHistogram count (" + otherCount + ") at value " +
                                valueFromIndex(i) + " is larger than this one's (" + getCountAtIndex(i) + ")");
                    }
                    addToCountAtIndex(i, -otherCount);
                    observedOtherTotalCount += otherCount;
                }
            }
            setTotalCount(getTotalCount() - observedOtherTotalCount);
            updatedMaxValue(Math.max(getMaxValue(), otherHistogram.getMaxValue()));
            updateMinNonZeroValue(Math.min(getMinNonZeroValue(), otherHistogram.getMinNonZeroValue()));
        } else {
            // Arrays are not a direct match, so we can't just stream through and add them.
            // Instead, go through the array and add each non-zero value found at it's proper value:
            for (int i = 0; i < otherHistogram.countsArrayLength; i++) {
                long otherCount = otherHistogram.getCountAtIndex(i);
                if (otherCount > 0) {
                    long otherValue = otherHistogram.valueFromIndex(i);
                    if (getCountAtValue(otherValue) < otherCount) {
                        throw new IllegalArgumentException("otherHistogram count (" + otherCount + ") at value " +
                                otherValue + " is larger than this one's (" + getCountAtValue(otherValue) + ")");
                    }
                    recordValueWithCount(otherValue, -otherCount);
                }
            }
        }
        // With subtraction, the max and minNonZero values could have changed:
        if ((getCountAtValue(getMaxValue()) <= 0) || getCountAtValue(getMinNonZeroValue()) <= 0) {
            establishInternalTackingValues();
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
    // Shifting support:
    //
    //
    //

    /**
     * Shift recorded values to the left (the equivalent of a &lt;&lt; shift operation on all recorded values). The
     * configured integer value range limits and value precision setting will remain unchanged.
     *
     * An {@link ArrayIndexOutOfBoundsException} will be thrown if any recorded values may be lost
     * as a result of the attempted operation, reflecting an "overflow" conditions. Expect such an overflow
     * exception if the operation would cause the current maxValue to be scaled to a value that is outside
     * of the covered value range.
     *
     * @param numberOfBinaryOrdersOfMagnitude The number of binary orders of magnitude to shift by
     */
    public void shiftValuesLeft(final int numberOfBinaryOrdersOfMagnitude) {
        if (numberOfBinaryOrdersOfMagnitude < 0) {
            throw new IllegalArgumentException("Cannot shift by a negative number of magnitudes");
        }

        if (numberOfBinaryOrdersOfMagnitude == 0) {
            return;
        }
        if (getTotalCount() == getCountAtIndex(0)) {
            // (no need to shift any values if all recorded values are at the 0 value level:)
            return;
        }

        final int shiftAmount = numberOfBinaryOrdersOfMagnitude << subBucketHalfCountMagnitude;
        int maxValueIndex = countsArrayIndex(getMaxValue());
        // indicate overflow if maxValue is in the range being wrapped:
        if (maxValueIndex >= (countsArrayLength - shiftAmount)) {
            throw new ArrayIndexOutOfBoundsException(
                    "Operation would overflow, would discard recorded value counts");
        }

        long maxValueBeforeShift = maxValueUpdater.getAndSet(this, 0);
        long minNonZeroValueBeforeShift = minNonZeroValueUpdater.getAndSet(this, Long.MAX_VALUE);

        boolean lowestHalfBucketPopulated = (minNonZeroValueBeforeShift < subBucketHalfCount);

            // Perform the shift:
        shiftNormalizingIndexByOffset(shiftAmount, lowestHalfBucketPopulated);

        // adjust min, max:
        updateMinAndMax(maxValueBeforeShift << numberOfBinaryOrdersOfMagnitude);
        if (minNonZeroValueBeforeShift < Long.MAX_VALUE) {
            updateMinAndMax(minNonZeroValueBeforeShift << numberOfBinaryOrdersOfMagnitude);
        }
    }

    void nonConcurrentNormalizingIndexShift(int shiftAmount, boolean lowestHalfBucketPopulated) {

        // Save and clear the 0 value count:
        long zeroValueCount = getCountAtIndex(0);
        setCountAtIndex(0, 0);

        setNormalizingIndexOffset(getNormalizingIndexOffset() + shiftAmount);

        // Deal with lower half bucket if needed:
        if (lowestHalfBucketPopulated) {
            shiftLowestHalfBucketContentsLeft(shiftAmount);
        }

        // Restore the 0 value count:
        setCountAtIndex(0, zeroValueCount);
    }

    void shiftLowestHalfBucketContentsLeft(int shiftAmount) {
        final int numberOfBinaryOrdersOfMagnitude = shiftAmount >> subBucketHalfCountMagnitude;

        // The lowest half-bucket (not including the 0 value) is special: unlike all other half
        // buckets, the lowest half bucket values cannot be scaled by simply changing the
        // normalizing offset. Instead, they must be individually re-recorded at the new
        // scale, and cleared from the current one.
        //
        // We know that all half buckets "below" the current lowest one are full of 0s, because
        // we would have overflowed otherwise. So we need to shift the values in the current
        // lowest half bucket into that range (including the current lowest half bucket itself).
        // Iterating up from the lowermost non-zero "from slot" and copying values to the newly
        // scaled "to slot" (and then zeroing the "from slot"), will work in a single pass,
        // because the scale "to slot" index will always be a lower index than its or any
        // preceding non-scaled "from slot" index:
        //
        // (Note that we specifically avoid slot 0, as it is directly handled in the outer case)

        for (int fromIndex = 1; fromIndex < subBucketHalfCount; fromIndex++) {
            long toValue = valueFromIndex(fromIndex) << numberOfBinaryOrdersOfMagnitude;
            int toIndex = countsArrayIndex(toValue);
            long countAtFromIndex = getCountAtNormalizedIndex(fromIndex);
            setCountAtIndex(toIndex, countAtFromIndex);
            setCountAtNormalizedIndex(fromIndex, 0);
        }

        // Note that the above loop only creates O(N) work for histograms that have values in
        // the lowest half-bucket (excluding the 0 value). Histograms that never have values
        // there (e.g. all integer value histograms used as internal storage in DoubleHistograms)
        // will never loop, and their shifts will remain O(1).
    }

    /**
     * Shift recorded values to the right (the equivalent of a &gt;&gt; shift operation on all recorded values). The
     * configured integer value range limits and value precision setting will remain unchanged.
     * <p>
     * Shift right operations that do not underflow are reversible with a shift left operation with no loss of
     * information. An {@link ArrayIndexOutOfBoundsException} reflecting an "underflow" conditions will be thrown
     * if any recorded values may lose representation accuracy as a result of the attempted shift operation.
     * <p>
     * For a shift of a single order of magnitude, expect such an underflow exception if any recorded non-zero
     * values up to [numberOfSignificantValueDigits (rounded up to nearest power of 2) multiplied by
     * (2 ^ numberOfBinaryOrdersOfMagnitude) currently exist in the histogram.
     *
     * @param numberOfBinaryOrdersOfMagnitude The number of binary orders of magnitude to shift by
     */
    public void shiftValuesRight(final int numberOfBinaryOrdersOfMagnitude) {
        if (numberOfBinaryOrdersOfMagnitude < 0) {
            throw new IllegalArgumentException("Cannot shift by a negative number of magnitudes");
        }

        if (numberOfBinaryOrdersOfMagnitude == 0) {
            return;
        }
        if (getTotalCount() == getCountAtIndex(0)) {
            // (no need to shift any values if all recorded values are at the 0 value level:)
            return;
        }

        final int shiftAmount = subBucketHalfCount * numberOfBinaryOrdersOfMagnitude;

        // indicate underflow if minValue is in the range being shifted from:
        int minNonZeroValueIndex = countsArrayIndex(getMinNonZeroValue());
        // Any shifting into the bottom-most half bucket would represents a loss of accuracy,
        // and a non-reversible operation. Therefore any non-0 value that falls in an
        // index below (shiftAmount + subBucketHalfCount) would represent an underflow:
        if (minNonZeroValueIndex < shiftAmount + subBucketHalfCount) {
            throw new ArrayIndexOutOfBoundsException(
                    "Operation would underflow and lose precision of already recorded value counts");
        }

        // perform shift:

        long maxValueBeforeShift = maxValueUpdater.getAndSet(this, 0);
        long minNonZeroValueBeforeShift = minNonZeroValueUpdater.getAndSet(this, Long.MAX_VALUE);

        // move normalizingIndexOffset
        shiftNormalizingIndexByOffset(-shiftAmount, false);

        // adjust min, max:
        updateMinAndMax(maxValueBeforeShift >> numberOfBinaryOrdersOfMagnitude);
        if (minNonZeroValueBeforeShift < Long.MAX_VALUE) {
            updateMinAndMax(minNonZeroValueBeforeShift >> numberOfBinaryOrdersOfMagnitude);
        }
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
                (1L << ( unitMagnitude + ((subBucketIndex >= subBucketCount) ? (bucketIndex + 1) : bucketIndex)));
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
    @Override
    public long getStartTimeStamp() {
        return startTimeStampMsec;
    }

    /**
     * Set the start time stamp value associated with this histogram to a given value.
     * @param timeStampMsec the value to set the time stamp to, [by convention] in msec since the epoch.
     */
    @Override
    public void setStartTimeStamp(final long timeStampMsec) {
        this.startTimeStampMsec = timeStampMsec;
    }

    /**
     * get the end time stamp [optionally] stored with this histogram
     * @return the end time stamp [optionally] stored with this histogram
     */
    @Override
    public long getEndTimeStamp() {
        return endTimeStampMsec;
    }

    /**
     * Set the end time stamp value associated with this histogram to a given value.
     * @param timeStampMsec the value to set the time stamp to, [by convention] in msec since the epoch.
     */
    @Override
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
        if ((getCountAtIndex(0) > 0) || (getTotalCount() == 0)) {
            return 0;
        }
        return getMinNonZeroValue();
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
     * Get the lowest recorded non-zero value level in the histogram
     *
     * @return the lowest recorded non-zero value level in the histogram
     */
    public long getMinNonZeroValue() {
        return (minNonZeroValue == Long.MAX_VALUE) ?
                Long.MAX_VALUE : lowestEquivalentValue(minNonZeroValue);
    }

    /**
     * Get the highest recorded value level in the histogram as a double
     *
     * @return the Max value recorded in the histogram
     */
    @Override
    public double getMaxValueAsDouble() {
        return getMaxValue();
    }

    /**
     * Get the computed mean value of all recorded values in the histogram
     *
     * @return the mean value (in value units) of the histogram data
     */
    public double getMean() {
        if (getTotalCount() == 0) {
            return 0.0;
        }
        recordedValuesIterator.reset();
        double totalValue = 0;
        while (recordedValuesIterator.hasNext()) {
            HistogramIterationValue iterationValue = recordedValuesIterator.next();
            totalValue += medianEquivalentValue(iterationValue.getValueIteratedTo())
                     * iterationValue.getCountAtValueIteratedTo();
        }
        return (totalValue * 1.0) / getTotalCount();
    }

    /**
     * Get the computed standard deviation of all recorded values in the histogram
     *
     * @return the standard deviation (in value units) of the histogram data
     */
    public double getStdDeviation() {
        if (getTotalCount() == 0) {
            return 0.0;
        }
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
     * Get the value at a given percentile.
     * When the given percentile is &gt; 0.0, the value returned is the value that the given
     * percentage of the overall recorded value entries in the histogram are either smaller than
     * or equivalent to. When the given percentile is 0.0, the value returned is the value that all value
     * entries in the histogram are either larger than or equivalent to.
     * <p>
     * Note that two values are "equivalent" in this statement if
     * {@link org.HdrHistogram.AbstractHistogram#valuesAreEquivalent} would return true.
     *
     * @param percentile  The percentile for which to return the associated value
     * @return The value that the given percentage of the overall recorded value entries in the
     * histogram are either smaller than or equivalent to. When the percentile is 0.0, returns the
     * value that all value entries in the histogram are either larger than or equivalent to.
     */
    public long getValueAtPercentile(final double percentile) {
        final double requestedPercentile = Math.min(percentile, 100.0); // Truncate down to 100%
        long countAtPercentile = (long)(((requestedPercentile / 100.0) * getTotalCount()) + 0.5); // round to nearest
        countAtPercentile = Math.max(countAtPercentile, 1); // Make sure we at least reach the first recorded entry
        long totalToCurrentIndex = 0;
        for (int i = 0; i < countsArrayLength; i++) {
            totalToCurrentIndex += getCountAtIndex(i);
            if (totalToCurrentIndex >= countAtPercentile) {
                long valueAtIndex = valueFromIndex(i);
                return (percentile == 0.0) ?
                        lowestEquivalentValue(valueAtIndex) :
                        highestEquivalentValue(valueAtIndex);
            }
        }
        return 0;
    }

    /**
     * Get the percentile at a given value.
     * The percentile returned is the percentile of values recorded in the histogram that are smaller
     * than or equivalent to the given value.
     * <p>
     * Note that two values are "equivalent" in this statement if
     * {@link org.HdrHistogram.AbstractHistogram#valuesAreEquivalent} would return true.
     *
     * @param value The value for which to return the associated percentile
     * @return The percentile of values recorded in the histogram that are smaller than or equivalent
     * to the given value.
     */
    public double getPercentileAtOrBelowValue(final long value) {
        if (getTotalCount() == 0) {
            return 100.0;
        }
        final int targetIndex = Math.min(countsArrayIndex(value), (countsArrayLength - 1));
        long totalToCurrentIndex = 0;
        for (int i = 0; i <= targetIndex; i++) {
            totalToCurrentIndex += getCountAtIndex(i);
        }
        return (100.0 * totalToCurrentIndex) / getTotalCount();
    }

    /**
     * Get the count of recorded values within a range of value levels (inclusive to within the histogram's resolution).
     *
     * @param lowValue  The lower value bound on the range for which
     *                  to provide the recorded count. Will be rounded down with
     *                  {@link Histogram#lowestEquivalentValue lowestEquivalentValue}.
     * @param highValue  The higher value bound on the range for which to provide the recorded count.
     *                   Will be rounded up with {@link Histogram#highestEquivalentValue highestEquivalentValue}.
     * @return the total count of values recorded in the histogram within the value range that is
     * {@literal >=} lowestEquivalentValue(<i>lowValue</i>) and {@literal <=} highestEquivalentValue(<i>highValue</i>)
     */
    public long getCountBetweenValues(final long lowValue, final long highValue) throws ArrayIndexOutOfBoundsException {
        final int lowIndex = Math.max(0, countsArrayIndex(lowValue));
        final int highIndex = Math.min(countsArrayIndex(highValue), (countsArrayLength - 1));
        long count = 0;
        for (int i = lowIndex ; i <= highIndex; i++) {
            count += getCountAtIndex(i);
        }
        return count;
    }

    /**
     * Get the count of recorded values at a specific value (to within the histogram resolution at the value level).
     *
     * @param value The value for which to provide the recorded count
     * @return The total count of values recorded in the histogram within the value range that is
     * {@literal >=} lowestEquivalentValue(<i>value</i>) and {@literal <=} highestEquivalentValue(<i>value</i>)
     */
    public long getCountAtValue(final long value) throws ArrayIndexOutOfBoundsException {
        final int index = Math.min(Math.max(0, countsArrayIndex(value)), (countsArrayLength - 1));
        return getCountAtIndex(index);
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
     * @param logBase The multiplier by which bucket sizes will grow in each iteration step
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
     * a {@link AllValuesIterator}
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
    }

    //
    //
    //
    // Serialization support:
    //
    //
    //

    private static final long serialVersionUID = 0x1c849301;

    private void writeObject(final ObjectOutputStream o)
            throws IOException
    {
        o.writeLong(lowestDiscernibleValue);
        o.writeLong(highestTrackableValue);
        o.writeInt(numberOfSignificantValueDigits);
        o.writeInt(getNormalizingIndexOffset());
        o.writeDouble(integerToDoubleValueConversionRatio);
        o.writeLong(getTotalCount());
        // Max Value is added to the serialized form because establishing max via scanning is "harder" during
        // deserialization, as the counts array is not available at the subclass deserializing level, and we don't
        // really want to have each subclass establish max on it's own...
        o.writeLong(maxValue);
        o.writeLong(minNonZeroValue);
        o.writeLong(startTimeStampMsec);
        o.writeLong(endTimeStampMsec);
        o.writeBoolean(autoResize);
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        final long lowestDiscernibleValue = o.readLong();
        final long highestTrackableValue = o.readLong();
        final int numberOfSignificantValueDigits = o.readInt();
        final int normalizingIndexOffset = o.readInt();
        final double integerToDoubleValueConversionRatio = o.readDouble();
        final long indicatedTotalCount = o.readLong();
        final long indicatedMaxValue = o.readLong();
        final long indicatedMinNonZeroValue = o.readLong();
        final long indicatedStartTimeStampMsec = o.readLong();
        final long indicatedEndTimeStampMsec = o.readLong();
        final boolean indicatedAutoResize = o.readBoolean();

        init(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits,
                integerToDoubleValueConversionRatio, normalizingIndexOffset);
        // Set internalTrackingValues (can't establish them from array yet, because it's not yet read...)
        setTotalCount(indicatedTotalCount);
        maxValue = indicatedMaxValue;
        minNonZeroValue = indicatedMinNonZeroValue;
        startTimeStampMsec = indicatedStartTimeStampMsec;
        endTimeStampMsec = indicatedEndTimeStampMsec;
        autoResize = indicatedAutoResize;
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
    @Override
    public int getNeededByteBufferCapacity() {
        return getNeededByteBufferCapacity(countsArrayLength);
    }

    private static final int ENCODING_HEADER_SIZE = 40;
    private static final int V0_ENCODING_HEADER_SIZE = 32;

    int getNeededByteBufferCapacity(final int relevantLength) {
        return getNeededPayloadByteBufferCapacity(relevantLength) + ENCODING_HEADER_SIZE;
    }

    int getNeededPayloadByteBufferCapacity(final int relevantLength) {
        return (relevantLength * wordSizeInBytes);
    }

    abstract void fillCountsArrayFromBuffer(ByteBuffer buffer, int length);

    abstract void fillBufferFromCountsArray(ByteBuffer buffer, int length);

    private static final int V0EncodingCookieBase = 0x1c849308;
    private static final int V0EcompressedEncodingCookieBase = 0x1c849309;

    private static final int encodingCookieBase = 0x1c849301;
    private static final int compressedEncodingCookieBase = 0x1c849302;

    private int getV0EncodingCookie() {
        return V0EncodingCookieBase + (wordSizeInBytes << 4);
    }

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
        final int relevantLength = countsArrayIndex(maxValue) + 1;
        if (buffer.capacity() < getNeededByteBufferCapacity(relevantLength)) {
            throw new ArrayIndexOutOfBoundsException("buffer does not have capacity for" +
                    getNeededByteBufferCapacity(relevantLength) + " bytes");
        }
        int initialPosition = buffer.position();
        buffer.putInt(getEncodingCookie());
        buffer.putInt(relevantLength * wordSizeInBytes);
        buffer.putInt(getNormalizingIndexOffset());
        buffer.putInt(numberOfSignificantValueDigits);
        buffer.putLong(lowestDiscernibleValue);
        buffer.putLong(highestTrackableValue);
        buffer.putDouble(getIntegerToDoubleValueConversionRatio());

        fillBufferFromCountsArray(buffer, relevantLength);

        int bytesWritten = getNeededByteBufferCapacity(relevantLength);
        buffer.position(initialPosition + bytesWritten);
        return bytesWritten;
    }

    /**
     * Encode this histogram in compressed form into a byte array
     * @param targetBuffer The buffer to encode into
     * @param compressionLevel Compression level (for java.util.zip.Deflater).
     * @return The number of bytes written to the buffer
     */
    @Override
    synchronized public int encodeIntoCompressedByteBuffer(
            final ByteBuffer targetBuffer,
            final int compressionLevel) {
        int neededCapacity = getNeededByteBufferCapacity(countsArrayLength);
        if (intermediateUncompressedByteBuffer == null || intermediateUncompressedByteBuffer.capacity() < neededCapacity) {
            intermediateUncompressedByteBuffer = ByteBuffer.allocate(neededCapacity);
        }
        intermediateUncompressedByteBuffer.clear();
        final int uncompressedLength = encodeIntoByteBuffer(intermediateUncompressedByteBuffer);

        int initialTargetPosition = targetBuffer.position();
        targetBuffer.putInt(getCompressedEncodingCookie());
        targetBuffer.putInt(0); // Placeholder for compressed contents length

        Deflater compressor = new Deflater(compressionLevel);
        compressor.setInput(intermediateUncompressedByteBuffer.array(), 0, uncompressedLength);
        compressor.finish();

        byte[] targetArray = targetBuffer.array();
        int compressedTargetOffset = initialTargetPosition + 8;
        int compressedDataLength =
                compressor.deflate(
                        targetArray,
                        compressedTargetOffset,
                        targetArray.length - compressedTargetOffset
                );
        compressor.end();

        targetBuffer.putInt(initialTargetPosition + 4, compressedDataLength); // Record the compressed length
        int bytesWritten = compressedDataLength + 8;
        targetBuffer.position(initialTargetPosition + bytesWritten);
        return bytesWritten;
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

    static AbstractHistogram decodeFromByteBuffer(
            final ByteBuffer buffer,
            final Class histogramClass,
            final long minBarForHighestTrackableValue) {
        try {
            return decodeFromByteBuffer(buffer, histogramClass, minBarForHighestTrackableValue, null, null);
        } catch (DataFormatException ex) {
            throw new RuntimeException(ex);
        }
    }

    static AbstractHistogram decodeFromByteBuffer(
            final ByteBuffer buffer,
            final Class histogramClass,
            final long minBarForHighestTrackableValue,
            final Inflater decompressor,
            final ByteBuffer intermediateUncompressedByteBuffer) throws DataFormatException {

        final int cookie = buffer.getInt();
        final int payloadLength;
        final int normalizingIndexOffset;
        final int numberOfSignificantValueDigits;
        final long lowestTrackableUnitValue;
        long highestTrackableValue;
        final Double integerToDoubleValueConversionRatio;

        if (getCookieBase(cookie) == encodingCookieBase) {
            payloadLength = buffer.getInt();
            normalizingIndexOffset = buffer.getInt();
            numberOfSignificantValueDigits = buffer.getInt();
            lowestTrackableUnitValue = buffer.getLong();
            highestTrackableValue = buffer.getLong();
            integerToDoubleValueConversionRatio = buffer.getDouble();
        } else if (getCookieBase(cookie) == V0EncodingCookieBase) {
            numberOfSignificantValueDigits = buffer.getInt();
            lowestTrackableUnitValue = buffer.getLong();
            highestTrackableValue = buffer.getLong();
            buffer.getLong(); // Discard totalCount field in V0 header.
            payloadLength = Integer.MAX_VALUE;
            integerToDoubleValueConversionRatio = 1.0;
            normalizingIndexOffset = 0;
        } else {
            throw new IllegalArgumentException("The buffer does not contain a Histogram");
        }
        highestTrackableValue = Math.max(highestTrackableValue, minBarForHighestTrackableValue);

        AbstractHistogram histogram;

        // Construct histogram:
        try {
            @SuppressWarnings("unchecked")
            Constructor<AbstractHistogram> constructor = histogramClass.getConstructor(constructorArgsTypes);
            histogram = constructor.newInstance(lowestTrackableUnitValue, highestTrackableValue,
                    numberOfSignificantValueDigits);
            histogram.setIntegerToDoubleValueConversionRatio(integerToDoubleValueConversionRatio);
            histogram.setNormalizingIndexOffset(normalizingIndexOffset);
            if ((cookie != histogram.getEncodingCookie()) &&
                    (cookie != histogram.getV0EncodingCookie())) {
                throw new IllegalArgumentException(
                        "The buffer's encoded value byte size (" +
                                getWordSizeInBytesFromCookie(cookie) +
                                ") does not match the Histogram's (" +
                                histogram.wordSizeInBytes + ")");
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException(ex);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException(ex);
        } catch (InstantiationException ex) {
            throw new IllegalArgumentException(ex);
        } catch (InvocationTargetException ex) {
            throw new IllegalArgumentException(ex);
        }

        ByteBuffer payLoadSourceBuffer;

        final int expectedCapacity =
                Math.min(
                        histogram.getNeededPayloadByteBufferCapacity(histogram.countsArrayLength),
                        payloadLength
                );

        if (decompressor == null) {
            // No compressed source buffer. Payload is in buffer, after header.
            if (expectedCapacity > buffer.remaining()) {
                throw new IllegalArgumentException("The buffer does not contain the full Histogram payload");
            }
            payLoadSourceBuffer = buffer;
        } else {
            // Compressed source buffer. Payload needs to be decoded from there.
            payLoadSourceBuffer = intermediateUncompressedByteBuffer;
            if (payLoadSourceBuffer == null) {
                payLoadSourceBuffer = ByteBuffer.allocate(expectedCapacity);
            } else {
                payLoadSourceBuffer.reset();
                if (payLoadSourceBuffer.remaining() < expectedCapacity) {
                    throw new IllegalArgumentException("Supplied intermediate not large enough (capacity = " +
                            payLoadSourceBuffer.capacity() + ", expected = " + expectedCapacity);
                }
                payLoadSourceBuffer.limit(expectedCapacity);
            }
            int decompressedByteCount = decompressor.inflate(payLoadSourceBuffer.array());
            if ((payloadLength < Integer.MAX_VALUE) && (decompressedByteCount < payloadLength)) {
                throw new IllegalArgumentException("The buffer does not contain the indicated payload amount");
            }
        }

        histogram.fillCountsArrayFromSourceBuffer(
                payLoadSourceBuffer,
                expectedCapacity / getWordSizeInBytesFromCookie(cookie),
                getWordSizeInBytesFromCookie(cookie));

        histogram.establishInternalTackingValues();

        return histogram;
    }

    private void fillCountsArrayFromSourceBuffer(ByteBuffer sourceBuffer, int lengthInWords, int wordSizeInBytes) {
        switch (wordSizeInBytes) {
            case 2: {
                ShortBuffer source = sourceBuffer.asShortBuffer();
                for (int i = 0; i < lengthInWords; i++) {
                    setCountAtIndex(i, source.get());
                }
                break;
            }
            case 4: {
                IntBuffer source = sourceBuffer.asIntBuffer();
                for (int i = 0; i < lengthInWords; i++) {
                    setCountAtIndex(i, source.get());
                }
                break;
            }
            case 8: {
                LongBuffer source = sourceBuffer.asLongBuffer();
                for (int i = 0; i < lengthInWords; i++) {
                    setCountAtIndex(i, source.get());
                }
                break;
            }
            default:
                throw new IllegalArgumentException("word size must be 2, 4, or 8 bytes");
        }
    }

    static AbstractHistogram decodeFromCompressedByteBuffer(final ByteBuffer buffer,
                                                            final Class histogramClass,
                                                            final long minBarForHighestTrackableValue)
            throws DataFormatException {
        int initialTargetPosition = buffer.position();
        final int cookie = buffer.getInt();
        final int headerSize;
        if (getCookieBase(cookie) == compressedEncodingCookieBase) {
            headerSize = ENCODING_HEADER_SIZE;
        } else if (getCookieBase(cookie) == V0EcompressedEncodingCookieBase) {
            headerSize = V0_ENCODING_HEADER_SIZE;
        } else {
            throw new IllegalArgumentException("The buffer does not contain a compressed Histogram");
        }

        final int lengthOfCompressedContents = buffer.getInt();
        final Inflater decompressor = new Inflater();
        decompressor.setInput(buffer.array(), initialTargetPosition + 8, lengthOfCompressedContents);

        final ByteBuffer headerBuffer = ByteBuffer.allocate(headerSize);
        decompressor.inflate(headerBuffer.array());
        AbstractHistogram histogram = decodeFromByteBuffer(
                headerBuffer, histogramClass, minBarForHighestTrackableValue, decompressor, null);
        return histogram;
    }

    //
    //
    //
    // Internal helper methods:
    //
    //
    //

    void establishInternalTackingValues() {
        resetMaxValue(0);
        resetMinNonZeroValue(Long.MAX_VALUE);
        int maxIndex = -1;
        int minNonZeroIndex = -1;
        long observedTotalCount = 0;
        for (int index = 0; index < countsArrayLength; index++) {
            long countAtIndex;
            if ((countAtIndex = getCountAtIndex(index)) > 0) {
                observedTotalCount += countAtIndex;
                maxIndex = index;
                if ((minNonZeroIndex == -1) && (index != 0)) {
                    minNonZeroIndex = index;
                }
            }
        }
        if (maxIndex >= 0) {
            updatedMaxValue(highestEquivalentValue(valueFromIndex(maxIndex)));
        }
        if (minNonZeroIndex >= 0) {
            updateMinNonZeroValue(valueFromIndex(minNonZeroIndex));
        }
        setTotalCount(observedTotalCount);
    }

    int getBucketsNeededToCoverValue(final long value) {
        long smallestUntrackableValue = ((long)subBucketCount) << unitMagnitude;
        int bucketsNeeded = 1;
        while (smallestUntrackableValue <= value) {
            if (smallestUntrackableValue > (Long.MAX_VALUE / 2)) {
                return bucketsNeeded + 1;
            }
            smallestUntrackableValue <<= 1;
            bucketsNeeded++;
        }
        return bucketsNeeded;
    }

    int getLengthForNumberOfBuckets(final int numberOfBuckets) {
        final int lengthNeeded = (numberOfBuckets + 1) * (subBucketCount / 2);
        return lengthNeeded;
    }

    int countsArrayIndex(final long value) {
        if (value < 0) {
            throw new ArrayIndexOutOfBoundsException("Histogram recorded value cannot be negative.");
        }
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
        // Calculate the offset in the bucket (can be negative for first bucket):
        final int offsetInBucket = subBucketIndex - subBucketHalfCount;
        // The following is the equivalent of ((subBucketIndex  - subBucketHalfCount) + bucketBaseIndex;
        return bucketBaseIndex + offsetInBucket;
    }

    int getBucketIndex(final long value) {
        return leadingZeroCountBase - Long.numberOfLeadingZeros(value | subBucketMask);
    }

    int getSubBucketIndex(final long value, final int bucketIndex) {
        return  (int)(value >>> (bucketIndex + unitMagnitude));
    }

    int normalizeIndex(int index, int normalizingIndexOffset, int arrayLength) {
        if (normalizingIndexOffset == 0) {
            // Fastpath out of normalization. Keeps integer value histograms fast while allowing
            // others (like DoubleHistogram) to use normalization at a cost...
            return index;
        }
        if ((index > arrayLength) || (index < 0)) {
            throw new ArrayIndexOutOfBoundsException("index out of covered value range");
        }
        int normalizedIndex = index - normalizingIndexOffset;
        // The following is the same as an unsigned remainder operation, as long as no double wrapping happens
        // (which shouldn't happen, as normalization is never supposed to wrap, since it would have overflowed
        // or underflowed before it did). This (the + and - tests) seems to be faster than a % op with a
        // correcting if < 0...:
        if (normalizedIndex < 0) {
            normalizedIndex += arrayLength;
        } else if (normalizedIndex >= arrayLength) {
            normalizedIndex -= arrayLength;
        }
        return normalizedIndex;
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