/**
 * hdrh_test.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <errno.h>

#include <stdio.h>
#include <hdr_histogram.h>
#include "minunit.h"

bool compare_percentile(int64_t a, double b, double variation)
{
    return fabs(a - b) <= b * variation;
}

int tests_run = 0;

static struct hdr_histogram* raw_histogram = NULL;
static struct hdr_histogram* cor_histogram = NULL;

static void load_histograms()
{
    int i;
    if (raw_histogram)
    {
        free(raw_histogram);
    }

    hdrh_alloc(3600L * 1000 * 1000, 3, &raw_histogram);

    if (cor_histogram)
    {
        free(cor_histogram);
    }

    hdrh_alloc(3600L * 1000 * 1000, 3, &cor_histogram);

    for (i = 0; i < 10000; i++)
    {
        hdrh_record_value(raw_histogram, 1000L);
        hdrh_record_corrected_value(cor_histogram, 1000L, 10000L);
    }

    hdrh_record_value(raw_histogram, 100000000L);
    hdrh_record_corrected_value(cor_histogram, 100000000L, 10000L);
}

static char* test_create()
{
    struct hdr_histogram* h = NULL;
    int r = hdrh_alloc(36000000, 4, &h);

    mu_assert("Failed to allocate hdr_histogram", r == 0);
    mu_assert("Failed to allocate hdr_histogram", h != 0);

    free(h);

    return 0;
}

static char* test_invalid_significant_figures()
{
    struct hdr_histogram* h = NULL;

    int r = hdrh_alloc(36000000, 2, &h);
    mu_assert("Result was not -1",      r == -1);
    mu_assert("Histogram was not null", h == 0);

    return 0;
}

static char* test_total_count()
{
    load_histograms();

    mu_assert("Total raw count != 10001",       raw_histogram->total_count == 10001);
    mu_assert("Total corrected count != 20000", cor_histogram->total_count == 20000);

    return 0;
}

static char* test_get_max_value()
{
    load_histograms();


    int64_t actual_raw_max = hdrh_max(raw_histogram);
    mu_assert("hdrh_max(raw_histogram) != 100000000L",
              hdrh_values_are_equivalent(raw_histogram, actual_raw_max, 100000000L));
    int64_t actual_cor_max = hdrh_max(cor_histogram);
    mu_assert("hdrh_max(cor_histogram) != 100000000L",
              hdrh_values_are_equivalent(cor_histogram, actual_cor_max, 100000000L));

    return 0;
}

static char* test_get_min_value()
{
    load_histograms();

    mu_assert("hdrh_min(raw_histogram) != 1000", hdrh_min(raw_histogram) == 1000L);
    mu_assert("hdrh_min(cor_histogram) != 1000", hdrh_min(cor_histogram) == 1000L);

    return 0;
}

static char* test_percentiles()
{
    load_histograms();

    mu_assert("Value at 30% not 1000.0",
              compare_percentile(hdrh_value_at_percentile(raw_histogram, 30.0), 1000.0, 0.001));
    mu_assert("Value at 99% not 1000.0",
              compare_percentile(hdrh_value_at_percentile(raw_histogram, 99.0), 1000.0, 0.001));
    mu_assert("Value at 99.99% not 1000.0",
              compare_percentile(hdrh_value_at_percentile(raw_histogram, 99.99), 1000.0, 0.001));
    mu_assert("Value at 99.999% not 100000000.0",
              compare_percentile(hdrh_value_at_percentile(raw_histogram, 99.999), 100000000.0, 0.001));
    mu_assert("Value at 100% not 100000000.0",
              compare_percentile(hdrh_value_at_percentile(raw_histogram, 100.0), 100000000.0, 0.001));

    mu_assert("Value at 30% not 1000.0",
              compare_percentile(hdrh_value_at_percentile(cor_histogram, 30.0), 1000.0, 0.001));
    mu_assert("Value at 50% not 1000.0",
              compare_percentile(hdrh_value_at_percentile(cor_histogram, 50.0), 1000.0, 0.001));
    mu_assert("Value at 75% not 50000000.0",
              compare_percentile(hdrh_value_at_percentile(cor_histogram, 75.0), 50000000.0, 0.001));
    mu_assert("Value at 90% not 80000000.0",
              compare_percentile(hdrh_value_at_percentile(cor_histogram, 90.0), 80000000.0, 0.001));
    mu_assert("Value at 99% not 98000000.0",
              compare_percentile(hdrh_value_at_percentile(cor_histogram, 99.0), 98000000.0, 0.001));
    mu_assert("Value at 99.999% not 100000000.0",
              compare_percentile(hdrh_value_at_percentile(cor_histogram, 99.999), 100000000.0, 0.001));
    mu_assert("Value at 100% not 100000000.0",
              compare_percentile(hdrh_value_at_percentile(cor_histogram, 100.0), 100000000.0, 0.001));

    return 0;
}

static char* test_recorded_values()
{
    load_histograms();
    struct hdrh_recorded_iter iter;
    int index;

    // Raw Histogram
    hdrh_recorded_iter_init(&iter, raw_histogram);

    index = 0;
    while (hdrh_recorded_iter_next(&iter))
    {
        int64_t count_added_in_this_bucket = iter.count_added_in_this_iteration_step;
        if (index == 0)
        {
            mu_assert("Value at 0 is not 10000", count_added_in_this_bucket == 10000);
        }
        else
        {
            mu_assert("Value at 1 is not 1", count_added_in_this_bucket == 1);
        }

        index++;
    }
    mu_assert("Should have encountered 2 values", index == 2);

    // Corrected Histogram
    hdrh_recorded_iter_init(&iter, cor_histogram);

    index = 0;
    int64_t total_added_count = 0;
    while (hdrh_recorded_iter_next(&iter))
    {
        int64_t count_added_in_this_bucket = iter.count_added_in_this_iteration_step;
        if (index == 0)
        {
            mu_assert("Count at 0 is not 10000", count_added_in_this_bucket == 10000);
        }
        mu_assert("Count should not be 0", iter.iter.count_at_index != 0);
        mu_assert("Count at value iterated to should be count added in this step",
                  iter.iter.count_at_index == count_added_in_this_bucket);
        total_added_count += count_added_in_this_bucket;
        index++;
    }
    mu_assert("Total counts should be 20000", total_added_count == 20000);

    return 0;
}

static char* test_linear_values()
{
    load_histograms();
    struct hdrh_linear_iter iter;
    int index;

    // Raw Histogram
    hdrh_linear_iter_init(&iter, raw_histogram, 100000);
    index = 0;
    while (hdrh_linear_iter_next(&iter))
    {
        int64_t count_added_in_this_bucket = iter.count_added_in_this_iteration_step;

        if (index == 0)
        {
            mu_assert("Count at 0 is not 10000", count_added_in_this_bucket == 10000);
        }
        else if (index == 999)
        {
            mu_assert("Count at 999 is not 1", count_added_in_this_bucket == 1);
        }
        else
        {
            mu_assert("Count should be 0", count_added_in_this_bucket == 0);
        }

        index++;
    }
    mu_assert("Should of met 1000 values", index == 1000);

    // Corrected Histogram

    hdrh_linear_iter_init(&iter, cor_histogram, 10000);
    index = 0;
    int64_t total_added_count = 0;
    while (hdrh_linear_iter_next(&iter))
    {
        int64_t count_added_in_this_bucket = iter.count_added_in_this_iteration_step;

        if (index == 0)
        {
            mu_assert("Count at 0 is not 10001", count_added_in_this_bucket == 10001);
        }

        total_added_count += count_added_in_this_bucket;
        index++;
    }
    mu_assert("Should of met 10001 values", index == 10000);
    mu_assert("Should of met 20000 counts", total_added_count == 20000);

    return 0;
}

static char* test_logarithmic_values()
{
    load_histograms();
    struct hdrh_log_iter iter;
    int index;

    hdrh_log_iter_init(&iter, raw_histogram, 10000, 2.0);
    index = 0;

    while(hdrh_log_iter_next(&iter))
    {
        long count_added_in_this_bucket = iter.count_added_in_this_iteration_step;
        if (index == 0)
        {
            mu_assert("Raw Logarithmic 10 msec bucket # 0 added a count of 10000", 10000 == count_added_in_this_bucket);
        }
        else if (index == 14)
        {
            mu_assert("Raw Logarithmic 10 msec bucket # 14 added a count of 1", 1 == count_added_in_this_bucket);            
        }
        else
        {
            mu_assert("Raw Logarithmic 10 msec bucket added a count of 0", 0 == count_added_in_this_bucket);            
        }

        index++;
    }

    mu_assert("Should of seen 14 values", index - 1 == 14);

    hdrh_log_iter_init(&iter, cor_histogram, 10000, 2.0);
    index = 0;
    int total_added_count = 0;
    while (hdrh_log_iter_next(&iter))
    {
        long count_added_in_this_bucket = iter.count_added_in_this_iteration_step;

        if (index == 0)
        {
            mu_assert("Corrected Logarithmic 10 msec bucket # 0 added a count of 10001", 10001 == count_added_in_this_bucket);
        }
        total_added_count += count_added_in_this_bucket;
        index++;
    }

    mu_assert("Should of seen 14 values", index - 1 == 14);
    mu_assert("Should of seen count of 20000", total_added_count == 20000);

    return 0;
}

static struct mu_result all_tests()
{
    mu_run_test(test_create);
    mu_run_test(test_invalid_significant_figures);
    mu_run_test(test_total_count);
    mu_run_test(test_get_min_value);
    mu_run_test(test_get_max_value);
    mu_run_test(test_percentiles);
    mu_run_test(test_recorded_values);
    mu_run_test(test_linear_values);
    mu_run_test(test_logarithmic_values);

    mu_ok;
}

int main(int argc, char **argv)
{
    struct mu_result result = all_tests();

    if (result.message != 0)
    {
        printf("%s(): %s\n", result.test, result.message);
    }
    else
    {
        printf("ALL TESTS PASSED\n");
    }

    printf("Tests run: %d\n", tests_run);

    return result.message != 0;
}
