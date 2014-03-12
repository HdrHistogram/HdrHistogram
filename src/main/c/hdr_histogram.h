/**
 * hdr_histogram.h
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This code follows the Plan 9 approach to header declaration.  In order
 * to maintain fast builds does not define it's dependent headers.
 * They should be included manually by the user.  This code requires:
 *
 * - #include <stdint.h>
 * - #include <stdbool.h>
 * - #include <stdio.h>
 */

#ifndef _HDR_HISTOGRAM_H_
#define _HDR_HISTOGRAM_H_ 1

struct hdr_histogram
{
    int64_t highest_trackable_value;
    int64_t significant_figures;
    int32_t sub_bucket_half_count_magnitude;
    int32_t sub_bucket_half_count;
    int64_t sub_bucket_mask;
    int32_t sub_bucket_count;
    int32_t bucket_count;
    int32_t counts_len;
    int64_t total_count;
    int64_t counts[0];
};

/**
 * Allocate the memory and initialise the hdr_histogram.
 *
 * Due to the size of the histogram being the result of some reasonably
 * involved math on the input parameters this function it is tricky to stack allocate.
 * The histogram is allocated in a single contigious block so can be delete via free,
 * without any structure specific destructor.
 *
 * @param highest_trackable_value The largest possible value to be put into the
 * histogram.
 * @param significant_figures The level of precision for this histogram, i.e. the number
 * of figures in a decimal number that will be maintained.  E.g. a value of 3 will mean
 * the results from the histogram will be accurate up to the first three digits.  Must
 * be a value between 3 and 6 (inclusive).
 * @param result Output parameter to capture allocated histogram.
 * @return 0 on success, -1 if the significant_figure value is outside of the allowed range.
 * or -2 if malloc failed.
 */
int hdrh_alloc(int64_t highest_trackable_value, int significant_figures, struct hdr_histogram** result);

/**
 * Record a value in the histogram.
 *
 * Records a value in the histogram, will round this value of to a precision at or better
 * than the significant_figure specified at contruction time.

 * @param h "This" pointer
 * @param value Value to add to the histogram
 * @return false if the value is larger than the highest_trackable_value and can't be recorded,
 * true otherwise.
 */
bool hdrh_record_value(struct hdr_histogram* h, int64_t value);

/**
 * Record a value in the histogram and backfill based on an expected interval.
 *
 * Records a value in the histogram, will round this value of to a precision at or better
 * than the significant_figure specified at contruction time.  This is specifically used
 * for recording latency.  If the value is larger than the expected_interval then the
 * latency recording system has experienced co-ordinated omission.  This method fill in the
 * values that would of occured had the client providing the load not been blocked.

 * @param h "This" pointer
 * @param value Value to add to the histogram
 * @param expected_interval The delay between recording values.
 * @return false if the value is larger than the highest_trackable_value and can't be recorded,
 * true otherwise.
 */
bool hdrh_record_corrected_value(struct hdr_histogram* h, int64_t value, int64_t expexcted_interval);

int64_t hdrh_min(struct hdr_histogram* h);
int64_t hdrh_max(struct hdr_histogram* h);
int64_t hdrh_value_at_percentile(struct hdr_histogram* h, double percentile);

double hdrh_mean(struct hdr_histogram* h);
bool hdrh_values_are_equivalent(struct hdr_histogram* h, int64_t a, int64_t b);

/**
 * The basic iterator.  This is the equivlent of the
 * AllValues iterator from the Java implementation.  It iterates
 * through all entries in the histogram whether or not a value
 * is recorded.
 */
struct hdrh_iter
{
    struct hdr_histogram* h;
    int32_t bucket_index;
    int32_t sub_bucket_index;
    int64_t count_at_index;
    int64_t count_to_index;
    int64_t value_from_index;
    int64_t highest_equivalent_value;
};

/**
 * Initalises the basic iterator.
 *
 * @param itr 'This' pointer
 * @param h The histogram to iterate over
 */
void hdrh_iter_init(struct hdrh_iter* iter, struct hdr_histogram* h);
/**
 * Iterate to the next value for the iterator.  If there are no more values
 * available return faluse.
 *
 * @param itr 'This' pointer
 * @return 'false' if there are no values remaining for this iterator.
 */
bool hdrh_iter_next(struct hdrh_iter* iter);

/**
 * Iterator for percentile values.  Equivalent to the PercentileIterator
 * from the Java implementation.
 */
struct hdrh_percentile_iter
{
    struct hdrh_iter iter;
    bool seen_last_value;
    int32_t ticks_per_half_distance;
    double percentile_to_iterate_to;
    double percentile;
};

/**
 * Initialise the percentiles.
 *
 * @param percentiles 'This' pointer
 * @param h The histogram to iterate over
 * @param ticks_per_half_distance The number of iteration steps per half-distance to 100%
 */
void hdrh_percentile_iter_init(struct hdrh_percentile_iter* percentiles,
                               struct hdr_histogram* h,
                               int32_t ticks_per_half_distance);

/**
 * Iterate to the next percentile step, defined by the ticks_per_half_distance.
 *
 * @param percentiles 'This' pointer
 * @return 'false' if there are no values remaining for this iterator.
 */
bool hdrh_percentile_iter_next(struct hdrh_percentile_iter* percentiles);

typedef enum {
    CLASSIC,
    CSV
} format_type;

/**
 * Print out a percentile based histogram to the supplied stream.
 *
 * @param h 'This' pointer
 * @param stream The FILE to write the output to
 * @param ticks_per_half_distance The number of iteration steps per half-distance to 100%
 * @param value_scale Scale the output values by this amount
 * @param format_type Format to use, e.g. CSV.
 */
void hdrh_percentiles_print(struct hdr_histogram* h,
                            FILE* stream,
                            int32_t ticks_per_half_distance,
                            double value_scale,
                            format_type format);

struct hdrh_recorded_iter
{
    struct hdrh_iter iter;
    int64_t count_added_in_this_iteration_step;
};

void hdrh_recorded_iter_init(struct hdrh_recorded_iter* recorded, struct hdr_histogram* h);

bool hdrh_recorded_iter_next(struct hdrh_recorded_iter* recorded);

struct hdrh_linear_iter
{
    struct hdrh_iter iter;
    int value_units_per_bucket;
    int64_t count_added_in_this_iteration_step;
    int64_t next_value_reporting_level;
    int64_t next_value_reporting_level_lowest_equivalent;
};

void hdrh_linear_iter_init(struct hdrh_linear_iter* linear, struct hdr_histogram* h, int value_units_per_bucket);

bool hdrh_linear_iter_next(struct hdrh_linear_iter* linear);

#endif
