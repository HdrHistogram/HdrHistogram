/**
 * hdr_dbl_histogram.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdlib.h>
#include <errno.h>
#include <hdr_dbl_histogram.h>
#include <math.h>
#include <string.h>

// ##     ## ######## #### ##       #### ######## ##    ##
// ##     ##    ##     ##  ##        ##     ##     ##  ##
// ##     ##    ##     ##  ##        ##     ##      ####
// ##     ##    ##     ##  ##        ##     ##       ##
// ##     ##    ##     ##  ##        ##     ##       ##
// ##     ##    ##     ##  ##        ##     ##       ##
//  #######     ##    #### ######## ####    ##       ##

static int64_t power(int64_t base, int64_t exp)
{
    int result = 1;
    while(exp)
    {
        result *= base; exp--;
    }
    return result;
}

static int32_t number_of_sub_buckets(int32_t significant_figures)
{
    int64_t largest_value_with_single_unit_resolution = 2 * (int64_t) pow(10, significant_figures);
    int32_t sub_bucket_count_magnitude = (int32_t) ceil(log(largest_value_with_single_unit_resolution/log(2)));

    return (int32_t) pow(2, sub_bucket_count_magnitude);
}

static int find_containing_binary_order_of_magnitude(int64_t value)
{
    return 64 - __builtin_clzll(value);
}

static int64_t calculate_internal_highest_to_lowest_value_ratio(int64_t external_highest_to_lowest_value_ratio)
{
    return INT64_C(1) << (find_containing_binary_order_of_magnitude(external_highest_to_lowest_value_ratio) + 1);
}

static int64_t calculate_integer_value_range(
        int64_t external_highest_to_lowest_value_ratio,
        int32_t significant_figures)
{
    int64_t internal_highest_to_lowest_value_ratio = calculate_internal_highest_to_lowest_value_ratio(external_highest_to_lowest_value_ratio);
    int64_t lowest_tracking_integer_value = number_of_sub_buckets(significant_figures) / 2;

    return lowest_tracking_integer_value * internal_highest_to_lowest_value_ratio;
}

/*
    private int findCappedContainingBinaryOrderOfMagnitude(final double doubleNumber) {
        if (doubleNumber > configuredHighestToLowestValueRatio) {
            return (int) (Math.log(configuredHighestToLowestValueRatio)/Math.log(2));
        }
        if (doubleNumber > Math.pow(2.0, 50)) {
            return 50;
        }
        return findContainingBinaryOrderOfMagnitude(doubleNumber);
    }
 */
static int32_t find_capped_containing_binary_order_of_magnitude(struct hdr_dbl_histogram *h, double d)
{
    if (d > h->highest_to_lowest_value_ratio)
    {
        return (int32_t) log(h->highest_to_lowest_value_ratio / log(2));
    }
    if (d > pow(2.0, 50))
    {
        return 50;
    }

    return find_containing_binary_order_of_magnitude(d);
}

/*
   private synchronized void autoAdjustRangeForValueSlowPath(final double value) {
       if (value < currentLowestValueInAutoRange) {
           if (value < 0.0) {
               throw new ArrayIndexOutOfBoundsException("Negative values cannot be recorded");
           }
           do {
               final int shiftAmount =
                       findCappedContainingBinaryOrderOfMagnitude(
                               Math.ceil(currentLowestValueInAutoRange / value) - 1.0);
               shiftCoveredRangeToTheRight(shiftAmount);
           } while (value < currentLowestValueInAutoRange);
       } else if (value >= currentHighestValueLimitInAutoRange) {
           if (value > highestAllowedValueEver) {
               throw new ArrayIndexOutOfBoundsException(
                       "Values above " + highestAllowedValueEver + " cannot be recorded");
           }
           do {
               // If value is an exact whole multiple of currentHighestValueLimitInAutoRange, it "belongs" with
               // the next level up, as it crosses the limit. With floating point values, the simplest way to
               // make this shift on exact multiple values happen (but not for any just-smaller-than-exact-multiple
               // values) is to use a value that is 1 ulp bigger in computing the ratio for the shift amount:
               final int shiftAmount =
                       findCappedContainingBinaryOrderOfMagnitude(
                               Math.ceil((value + Math.ulp(value)) / currentHighestValueLimitInAutoRange) - 1.0);
               shiftCoveredRangeToTheLeft(shiftAmount);
           } while (value >= currentHighestValueLimitInAutoRange);
       }
   }
*/

