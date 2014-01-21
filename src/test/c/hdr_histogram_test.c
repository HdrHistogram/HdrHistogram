/**
 * hdr_histogram_test.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <math.h>

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

    hdr_histogram_alloc(100000000, 3, &raw_histogram);

    if (cor_histogram)
    {
        free(cor_histogram);
    }

    hdr_histogram_alloc(100000000, 3, &cor_histogram);

    for (i = 0; i < 10000; i++)
    {
        hdr_histogram_record_value(raw_histogram, 1000L);
        hdr_histogram_record_corrected_value(cor_histogram, 1000L, 10000L);
    }

    hdr_histogram_record_value(raw_histogram, 100000000L);
    hdr_histogram_record_corrected_value(cor_histogram, 100000000L, 10000L);
}

static char* test_create()
{
    struct hdr_histogram* h = NULL;
    int r = hdr_histogram_alloc(36000000, 4, &h);

    mu_assert("Failed to allocate hdr_histogram", r == 0);
    mu_assert("Failed to allocate hdr_histogram", h != 0);

    free(h);

    return 0;
}

static char* test_invalid_significant_figures()
{
    struct hdr_histogram* h = NULL;

    int r = hdr_histogram_alloc(36000000, 2, &h);
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


    int64_t actual_raw_max = hdr_histogram_max(raw_histogram);
    mu_assert("hdr_histogram_max(raw_histogram) != 100000000L",
              hdr_histogram_values_are_equivalent(raw_histogram, actual_raw_max, 100000000L));
    int64_t actual_cor_max = hdr_histogram_max(cor_histogram);
    mu_assert("hdr_histogram_max(cor_histogram) != 100000000L",
              hdr_histogram_values_are_equivalent(cor_histogram, actual_cor_max, 100000000L));

    return 0;
}

static char* test_get_min_value()
{
    load_histograms();

    mu_assert("hdr_histogram_min(raw_histogram) != 1000", hdr_histogram_min(raw_histogram) == 1000L);
    mu_assert("hdr_histogram_min(cor_histogram) != 1000", hdr_histogram_min(cor_histogram) == 1000L);

    return 0;
}

static char* test_percentiles()
{
    load_histograms();

    mu_assert("Value at 30% not 1000.0",
              compare_percentile(hdr_histogram_value_at_percentile(raw_histogram, 30.0), 1000.0, 0.001));
    mu_assert("Value at 99% not 1000.0",
              compare_percentile(hdr_histogram_value_at_percentile(raw_histogram, 99.0), 1000.0, 0.001));
    mu_assert("Value at 99.99% not 1000.0",
              compare_percentile(hdr_histogram_value_at_percentile(raw_histogram, 99.99), 1000.0, 0.001));
    mu_assert("Value at 99.999% not 100000000.0",
              compare_percentile(hdr_histogram_value_at_percentile(raw_histogram, 99.999), 100000000.0, 0.001));
    mu_assert("Value at 100% not 100000000.0",
              compare_percentile(hdr_histogram_value_at_percentile(raw_histogram, 100.0), 100000000.0, 0.001));

    mu_assert("Value at 30% not 1000.0",
              compare_percentile(hdr_histogram_value_at_percentile(cor_histogram, 30.0), 1000.0, 0.001));
    mu_assert("Value at 50% not 1000.0",
              compare_percentile(hdr_histogram_value_at_percentile(cor_histogram, 50.0), 1000.0, 0.001));
    mu_assert("Value at 75% not 50000000.0",
              compare_percentile(hdr_histogram_value_at_percentile(cor_histogram, 75.0), 50000000.0, 0.001));
    mu_assert("Value at 90% not 80000000.0",
              compare_percentile(hdr_histogram_value_at_percentile(cor_histogram, 90.0), 80000000.0, 0.001));
    mu_assert("Value at 99% not 98000000.0",
              compare_percentile(hdr_histogram_value_at_percentile(cor_histogram, 99.0), 98000000.0, 0.001));
    mu_assert("Value at 99.999% not 100000000.0",
              compare_percentile(hdr_histogram_value_at_percentile(cor_histogram, 99.999), 100000000.0, 0.001));
    mu_assert("Value at 100% not 100000000.0",
              compare_percentile(hdr_histogram_value_at_percentile(cor_histogram, 100.0), 100000000.0, 0.001));

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
