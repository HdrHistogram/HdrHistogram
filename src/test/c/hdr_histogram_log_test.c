/**
 * hdr_histogram_log_test.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <errno.h>
#include <inttypes.h>
#include <time.h>

#include <stdio.h>
#include "hdr_time.h"
#include <hdr_histogram.h>
#include <hdr_histogram_log.h>
#include "minunit.h"

int tests_run = 0;

static bool compare_int(int a, int b)
{
    if (a == b)
    {
        return true;
    }

    printf("%d != %d\n", a, b);
    return false;
}

static bool compare_int64_t(int64_t a, int64_t b)
{
    if (a == b)
    {
        return true;
    }

    printf("%"PRIu64" != %"PRIu64"\n", a, b);
    return false;
}

static long ns_to_ms(long ns)
{
    return (ns / 1000000) * 1000000;
}

static bool compare_timespec(struct timespec* a, struct timespec* b)
{
    char a_str[128];
    char b_str[128];

    long a_tv_msec = ns_to_ms(a->tv_nsec);
    long b_tv_msec = ns_to_ms(b->tv_nsec);

    if (a->tv_sec == a->tv_sec && a_tv_msec == b_tv_msec)
    {
        return true;
    }

    if (a->tv_sec != b->tv_sec)
    {
        printf(
            "tv_sec: %s != %s\n",
            ctime_r(&a->tv_sec, a_str),
            ctime_r(&b->tv_sec, b_str));
    }

    if (a_tv_msec == b_tv_msec)
    {
        printf("%ld != %ld\n", a->tv_nsec, b->tv_nsec);
    }

    return false;
}

static bool compare_string(const char* a, const char* b, int len)
{
    if (strncmp(a, b, len) == 0)
    {
        return true;
    }

    printf("%s != %s\n", a, b);
    return false;
}

static bool compare_histogram(struct hdr_histogram* a, struct hdr_histogram* b)
{
    if (a->counts_len != b->counts_len)
    {
        printf(
            "a.counts_len = %"PRIu32", b.counts_len = %"PRIu32"\n",
            a->counts_len, b->counts_len);
        return false;
    }

    int64_t a_max = hdr_max(a);
    int64_t b_max = hdr_max(b);

    if (a_max != b_max)
    {
        printf("a.max = %"PRIu64", b.max = %"PRIu64"\n", a_max, b_max);
        return false;
    }

    int64_t a_min = hdr_min(a);
    int64_t b_min = hdr_min(b);

    if (a_min != b_min)
    {
        printf("a.min = %"PRIu64", b.min = %"PRIu64"\n", a_min, b_min);
        return false;
    }

    size_t a_size = hdr_get_memory_size(a);
    size_t b_size = hdr_get_memory_size(b);

    if (a_size != b_size)
    {
        printf("a.size: %zu, b.size: %zu\n", a_size, b_size);
        return false;
    }

    size_t counts_size = a->counts_len * sizeof(int64_t);

    if (memcmp(a->counts, b->counts, counts_size) == 0)
    {
        return true;
    }

    printf("%s\n", "Counts incorrect");

    struct hdr_iter iter_a;
    struct hdr_iter iter_b;

    hdr_iter_init(&iter_a, a);
    hdr_iter_init(&iter_b, b);

    while (hdr_iter_next(&iter_a) && hdr_iter_next(&iter_b))
    {
        if (iter_a.count_at_index != iter_b.count_at_index ||
            iter_a.value_from_index != iter_b.value_from_index)
        {
            printf(
                "A - value: %"PRIu64", count: %"PRIu64", B - value: %"PRIu64", count: %"PRIu64"\n",
                iter_a.value_from_index, iter_a.count_at_index,
                iter_b.value_from_index, iter_b.count_at_index);
        }
    }

    return false;
}

static struct hdr_histogram* raw_histogram = NULL;
static struct hdr_histogram* cor_histogram = NULL;

static void load_histograms()
{
    free(raw_histogram);
    free(cor_histogram);

    hdr_alloc(3600L * 1000 * 1000, 3, &raw_histogram);
    hdr_alloc(3600L * 1000 * 1000, 3, &cor_histogram);

    for (int i = 0; i < 10000; i++)
    {
        hdr_record_value(raw_histogram, 1000L);
        hdr_record_corrected_value(cor_histogram, 1000L, 10000L);
    }

    hdr_record_value(raw_histogram, 100000000L);
    hdr_record_corrected_value(cor_histogram, 100000000L, 10000L);
}

static bool validate_return_code(int rc)
{
    if (rc == 0)
    {
        return true;
    }

    printf("%s\n", hdr_strerror(rc));
    return false;
}

// Prototypes to avoid exporting in header file.
void base64_encode_block(const uint8_t* input, char* output);
int base64_encode(
    const uint8_t* input, size_t input_len, char* output, size_t output_len);

void base64_decode_block(const char* input, uint8_t* output);
int base64_decode(
    const char* input, size_t input_len, uint8_t* output, size_t output_len);
int hdr_encode_compressed(struct hdr_histogram* h, uint8_t** buffer, size_t* length);
int hdr_decode_compressed(
    uint8_t* buffer, size_t length, struct hdr_histogram** histogram);

static char* test_encode_and_decode_compressed()
{
    load_histograms();

    uint8_t* buffer = NULL;
    size_t len = 0;
    int rc = 0;
    struct hdr_histogram* actual = NULL;
    struct hdr_histogram* expected = raw_histogram;

    rc = hdr_encode_compressed(expected, &buffer, &len);
    mu_assert("Did not encode", validate_return_code(rc));

    rc = hdr_decode_compressed(buffer, len, &actual);
    mu_assert("Did not decode", validate_return_code(rc));

    mu_assert("Loaded histogram is null", actual != NULL);

    mu_assert(
        "Comparison did not match",
        compare_histogram(expected, actual));

    free(actual);

    return 0;
}

static char* test_encode_and_decode_compressed_large()
{
    const int64_t limit = 3600L * 1000 * 1000;
    struct hdr_histogram* actual = NULL;
    struct hdr_histogram* expected = NULL;
    uint8_t* buffer = NULL;
    size_t len = 0;
    int rc = 0;
    hdr_init(1, limit, 4, &expected);
    srand(5);

    for (int i = 0; i < 8070; i++)
    {
        hdr_record_value(expected, rand() % limit);
    }

    rc = hdr_encode_compressed(expected, &buffer, &len);
    mu_assert("Did not encode", validate_return_code(rc));

    rc = hdr_decode_compressed(buffer, len, &actual);
    mu_assert("Did not decode", validate_return_code(rc));

    mu_assert("Loaded histogram is null", actual != NULL);

    mu_assert(
        "Comparison did not match",
        compare_histogram(expected, actual));

    free(expected);
    free(actual);

    return 0;
}


static bool assert_base64_encode(const char* input, const char* expected)
{
    int input_len = strlen(input);
    int output_len = (int) (ceil(input_len / 3.0) * 4.0);

    char* output = calloc(sizeof(char), output_len);

    int r = base64_encode((uint8_t*)input, input_len, output, output_len);
    bool result = r == 0 && compare_string(expected, output, output_len);

    free(output);

    return result;
}

static char* base64_encode_encodes_without_padding()
{
    mu_assert(
        "Encoding without padding",
        assert_base64_encode(
            "any carnal pleasur",
            "YW55IGNhcm5hbCBwbGVhc3Vy"));

    return 0;
}

static char* base64_encode_encodes_with_padding()
{
    mu_assert(
        "Encoding with padding '='",
        assert_base64_encode(
            "any carnal pleasure.",
            "YW55IGNhcm5hbCBwbGVhc3VyZS4="));
    mu_assert(
        "Encoding with padding '=='",
        assert_base64_encode(
            "any carnal pleasure",
            "YW55IGNhcm5hbCBwbGVhc3VyZQ=="));

    return 0;
}

static char* base64_encode_fails_with_invalid_lengths()
{
    mu_assert(
        "Output length not 4/3 of input length",
        base64_encode(NULL, 9, NULL, 11));

    return 0;
}

static char* base64_encode_block_encodes_3_bytes()
{
    char output[5] = { 0 };

    base64_encode_block((uint8_t*)"Man", output);
    mu_assert("Encoding", compare_string("TWFu", output, 4));

    return 0;
}

static char* base64_decode_block_decodes_4_chars()
{
    uint8_t output[4] = { 0 };

    base64_decode_block("TWFu", output);
    mu_assert("Decoding", compare_string("Man", (char*) output, 3));

    return 0;
}

static bool assert_base64_decode(const char* base64_encoded, const char* expected)
{
    int encoded_len = strlen(base64_encoded);
    int output_len = (encoded_len / 4) * 3;

    uint8_t* output = calloc(sizeof(uint8_t), output_len);

    int result = base64_decode(base64_encoded, encoded_len, output, output_len);

    return result == 0 && compare_string(expected, (char*)output, output_len);
}

static char* base64_decode_decodes_strings_without_padding()
{
    mu_assert(
        "Encoding without padding",
        assert_base64_decode(
            "YW55IGNhcm5hbCBwbGVhc3Vy",
            "any carnal pleasur"));

    return 0;
}

static char* base64_decode_decodes_strings_with_padding()
{
    mu_assert(
        "Encoding with padding '='",
        assert_base64_decode(
            "YW55IGNhcm5hbCBwbGVhc3VyZS4=",
            "any carnal pleasure."));

    mu_assert(
        "Encoding with padding '=='",
        assert_base64_decode(
            "YW55IGNhcm5hbCBwbGVhc3VyZQ==",
            "any carnal pleasure"));

    return 0;
}

static char* base64_decode_fails_with_invalid_lengths()
{
    mu_assert("Input length % 4 != 0", base64_decode(NULL, 5, NULL, 3) != 0);
    mu_assert("Input length < 4", base64_decode(NULL, 3, NULL, 3) != 0);
    mu_assert(
        "Output length not 3/4 of input length",
        base64_decode(NULL, 8, NULL, 7) != 0);

    return 0;
}

static char* writes_and_reads_log()
{
    const char* file_name = "histogram.log";
    struct timespec timestamp;
    struct timespec interval;

    hdr_gettime(&timestamp);
    interval.tv_sec = 5;
    interval.tv_nsec = 2000000;

    struct hdr_log_writer writer;
    struct hdr_log_reader reader;
    hdr_log_writer_init(&writer);
    hdr_log_reader_init(&reader);
    int rc = 0;

    FILE* log_file = fopen(file_name, "w+");

    rc = hdr_log_write_header(&writer, log_file, "Test log", &timestamp);
    mu_assert("Failed header write", validate_return_code(rc));
    hdr_log_write(&writer, log_file, &timestamp, &interval, cor_histogram);
    mu_assert("Failed corrected write", validate_return_code(rc));
    hdr_log_write(&writer, log_file, &timestamp, &interval, raw_histogram);
    mu_assert("Failed raw write", validate_return_code(rc));

    fprintf(log_file, "\n");

    fflush(log_file);
    fclose(log_file);

    log_file = fopen(file_name, "r");

    struct hdr_histogram* read_cor_histogram = NULL;
    struct hdr_histogram* read_raw_histogram = NULL;

    rc = hdr_log_read_header(&reader, log_file);
    mu_assert("Failed header read", validate_return_code(rc));
    mu_assert("Incorrect major version", compare_int(reader.major_version, 1));
    mu_assert("Incorrect minor version", compare_int(reader.minor_version, 1));
    mu_assert(
        "Incorrect start timestamp",
        compare_timespec(&reader.start_timestamp, &timestamp));

    struct timespec actual_timestamp;
    struct timespec actual_interval;

    rc = hdr_log_read(
        &reader, log_file, &read_cor_histogram,
        &actual_timestamp, &actual_interval);
    mu_assert("Failed corrected read", validate_return_code(rc));
    mu_assert(
        "Incorrect first timestamp", compare_timespec(&actual_timestamp, &timestamp));
    mu_assert(
        "Incorrect first interval", compare_timespec(&actual_interval, &interval));

    rc = hdr_log_read(&reader, log_file, &read_raw_histogram, NULL, NULL);
    mu_assert("Failed raw read", validate_return_code(rc));

    mu_assert(
        "Histograms do not match",
        compare_histogram(cor_histogram, read_cor_histogram));

    mu_assert(
        "Histograms do not match",
        compare_histogram(raw_histogram, read_raw_histogram));

    rc = hdr_log_read(&reader, log_file, &read_cor_histogram, NULL, NULL);
    mu_assert("No EOF at end of file", rc == EOF);

    fclose(log_file);
    remove(file_name);

    return 0;
}

static char* log_reader_aggregates_into_single_histogram()
{
    const char* file_name = "histogram.log";
    struct timespec timestamp;
    struct timespec interval;

    hdr_gettime(&timestamp);
    interval.tv_sec = 5;
    interval.tv_nsec = 2000000;

    struct hdr_log_writer writer;
    struct hdr_log_reader reader;
    hdr_log_writer_init(&writer);
    hdr_log_reader_init(&reader);
    int rc = 0;

    FILE* log_file = fopen(file_name, "w+");

    hdr_log_write_header(&writer, log_file, "Test log", &timestamp);
    hdr_log_write(&writer, log_file, &timestamp, &interval, cor_histogram);
    hdr_log_write(&writer, log_file, &timestamp, &interval, raw_histogram);
    fflush(log_file);
    fclose(log_file);

    log_file = fopen(file_name, "r");

    struct hdr_histogram* histogram;
    hdr_alloc(3600L * 1000 * 1000, 3, &histogram);

    rc = hdr_log_read_header(&reader, log_file);
    mu_assert("Failed header read", validate_return_code(rc));
    rc = hdr_log_read(&reader, log_file, &histogram, NULL, NULL);
    mu_assert("Failed corrected read", validate_return_code(rc));
    rc = hdr_log_read(&reader, log_file, &histogram, NULL, NULL);
    mu_assert("Failed raw read", validate_return_code(rc));

    struct hdr_recorded_iter iter;
    hdr_recorded_iter_init(&iter, histogram);
    int64_t expected_total_count =
        raw_histogram->total_count + cor_histogram->total_count;

    mu_assert(
        "Total counts incorrect",
        compare_int64_t(histogram->total_count, expected_total_count));

    while (hdr_recorded_iter_next(&iter))
    {
        int64_t count = iter.iter.count_at_index;
        int64_t value = iter.iter.value_from_index;

        int64_t expected_count =
            hdr_count_at_value(raw_histogram, value) +
            hdr_count_at_value(cor_histogram, value);

        mu_assert("Incorrect count", compare_int64_t(count, expected_count));
    }

    fclose(log_file);
    remove(file_name);
    free(histogram);

    return 0;
}

static char* log_reader_fails_with_incorrect_version()
{
    const char* log_with_invalid_version =
    "#[Test log]\n"
    "#[Histogram log format version 1.00]\n"
    "#[StartTime: 1404700005.222 (seconds since epoch), Mon Jul 02:26:45 GMT 2014]\n"
    "StartTimestamp\",\"EndTimestamp\",\"Interval_Max\",\"Interval_Compressed_Histogram\"\n";
    const char* file_name = "histogram_with_invalid_version.log";
    struct hdr_log_reader reader;
    FILE* log_file;

    log_file = fopen(file_name, "w+");
    fprintf(log_file, "%s", log_with_invalid_version);
    fflush(log_file);
    fclose(log_file);

    log_file = fopen(file_name, "r");
    hdr_log_reader_init(&reader);
    int r = hdr_log_read_header(&reader, log_file);

    mu_assert("Should error with incorrect version", r == HDR_LOG_INVALID_VERSION);

    fclose(log_file);
    remove(file_name);

    return 0;
}


static struct mu_result all_tests()
{
    tests_run = 0;

    mu_run_test(test_encode_and_decode_compressed);
    mu_run_test(test_encode_and_decode_compressed_large);

    mu_run_test(base64_decode_block_decodes_4_chars);
    mu_run_test(base64_decode_fails_with_invalid_lengths);
    mu_run_test(base64_decode_decodes_strings_without_padding);
    mu_run_test(base64_decode_decodes_strings_with_padding);

    mu_run_test(base64_encode_block_encodes_3_bytes);
    mu_run_test(base64_encode_fails_with_invalid_lengths);
    mu_run_test(base64_encode_encodes_without_padding);
    mu_run_test(base64_encode_encodes_with_padding);

    mu_run_test(writes_and_reads_log);
    mu_run_test(log_reader_aggregates_into_single_histogram);
    mu_run_test(log_reader_fails_with_incorrect_version);

    free(raw_histogram);
    free(cor_histogram);

    mu_ok;
}

int hdr_histogram_log_run_tests()
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
    return hdr_histogram_log_run_tests();
}
