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

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JUnit test for {@link Histogram}
 */
public class ConcurrentHistogramTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000 * 1000; // e.g. for 1 hr in usec units
    volatile boolean doRun = true;
    volatile boolean waitToRun = true;

    @Test
    public void testConcurrentAutoSizedRecording() throws Exception {
        for (int i = 0; i < 10; i++) {
            doConcurrentRecordValues();
        }
    }

    public void doConcurrentRecordValues() throws Exception {
        ConcurrentHistogram histogram = new ConcurrentHistogram(2);
        ValueRecorder valueRecorders[] = new ValueRecorder[12];
        doRun = true;
        waitToRun = true;
        for (int i = 0; i < valueRecorders.length; i++) {
            valueRecorders[i] = new ValueRecorder(histogram);
            valueRecorders[i].start();
        }
        waitToRun = false;
        Thread.sleep(50);
        doRun = false;
        long sumOfCounts = 0;
        for(ValueRecorder v: valueRecorders) {
            while (!v.done) {}
            sumOfCounts += v.count;
        }
        Assert.assertEquals("totalCount must be equal to sum of counts",
                sumOfCounts,
                histogram.getTotalCount());
    }

    static AtomicLong valueRecorderId = new AtomicLong(42);

    class ValueRecorder extends Thread {
        final ConcurrentHistogram histogram;
        long count = 0;
        volatile boolean done = false;

        Random random = new Random(valueRecorderId.getAndIncrement());

        ValueRecorder(ConcurrentHistogram histogram) {
            this.histogram = histogram;
        }

        public void run() {
            while(waitToRun) {
                // wait for doRun to be set.
            }
            while (doRun) {
                histogram.recordValue((long)(highestTrackableValue * random.nextDouble()));
                count++;
            }
            done = true;
        }
    }

}
