/**
 * hdr_dbl_histogram_test.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */
#include <stdlib.h>
#include <stdio.h>
#include <math.h>

#include <hdr_dbl_histogram.h>

#include "minunit.h"

int tests_run = 0;

const int64_t TRACKABLE_VALUE_RANGE_SIZE = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
const int32_t SIGNIFICANT_FIGURES = 3;

char* test_construct_argument_ranges()
{
    struct hdr_dbl_histogram* h = NULL;

    mu_assert("highest_to_lowest_value_ratio must be >= 2", 0 != hdr_dbl_init(1, SIGNIFICANT_FIGURES, &h));
    mu_assert("significant_figures must be > 0", 0 != hdr_dbl_init(TRACKABLE_VALUE_RANGE_SIZE, -1, &h));
    mu_assert(
            "(highest_to_lowest_value_ratio * 10^significant_figures) must be < (1L << 61)",
            0 != hdr_dbl_init(TRACKABLE_VALUE_RANGE_SIZE, 6, &h));

    return 0;
}

/*
    @Test
    public void testConstructionArgumentGets() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        // Record 1.0, and verify that the range adjust to it:
        histogram.recordValue(Math.pow(2.0, 20));
        histogram.recordValue(1.0);
        Assert.assertEquals(1.0, histogram.getCurrentLowestTrackableNonZeroValue(), 0.001);
        Assert.assertEquals(trackableValueRangeSize, histogram.getHighestToLowestValueRatio(), 0.001);
        Assert.assertEquals(numberOfSignificantValueDigits, histogram.getNumberOfSignificantValueDigits(), 0.001);

        DoubleHistogram histogram2 = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        // Record a larger value, and verify that the range adjust to it too:
        histogram2.recordValue(2048.0 * 1024.0 * 1024.0);
        Assert.assertEquals(2048.0 * 1024.0 * 1024.0, histogram2.getCurrentLowestTrackableNonZeroValue(), 0.001);

        DoubleHistogram histogram3 = new DoubleHistogram(trackableValueRangeSize, numberOfSignificantValueDigits);
        // Record a value that is 1000x outside of the initially set range, which should scale us by 1/1024x:
        histogram3.recordValue(1/1000.0);
        Assert.assertEquals(1.0/1024, histogram3.getCurrentLowestTrackableNonZeroValue(), 0.001);
    }
 */

char* test_construction_argument_gets()
{
    struct hdr_dbl_histogram* h;

    mu_assert("Should construct", 0 == hdr_dbl_init(TRACKABLE_VALUE_RANGE_SIZE, SIGNIFICANT_FIGURES, &h));
    mu_assert("Should record", hdr_dbl_record_value(h, pow(2.0, 20)));
    mu_assert("Should record", hdr_dbl_record_value(h, 1.0));

    mu_assert("Significant figures", SIGNIFICANT_FIGURES == h->values.significant_figures);
    mu_assert("Significant figures", TRACKABLE_VALUE_RANGE_SIZE == h->highest_to_lowest_value_ratio);
    mu_assert("Lowest value should be correct", compare_double(1.0, h->current_lowest_value, 0.001));

    return 0;
}

static struct mu_result all_tests()
{
    //mu_run_test(test_construct_argument_ranges);
    //mu_run_test(test_construction_argument_gets);

    mu_ok;
}

int hdr_dbl_histogram_run_tests()
{
    struct mu_result result = all_tests();

    if (result.message != 0)
    {
        printf("hdr_histogram_log_test.%s(): %s\n", result.test, result.message);
    }
    else
    {
        printf("ALL TESTS PASSED\n");
    }

    printf("Tests run: %d\n", tests_run);

    return (int) result.message;
}

int main(int argc, char **argv)
{
    return hdr_dbl_histogram_run_tests();
}
