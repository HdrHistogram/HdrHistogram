/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.io.PrintStream;
import java.nio.ByteBuffer;

/**
 * <h3>A floating point values High Dynamic Range (HDR) Histogram that is synchronized as a whole</h3>
 * <p>
 * A {@link SynchronizedDoubleHistogram} is a variant of {@link org.HdrHistogram.DoubleHistogram} that is
 * synchronized as a whole, such that queries, copying, and addition operations are atomic with relation to
 * modification on the {@link SynchronizedDoubleHistogram}, nd such that external accessors (e.g. iterations on the
 * histogram data) that synchronize on the {@link SynchronizedDoubleHistogram} instance can safely assume that no
 * modifications to the histogram data occur within their synchronized block.
 * <p>
 * It is important to note that synchronization can result in blocking recoding calls. If non-blocking recoding
 * operations are required, consider using {@link org.HdrHistogram.ConcurrentDoubleHistogram}, or (recommended)
 * {@link DoubleRecorder} which were intended for concurrent operations.
 * <p>
 * {@link SynchronizedDoubleHistogram} supports the recording and analyzing sampled data value counts across a
 * configurable dynamic range of floating point (double) values, with configurable value precision within the range.
 * Dynamic range is expressed as a ratio between the highest and lowest non-zero values trackable within the histogram
 * at any given time. Value precision is expressed as the number of significant [decimal] digits in the value recording,
 * and provides control over value quantization behavior across the value range and the subsequent value resolution at
 * any given level.
 * <p>
 * Auto-ranging: Unlike integer value based histograms, the specific value range tracked by a {@link
 * SynchronizedDoubleHistogram} is not specified upfront. Only the dynamic range of values that the histogram can
 * cover is (optionally) specified. E.g. When a {@link ConcurrentDoubleHistogram} is created to track a dynamic range of
 * 3600000000000 (enough to track values from a nanosecond to an hour), values could be recorded into into it in any
 * consistent unit of time as long as the ratio between the highest and lowest non-zero values stays within the
 * specified dynamic range, so recording in units of nanoseconds (1.0 thru 3600000000000.0), milliseconds (0.000001
 * thru 3600000.0) seconds (0.000000001 thru 3600.0), hours (1/3.6E12 thru 1.0) will all work just as well.
 * <p>
 * Auto-resizing: When constructed with no specified dynamic range (or when auto-resize is turned on with {@link
 * SynchronizedDoubleHistogram#setAutoResize}) a {@link SynchronizedDoubleHistogram} will auto-resize its dynamic
 * range to include recorded values as they are encountered. Note that recording calls that cause auto-resizing may
 * take longer to execute, as resizing incurs allocation and copying of internal data structures.
 * <p>
 * Attempts to record non-zero values that range outside of the specified dynamic range (or exceed the limits of
 * of dynamic range when auto-resizing) may results in {@link ArrayIndexOutOfBoundsException} exceptions, either
 * due to overflow or underflow conditions. These exceptions will only be thrown if recording the value would have
 * resulted in discarding or losing the required value precision of values already recorded in the histogram.
 * <p>
 * See package description for {@link org.HdrHistogram} for details.
 */

public class SynchronizedDoubleHistogram extends DoubleHistogram {

    /**
     * Construct a new auto-resizing DoubleHistogram using a precision stated as a number of significant
     * decimal digits.
     *
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SynchronizedDoubleHistogram(final int numberOfSignificantValueDigits) {
        this(2, numberOfSignificantValueDigits);
        setAutoResize(true);
    }

    /**
     * Construct a new DoubleHistogram with the specified dynamic range (provided in
     * {@code highestToLowestValueRatio}) and using a precision stated as a number of significant
     * decimal digits.
     *
     * @param highestToLowestValueRatio specifies the dynamic range to use
     * @param numberOfSignificantValueDigits Specifies the precision to use. This is the number of significant
     *                                       decimal digits to which the histogram will maintain value resolution
     *                                       and separation. Must be a non-negative integer between 0 and 5.
     */
    public SynchronizedDoubleHistogram(final long highestToLowestValueRatio, final int numberOfSignificantValueDigits) {
        super(highestToLowestValueRatio, numberOfSignificantValueDigits, SynchronizedHistogram.class);
    }

