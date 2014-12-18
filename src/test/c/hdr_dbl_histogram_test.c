/**
 * hdr_dbl_histogram_test.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */
#include <stdlib.h>
#include <stdio.h>

#include <hdr_dbl_histogram.h>

#include "minunit.h"

int tests_run = 0;

const int64_t TRACKABLE_VALUE_RANGE_SIZE = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
const int32_t SIGNIFICANT_FIGURES = 3;

char* test_construct_argument_ranges()
{
    struct hdr_dbl_histogram* h = NULL;

    mu_assert("Should of failed allocation", 0 != hdr_dbl_init(1, SIGNIFICANT_FIGURES, &h));
    mu_assert("Should of failed allocation", 0 != hdr_dbl_init(TRACKABLE_VALUE_RANGE_SIZE, 6, &h));
    mu_assert("Should of failed allocation", 0 != hdr_dbl_init(TRACKABLE_VALUE_RANGE_SIZE, -1, &h));

    return NULL;
}

static struct mu_result all_tests()
{
    mu_run_test(test_construct_argument_ranges);

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