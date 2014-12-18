/**
* hdr_atomic_histogram.h
*
* Written by Michael Barker and released to the public domain,
* as explained at http://creativecommons.org/publicdomain/zero/1.0/
*
*/

#ifndef HDR_ATOMIC_HISTOGRAM_H
#define HDR_ATOMIC_HISTOGRAM_H 1

#include "hdr_histogram.h"

/**
 * Initialise an atomic version of the hdr_histogram.
 */
int hdr_atomic_init(
        int64_t lowest_trackable_value,
        int64_t highest_trackable_value,
        int significant_figures,
        struct hdr_histogram** result);

#endif
