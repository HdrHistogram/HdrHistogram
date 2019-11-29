/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;

/**
 * <h3>An integer values High Dynamic Range (HDR) Histogram that is synchronized as a whole</h3>
 * <p>
 * A {@link SynchronizedHistogram} is a variant of {@link Histogram} that is
 * synchronized as a whole, such that queries, copying, and addition operations are atomic with relation to
 * modification on the {@link SynchronizedHistogram}, and such that external accessors (e.g. iterations on the
 * histogram data) that synchronize on the {@link SynchronizedHistogram} instance can safely assume that no
 * modifications to the histogram data occur within their synchronized block.
 * <p>
 * It is important to note that synchronization can result in blocking recoding calls. If non-blocking recoding
 * operations are required, consider using {@link ConcurrentHistogram}, {@link AtomicHistogram}, or (recommended)
 * {@link Recorder} or {@link org.HdrHistogram.SingleWriterRecorder} which were intended for concurrent operations.
 * <p>
 * See package description for {@link org.HdrHistogram} and {@link org.HdrHistogram.Histogram} for more details.
 */


public class SynchronizedHistogram extends Histogram {

    /**
     * Construct an auto-resizing SynchronizedHistogram with a lowest discernible value of 1 and an auto-adjusting
     * highestTrackableValue. Can auto-resize up to track values up to (Long.MAX_VALUE / 2).
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SynchronizedHistogram(final int numberOfSignificantValueDigits) {
        this(1, 2, numberOfSignificantValueDigits);
        setAutoResize(true);
    }

    /**
     * Construct a SynchronizedHistogram given the Highest value to be tracked and a number of significant decimal digits. The
     * histogram will be constructed to implicitly track (distinguish from 0) values as low as 1.
     *
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} 2.
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SynchronizedHistogram(final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        this(1, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Construct a SynchronizedHistogram given the Lowest and Highest values to be tracked and a number of significant
     * decimal digits. Providing a lowestDiscernibleValue is useful is situations where the units used
     * for the histogram's values are much smaller that the minimal accuracy required. E.g. when tracking
     * time values stated in nanosecond units, where the minimal accuracy required is a microsecond, the
     * proper value for lowestDiscernibleValue would be 1000.
     *
     * @param lowestDiscernibleValue The lowest value that can be tracked (distinguished from 0) by the histogram.
     *                               Must be a positive integer that is {@literal >=} 1. May be internally rounded
     *                               down to nearest power of 2.
     * @param highestTrackableValue The highest value to be tracked by the histogram. Must be a positive
     *                              integer that is {@literal >=} (2 * lowestDiscernibleValue).
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SynchronizedHistogram(final long lowestDiscernibleValue, final long highestTrackableValue, final int numberOfSignificantValueDigits) {
        super(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
    }

    /**
     * Construct a histogram with the same range settings as a given source histogram,
     * duplicating the source's start/end timestamps (but NOT it's contents)
     * @param source The source histogram to duplicate
     */
    public SynchronizedHistogram(final AbstractHistogram source) {
        super(source);
    }

    /**
     * Construct a new histogram by decoding it from a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     */
    public static SynchronizedHistogram decodeFromByteBuffer(final ByteBuffer buffer,
                                                             final long minBarForHighestTrackableValue) {
        return decodeFromByteBuffer(buffer, SynchronizedHistogram.class, minBarForHighestTrackableValue);
    }

    /**
     * Construct a new histogram by decoding it from a compressed form in a ByteBuffer.
     * @param buffer The buffer to decode from
     * @param minBarForHighestTrackableValue Force highestTrackableValue to be set at least this high
     * @return The newly constructed histogram
     * @throws DataFormatException on error parsing/decompressing the buffer
     */
    public static SynchronizedHistogram decodeFromCompressedByteBuffer(final ByteBuffer buffer,
                                                                       final long minBarForHighestTrackableValue) throws DataFormatException {
        return decodeFromCompressedByteBuffer(buffer, SynchronizedHistogram.class, minBarForHighestTrackableValue);
    }

