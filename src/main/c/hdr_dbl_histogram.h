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

};

int hdr_dbl_histogram_init(
    int64_t highest_to_lowest_value_ratio,
    int64_t significant_figures);

#endif