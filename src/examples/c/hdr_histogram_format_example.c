/**
 * hdr_histogram_format_example.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <math.h>

#include <stdio.h>
#include <hdr_histogram.h>

int main(int argc, char **argv)
{
    int i;
    struct hdr_histogram* raw_histogram = NULL;
    struct hdr_histogram* cor_histogram = NULL;
    hdr_histogram_alloc(100000000, 3, &raw_histogram);
    hdr_histogram_alloc(100000000, 3, &cor_histogram);

    for (i = 0; i < 10000; i++)
    {
        hdr_histogram_record_value(raw_histogram, 1000L);
        hdr_histogram_record_corrected_value(cor_histogram, 1000L, 10000L);
    }

    hdr_histogram_record_value(raw_histogram, 100000000L);
    hdr_histogram_record_corrected_value(cor_histogram, 100000000L, 10000L);

    hdr_histogram_percentiles_print(raw_histogram, stdout, 5, 1.0);
    hdr_histogram_percentiles_print(cor_histogram, stdout, 5, 1.0);
}