    /**
     * Construct a new SynchronizedHistogram by decoding it from a String containing a base64 encoded
     * compressed histogram representation.
     *
     * @param base64CompressedHistogramString A string containing a base64 encoding of a compressed histogram
     * @return A SynchronizedHistogram decoded from the string
     * @throws DataFormatException on error parsing/decompressing the input
     */
    public static SynchronizedHistogram fromString(final String base64CompressedHistogramString)
            throws DataFormatException {
        return decodeFromCompressedByteBuffer(
                ByteBuffer.wrap(Base64Helper.parseBase64Binary(base64CompressedHistogramString)),
                0);
    }

    @Override
    public synchronized long getTotalCount() {
        return super.getTotalCount();
    }

    @Override
    public synchronized boolean isAutoResize() {
        return super.isAutoResize();
    }

    @Override
    public synchronized void setAutoResize(boolean autoResize) {
        super.setAutoResize(autoResize);
    }

    @Override
    public synchronized void recordValue(final long value) throws ArrayIndexOutOfBoundsException {
        super.recordValue(value);
    }

    @Override
    public synchronized void recordValueWithCount(final long value, final long count) throws ArrayIndexOutOfBoundsException {
        super.recordValueWithCount(value, count);
    }

    @Override
    public synchronized void recordValueWithExpectedInterval(final long value, final long expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        super.recordValueWithExpectedInterval(value, expectedIntervalBetweenValueSamples);
    }

    /**
     * @deprecated
     */
    @SuppressWarnings("deprecation")
    @Override
    public synchronized void recordValue(final long value, final long expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        super.recordValue(value, expectedIntervalBetweenValueSamples);
    }

    @Override
    public synchronized void reset() {
        super.reset();
    }

    @Override
    public synchronized SynchronizedHistogram copy() {
        SynchronizedHistogram toHistogram = new SynchronizedHistogram(this);
        toHistogram.add(this);
        return toHistogram;
    }

