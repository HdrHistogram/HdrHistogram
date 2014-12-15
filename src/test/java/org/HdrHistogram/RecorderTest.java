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

}