    /**
     * Construct a {@link SynchronizedDoubleHistogram} with the same range settings as a given source,
     * duplicating the source's start/end timestamps (but NOT it's contents)
     * @param source The source histogram to duplicate
     */
    public SynchronizedDoubleHistogram(final ConcurrentDoubleHistogram source) {
        super(source);
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
    public synchronized void recordValue(final double value) throws ArrayIndexOutOfBoundsException {
        super.recordValue(value);
    }

    @Override
    public synchronized void recordValueWithCount(final double value, final long count) throws ArrayIndexOutOfBoundsException {
        super.recordValueWithCount(value, count);
    }

    @Override
    public synchronized void recordValueWithExpectedInterval(final double value, final double expectedIntervalBetweenValueSamples)
            throws ArrayIndexOutOfBoundsException {
        super.recordValueWithExpectedInterval(value, expectedIntervalBetweenValueSamples);
    }

    @Override
    public synchronized void reset() {
        super.reset();
    }

    @Override
    public synchronized DoubleHistogram copy() {
        final DoubleHistogram targetHistogram =
                new DoubleHistogram(this);
        integerValuesHistogram.copyInto(targetHistogram.integerValuesHistogram);
        return targetHistogram;
    }

    @Override
    public synchronized DoubleHistogram copyCorrectedForCoordinatedOmission(final double expectedIntervalBetweenValueSamples) {
        final DoubleHistogram targetHistogram =
                new DoubleHistogram(this);
        targetHistogram.addWhileCorrectingForCoordinatedOmission(this, expectedIntervalBetweenValueSamples);
        return targetHistogram;
    }

    @Override
    public synchronized void copyInto(final DoubleHistogram targetHistogram) {
        // Synchronize copyInto(). Avoid deadlocks by synchronizing in order of construction identity count.
        if (integerValuesHistogram.identity < targetHistogram.integerValuesHistogram.identity) {
            synchronized (this) {
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

    @Override
    public synchronized void copyIntoCorrectedForCoordinatedOmission(final DoubleHistogram targetHistogram,
                                                        final double expectedIntervalBetweenValueSamples) {
        // Synchronize copyIntoCorrectedForCoordinatedOmission(). Avoid deadlocks by synchronizing in order
        // of construction identity count.
        if (integerValuesHistogram.identity < targetHistogram.integerValuesHistogram.identity) {
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

    @Override
    public synchronized void add(final DoubleHistogram fromHistogram) throws ArrayIndexOutOfBoundsException {
        // Synchronize add(). Avoid deadlocks by synchronizing in order of construction identity count.
        if (integerValuesHistogram.identity < fromHistogram.integerValuesHistogram.identity) {
            synchronized (this) {
                synchronized (fromHistogram) {
                    super.add(fromHistogram);
                }
            }
        } else {
            synchronized (fromHistogram) {
                synchronized (this) {
                    super.add(fromHistogram);
                }
            }
        }
    }


    @Override
    public synchronized void subtract(final DoubleHistogram fromHistogram) {
        // Synchronize subtract(). Avoid deadlocks by synchronizing in order of construction identity count.
        if (integerValuesHistogram.identity < fromHistogram.integerValuesHistogram.identity) {
            synchronized (this) {
                synchronized (fromHistogram) {
                    super.subtract(fromHistogram);
                }
            }
        } else {
            synchronized (fromHistogram) {
                synchronized (this) {
                    super.subtract(fromHistogram);
                }
            }
        }
    }

    @Override
    public synchronized void addWhileCorrectingForCoordinatedOmission(final DoubleHistogram fromHistogram,
                                                         final double expectedIntervalBetweenValueSamples) {
        // Synchronize addWhileCorrectingForCoordinatedOmission(). Avoid deadlocks by synchronizing in
        // order of construction identity count.
        if (integerValuesHistogram.identity < fromHistogram.integerValuesHistogram.identity) {
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
    public synchronized boolean equals(final Object other) {
        if ( this == other ) {
            return true;
        }
        if (other instanceof DoubleHistogram) {
            DoubleHistogram otherHistogram = (DoubleHistogram) other;
            if (integerValuesHistogram.identity < otherHistogram.integerValuesHistogram.identity) {
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
    public synchronized long getTotalCount() {
        return super.getTotalCount();
    }

    @Override
    public synchronized double getIntegerToDoubleValueConversionRatio() {
        return super.getIntegerToDoubleValueConversionRatio();
    }

    @Override
    public synchronized int getNumberOfSignificantValueDigits() {
        return super.getNumberOfSignificantValueDigits();
    }

    @Override
    public synchronized long getHighestToLowestValueRatio() {
        return super.getHighestToLowestValueRatio();
    }

    @Override
    public synchronized double sizeOfEquivalentValueRange(final double value) {
        return super.sizeOfEquivalentValueRange(value);
    }

    @Override
    public synchronized double lowestEquivalentValue(final double value) {
        return super.lowestEquivalentValue(value);
    }

    @Override
    public synchronized double highestEquivalentValue(final double value) {
        return super.highestEquivalentValue(value);
    }

    @Override
    public synchronized double medianEquivalentValue(final double value) {
        return super.medianEquivalentValue(value);
    }

    @Override
    public synchronized double nextNonEquivalentValue(final double value) {
        return super.nextNonEquivalentValue(value);
    }

    @Override
    public synchronized boolean valuesAreEquivalent(final double value1, final double value2) {
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
    public synchronized double getMinValue() {
        return super.getMinValue();
    }

    @Override
    public synchronized double getMaxValue() {
        return super.getMaxValue();
    }

    @Override
    public synchronized double getMinNonZeroValue() {
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
    public synchronized double getValueAtPercentile(final double percentile) {
        return super.getValueAtPercentile(percentile);
    }

    @Override
    public synchronized double getPercentileAtOrBelowValue(final double value) {
        return super.getPercentileAtOrBelowValue(value);
    }

    @Override
    public synchronized double getCountBetweenValues(final double lowValue, final double highValue)
            throws ArrayIndexOutOfBoundsException {
        return super.getCountBetweenValues(lowValue, highValue);
    }

    @Override
    public synchronized long getCountAtValue(final double value) throws ArrayIndexOutOfBoundsException {
        return super.getCountAtValue(value);
    }

    @Override
    public synchronized Percentiles percentiles(final int percentileTicksPerHalfDistance) {
        return super.percentiles(percentileTicksPerHalfDistance);
    }

    @Override
    public synchronized LinearBucketValues linearBucketValues(final double valueUnitsPerBucket) {
        return super.linearBucketValues(valueUnitsPerBucket);
    }

    @Override
    public synchronized LogarithmicBucketValues logarithmicBucketValues(final double valueUnitsInFirstBucket,
                                                           final double logBase) {
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
        super.outputPercentileDistribution(
                printStream,
                percentileTicksPerHalfDistance,
                outputValueUnitScalingRatio,
                useCsvFormat);
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
}