    @Override
    public synchronized SynchronizedHistogram copyCorrectedForCoordinatedOmission(
            final long expectedIntervalBetweenValueSamples) {
        SynchronizedHistogram toHistogram = new SynchronizedHistogram(this);
        toHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        return toHistogram;
    }


    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public void copyInto(final AbstractHistogram targetHistogram) {
        // Synchronize copyInto(). Avoid deadlocks by synchronizing in order of construction identity count.
        if (identity < targetHistogram.identity) {
            synchronized (this) {
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (targetHistogram) {
                    super.copyInto(targetHistogram);
                }
            }
        } else {
            synchronized (targetHistogram) {
                synchronized (this) {
                    super.copyInto(targetHistogram);
                }
            }
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public void copyIntoCorrectedForCoordinatedOmission(final AbstractHistogram targetHistogram,
                                                        final long expectedIntervalBetweenValueSamples) {
        // Synchronize copyIntoCorrectedForCoordinatedOmission(). Avoid deadlocks by synchronizing in order
        // of construction identity count.
        if (identity < targetHistogram.identity) {
            synchronized (this) {
                synchronized (targetHistogram) {
                    super.copyIntoCorrectedForCoordinatedOmission(targetHistogram, expectedIntervalBetweenValueSamples);
                }
            }
        } else {
            synchronized (targetHistogram) {
                synchronized (this) {
                    super.copyIntoCorrectedForCoordinatedOmission(targetHistogram, expectedIntervalBetweenValueSamples);
                }
            }
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public void add(final AbstractHistogram otherHistogram) {
        // Synchronize add(). Avoid deadlocks by synchronizing in order of construction identity count.
        if (identity < otherHistogram.identity) {
            synchronized (this) {
                synchronized (otherHistogram) {
                    super.add(otherHistogram);
                }
            }
        } else {
            synchronized (otherHistogram) {
                synchronized (this) {
                    super.add(otherHistogram);
                }
            }
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public void subtract(final AbstractHistogram otherHistogram)
            throws ArrayIndexOutOfBoundsException, IllegalArgumentException {
        // Synchronize subtract(). Avoid deadlocks by synchronizing in order of construction identity count.
        if (identity < otherHistogram.identity) {
            synchronized (this) {
                synchronized (otherHistogram) {
                    super.subtract(otherHistogram);
                }
            }
        } else {
            synchronized (otherHistogram) {
                synchronized (this) {
                    super.subtract(otherHistogram);
                }
            }
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public void addWhileCorrectingForCoordinatedOmission(final AbstractHistogram fromHistogram,
                                                         final long expectedIntervalBetweenValueSamples) {
        // Synchronize addWhileCorrectingForCoordinatedOmission(). Avoid deadlocks by synchronizing in
        // order of construction identity count.
        if (identity < fromHistogram.identity) {
            synchronized (this) {
                synchronized (fromHistogram) {
                    super.addWhileCorrectingForCoordinatedOmission(fromHistogram, expectedIntervalBetweenValueSamples);
                }
            }
        } else {
            synchronized (fromHistogram) {
                synchronized (this) {
                    super.addWhileCorrectingForCoordinatedOmission(fromHistogram, expectedIntervalBetweenValueSamples);
                }
            }
        }
    }
    @Override
    public synchronized void shiftValuesLeft(final int numberOfBinaryOrdersOfMagnitude) {
        super.shiftValuesLeft(numberOfBinaryOrdersOfMagnitude);
    }

    @Override
    public synchronized void shiftValuesRight(final int numberOfBinaryOrdersOfMagnitude) {
        super.shiftValuesRight(numberOfBinaryOrdersOfMagnitude);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public boolean equals(final Object other){
        if ( this == other ) {
            return true;
        }
        if (other instanceof AbstractHistogram) {
            AbstractHistogram otherHistogram = (AbstractHistogram) other;
            if (identity < otherHistogram.identity) {
                synchronized (this) {
                    synchronized (otherHistogram) {
                        return super.equals(otherHistogram);
                    }
                }
            } else {
                synchronized (otherHistogram) {
                    synchronized (this) {
                        return super.equals(otherHistogram);
                    }
                }
            }
        } else {
            synchronized (this) {
                return super.equals(other);
            }
        }
    }

    @Override
    public synchronized int hashCode() {
        return super.hashCode();
    }

    @Override
    public synchronized long getLowestDiscernibleValue() {
        return super.getLowestDiscernibleValue();
    }

    @Override
    public synchronized long getHighestTrackableValue() {
        return super.getHighestTrackableValue();
    }

    @Override
    public synchronized int getNumberOfSignificantValueDigits() {
        return super.getNumberOfSignificantValueDigits();
    }

    @Override
    public synchronized long sizeOfEquivalentValueRange(final long value) {
        return super.sizeOfEquivalentValueRange(value);
    }

    @Override
    public synchronized long lowestEquivalentValue(final long value) {
        return super.lowestEquivalentValue(value);
    }

    @Override
    public synchronized long highestEquivalentValue(final long value) {
        return super.highestEquivalentValue(value);
    }

    @Override
    public synchronized long medianEquivalentValue(final long value) {
        return super.medianEquivalentValue(value);
    }

    @Override
    public synchronized long nextNonEquivalentValue(final long value) {
        return super.nextNonEquivalentValue(value);
    }

    @Override
    public synchronized boolean valuesAreEquivalent(final long value1, final long value2) {
        return super.valuesAreEquivalent(value1, value2);
    }

    @Override
    public synchronized int getEstimatedFootprintInBytes() {
        return super.getEstimatedFootprintInBytes();
    }

    @Override
    public synchronized long getStartTimeStamp() {
        return super.getStartTimeStamp();
    }

    @Override
    public synchronized void setStartTimeStamp(final long timeStampMsec) {
        super.setStartTimeStamp(timeStampMsec);
    }

    @Override
    public synchronized long getEndTimeStamp() {
        return super.getEndTimeStamp();
    }

    @Override
    public synchronized void setEndTimeStamp(final long timeStampMsec) {
        super.setEndTimeStamp(timeStampMsec);
    }

    @Override
    public synchronized long getMinValue() {
        return super.getMinValue();
    }

    @Override
    public synchronized long getMaxValue() {
        return super.getMaxValue();
    }

    @Override
    public synchronized long getMinNonZeroValue() {
        return super.getMinNonZeroValue();
    }

    @Override
    public synchronized double getMaxValueAsDouble() {
        return super.getMaxValueAsDouble();
    }

    @Override
    public synchronized double getMean() {
        return super.getMean();
    }

    @Override
    public synchronized double getStdDeviation() {
        return super.getStdDeviation();
    }

    @Override
    public synchronized long getValueAtPercentile(final double percentile) {
        return super.getValueAtPercentile(percentile);
    }

    @Override
    public synchronized double getPercentileAtOrBelowValue(final long value) {
        return super.getPercentileAtOrBelowValue(value);
    }

    @Override
    public synchronized long getCountBetweenValues(final long lowValue, final long highValue) throws ArrayIndexOutOfBoundsException {
        return super.getCountBetweenValues(lowValue, highValue);
    }

    @Override
    public synchronized long getCountAtValue(final long value) throws ArrayIndexOutOfBoundsException {
        return super.getCountAtValue(value);
    }

    @Override
    public synchronized Percentiles percentiles(final int percentileTicksPerHalfDistance) {
        return super.percentiles(percentileTicksPerHalfDistance);
    }

    @Override
    public synchronized LinearBucketValues linearBucketValues(final long valueUnitsPerBucket) {
        return super.linearBucketValues(valueUnitsPerBucket);
    }

    @Override
    public synchronized LogarithmicBucketValues logarithmicBucketValues(final long valueUnitsInFirstBucket, final double logBase) {
        return super.logarithmicBucketValues(valueUnitsInFirstBucket, logBase);
    }

    @Override
    public synchronized RecordedValues recordedValues() {
        return super.recordedValues();
    }

    @Override
    public synchronized AllValues allValues() {
        return super.allValues();
    }

    @Override
    public synchronized void outputPercentileDistribution(final PrintStream printStream,
                                             final Double outputValueUnitScalingRatio) {
        super.outputPercentileDistribution(printStream, outputValueUnitScalingRatio);
    }

    @Override
    public synchronized void outputPercentileDistribution(final PrintStream printStream,
                                             final int percentileTicksPerHalfDistance,
                                             final Double outputValueUnitScalingRatio) {
        super.outputPercentileDistribution(printStream, percentileTicksPerHalfDistance, outputValueUnitScalingRatio);
    }

    @Override
    public synchronized void outputPercentileDistribution(final PrintStream printStream,
                                             final int percentileTicksPerHalfDistance,
                                             final Double outputValueUnitScalingRatio,
                                             final boolean useCsvFormat) {
        super.outputPercentileDistribution(printStream, percentileTicksPerHalfDistance, outputValueUnitScalingRatio, useCsvFormat);
    }

    @Override
    public synchronized int getNeededByteBufferCapacity() {
        return super.getNeededByteBufferCapacity();
    }


    @Override
    public synchronized int encodeIntoByteBuffer(final ByteBuffer buffer) {
        return super.encodeIntoByteBuffer(buffer);
    }

    @Override
    public synchronized int encodeIntoCompressedByteBuffer(
            final ByteBuffer targetBuffer,
            final int compressionLevel) {
        return super.encodeIntoCompressedByteBuffer(targetBuffer, compressionLevel);
    }

    @Override
    public synchronized int encodeIntoCompressedByteBuffer(final ByteBuffer targetBuffer) {
        return super.encodeIntoCompressedByteBuffer(targetBuffer);
    }

    private void readObject(final ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        o.defaultReadObject();
    }
}