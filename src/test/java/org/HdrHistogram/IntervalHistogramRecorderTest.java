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

import java.nio.ByteBuffer;

/**
 * JUnit test for {@link Histogram}
 */
public class IntervalHistogramRecorderTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units

    @Test
    public void testIntervalRecording() throws Exception {

        Histogram histogram = new Histogram(highestTrackableValue, 3);
        DoubleHistogram doubleHistogram = new DoubleHistogram(highestTrackableValue * 1000, 3);
        IntervalHistogramRecorder intervalHistogramRecorder1 =
                new IntervalHistogramRecorder(highestTrackableValue, 3);
        IntervalHistogramRecorder intervalHistogramRecorder2 =
                new IntervalHistogramRecorder(highestTrackableValue, 3);
        IntervalDoubleHistogramRecorder intervalDoubleHistogramRecorder1 =
                new IntervalDoubleHistogramRecorder(highestTrackableValue * 1000, 3);
        IntervalDoubleHistogramRecorder intervalDoubleHistogramRecorder2 =
                new IntervalDoubleHistogramRecorder(highestTrackableValue * 1000, 3);


        for (int i = 0; i < 10000; i++) {
            histogram.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalHistogramRecorder1.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalHistogramRecorder2.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            doubleHistogram.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalDoubleHistogramRecorder1.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalDoubleHistogramRecorder2.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            doubleHistogram.recordValue(0.001); // Makes some internal shifts happen.
            intervalDoubleHistogramRecorder1.recordValue(0.001); // Makes some internal shifts happen.
            intervalDoubleHistogramRecorder2.recordValue(0.001); // Makes some internal shifts happen.
        }

        Histogram histogram2 = intervalHistogramRecorder1.getIntervalHistogram();
        Assert.assertEquals(histogram, histogram2);

        intervalHistogramRecorder2.getIntervalHistogramInto(histogram2);
        Assert.assertEquals(histogram, histogram2);

        DoubleHistogram doubleHistogram2 = intervalDoubleHistogramRecorder1.getIntervalHistogram();
        Assert.assertEquals(doubleHistogram, doubleHistogram2);

        intervalDoubleHistogramRecorder2.getIntervalHistogramInto(doubleHistogram2);
        Assert.assertEquals(doubleHistogram, doubleHistogram2);

        for (int i = 0; i < 5000; i++) {
            histogram.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalHistogramRecorder1.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalHistogramRecorder2.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            doubleHistogram.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalDoubleHistogramRecorder1.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalDoubleHistogramRecorder2.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            doubleHistogram.recordValue(0.001); // Makes some internal shifts happen.
            intervalDoubleHistogramRecorder1.recordValue(0.001); // Makes some internal shifts happen.
            intervalDoubleHistogramRecorder2.recordValue(0.001); // Makes some internal shifts happen.
        }

        Histogram histogram3 = intervalHistogramRecorder1.getIntervalHistogram();

        Histogram sumHistogram = histogram2.copy();
        sumHistogram.add(histogram3);
        Assert.assertEquals(histogram, sumHistogram);

        DoubleHistogram doubleHistogram3 = intervalDoubleHistogramRecorder1.getIntervalHistogram();

        DoubleHistogram sumDoubleHistogram = doubleHistogram2.copy();
        sumDoubleHistogram.add(doubleHistogram3);
        Assert.assertEquals(doubleHistogram, sumDoubleHistogram);

        intervalHistogramRecorder2.getIntervalHistogram();
        intervalDoubleHistogramRecorder2.getIntervalHistogram();

        for (int i = 5000; i < 10000; i++) {
            histogram.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalHistogramRecorder1.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalHistogramRecorder2.recordValueWithExpectedInterval(3000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            doubleHistogram.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalDoubleHistogramRecorder1.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            intervalDoubleHistogramRecorder2.recordValueWithExpectedInterval(5000 * i /* 1 msec */, 10000 /* 10 msec expected interval */);
            doubleHistogram.recordValue(0.001); // Makes some internal shifts happen.
            intervalDoubleHistogramRecorder1.recordValue(0.001); // Makes some internal shifts happen.
            intervalDoubleHistogramRecorder2.recordValue(0.001); // Makes some internal shifts happen.
        }

        Histogram histogram4 = intervalHistogramRecorder1.getIntervalHistogram();
        histogram4.add(histogram3);
        Assert.assertEquals(histogram4, histogram2);

        intervalHistogramRecorder2.getIntervalHistogramInto(histogram4);
        histogram4.add(histogram3);
        Assert.assertEquals(histogram4, histogram2);

        DoubleHistogram doubleHistogram4 = intervalDoubleHistogramRecorder1.getIntervalHistogram();
        doubleHistogram4.add(doubleHistogram3);
        Assert.assertEquals(doubleHistogram4, doubleHistogram2);

        doubleHistogram4.reset();
        intervalDoubleHistogramRecorder2.getIntervalHistogramInto(doubleHistogram4);
        doubleHistogram4.add(doubleHistogram3);
        Assert.assertEquals(doubleHistogram4, doubleHistogram2);
    }
}
