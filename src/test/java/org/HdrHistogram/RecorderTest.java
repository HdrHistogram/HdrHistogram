/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import org.junit.Assert;
import org.junit.Test;

/**
 * JUnit test for {@link Histogram}
 */
public class RecorderTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units

    @Test
    public void testIntervalRecording() throws Exception {

        Histogram histogram = new Histogram(highestTrackableValue, 3);
        DoubleHistogram doubleHistogram = new DoubleHistogram(highestTrackableValue * 1000, 3);
        Recorder recorder1 =
                new Recorder(highestTrackableValue, 3);
        Recorder recorder2 =
                new Recorder(highestTrackableValue, 3);
        DoubleRecorder doubleRecorder1 =
                new DoubleRecorder(highestTrackableValue * 1000, 3);
        DoubleRecorder doubleRecorder2 =
                new DoubleRecorder(highestTrackableValue * 1000, 3);


        for (int i = 0; i < 10000; i++) {
            histogram.recordValue(3000 * i);
            recorder1.recordValue(3000 * i);
            recorder2.recordValue(3000 * i);
            doubleHistogram.recordValue(5000 * i);
            doubleRecorder1.recordValue(5000 * i);
            doubleRecorder2.recordValue(5000 * i);
            doubleHistogram.recordValue(0.001); // Makes some internal shifts happen.
            doubleRecorder1.recordValue(0.001); // Makes some internal shifts happen.
            doubleRecorder2.recordValue(0.001); // Makes some internal shifts happen.
        }

        Histogram histogram2 = recorder1.getIntervalHistogram();
        Assert.assertEquals(histogram, histogram2);

        recorder2.getIntervalHistogramInto(histogram2);
        Assert.assertEquals(histogram, histogram2);

        DoubleHistogram doubleHistogram2 = doubleRecorder1.getIntervalHistogram();
        Assert.assertEquals(doubleHistogram, doubleHistogram2);

        doubleRecorder2.getIntervalHistogramInto(doubleHistogram2);
        Assert.assertEquals(doubleHistogram, doubleHistogram2);

        for (int i = 0; i < 5000; i++) {
            histogram.recordValue(3000 * i);
            recorder1.recordValue(3000 * i);
            recorder2.recordValue(3000 * i);
            doubleHistogram.recordValue(5000 * i);
            doubleRecorder1.recordValue(5000 * i);
            doubleRecorder2.recordValue(5000 * i);
            doubleHistogram.recordValue(0.001);
            doubleRecorder1.recordValue(0.001);
            doubleRecorder2.recordValue(0.001);
        }

        Histogram histogram3 = recorder1.getIntervalHistogram();

        Histogram sumHistogram = histogram2.copy();
        sumHistogram.add(histogram3);
        Assert.assertEquals(histogram, sumHistogram);

        DoubleHistogram doubleHistogram3 = doubleRecorder1.getIntervalHistogram();

        DoubleHistogram sumDoubleHistogram = doubleHistogram2.copy();
        sumDoubleHistogram.add(doubleHistogram3);
        Assert.assertEquals(doubleHistogram, sumDoubleHistogram);

        recorder2.getIntervalHistogram();
        doubleRecorder2.getIntervalHistogram();

        for (int i = 5000; i < 10000; i++) {
            histogram.recordValue(3000 * i);
            recorder1.recordValue(3000 * i);
            recorder2.recordValue(3000 * i);
            doubleHistogram.recordValue(5000 * i);
            doubleRecorder1.recordValue(5000 * i);
            doubleRecorder2.recordValue(5000 * i);
            doubleHistogram.recordValue(0.001);
            doubleRecorder1.recordValue(0.001);
            doubleRecorder2.recordValue(0.001);
        }

        Histogram histogram4 = recorder1.getIntervalHistogram();
        histogram4.add(histogram3);
        Assert.assertEquals(histogram4, histogram2);

        recorder2.getIntervalHistogramInto(histogram4);
        histogram4.add(histogram3);
        Assert.assertEquals(histogram4, histogram2);

        DoubleHistogram doubleHistogram4 = doubleRecorder1.getIntervalHistogram();
        doubleHistogram4.add(doubleHistogram3);
        Assert.assertEquals(doubleHistogram4, doubleHistogram2);

        doubleHistogram4.reset();
        doubleRecorder2.getIntervalHistogramInto(doubleHistogram4);
        doubleHistogram4.add(doubleHistogram3);
        Assert.assertEquals(doubleHistogram4, doubleHistogram2);
    }

    @Test
    public void testSimpleAutosizingRecorder() throws Exception {
        Recorder recorder = new Recorder(3);
        Histogram histogram = recorder.getIntervalHistogram();
    }

    // Recorder Recycling tests:

    @Test
    public void testRecycling() throws Exception {
        Recorder recorder = new Recorder(3);
        Histogram histogramA = recorder.getIntervalHistogram();
        Histogram histogramB = recorder.getIntervalHistogram(histogramA);
        Histogram histogramC = recorder.getIntervalHistogram(histogramA, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecyclingContainingClassEnforcement() throws Exception {
        Histogram histToRecycle = new Histogram(3);
        Recorder recorder = new Recorder(3);
        Histogram histogramA = recorder.getIntervalHistogram(histToRecycle);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRecyclingContainingInstanceEnforcement() throws Exception {
        Recorder recorder1 = new Recorder(3);
        Recorder recorder2 = new Recorder(3);
        Histogram histToRecycle = recorder1.getIntervalHistogram();
        Histogram histToRecycle2 = recorder2.getIntervalHistogram(histToRecycle);
    }

    @Test
    public void testRecyclingContainingInstanceNonEnforcement() throws Exception {
        Recorder recorder1 = new Recorder(3);
        Recorder recorder2 = new Recorder(3);
        Histogram histToRecycle = recorder1.getIntervalHistogram();
        Histogram histToRecycle2 = recorder2.getIntervalHistogram(histToRecycle, false);
    }

    // SingleWriterRecorder Recycling tests:

    @Test
    public void testSWRecycling() throws Exception {
        SingleWriterRecorder recorder = new SingleWriterRecorder(3);
        Histogram histogramA = recorder.getIntervalHistogram();
        Histogram histogramB = recorder.getIntervalHistogram(histogramA);
        Histogram histogramC = recorder.getIntervalHistogram(histogramA, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSWRecyclingContainingClassEnforcement() throws Exception {
        Histogram histToRecycle = new Histogram(3);
        SingleWriterRecorder recorder = new SingleWriterRecorder(3);
        Histogram histogramA = recorder.getIntervalHistogram(histToRecycle);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSWRecyclingContainingInstanceEnforcement() throws Exception {
        SingleWriterRecorder recorder1 = new SingleWriterRecorder(3);
        SingleWriterRecorder recorder2 = new SingleWriterRecorder(3);
        Histogram histToRecycle = recorder1.getIntervalHistogram();
        Histogram histToRecycle2 = recorder2.getIntervalHistogram(histToRecycle);
    }

    @Test
    public void testSWRecyclingContainingInstanceNonEnforcement() throws Exception {
        SingleWriterRecorder recorder1 = new SingleWriterRecorder(3);
        SingleWriterRecorder recorder2 = new SingleWriterRecorder(3);
        Histogram histToRecycle = recorder1.getIntervalHistogram();
        Histogram histToRecycle2 = recorder2.getIntervalHistogram(histToRecycle, false);
    }

    // DoubleRecorder Recycling tests:

    @Test
    public void testDRecycling() throws Exception {
        DoubleRecorder recorder = new DoubleRecorder(3);
        DoubleHistogram histogramA = recorder.getIntervalHistogram();
        DoubleHistogram histogramB = recorder.getIntervalHistogram(histogramA);
        DoubleHistogram histogramC = recorder.getIntervalHistogram(histogramA, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDRecyclingContainingClassEnforcement() throws Exception {
        DoubleHistogram histToRecycle = new DoubleHistogram(3);
        DoubleRecorder recorder = new DoubleRecorder(3);
        DoubleHistogram histogramA = recorder.getIntervalHistogram(histToRecycle);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDRecyclingContainingInstanceEnforcement() throws Exception {
        DoubleRecorder recorder1 = new DoubleRecorder(3);
        DoubleRecorder recorder2 = new DoubleRecorder(3);
        DoubleHistogram histToRecycle = recorder1.getIntervalHistogram();
        DoubleHistogram histToRecycle2 = recorder2.getIntervalHistogram(histToRecycle);
    }

    @Test
    public void testDRecyclingContainingInstanceNonEnforcement() throws Exception {
        DoubleRecorder recorder1 = new DoubleRecorder(3);
        DoubleRecorder recorder2 = new DoubleRecorder(3);
        DoubleHistogram histToRecycle = recorder1.getIntervalHistogram();
        DoubleHistogram histToRecycle2 = recorder2.getIntervalHistogram(histToRecycle, false);
    }

    // SingleWriterDoubleRecorder Recycling tests:

    @Test
    public void testSWDRecycling() throws Exception {
        SingleWriterDoubleRecorder recorder = new SingleWriterDoubleRecorder(3);
        DoubleHistogram histogramA = recorder.getIntervalHistogram();
        DoubleHistogram histogramB = recorder.getIntervalHistogram(histogramA);
        DoubleHistogram histogramC = recorder.getIntervalHistogram(histogramA, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSWDRecyclingContainingClassEnforcement() throws Exception {
        DoubleHistogram histToRecycle = new DoubleHistogram(3);
        SingleWriterDoubleRecorder recorder = new SingleWriterDoubleRecorder(3);
        DoubleHistogram histogramA = recorder.getIntervalHistogram(histToRecycle);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSWDRecyclingContainingInstanceEnforcement() throws Exception {
        SingleWriterDoubleRecorder recorder1 = new SingleWriterDoubleRecorder(3);
        SingleWriterDoubleRecorder recorder2 = new SingleWriterDoubleRecorder(3);
        DoubleHistogram histToRecycle = recorder1.getIntervalHistogram();
        DoubleHistogram histToRecycle2 = recorder2.getIntervalHistogram(histToRecycle);
    }

    @Test
    public void testSWDRecyclingContainingInstanceNonEnforcement() throws Exception {
        SingleWriterDoubleRecorder recorder1 = new SingleWriterDoubleRecorder(3);
        SingleWriterDoubleRecorder recorder2 = new SingleWriterDoubleRecorder(3);
        DoubleHistogram histToRecycle = recorder1.getIntervalHistogram();
        DoubleHistogram histToRecycle2 = recorder2.getIntervalHistogram(histToRecycle, false);
    }
}
