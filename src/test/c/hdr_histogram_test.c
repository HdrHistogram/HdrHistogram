/**
 * hdr_histogram_test.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <errno.h>
#include <inttypes.h>

#include <stdio.h>
#include <hdr_histogram.h>

#include "minunit.h"

static bool compare_int64(int64_t a, int64_t b)
{
    if (a == b)
    {
        return true;
    }

    printf("[compare_int64] %" PRIu64 " == %" PRIu64 " == false\n", a, b);
    return false;
}

bool compare_values(double a, double b, double variation)
{
    return compare_double(a, b, b * variation);
}

bool compare_percentile(int64_t a, double b, double variation)
{
    return compare_values((double) a, b, variation);
    // return fabs(a - b) <= b * variation;
}


int tests_run = 0;

static struct hdr_histogram* raw_histogram = NULL;
static struct hdr_histogram* cor_histogram = NULL;
static struct hdr_histogram* scaled_raw_histogram = NULL;
static struct hdr_histogram* scaled_cor_histogram = NULL;

static void load_histograms()
{
    const int64_t highest_trackable_value = 3600L * 1000 * 1000;
    const int32_t significant_figures = 3;
    const int64_t interval = 10000L;
    const int64_t scale = 512;
    const int64_t scaled_interval = interval * scale;

    int i;
    if (raw_histogram)
    {
        free(raw_histogram);
    }

    hdr_init(1, highest_trackable_value, significant_figures, &raw_histogram);

    if (cor_histogram)
    {
        free(cor_histogram);
    }

    hdr_init(1, highest_trackable_value, significant_figures, &cor_histogram);

    if (scaled_raw_histogram)
    {
        free(scaled_raw_histogram);
    }

    hdr_init(1000, highest_trackable_value * 512, significant_figures, &scaled_raw_histogram);

    if (scaled_cor_histogram)
    {
        free(scaled_cor_histogram);
    }

    hdr_init(1000, highest_trackable_value * 512, significant_figures, &scaled_cor_histogram);

    for (i = 0; i < 10000; i++)
    {
        hdr_record_value(raw_histogram, 1000L);
        hdr_record_corrected_value(cor_histogram, 1000L, interval);

        hdr_record_value(scaled_raw_histogram, 1000L * scale);
        hdr_record_corrected_value(scaled_cor_histogram, 1000L * scale, scaled_interval);
    }

    hdr_record_value(raw_histogram, 100000000L);
    hdr_record_corrected_value(cor_histogram, 100000000L, 10000L);

    hdr_record_value(scaled_raw_histogram, 100000000L * scale);
    hdr_record_corrected_value(scaled_cor_histogram, 100000000L * scale, scaled_interval);
}

static char* test_create()
{
    struct hdr_histogram* h = NULL;
    int r = hdr_alloc(36000000, 4, &h);
    size_t s = hdr_get_memory_size(h);

    mu_assert("Failed to allocate hdr_histogram", r == 0);
    mu_assert("Failed to allocate hdr_histogram", h != NULL);
    mu_assert("Size is incorrect", compare_int64(s, 1704064));

    free(h);

    return 0;
}

static char* test_create_with_large_values()
{
    struct hdr_histogram* h = NULL;
    int r = hdr_init(20000000, 100000000, 5, &h);
    mu_assert("Didn't create", r == 0);

    hdr_record_value(h, 100000000);
    hdr_record_value(h, 20000000);
    hdr_record_value(h, 30000000);

    mu_assert(
        "50.0% Percentile",
        hdr_values_are_equivalent(h, 20000000, hdr_value_at_percentile(h, 50.0)));

    mu_assert(
        "83.33% Percentile",
        hdr_values_are_equivalent(h, 30000000, hdr_value_at_percentile(h, 83.33)));

    mu_assert(
        "83.34% Percentile",
        hdr_values_are_equivalent(h, 100000000, hdr_value_at_percentile(h, 83.34)));

    mu_assert(
        "99.0% Percentile",
        hdr_values_are_equivalent(h, 100000000, hdr_value_at_percentile(h, 99.0)));

    return 0;
}

static char* test_invalid_significant_figures()
{
    struct hdr_histogram* h = NULL;

    int r = hdr_alloc(36000000, -1, &h);
    mu_assert("Result was not EINVAL", r == EINVAL);
    mu_assert("Histogram was not null", h == 0);

    r = hdr_alloc(36000000, 6, &h);
    mu_assert("Result was not EINVAL", r == EINVAL);
    mu_assert("Histogram was not null", h == 0);

    return 0;
}

static char* test_invalid_init()
{
    struct hdr_histogram* h = NULL;

    int r = hdr_init(0, 64*1024, 2, &h);
    mu_assert("Result was not EINVAL", r == EINVAL);

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


    int64_t actual_raw_max = hdr_max(raw_histogram);
    mu_assert("hdr_max(raw_histogram) != 100000000L",
              hdr_values_are_equivalent(raw_histogram, actual_raw_max, 100000000L));
    int64_t actual_cor_max = hdr_max(cor_histogram);
    mu_assert("hdr_max(cor_histogram) != 100000000L",
              hdr_values_are_equivalent(cor_histogram, actual_cor_max, 100000000L));

    return 0;
}

static char* test_get_min_value()
{
    load_histograms();

    mu_assert("hdr_min(raw_histogram) != 1000", hdr_min(raw_histogram) == 1000L);
    mu_assert("hdr_min(cor_histogram) != 1000", hdr_min(cor_histogram) == 1000L);

    return 0;
}

static char* test_percentiles()
{
    load_histograms();

    mu_assert("Value at 30% not 1000.0",
              compare_percentile(hdr_value_at_percentile(raw_histogram, 30.0), 1000.0, 0.001));
    mu_assert("Value at 99% not 1000.0",
              compare_percentile(hdr_value_at_percentile(raw_histogram, 99.0), 1000.0, 0.001));
    mu_assert("Value at 99.99% not 1000.0",
              compare_percentile(hdr_value_at_percentile(raw_histogram, 99.99), 1000.0, 0.001));
    mu_assert("Value at 99.999% not 100000000.0",
              compare_percentile(hdr_value_at_percentile(raw_histogram, 99.999), 100000000.0, 0.001));
    mu_assert("Value at 100% not 100000000.0",
              compare_percentile(hdr_value_at_percentile(raw_histogram, 100.0), 100000000.0, 0.001));

    mu_assert("Value at 30% not 1000.0",
              compare_percentile(hdr_value_at_percentile(cor_histogram, 30.0), 1000.0, 0.001));
    mu_assert("Value at 50% not 1000.0",
              compare_percentile(hdr_value_at_percentile(cor_histogram, 50.0), 1000.0, 0.001));
    mu_assert("Value at 75% not 50000000.0",
              compare_percentile(hdr_value_at_percentile(cor_histogram, 75.0), 50000000.0, 0.001));
    mu_assert("Value at 90% not 80000000.0",
              compare_percentile(hdr_value_at_percentile(cor_histogram, 90.0), 80000000.0, 0.001));
    mu_assert("Value at 99% not 98000000.0",
              compare_percentile(hdr_value_at_percentile(cor_histogram, 99.0), 98000000.0, 0.001));
    mu_assert("Value at 99.999% not 100000000.0",
              compare_percentile(hdr_value_at_percentile(cor_histogram, 99.999), 100000000.0, 0.001));
    mu_assert("Value at 100% not 100000000.0",
              compare_percentile(hdr_value_at_percentile(cor_histogram, 100.0), 100000000.0, 0.001));

    return 0;
}

static char* test_recorded_values()
{
    load_histograms();
    struct hdr_recorded_iter iter;
    int index;

    // Raw Histogram
    hdr_recorded_iter_init(&iter, raw_histogram);

    index = 0;
    while (hdr_recorded_iter_next(&iter))
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
    hdr_recorded_iter_init(&iter, cor_histogram);

    index = 0;
    int64_t total_added_count = 0;
    while (hdr_recorded_iter_next(&iter))
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
    struct hdr_linear_iter iter;
    int index;

    // Raw Histogram
    hdr_linear_iter_init(&iter, raw_histogram, 100000);
    index = 0;
    while (hdr_linear_iter_next(&iter))
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

    hdr_linear_iter_init(&iter, cor_histogram, 10000);
    index = 0;
    int64_t total_added_count = 0;
    while (hdr_linear_iter_next(&iter))
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
    struct hdr_log_iter iter;
    int index;

    hdr_log_iter_init(&iter, raw_histogram, 10000, 2.0);
    index = 0;

    while(hdr_log_iter_next(&iter))
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

    hdr_log_iter_init(&iter, cor_histogram, 10000, 2.0);
    index = 0;
    int total_added_count = 0;
    while (hdr_log_iter_next(&iter))
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

static char* test_reset()
{
    load_histograms();

    // before
    mu_assert("Value at 99% == 0.0", hdr_value_at_percentile(raw_histogram, 99.0) != 0);
    mu_assert("Value at 99% == 0.0", hdr_value_at_percentile(cor_histogram, 99.0) != 0);

    hdr_reset(raw_histogram);
    hdr_reset(cor_histogram);

    //after
    mu_assert("Total raw count != 0",       raw_histogram->total_count == 0);
    mu_assert("Total corrected count != 0", cor_histogram->total_count == 0);

    mu_assert("Value at 99% not 0.0", hdr_value_at_percentile(raw_histogram, 99.0) == 0);
    mu_assert("Value at 99% not 0.0", hdr_value_at_percentile(cor_histogram, 99.0) == 0);

    return 0;
}

static char* test_scaling_equivalence()
{
    load_histograms();

    mu_assert(
            "Averages should be equivalent",
            compare_values(
                    hdr_mean(cor_histogram) * 512,
                    hdr_mean(scaled_cor_histogram),
                    0.000001));

    mu_assert(
            "Total count should be equivalent",
            compare_int64(
                    cor_histogram->total_count,
                    scaled_cor_histogram->total_count));

    int64_t expected_99th = hdr_value_at_percentile(cor_histogram, 99.0) * 512;
    int64_t scaled_99th = hdr_value_at_percentile(scaled_cor_histogram, 99.0);
    mu_assert(
            "99%'iles should be equivalent",
            compare_int64(
                    hdr_lowest_equivalent_value(cor_histogram, expected_99th),
                    hdr_lowest_equivalent_value(scaled_cor_histogram, scaled_99th)));

    return 0;
}

static struct mu_result all_tests()
{
    mu_run_test(test_create);
    mu_run_test(test_invalid_init);
    mu_run_test(test_create_with_large_values);
    mu_run_test(test_invalid_significant_figures);
    mu_run_test(test_total_count);
    mu_run_test(test_get_min_value);
    mu_run_test(test_get_max_value);
    mu_run_test(test_percentiles);
    mu_run_test(test_recorded_values);
    mu_run_test(test_linear_values);
    mu_run_test(test_logarithmic_values);
    mu_run_test(test_reset);
    mu_run_test(test_scaling_equivalence);

    mu_ok;
}

int hdr_histogram_run_tests()
{
    struct mu_result result = all_tests();

    if (result.message != 0)
    {
        printf("hdr_histogram_test.%s(): %s\n", result.test, result.message);
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
    return hdr_histogram_run_tests();
}
