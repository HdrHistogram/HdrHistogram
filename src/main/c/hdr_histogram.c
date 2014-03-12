/**
 * hdr_histogram.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdlib.h>
#include <stdbool.h>
#include <math.h>
#include <x86intrin.h>
#include <assert.h>
#include <stdio.h>
#include <strings.h>
#include <stdint.h>

#include "hdr_histogram.h"

/////////////////////////////////// Utility ///////////////////////////////////

static int64_t power(int64_t base, int64_t exp)
{
    int result = 1;
    while(exp)
    {
        result *= base; exp--;
    }
    return result;
}

static int32_t get_bucket_index(struct hdr_histogram* h, int64_t value)
{
    int32_t pow2ceiling = 64 - __lzcnt64(value | h->sub_bucket_mask); // smallest power of 2 containing value
    return pow2ceiling - (h->sub_bucket_half_count_magnitude + 1);
}

static int32_t get_sub_bucket_index(int64_t value, int32_t bucket_index)
{
    return (int32_t)(value >> bucket_index);
}

static int32_t counts_index(struct hdr_histogram* h, int32_t bucket_index, int32_t sub_bucket_index)
{
    assert(bucket_index < h->bucket_count);
    assert(sub_bucket_index < h->sub_bucket_count);
    assert(bucket_index == 0 || (sub_bucket_index >= h->sub_bucket_half_count));

    // Calculate the index for the first entry in the bucket:
    // (The following is the equivalent of ((bucket_index + 1) * subBucketHalfCount) ):
    int32_t bucket_base_index = (bucket_index + 1) << h->sub_bucket_half_count_magnitude;
    // Calculate the offset in the bucket:
    int32_t offset_in_bucket = sub_bucket_index - h->sub_bucket_half_count;
    // The following is the equivalent of ((sub_bucket_index  - subBucketHalfCount) + bucketBaseIndex;
    return bucket_base_index + offset_in_bucket;
}

static int32_t counts_index_for(struct hdr_histogram* h, int64_t value)
{
    int32_t bucket_index     = get_bucket_index(h, value);
    int32_t sub_bucket_index = get_sub_bucket_index(value, bucket_index);

    return counts_index(h, bucket_index, sub_bucket_index);
}

static int64_t value_from_index(int32_t bucket_index, int32_t sub_bucket_index)
{
    return ((int64_t) sub_bucket_index) << bucket_index;
}

static int64_t get_count_at_index(struct hdr_histogram* h, int32_t bucket_index, int32_t sub_bucket_index)
{
    return h->counts[counts_index(h, bucket_index, sub_bucket_index)];
}

static int64_t size_of_equivalent_value_range(struct hdr_histogram* h, int64_t value)
{
    int32_t bucket_index     = get_bucket_index(h, value);
    int32_t sub_bucket_index = get_sub_bucket_index(value, bucket_index);
    return (1 << ((sub_bucket_index >= h->sub_bucket_count) ? (bucket_index + 1) : bucket_index));
}

static int64_t lowest_equivalent_value(struct hdr_histogram* h, int64_t value)
{
    int32_t bucket_index     = get_bucket_index(h, value);
    int32_t sub_bucket_index = get_sub_bucket_index(value, bucket_index);
    return value_from_index(bucket_index, sub_bucket_index);
}

static int64_t next_non_equivalent_value(struct hdr_histogram* h, int64_t value)
{
    return lowest_equivalent_value(h, value) + size_of_equivalent_value_range(h, value);
}

static int64_t highest_equivalent_value(struct hdr_histogram* h, int64_t value)
{
    return next_non_equivalent_value(h, value) - 1;
}

static int64_t median_equivalent_value(struct hdr_histogram* h, int64_t value)
{
    return lowest_equivalent_value(h, value) + (size_of_equivalent_value_range(h, value) >> 1);
}

//////////////////////////////////// Memory ///////////////////////////////////

int hdrh_alloc(int64_t highest_trackable_value, int significant_figures, struct hdr_histogram** result)
{
    if (significant_figures < 3 || 6 < significant_figures)
    {
        return -1;
    }

    int64_t largest_value_with_single_unit_resolution = 2 * power(10, significant_figures);
    int32_t sub_bucket_count_magnitude                = (int32_t) ceil(log(largest_value_with_single_unit_resolution) / log(2));
    int32_t sub_bucket_half_count_magnitude           = ((sub_bucket_count_magnitude > 1) ? sub_bucket_count_magnitude : 1) - 1;

    int32_t sub_bucket_count      = (int32_t) pow(2, (sub_bucket_half_count_magnitude + 1));
    int32_t sub_bucket_half_count = sub_bucket_count / 2;
    int32_t sub_bucket_mask       = sub_bucket_count - 1;

    // determine exponent range needed to support the trackable value with no overflow:
    int64_t trackable_value = (int64_t) sub_bucket_count - 1;
    int32_t buckets_needed  = 1;
    while (trackable_value < highest_trackable_value)
    {
        trackable_value <<= 1;
        buckets_needed++;
    }
    int32_t bucket_count = buckets_needed;
    int32_t counts_len   = (bucket_count + 1) * (sub_bucket_count / 2);

    size_t histogram_size           = sizeof(struct hdr_histogram) + counts_len * sizeof(long);
    struct hdr_histogram* histogram = (struct hdr_histogram*) malloc(histogram_size);

    if (!histogram)
    {
        return -2;
    }

    bzero((void*) histogram, histogram_size);

    histogram->highest_trackable_value         = highest_trackable_value;
    histogram->significant_figures             = significant_figures;
    histogram->sub_bucket_half_count_magnitude = sub_bucket_half_count_magnitude;
    histogram->sub_bucket_half_count           = sub_bucket_half_count;
    histogram->sub_bucket_mask                 = sub_bucket_mask;
    histogram->sub_bucket_count                = sub_bucket_count;
    histogram->bucket_count                    = bucket_count;
    histogram->counts_len                      = counts_len;
    histogram->total_count                     = 0;

    *result = histogram;

    return 0;
}

/////////////////////////////////// Updates ///////////////////////////////////

bool hdrh_record_value(struct hdr_histogram* h, int64_t value)
{
    int32_t counts_index = counts_index_for(h, value);

    if (counts_index < 0 || h->counts_len <= counts_index)
    {
        return false;
    }

    h->counts[counts_index]++;
    h->total_count++;

    return true;
}

bool hdrh_record_corrected_value(struct hdr_histogram* h, int64_t value, int64_t expected_interval)
{
    if (!hdrh_record_value(h, value))
    {
        return false;
    }

    if (expected_interval <= 0 || value <= expected_interval)
    {
        return true;
    }

    int64_t missing_value = value - expected_interval;
    for (; missing_value >= expected_interval; missing_value -= expected_interval)
    {
        if (!hdrh_record_value(h, missing_value))
        {
            return false;
        }
    }

    return true;
}

//////////////////////////////////// Values ///////////////////////////////////

int64_t hdrh_max(struct hdr_histogram* h)
{
    int64_t max = 0;
    struct hdrh_iter iter;

    hdrh_iter_init(&iter, h);

    while (hdrh_iter_next(&iter))
    {
        if (0 != iter.count_at_index)
        {
            max = iter.highest_equivalent_value;
        }
    }
    return lowest_equivalent_value(h, max);
}

int64_t hdrh_min(struct hdr_histogram* h)
{
    int64_t min = 0;
    struct hdrh_iter iter;

    hdrh_iter_init(&iter, h);

    while (hdrh_iter_next(&iter))
    {
        if (0 != iter.count_to_index && 0 == min)
        {
            min = iter.value_from_index;
            break;
        }
    }

    return min;
}

int64_t hdrh_value_at_percentile(struct hdr_histogram* h, double percentile)
{
    struct hdrh_iter iter;
    hdrh_iter_init(&iter, h);

    double requested_percentile = percentile < 100.0 ? percentile : 100.0;
    int64_t count_at_percentile = (int64_t) (((requested_percentile / 100) * h->total_count) + 0.5);
    count_at_percentile = count_at_percentile > 1 ? count_at_percentile : 1;
    int64_t total = 0;

    while (hdrh_iter_next(&iter))
    {
        total += iter.count_at_index;

        if (total >= count_at_percentile)
        {
            return iter.value_from_index;
        }
    }

    return 0;
}

double hdrh_mean(struct hdr_histogram* h)
{
    struct hdrh_iter iter;
    int64_t total = 0;

    hdrh_iter_init(&iter, h);

    while (hdrh_iter_next(&iter))
    {
        if (0 != iter.count_at_index)
        {
            total += iter.count_at_index * median_equivalent_value(h, iter.value_from_index);
        }
    }

    return (total * 1.0) / h->total_count;
}

double hdrh_stddev(struct hdr_histogram* h)
{
    double mean = hdrh_mean(h);
    double geometric_dev_total = 0.0;

    struct hdrh_iter iter;
    hdrh_iter_init(&iter, h);

    while (hdrh_iter_next(&iter))
    {
        if (0 != iter.count_at_index)
        {
            double dev = (median_equivalent_value(h, iter.value_from_index) * 1.0) - mean;
            geometric_dev_total += (dev * dev) * iter.count_at_index;
        }
    }

    return sqrt(geometric_dev_total / h->total_count);
}

bool hdrh_values_are_equivalent(struct hdr_histogram* h, int64_t a, int64_t b)
{
    return lowest_equivalent_value(h, a) == lowest_equivalent_value(h, b);
}

/////////////////////////////////// Iterators /////////////////////////////////

static bool has_next(struct hdrh_iter* iter)
{
    return iter->count_to_index < iter->h->total_count;
}

void hdrh_iter_init(struct hdrh_iter* itr, struct hdr_histogram* h)
{
    itr->h = h;

    itr->bucket_index       =  0;
    itr->sub_bucket_index   = -1;
    itr->count_at_index     =  0;
    itr->count_to_index     =  0;
    itr->value_from_index   =  0;
    itr->highest_equivalent_value = 0;
}

bool hdrh_iter_next(struct hdrh_iter* iter)
{
    if (!has_next(iter))
    {
        return false;
    }

    iter->sub_bucket_index++;

    if (iter->sub_bucket_index >= iter->h->sub_bucket_count)
    {
        iter->sub_bucket_index = iter->h->sub_bucket_half_count;
        iter->bucket_index++;
    }

    iter->count_at_index  = get_count_at_index(iter->h, iter->bucket_index, iter->sub_bucket_index);
    iter->count_to_index += iter->count_at_index;

    iter->value_from_index = value_from_index(iter->bucket_index, iter->sub_bucket_index);
    iter->highest_equivalent_value = highest_equivalent_value(iter->h, iter->value_from_index);

   return true;
}

////////////////////////////////// Percentiles ////////////////////////////////

void hdrh_percentiles_init(struct hdrh_percentiles* percentiles,
                           struct hdr_histogram* h,
                           int32_t ticks_per_half_distance)
{
    hdrh_iter_init(&percentiles->iter, h);

    percentiles->seen_last_value          = false;
    percentiles->ticks_per_half_distance  = ticks_per_half_distance;
    percentiles->percentile_to_iterate_to = 0.0;
    percentiles->percentile               = 0.0;
}

bool hdrh_percentiles_next(struct hdrh_percentiles* percentiles)
{
    if (!has_next(&percentiles->iter))
    {
        if (percentiles->seen_last_value)
        {
            return false;
        }

        percentiles->seen_last_value = true;
        percentiles->percentile = 100.0;

        return true;
    }

    if (percentiles->iter.sub_bucket_index == -1 && !hdrh_iter_next(&percentiles->iter))
    {
        return false;
    }

    do
    {
        double current_percentile = (100.0 * (double) percentiles->iter.count_to_index) / percentiles->iter.h->total_count;
        if (percentiles->iter.count_at_index != 0 &&
            percentiles->percentile_to_iterate_to <= current_percentile)
        {
            percentiles->percentile = percentiles->percentile_to_iterate_to;

            int64_t half_distance = (int64_t) pow(2, (int64_t) (log(100 / (100.0 - (percentiles->percentile_to_iterate_to))) / log(2)) + 1);
            int64_t percentile_reporting_ticks = percentiles->ticks_per_half_distance * half_distance;
            percentiles->percentile_to_iterate_to += 100.0 / percentile_reporting_ticks;

            return true;
        }
    }
    while (hdrh_iter_next(&percentiles->iter));

    return true;
}

static void format_line_string(char* str, int len, int significant_figures, format_type format)
{
    const char* format_str = "%s%d%s";

    switch (format)
    {
        case CSV:
            snprintf(str, len, format_str, "%.", significant_figures, "f,%f,%d,%.2f\n");
            break;
        case CLASSIC:
            snprintf(str, len, format_str, "%12.", significant_figures, "f %12f %12d %12.2f\n");
            break;
        default:
            snprintf(str, len, format_str, "%12.", significant_figures, "f %12f %12d %12.2f\n");
    }
}

static const char* format_head_string(format_type format)
{
    switch (format)
    {
        case CSV:
            return "%s,%s,%s,%s\n";
        case CLASSIC:
            return "%12s %12s %12s %12s\n\n";
        default:
            return "%12s %12s %12s %12s\n\n";
    }
}

void hdrh_percentiles_print(struct hdr_histogram* h,
                            FILE* stream,
                            int32_t ticks_per_half_distance,
                            double value_scale,
                            format_type format)
{
    char line_format[25];
    format_line_string(line_format, 25, h->significant_figures, format);
    const char* head_format = format_head_string(format);

    struct hdrh_percentiles percentiles;
    hdrh_percentiles_init(&percentiles, h, ticks_per_half_distance);

    fprintf(stream, head_format, "Value", "Percentile", "TotalCount", "1/(1-Percentile)");

    while (hdrh_percentiles_next(&percentiles))
    {
        double  value               = percentiles.iter.highest_equivalent_value / value_scale;
        double  percentile          = percentiles.percentile / 100.0;
        int64_t total_count         = percentiles.iter.count_to_index;
        double  inverted_percentile = (1.0 / (1.0 - percentile));

        fprintf(stream, line_format, value, percentile, total_count, inverted_percentile);
    }

    if (CLASSIC == format)
    {
        double mean   = hdrh_mean(h)   / value_scale;
        double stddev = hdrh_stddev(h) / value_scale;
        double max    = hdrh_max(h)    / value_scale;

        fprintf(stream, "#[Mean    = %12.3f, StdDeviation   = %12.3f]\n", mean, stddev);
        fprintf(stream, "#[Max     = %12.3f, Total count    = %12ld]\n", max, h->total_count);
        fprintf(stream, "#[Buckets = %12d, SubBuckets     = %12d]\n", h->bucket_count, h->sub_bucket_count);
    }

    fflush(stream);
}