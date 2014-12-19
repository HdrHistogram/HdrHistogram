/**
 * hdr_histogram.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdlib.h>
#include <stdbool.h>
#include <math.h>
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <errno.h>
#include <inttypes.h>

#include "hdr_histogram.h"

//  ######   #######  ##     ## ##    ## ########  ######  
// ##    ## ##     ## ##     ## ###   ##    ##    ##    ## 
// ##       ##     ## ##     ## ####  ##    ##    ##       
// ##       ##     ## ##     ## ## ## ##    ##     ######  
// ##       ##     ## ##     ## ##  ####    ##          ## 
// ##    ## ##     ## ##     ## ##   ###    ##    ##    ## 
//  ######   #######   #######  ##    ##    ##     ######

static int32_t normalize_index(struct hdr_histogram* h, int32_t index)
{
    if (h->normalizing_index_offset == 0)
    {
        return index;
    }

    int32_t normalized_index = index - h->normalizing_index_offset;
    int32_t adjustment = 0;

    if (normalized_index < 0)
    {
        adjustment = h->counts_len;
    }
    else if (normalized_index >= h->counts_len)
    {
        adjustment = -h->counts_len;
    } 

    return normalized_index + adjustment;
}

static int64_t counts_get_direct(struct hdr_histogram* h, int32_t index)
{
    if (!h->_get)
    {
        return h->counts[index];
    }
    else
    {
        return h->_get(h, index);
    }
}

static int32_t counts_get_normalised(struct hdr_histogram* h, int32_t index)
{
    return counts_get_direct(h, normalize_index(h, index));
}

static void counts_inc_normalised(
    struct hdr_histogram* h, int32_t index, int64_t value)
{
    int32_t normalised_index = normalize_index(h, index);

    if (!h->_increment)
    {
        h->counts[normalised_index] += value;
        h->total_count += value;
    }
    else
    {
        h->_increment(h, normalised_index, value);
    }
}

static void update_min_max(struct hdr_histogram* h, int64_t value)
{
    if (!h->_update_min_max)
    {
        h->min_value = (value < h->min_value && value != 0) ? value : h->min_value;
        h->max_value = (value > h->max_value) ? value : h->max_value;
    }
    else
    {
        h->_update_min_max(h, value);
    }
}

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

static int32_t get_bucket_index(struct hdr_histogram* h, int64_t value)
{
    int32_t pow2ceiling = 64 - __builtin_clzll(value | h->sub_bucket_mask); // smallest power of 2 containing value
    return pow2ceiling - h->unit_magnitude - (h->sub_bucket_half_count_magnitude + 1);
}

static int32_t get_sub_bucket_index(int64_t value, int32_t bucket_index, int32_t unit_magnitude)
{
    return (int32_t)(value >> (bucket_index + unit_magnitude));
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
    int32_t sub_bucket_index = get_sub_bucket_index(value, bucket_index, h->unit_magnitude);

    return counts_index(h, bucket_index, sub_bucket_index);
}

static int64_t value_from_index(int32_t bucket_index, int32_t sub_bucket_index, int32_t unit_magnitude)
{
    return ((int64_t) sub_bucket_index) << (bucket_index + unit_magnitude);
}

static int64_t value_from_array_index(struct hdr_histogram* h, int index)
{
    int32_t bucket_index = (index >> h->sub_bucket_half_count_magnitude) - 1;
    int32_t sub_bucket_index = (index & (h->sub_bucket_half_count - 1)) + h->sub_bucket_half_count;

    if (bucket_index < 0)
    {
        sub_bucket_index -= h->sub_bucket_half_count;
        bucket_index = 0;
    }

    return value_from_index(bucket_index, sub_bucket_index, h->unit_magnitude);
}

static int64_t get_count_at_index(
        struct hdr_histogram* h,
        int32_t bucket_index,
        int32_t sub_bucket_index)
{
    return counts_get_normalised(h, counts_index(h, bucket_index, sub_bucket_index));
}

static int64_t size_of_equivalent_value_range(struct hdr_histogram* h, int64_t value)
{
    int32_t bucket_index     = get_bucket_index(h, value);
    int32_t sub_bucket_index = get_sub_bucket_index(value, bucket_index, h->unit_magnitude);
    int32_t adjusted_bucket  = (sub_bucket_index >= h->sub_bucket_count) ? (bucket_index + 1) : bucket_index;
    return INT64_C(1) << (h->unit_magnitude + adjusted_bucket);
}

static int64_t lowest_equivalent_value(struct hdr_histogram* h, int64_t value)
{
    int32_t bucket_index     = get_bucket_index(h, value);
    int32_t sub_bucket_index = get_sub_bucket_index(value, bucket_index, h->unit_magnitude);
    return value_from_index(bucket_index, sub_bucket_index, h->unit_magnitude);
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

void hdr_reset_internal_counters(struct hdr_histogram* h)
{
    int min_non_zero_index = -1;
    int max_index = -1;
    int64_t observed_total_count = 0;

    for (int i = 0; i < h->counts_len; i++)
    {
        int64_t count_at_index;

        if ((count_at_index = counts_get_direct(h, i)) > 0)
        {
            observed_total_count += count_at_index;
            max_index = i;
            if (min_non_zero_index == -1 && i != 0)
            {
                min_non_zero_index = i;
            }
        }
    }

    if (max_index == -1)
    {
        h->max_value = 0;
    }
    else
    {
        int64_t max_value = value_from_array_index(h, max_index);
        h->max_value = highest_equivalent_value(h, max_value);
    }

    if (min_non_zero_index == -1)
    {
        h->min_value = INT64_MAX;
    }
    else
    {
        h->min_value = value_from_array_index(h, min_non_zero_index);
    }

    h->total_count = observed_total_count;
}


// ##     ## ######## ##     ##  #######  ########  ##    ##
// ###   ### ##       ###   ### ##     ## ##     ##  ##  ##
// #### #### ##       #### #### ##     ## ##     ##   ####
// ## ### ## ######   ## ### ## ##     ## ########     ##
// ##     ## ##       ##     ## ##     ## ##   ##      ##
// ##     ## ##       ##     ## ##     ## ##    ##     ##
// ##     ## ######## ##     ##  #######  ##     ##    ##

int hdr_calculate_bucket_config(
        int64_t lowest_trackable_value,
        int64_t highest_trackable_value,
        int significant_figures,
        struct hdr_histogram_bucket_config* cfg)
{
    if (lowest_trackable_value < 1 ||
            significant_figures < 1 || 5 < significant_figures)
    {
        return EINVAL;
    }

    cfg->lowest_trackable_value = lowest_trackable_value;
    cfg->significant_figures = significant_figures;
    cfg->highest_trackable_value = highest_trackable_value;

    int64_t largest_value_with_single_unit_resolution = 2 * power(10, significant_figures);
    int32_t sub_bucket_count_magnitude                = (int32_t) ceil(log(largest_value_with_single_unit_resolution) / log(2));
    cfg->sub_bucket_half_count_magnitude           = ((sub_bucket_count_magnitude > 1) ? sub_bucket_count_magnitude : 1) - 1;

    cfg->unit_magnitude = (int32_t) floor(log(lowest_trackable_value) / log(2));

    cfg->sub_bucket_count      = (int32_t) pow(2, (cfg->sub_bucket_half_count_magnitude + 1));
    cfg->sub_bucket_half_count = cfg->sub_bucket_count / 2;
    cfg->sub_bucket_mask       = ((int64_t) cfg->sub_bucket_count - 1) << cfg->unit_magnitude;

    // determine exponent range needed to support the trackable value with no overflow:
    int64_t trackable_value = (int64_t) cfg->sub_bucket_mask;
    int32_t buckets_needed  = 1;
    while (trackable_value < highest_trackable_value)
    {
        trackable_value <<= 1;
        buckets_needed++;
    }
    cfg->bucket_count = buckets_needed;
    cfg->counts_len   = (cfg->bucket_count + 1) * (cfg->sub_bucket_count / 2);

    return 0;
}

void hdr_init_preallocated(struct hdr_histogram* h, struct hdr_histogram_bucket_config* cfg)
{
    h->lowest_trackable_value          = cfg->lowest_trackable_value;
    h->highest_trackable_value         = cfg->highest_trackable_value;
    h->unit_magnitude                  = cfg->unit_magnitude;
    h->significant_figures             = cfg->significant_figures;
    h->sub_bucket_half_count_magnitude = cfg->sub_bucket_half_count_magnitude;
    h->sub_bucket_half_count           = cfg->sub_bucket_half_count;
    h->sub_bucket_mask                 = cfg->sub_bucket_mask;
    h->sub_bucket_count                = cfg->sub_bucket_count;
    h->min_value                       = INT64_MAX;
    h->max_value                       = 0;
    h->normalizing_index_offset        = 0;
    h->conversion_ratio                = 1.0;
    h->bucket_count                    = cfg->bucket_count;
    h->counts_len                      = cfg->counts_len;
    h->total_count                     = 0;
}

int hdr_init(
        int64_t lowest_trackable_value,
        int64_t highest_trackable_value,
        int significant_figures,
        struct hdr_histogram** result)
{
    struct hdr_histogram_bucket_config cfg;

    int r = hdr_calculate_bucket_config(lowest_trackable_value, highest_trackable_value, significant_figures, &cfg);
    if (r)
    {
        return r;
    }

    size_t histogram_size           = sizeof(struct hdr_histogram) + cfg.counts_len * sizeof(int64_t);
    struct hdr_histogram* histogram = malloc(histogram_size);

    if (!histogram)
    {
        return ENOMEM;
    }

    // memset will ensure that all of the function pointers are null.
    memset((void*) histogram, 0, histogram_size);

    hdr_init_preallocated(histogram, &cfg);
    *result = histogram;

    return 0;
}


int hdr_alloc(int64_t highest_trackable_value, int significant_figures, struct hdr_histogram** result)
{
    return hdr_init(1, highest_trackable_value, significant_figures, result);
}

// reset a histogram to zero.
void hdr_reset(struct hdr_histogram *h)
{
     h->total_count=0;
     h->min_value = INT64_MAX;
     h->max_value = 0;
     memset((void *) &h->counts, 0, (sizeof(int64_t) * h->counts_len));
     return;
}

size_t hdr_get_memory_size(struct hdr_histogram *h)
{
    return sizeof(struct hdr_histogram) + h->counts_len * sizeof(int64_t);
}


// ##     ## ########  ########     ###    ######## ########  ######
// ##     ## ##     ## ##     ##   ## ##      ##    ##       ##    ##
// ##     ## ##     ## ##     ##  ##   ##     ##    ##       ##
// ##     ## ########  ##     ## ##     ##    ##    ######    ######
// ##     ## ##        ##     ## #########    ##    ##             ##
// ##     ## ##        ##     ## ##     ##    ##    ##       ##    ##
//  #######  ##        ########  ##     ##    ##    ########  ######


bool hdr_record_value(struct hdr_histogram* h, int64_t value)
{
    if (value < 0)
    {
        return false;
    }

    int32_t counts_index = counts_index_for(h, value);

    if (counts_index < 0 || h->counts_len <= counts_index)
    {
        return false;
    }

    counts_inc_normalised(h, counts_index, 1);
    update_min_max(h, value);

    return true;
}

bool hdr_record_values(struct hdr_histogram* h, int64_t value, int64_t count)
{
    if (value < 0)
    {
        return false;
    }

    int32_t counts_index = counts_index_for(h, value);

    if (counts_index < 0 || h->counts_len <= counts_index)
    {
        return false;
    }

    counts_inc_normalised(h, counts_index, count);
    update_min_max(h, value);

    return true;
}

bool hdr_record_corrected_value(struct hdr_histogram* h, int64_t value, int64_t expected_interval)
{
    if (!hdr_record_value(h, value))
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
        if (!hdr_record_value(h, missing_value))
        {
            return false;
        }
    }

    return true;
}

int64_t hdr_add(struct hdr_histogram* h, struct hdr_histogram* from)
{
    struct hdr_recorded_iter iter;
    hdr_recorded_iter_init(&iter, from);
    int64_t dropped = 0;

    while (hdr_recorded_iter_next(&iter))
    {
        int64_t value = iter.iter.value_from_index;
        int64_t count = iter.iter.count_at_index;

        if (!hdr_record_values(h, value, count))
        {
            dropped += count;
        }
    }

    return dropped;
}


// ##     ##    ###    ##       ##     ## ########  ######
// ##     ##   ## ##   ##       ##     ## ##       ##    ##
// ##     ##  ##   ##  ##       ##     ## ##       ##
// ##     ## ##     ## ##       ##     ## ######    ######
//  ##   ##  ######### ##       ##     ## ##             ##
//   ## ##   ##     ## ##       ##     ## ##       ##    ##
//    ###    ##     ## ########  #######  ########  ######


int64_t hdr_max(struct hdr_histogram* h)
{
    if (0 == h->max_value)
    {
        return 0;
    }

    return highest_equivalent_value(h, h->max_value);
}

int64_t hdr_min(struct hdr_histogram* h)
{
    if (INT64_MAX == h->min_value)
    {
        return INT64_MAX;
    }

    return lowest_equivalent_value(h, h->min_value);
}

int64_t hdr_value_at_percentile(struct hdr_histogram* h, double percentile)
{
    struct hdr_iter iter;
    hdr_iter_init(&iter, h);

    double requested_percentile = percentile < 100.0 ? percentile : 100.0;
    int64_t count_at_percentile =
        (int64_t) (((requested_percentile / 100) * h->total_count) + 0.5);
    count_at_percentile = count_at_percentile > 1 ? count_at_percentile : 1;
    int64_t total = 0;

    while (hdr_iter_next(&iter))
    {
        total += iter.count_at_index;

        if (total >= count_at_percentile)
        {
            int64_t value_from_index = iter.value_from_index;
            return highest_equivalent_value(h, value_from_index);
        }
    }

    return 0;
}

double hdr_mean(struct hdr_histogram* h)
{
    struct hdr_iter iter;
    int64_t total = 0;

    hdr_iter_init(&iter, h);

    while (hdr_iter_next(&iter))
    {
        if (0 != iter.count_at_index)
        {
            total += iter.count_at_index * median_equivalent_value(h, iter.value_from_index);
        }
    }

    return (total * 1.0) / h->total_count;
}

double hdr_stddev(struct hdr_histogram* h)
{
    double mean = hdr_mean(h);
    double geometric_dev_total = 0.0;

    struct hdr_iter iter;
    hdr_iter_init(&iter, h);

    while (hdr_iter_next(&iter))
    {
        if (0 != iter.count_at_index)
        {
            double dev = (median_equivalent_value(h, iter.value_from_index) * 1.0) - mean;
            geometric_dev_total += (dev * dev) * iter.count_at_index;
        }
    }

    return sqrt(geometric_dev_total / h->total_count);
}

bool hdr_values_are_equivalent(struct hdr_histogram* h, int64_t a, int64_t b)
{
    return lowest_equivalent_value(h, a) == lowest_equivalent_value(h, b);
}

int64_t hdr_lowest_equivalent_value(struct hdr_histogram* h, int64_t value)
{
    return lowest_equivalent_value(h, value);
}

int64_t hdr_count_at_value(struct hdr_histogram* h, int64_t value)
{
    return counts_get_normalised(h, counts_index_for(h, value));
}


// #### ######## ######## ########     ###    ########  #######  ########   ######
//  ##     ##    ##       ##     ##   ## ##      ##    ##     ## ##     ## ##    ##
//  ##     ##    ##       ##     ##  ##   ##     ##    ##     ## ##     ## ##
//  ##     ##    ######   ########  ##     ##    ##    ##     ## ########   ######
//  ##     ##    ##       ##   ##   #########    ##    ##     ## ##   ##         ##
//  ##     ##    ##       ##    ##  ##     ##    ##    ##     ## ##    ##  ##    ##
// ####    ##    ######## ##     ## ##     ##    ##     #######  ##     ##  ######


static bool has_buckets(struct hdr_iter* iter)
{
    return iter->bucket_index < iter->h->bucket_count;
}

static bool has_next(struct hdr_iter* iter)
{
    return iter->count_to_index < iter->h->total_count;
}

static void increment_bucket(struct hdr_histogram* h, int32_t* bucket_index, int32_t* sub_bucket_index)
{
    (*sub_bucket_index)++;

    if (*sub_bucket_index >= h->sub_bucket_count)
    {
        *sub_bucket_index = h->sub_bucket_half_count;
        (*bucket_index)++;
    }
}

static bool move_next(struct hdr_iter* iter)
{
    increment_bucket(iter->h, &iter->bucket_index, &iter->sub_bucket_index);

    if (!has_buckets(iter))
    {
        return false;
    }

    iter->count_at_index  = get_count_at_index(iter->h, iter->bucket_index, iter->sub_bucket_index);
    iter->count_to_index += iter->count_at_index;

    iter->value_from_index = value_from_index(iter->bucket_index, iter->sub_bucket_index, iter->h->unit_magnitude);
    iter->highest_equivalent_value = highest_equivalent_value(iter->h, iter->value_from_index);

    return true;
}

static int64_t peek_next_value_from_index(struct hdr_iter* iter)
{
    int32_t bucket_index     = iter->bucket_index;
    int32_t sub_bucket_index = iter->sub_bucket_index;

    increment_bucket(iter->h, &bucket_index, &sub_bucket_index);

    return value_from_index(bucket_index, sub_bucket_index, iter->h->unit_magnitude);
}

void hdr_iter_init(struct hdr_iter* itr, struct hdr_histogram* h)
{
    itr->h = h;

    itr->bucket_index       =  0;
    itr->sub_bucket_index   = -1;
    itr->count_at_index     =  0;
    itr->count_to_index     =  0;
    itr->value_from_index   =  0;
    itr->highest_equivalent_value = 0;
}

bool hdr_iter_next(struct hdr_iter* iter)
{
    if (!has_next(iter))
    {
        return false;
    }

    move_next(iter);

    return true;
}


// ########  ######## ########   ######  ######## ##    ## ######## #### ##       ########  ######
// ##     ## ##       ##     ## ##    ## ##       ###   ##    ##     ##  ##       ##       ##    ##
// ##     ## ##       ##     ## ##       ##       ####  ##    ##     ##  ##       ##       ##
// ########  ######   ########  ##       ######   ## ## ##    ##     ##  ##       ######    ######
// ##        ##       ##   ##   ##       ##       ##  ####    ##     ##  ##       ##             ##
// ##        ##       ##    ##  ##    ## ##       ##   ###    ##     ##  ##       ##       ##    ##
// ##        ######## ##     ##  ######  ######## ##    ##    ##    #### ######## ########  ######


void hdr_percentile_iter_init(struct hdr_percentile_iter* percentiles,
                               struct hdr_histogram* h,
                               int32_t ticks_per_half_distance)
{
    hdr_iter_init(&percentiles->iter, h);

    percentiles->seen_last_value          = false;
    percentiles->ticks_per_half_distance  = ticks_per_half_distance;
    percentiles->percentile_to_iterate_to = 0.0;
    percentiles->percentile               = 0.0;
}

bool hdr_percentile_iter_next(struct hdr_percentile_iter* percentiles)
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

    if (percentiles->iter.sub_bucket_index == -1 && !hdr_iter_next(&percentiles->iter))
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
    while (hdr_iter_next(&percentiles->iter));

    return true;
}

static void format_line_string(char* str, size_t len, int significant_figures, format_type format)
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

static const char CLASSIC_FOOTER[] =
    "#[Mean    = %12.3f, StdDeviation   = %12.3f]\n"
    "#[Max     = %12.3f, Total count    = %12" PRIu64 "]\n"
    "#[Buckets = %12d, SubBuckets     = %12d]\n";

int hdr_percentiles_print(
    struct hdr_histogram* h, FILE* stream, int32_t ticks_per_half_distance,
    double value_scale, format_type format)
{
    char line_format[25];
    format_line_string(line_format, 25, h->significant_figures, format);
    const char* head_format = format_head_string(format);
    int rc = 0;

    struct hdr_percentile_iter percentiles;
    hdr_percentile_iter_init(&percentiles, h, ticks_per_half_distance);

    if (fprintf(
        stream, head_format,
        "Value", "Percentile", "TotalCount", "1/(1-Percentile)") < 0)
    {
        rc = EIO;
        goto cleanup;
    }

    while (hdr_percentile_iter_next(&percentiles))
    {
        double  value               = percentiles.iter.highest_equivalent_value / value_scale;
        double  percentile          = percentiles.percentile / 100.0;
        int64_t total_count         = percentiles.iter.count_to_index;
        double  inverted_percentile = (1.0 / (1.0 - percentile));

        if (fprintf(
            stream, line_format, value, percentile, total_count, inverted_percentile) < 0)
        {
            rc = EIO;
            goto cleanup;
        }
    }

    if (CLASSIC == format)
    {
        double mean   = hdr_mean(h)   / value_scale;
        double stddev = hdr_stddev(h) / value_scale;
        double max    = hdr_max(h)    / value_scale;

        if (fprintf(
            stream, CLASSIC_FOOTER,  mean, stddev, max,
            h->total_count, h->bucket_count, h->sub_bucket_count) < 0)
        {
            rc = EIO;
            goto cleanup;
        }
    }

cleanup:
    return rc;
}


// ########  ########  ######   #######  ########  ########  ######## ########
// ##     ## ##       ##    ## ##     ## ##     ## ##     ## ##       ##     ##
// ##     ## ##       ##       ##     ## ##     ## ##     ## ##       ##     ##
// ########  ######   ##       ##     ## ########  ##     ## ######   ##     ##
// ##   ##   ##       ##       ##     ## ##   ##   ##     ## ##       ##     ##
// ##    ##  ##       ##    ## ##     ## ##    ##  ##     ## ##       ##     ##
// ##     ## ########  ######   #######  ##     ## ########  ######## ########


void hdr_recorded_iter_init(struct hdr_recorded_iter* recorded, struct hdr_histogram* h)
{
    hdr_iter_init(&recorded->iter, h);
    recorded->count_added_in_this_iteration_step = 0;
}

bool hdr_recorded_iter_next(struct hdr_recorded_iter* recorded)
{
    while (hdr_iter_next(&recorded->iter))
    {
        if (recorded->iter.count_at_index != 0)
        {
            recorded->count_added_in_this_iteration_step = recorded->iter.count_at_index;
            return true;
        }
    }

    return false;
}


// ##       #### ##    ## ########    ###    ########
// ##        ##  ###   ## ##         ## ##   ##     ##
// ##        ##  ####  ## ##        ##   ##  ##     ##
// ##        ##  ## ## ## ######   ##     ## ########
// ##        ##  ##  #### ##       ######### ##   ##
// ##        ##  ##   ### ##       ##     ## ##    ##
// ######## #### ##    ## ######## ##     ## ##     ##


void hdr_linear_iter_init(
    struct hdr_linear_iter* linear,
    struct hdr_histogram* h,
    int64_t value_units_per_bucket)
{
    hdr_iter_init(&linear->iter, h);
    linear->count_added_in_this_iteration_step = 0;
    linear->value_units_per_bucket = value_units_per_bucket;
    linear->next_value_reporting_level = value_units_per_bucket;
    linear->next_value_reporting_level_lowest_equivalent = lowest_equivalent_value(h, value_units_per_bucket);
}

bool hdr_linear_iter_next(struct hdr_linear_iter* linear)
{
    linear->count_added_in_this_iteration_step = 0;

    if (has_next(&linear->iter) ||
        peek_next_value_from_index(&linear->iter) > linear->next_value_reporting_level_lowest_equivalent)
    {
        do
        {
            if (linear->iter.value_from_index >= linear->next_value_reporting_level_lowest_equivalent)
            {
                linear->next_value_reporting_level += linear->value_units_per_bucket;
                linear->next_value_reporting_level_lowest_equivalent = lowest_equivalent_value(linear->iter.h, linear->next_value_reporting_level);

                return true;
            }

            if (!move_next(&linear->iter))
            {
                break;
            }
            linear->count_added_in_this_iteration_step += linear->iter.count_at_index;
        }
        while (true);
    }

    return false;
}


// ##        #######   ######      ###    ########  #### ######## ##     ## ##     ## ####  ######
// ##       ##     ## ##    ##    ## ##   ##     ##  ##     ##    ##     ## ###   ###  ##  ##    ##
// ##       ##     ## ##         ##   ##  ##     ##  ##     ##    ##     ## #### ####  ##  ##
// ##       ##     ## ##   #### ##     ## ########   ##     ##    ######### ## ### ##  ##  ##
// ##       ##     ## ##    ##  ######### ##   ##    ##     ##    ##     ## ##     ##  ##  ##
// ##       ##     ## ##    ##  ##     ## ##    ##   ##     ##    ##     ## ##     ##  ##  ##    ##
// ########  #######   ######   ##     ## ##     ## ####    ##    ##     ## ##     ## ####  ######


void hdr_log_iter_init(
    struct hdr_log_iter* logarithmic,
    struct hdr_histogram* h,
    int64_t value_units_first_bucket,
    double log_base)
{
    hdr_iter_init(&logarithmic->iter, h);
    logarithmic->count_added_in_this_iteration_step = 0;
    logarithmic->value_units_first_bucket = value_units_first_bucket;
    logarithmic->log_base = log_base;
    logarithmic->next_value_reporting_level = value_units_first_bucket;
    logarithmic->next_value_reporting_level_lowest_equivalent = lowest_equivalent_value(h, value_units_first_bucket);
}

bool hdr_log_iter_next(struct hdr_log_iter* logarithmic)
{
    logarithmic->count_added_in_this_iteration_step = 0;

    if (has_next(&logarithmic->iter) ||
        peek_next_value_from_index(&logarithmic->iter) > logarithmic->next_value_reporting_level_lowest_equivalent)
    {
        do
        {
            if (logarithmic->iter.value_from_index >= logarithmic->next_value_reporting_level_lowest_equivalent)
            {
                logarithmic->next_value_reporting_level *= logarithmic->log_base;
                logarithmic->next_value_reporting_level_lowest_equivalent = lowest_equivalent_value(logarithmic->iter.h, logarithmic->next_value_reporting_level);

                return true;
            }

            if (!move_next(&logarithmic->iter))
            {
                break;
            }

            logarithmic->count_added_in_this_iteration_step += logarithmic->iter.count_at_index;
        }
        while (true);
    }

    return false;
}