static bool adjust_range_for_value(struct hdr_dbl_histogram* h, double value)
{
    if (0.0 == value)
    {
        return true;
    }

    if (value < h->current_lowest_value)
    {
        if (value < 0.0)
        {
            return false;
        }
        do
        {
            int32_t shift_amount =
                    find_capped_containing_binary_order_of_magnitude(h, ceil(h->current_lowest_value / value) - 1.0);
        }
        while (value < h->current_lowest_value);
    }
}

// ##     ## ######## ##     ##  #######  ########  ##    ##
// ###   ### ##       ###   ### ##     ## ##     ##  ##  ##
// #### #### ##       #### #### ##     ## ##     ##   ####
// ## ### ## ######   ## ### ## ##     ## ########     ##
// ##     ## ##       ##     ## ##     ## ##   ##      ##
// ##     ## ##       ##     ## ##     ## ##    ##     ##
// ##     ## ######## ##     ##  #######  ##     ##    ##

int hdr_dbl_init(
    int64_t highest_to_lowest_value_ratio,
    int32_t significant_figures,
    struct hdr_dbl_histogram** result)
{
    if (highest_to_lowest_value_ratio < 2)
    {
        return EINVAL;
    }
    if (significant_figures < 1)
    {
        return EINVAL;
    }
    if ((highest_to_lowest_value_ratio * power(10, significant_figures)) >= (INT64_C(1) << INT64_C(61)))
    {
        return EINVAL;
    }

    struct hdr_dbl_histogram*dbl_histogram;
    struct hdr_histogram_bucket_config cfg;

    int64_t integer_value_range = calculate_integer_value_range(highest_to_lowest_value_ratio, significant_figures);

    hdr_calculate_bucket_config(1, integer_value_range - 1, significant_figures, &cfg);

    size_t histogram_size = sizeof(struct hdr_dbl_histogram) + cfg.counts_len * sizeof(int64_t);
    dbl_histogram = malloc(histogram_size);

    if (!dbl_histogram)
    {
        return ENOMEM;
    }

    // memset will ensure that all of the function pointers are null.
    memset((void*) dbl_histogram, 0, histogram_size);

    hdr_init_preallocated(&dbl_histogram->values, &cfg);
    *result = dbl_histogram;

    int64_t internal_highest_to_lowest_value_ratio =
            calculate_internal_highest_to_lowest_value_ratio(highest_to_lowest_value_ratio);

    dbl_histogram->highest_to_lowest_value_ratio = highest_to_lowest_value_ratio;
    dbl_histogram->current_lowest_value = pow(2.0, 800);
    dbl_histogram->current_highest_value = dbl_histogram->current_lowest_value * internal_highest_to_lowest_value_ratio;
    dbl_histogram->int_to_dbl_conversion_ratio = dbl_histogram->current_lowest_value / cfg.sub_bucket_half_count;
    dbl_histogram->dbl_to_int_conversion_ratio = 1.0 / dbl_histogram->int_to_dbl_conversion_ratio;
    dbl_histogram->values.conversion_ratio = dbl_histogram->int_to_dbl_conversion_ratio;

    return 0;
}

bool hdr_dbl_record_value(struct hdr_dbl_histogram* h, double value)
{
    if (value < h->current_lowest_value || h->current_highest_value <= value)
    {
        adjust_range_for_value(h, value);
    }

    return false;
}