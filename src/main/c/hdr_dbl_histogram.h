/**
 * hdr_dbl_histogram.h
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#ifndef HDR_DBL_HISTOGRAM_H
#define HDR_DBL_HISTOGRAM_H 1

#include <stdint.h>
#include "hdr_histogram.h"

struct hdr_dbl_histogram
{
    double current_lowest_value;
    double current_highest_value;
    int64_t highest_to_lowest_value_ratio;
    double int_to_dbl_conversion_ratio;
    double dbl_to_int_conversion_ratio;

    struct hdr_histogram values;
};

int hdr_dbl_init(
    int64_t highest_to_lowest_value_ratio,
    int32_t significant_figures,
    struct hdr_dbl_histogram** result);

bool hdr_dbl_record_value(struct hdr_dbl_histogram* h, double value);

#endif