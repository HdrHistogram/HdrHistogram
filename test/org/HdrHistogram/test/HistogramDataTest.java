/**
 * HistogramDataTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 * @version 1.0.1
 */

package org.HdrHistogram.test;

import org.HdrHistogram.*;

import org.junit.*;

/**
 * JUnit test for {@link HistogramData}
 */
public class HistogramDataTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // 1 hour in usec units
    static final int numberOfSignificantValueDigits = 3; // Maintain at least 3 decimal points of accuracy
    static final Histogram histogram;


    static {
        histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        // Log hypothetical scenario: 100 seconds of "perfect" 1msec results, sampled
        // 100 times per second (10,000 results), followed by a 100 second pause with
        // a single (100 second) recorded result. Recording is done indicating an expected
        // interval between samples of 10 msec:
        for (int i = 0; i < 10000; i++) {
            histogram.recordValue(1000 /* 1 msec */, 10000 /* 10 msec expected interval */);
        }
        histogram.recordValue(100000000L /* 100 sec */, 10000 /* 10 msec expected interval */);
    }

    @Test
    public void testGetTotalCount() throws Exception {
        // The overflow value should count in the total count:
        Assert.assertEquals("Raw total count is 10,001",
                10001L, histogram.getRawHistogramData().getTotalCount());
        Assert.assertEquals("Total count is 20,000",
                20000L, histogram.getHistogramData().getTotalCount());
    }

    @Test
    public void testGetMaxValue() throws Exception {
        Assert.assertTrue(
                histogram.valuesAreEquivalent(100L * 1000 * 1000,
                        histogram.getHistogramData().getMaxValue()));
    }

    @Test
    public void testGetMinValue() throws Exception {
        Assert.assertTrue(
                histogram.valuesAreEquivalent(1000,
                        histogram.getHistogramData().getMinValue()));
    }

    @Test
    public void testGetMean() throws Exception {
        double expectedRawMean = ((10000.0 * 1000) + (1.0 * 100000000))/10001; /* direct avg. of raw results */
        double expectedMean = (1000.0 + 50000000.0)/2; /* avg. 1 msec for half the time, and 50 sec for other half */
        // We expect to see the mean to be accurate to ~3 decimal points (~0.1%):
        Assert.assertEquals("Raw mean is " + expectedRawMean + " +/- 0.1%",
                expectedRawMean, histogram.getRawHistogramData().getMean(), expectedRawMean * 0.001);
        Assert.assertEquals("Mean is " + expectedMean + " +/- 0.1%",
                expectedMean, histogram.getHistogramData().getMean(), expectedMean * 0.001);
    }

    @Test
    public void testGetStdDeviation() throws Exception {
        double expectedRawMean = ((10000.0 * 1000) + (1.0 * 100000000))/10001; /* direct avg. of raw results */
        double expectedRawStdDev =
                Math.sqrt(
                    ((10000.0 * Math.pow((1000.0 - expectedRawMean), 2)) +
                            Math.pow((100000000.0 - expectedRawMean), 2)) /
                            10001);

        double expectedMean = (1000.0 + 50000000.0)/2; /* avg. 1 msec for half the time, and 50 sec for other half */
        double expectedSquareDeviationSum = 10000 * Math.pow((1000.0 - expectedMean), 2);
        for (long value = 10000; value <= 100000000; value += 10000) {
            expectedSquareDeviationSum += Math.pow((value - expectedMean), 2);
        }
        double expectedStdDev = Math.sqrt(expectedSquareDeviationSum / 20000);

        // We expect to see the standard deviations to be accurate to ~3 decimal points (~0.1%):
        Assert.assertEquals("Raw standard deviation is " + expectedRawStdDev + " +/- 0.1%",
                expectedRawStdDev, histogram.getRawHistogramData().getStdDeviation(), expectedRawStdDev * 0.001);
        Assert.assertEquals("Standard deviation is " + expectedStdDev + " +/- 0.1%",
                expectedStdDev, histogram.getHistogramData().getStdDeviation(), expectedStdDev * 0.001);
    }

    @Test
    public void testGetValueAtPercentile() throws Exception {
        Assert.assertEquals("raw 30%'ile is 1 msec +/- 0.1%",
                1000.0, (double) histogram.getRawHistogramData().getValueAtPercentile(30.0),
                1000.0 * 0.001);
        Assert.assertEquals("raw 99%'ile is 1 msec +/- 0.1%",
                1000.0, (double) histogram.getRawHistogramData().getValueAtPercentile(99.0),
                1000.0 * 0.001);
        Assert.assertEquals("raw 99.99%'ile is 1 msec +/- 0.1%",
                1000.0, (double) histogram.getRawHistogramData().getValueAtPercentile(99.99)
                , 1000.0 * 0.001);
        Assert.assertEquals("raw 99.999%'ile is 100 sec +/- 0.1%",
                100000000.0, (double) histogram.getRawHistogramData().getValueAtPercentile(99.999),
                100000000.0 * 0.001);
        Assert.assertEquals("raw 100%'ile is 100 sec +/- 0.1%",
                100000000.0, (double) histogram.getRawHistogramData().getValueAtPercentile(100.0),
                100000000.0 * 0.001);

        Assert.assertEquals("30%'ile is 1 msec +/- 0.1%",
                1000.0, (double) histogram.getHistogramData().getValueAtPercentile(30.0),
                1000.0 * 0.001);
        Assert.assertEquals("50%'ile is 1 msec +/- 0.1%",
                1000.0, (double) histogram.getHistogramData().getValueAtPercentile(50.0),
                1000.0 * 0.001);
        Assert.assertEquals("75%'ile is 50 sec +/- 0.1%",
                50000000.0, (double) histogram.getHistogramData().getValueAtPercentile(75.0),
                50000000.0 * 0.001);
        Assert.assertEquals("90%'ile is 80 sec +/- 0.1%",
                80000000.0, (double) histogram.getHistogramData().getValueAtPercentile(90.0),
                80000000.0 * 0.001);
        Assert.assertEquals("99%'ile is 98 sec +/- 0.1%",
                98000000.0, (double) histogram.getHistogramData().getValueAtPercentile(99.0),
                98000000.0 * 0.001);
        Assert.assertEquals("99.999%'ile is 100 sec +/- 0.1%",
                100000000.0, (double) histogram.getHistogramData().getValueAtPercentile(99.999),
                100000000.0 * 0.001);
        Assert.assertEquals("100%'ile is 100 sec +/- 0.1%",
                100000000.0, (double) histogram.getHistogramData().getValueAtPercentile(100.0),
                100000000.0 * 0.001);
    }

    @Test
    public void testGetPercentileAtOrBelowValue() throws Exception {
        Assert.assertEquals("Raw percentile at or below 5 msec is 99.99% +/- 0.0001",
                99.99,
                histogram.getRawHistogramData().getPercentileAtOrBelowValue(5000), 0.0001);
        Assert.assertEquals("Percentile at or below 5 msec is 50% +/- 0.0001%",
                50.0,
                histogram.getHistogramData().getPercentileAtOrBelowValue(5000), 0.0001);
        Assert.assertEquals("Percentile at or below 100 sec is 100% +/- 0.0001%",
                100.0,
                histogram.getHistogramData().getPercentileAtOrBelowValue(100000000L), 0.0001);
    }

    @Test
    public void testGetCountBetweenValues() throws Exception {
        Assert.assertEquals("Count of raw values between 1 msec and 1 msec is 1",
                10000, histogram.getRawHistogramData().getCountBetweenValues(1000L, 1000L));
        Assert.assertEquals("Count of raw values between 5 msec and 150 sec is 1",
                1, histogram.getRawHistogramData().getCountBetweenValues(5000L, 150000000L));
        Assert.assertEquals("Count of values between 5 msec and 150 sec is 10,000",
                10000, histogram.getHistogramData().getCountBetweenValues(5000L, 150000000L));
    }

    @Test
    public void testGetCountAtValue() throws Exception {
        Assert.assertEquals("Count of raw values at 10 msec is 0",
                0, histogram.getRawHistogramData().getCountAtValue(10000L));
        Assert.assertEquals("Count of values at 10 msec is 0",
                1, histogram.getHistogramData().getCountAtValue(10000L));
        Assert.assertEquals("Count of raw values at 1 msec is 10,000",
                10000, histogram.getRawHistogramData().getCountAtValue(1000L));
        Assert.assertEquals("Count of values at 1 msec is 10,000",
                10000, histogram.getHistogramData().getCountAtValue(1000L));
    }

    @Test
    public void testPercentiles() throws Exception {

    }

    @Test
    public void testLinearBucketValues() throws Exception {
        int index = 0;
        // Note that using linear buckets should work "as expected" as long as the number of linear buckets
        // is lower than the resolution level determined by largestValueWithSingleUnitResolution
        // (2000 in this case). Above that count, some of the linear buckets can end up rounded up in size
        // (to the nearest local resolution unit level), which can result in a smaller number of buckets that
        // expected covering the range.

        // Iterate raw data using linear buckets of 100 msec each.
        for (HistogramIterationValue v : histogram.getRawHistogramData().linearBucketValues(100000)) {
            long countAddedInThisBucket = v.getCountAddedInThisIterationStep();
            if (index == 0) {
                Assert.assertEquals("Raw Linear 100 msec bucket # 0 added a count of 10000",
                        10000, countAddedInThisBucket);
            } else if (index == 999) {
                Assert.assertEquals("Raw Linear 100 msec bucket # 999 added a count of 1",
                        1, countAddedInThisBucket);
            } else {
                Assert.assertEquals("Raw Linear 100 msec bucket # " + index + " added a count of 0",
                        0 , countAddedInThisBucket);
            }
            index++;
        }
        Assert.assertEquals(1000, index - 1);

        index = 0;
        long totalAddedCounts = 0;
        // Iterate data using linear buckets of 1 sec each.
        for (HistogramIterationValue v : histogram.getHistogramData().linearBucketValues(10000)) {
            long countAddedInThisBucket = v.getCountAddedInThisIterationStep();
            if (index == 0) {
                Assert.assertEquals("Linear 1 sec bucket # 0 [" +
                        v.getValueIteratedFrom() + ".." + v.getValueIteratedTo() +
                        "] added a count of 10001",
                        10001, countAddedInThisBucket);
            }
            // Because value resolution is low enough (3 digits) that multiple linear buckets will end up
            // residing in a single value-equivalent range, some linear buckets will have counts of 2 or
            // more, and some will have 0 (when the first bucket in the equivalent range was the one that
            // got the total count bump).
            // However, we can still verify the sum of counts added in all the buckets...
            totalAddedCounts += v.getCountAddedInThisIterationStep();
            index++;
        }
        Assert.assertEquals("There should be 10001 linear buckets of size 10001 usec between 0 and 1 sec.",
                10001, index);
        Assert.assertEquals("Total added counts should be 20000", 20000, totalAddedCounts);

    }

    @Test
    public void testLogarithmicBucketValues() throws Exception {
        int index = 0;
        // Iterate raw data using logarithmic buckets starting at 10 msec.
        for (HistogramIterationValue v : histogram.getRawHistogramData().logarithmicBucketValues(10000, 2)) {
            long countAddedInThisBucket = v.getCountAddedInThisIterationStep();
            if (index == 0) {
                Assert.assertEquals("Raw Logarithmic 10 msec bucket # 0 added a count of 10000",
                        10000, countAddedInThisBucket);
            } else if (index == 14) {
                Assert.assertEquals("Raw Logarithmic 10 msec bucket # 14 added a count of 1",
                        1, countAddedInThisBucket);
            } else {
                Assert.assertEquals("Raw Logarithmic 100 msec bucket # " + index + " added a count of 0",
                        0, countAddedInThisBucket);
            }
            index++;
        }
        Assert.assertEquals(14, index - 1);

        index = 0;
        long totalAddedCounts = 0;
        // Iterate data using linear buckets of 1 sec each.
        for (HistogramIterationValue v : histogram.getHistogramData().logarithmicBucketValues(10000, 2)) {
            long countAddedInThisBucket = v.getCountAddedInThisIterationStep();
            if (index == 0) {
                Assert.assertEquals("Logarithmic 10 msec bucket # 0 [" +
                        v.getValueIteratedFrom() + ".." + v.getValueIteratedTo() +
                        "] added a count of 10001",
                        10001, countAddedInThisBucket);
            }
            totalAddedCounts += v.getCountAddedInThisIterationStep();
            index++;
        }
        Assert.assertEquals("There should be 14 Logarithmic buckets of size 10001 usec between 0 and 1 sec.",
                14, index - 1);
        Assert.assertEquals("Total added counts should be 20000", 20000, totalAddedCounts);
    }

    @Test
    public void testRecordedValues() throws Exception {
        int index = 0;
        // Iterate raw data by stepping through every value that ahs a count recorded:
        for (HistogramIterationValue v : histogram.getRawHistogramData().recordedValues()) {
            long countAddedInThisBucket = v.getCountAddedInThisIterationStep();
            if (index == 0) {
                Assert.assertEquals("Raw recorded value bucket # 0 added a count of 10000",
                        10000, countAddedInThisBucket);
            } else {
                Assert.assertEquals("Raw recorded value bucket # " + index + " added a count of 1",
                        1, countAddedInThisBucket);
            }
            index++;
        }
        Assert.assertEquals(2, index);

        index = 0;
        long totalAddedCounts = 0;
        // Iterate data using linear buckets of 1 sec each.
        for (HistogramIterationValue v : histogram.getHistogramData().recordedValues()) {
            long countAddedInThisBucket = v.getCountAddedInThisIterationStep();
            if (index == 0) {
                Assert.assertEquals("Recorded bucket # 0 [" +
                        v.getValueIteratedFrom() + ".." + v.getValueIteratedTo() +
                        "] added a count of 10000",
                        10000, countAddedInThisBucket);
            }
            Assert.assertTrue("The count in recorded bucket #" + index + " is not 0",
                    v.getCountAtValueIteratedTo() != 0);
            Assert.assertEquals("The count in recorded bucket #" + index +
                    " is exactly the amount added since the last iteration ",
                    v.getCountAtValueIteratedTo(), v.getCountAddedInThisIterationStep());
            totalAddedCounts += v.getCountAddedInThisIterationStep();
            index++;
        }
        Assert.assertEquals("Total added counts should be 20000", 20000, totalAddedCounts);
    }

    @Test
    public void testAllValues() throws Exception {
        int index = 0;
        long latestValueAtIndex = 0;
        // Iterate raw data by stepping through every value that ahs a count recorded:
        for (HistogramIterationValue v : histogram.getRawHistogramData().allValues()) {
            long countAddedInThisBucket = v.getCountAddedInThisIterationStep();
            if (index == 1000) {
                Assert.assertEquals("Raw allValues bucket # 0 added a count of 10000",
                        10000, countAddedInThisBucket);
            } else if (histogram.valuesAreEquivalent(v.getValueIteratedTo(), 100000000)) {
                Assert.assertEquals("Raw allValues value bucket # " + index + " added a count of 1",
                        1, countAddedInThisBucket);
            } else {
                Assert.assertEquals("Raw allValues value bucket # " + index + " added a count of 0",
                        0, countAddedInThisBucket);
            }
            latestValueAtIndex = v.getValueIteratedTo();
            index++;
        }
        Assert.assertEquals("Count at latest value iterated to is 1",
                1, histogram.getRawHistogramData().getCountAtValue(latestValueAtIndex));

        index = 0;
        long totalAddedCounts = 0;
        // Iterate data using linear buckets of 1 sec each.
        for (HistogramIterationValue v : histogram.getHistogramData().allValues()) {
            long countAddedInThisBucket = v.getCountAddedInThisIterationStep();
            if (index == 1000) {
                Assert.assertEquals("AllValues bucket # 0 [" +
                        v.getValueIteratedFrom() + ".." + v.getValueIteratedTo() +
                        "] added a count of 10000",
                        10000, countAddedInThisBucket);
            }
            Assert.assertEquals("The count in AllValues bucket #" + index +
                    " is exactly the amount added since the last iteration ",
                    v.getCountAtValueIteratedTo(), v.getCountAddedInThisIterationStep());
            totalAddedCounts += v.getCountAddedInThisIterationStep();
            index++;
        }
        Assert.assertEquals("Total added counts should be 20000", 20000, totalAddedCounts);
    }
}
