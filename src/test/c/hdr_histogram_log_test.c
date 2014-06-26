/**
 * hdr_test.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <errno.h>

#include <stdio.h>
#include <hdr_histogram.h>
#include <hdr_histogram_log.h>
#include "minunit.h"

static bool compare_string(const char* a, const char* b, int len)
{
    if (strncmp(a, b, len) == 0)
    {
        return true;
    }

    printf("%s != %s\n", a, b);
    return false;
}

static bool compare_binary(const void* a, const void* b, int len)
{
    if (memcmp(a, b, len) == 0)
    {
        return true;
    }

    uint8_t* u_a = (uint8_t*) a;
    uint8_t* u_b = (uint8_t*) b;

    int error_count = 0;
    for (int i = 0; i < len; i++)
    {
        if (u_a[i] != u_b[i])
        {
            printf("%d of %d, a = %d, b = %d\n", i, len, u_a[i], u_b[i]);
            if (++error_count >= 10)
            {
                printf("%d or more mismatches\n", error_count);
                break;
            }
        }
    }
    return false;
}

static bool compare_histogram(struct hdr_histogram* a, struct hdr_histogram* b)
{
    size_t a_size = hdr_get_memory_size(a);
    size_t b_size = hdr_get_memory_size(b);

    if (a_size == b_size && memcmp(a, b, a_size) == 0)
    {
        return true;
    }

    printf("Sizes a: %d, b: %d\n", a_size, b_size);

    struct hdr_iter iter_a;
    struct hdr_iter iter_b;

    hdr_iter_init(&iter_a, a);
    hdr_iter_init(&iter_b, b);

    int i = 0;
    while (hdr_iter_next(&iter_a) && hdr_iter_next(&iter_b))
    {
        if (iter_a.count_at_index != iter_b.count_at_index ||
            iter_a.value_from_index != iter_b.value_from_index)
        {
            printf(
                "A - value: %d, count: %d, B - value: %d, count: %d\n",
                iter_a.value_from_index, iter_a.count_at_index,
                iter_b.value_from_index, iter_b.count_at_index);
        }
    }

}

static struct hdr_histogram* raw_histogram = NULL;
static struct hdr_histogram* cor_histogram = NULL;

static void load_histograms()
{
    int i;
    free(raw_histogram);
    free(cor_histogram);

    hdr_alloc(3600L * 1000 * 1000, 3, &raw_histogram);
    hdr_alloc(3600L * 1000 * 1000, 3, &cor_histogram);

    for (i = 0; i < 10000; i++)
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
int hdr_encode_compressed(struct hdr_histogram* h, uint8_t** buffer, int* length);

static char* test_encode_and_decode_compressed()
{
    load_histograms();

    uint8_t* buffer = NULL;
    int len = 0;
    int rc = 0;
    struct hdr_histogram* actual = NULL;
    struct hdr_histogram* expected = raw_histogram;
    size_t histogram_size = hdr_get_memory_size(expected);

    rc = hdr_encode_compressed(expected, &buffer, &len);
    mu_assert("Did not encode", validate_return_code(rc));

    rc = hdr_decode_compressed(buffer, len, &actual);
    mu_assert("Did not decode", validate_return_code(rc));

    mu_assert("Loaded histogram is null", actual != NULL);

    mu_assert(
        "Comparison did not match",
        compare_binary(expected, actual, histogram_size));

    free(actual);

    return 0;
}

static char* test_encode_and_decode_compressed_large()
{
    const int64_t limit = 3600L * 1000 * 1000;
    struct hdr_histogram* actual = NULL;
    struct hdr_histogram* expected = NULL;
    uint8_t* buffer = NULL;
    int len = 0;
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

static char* test_parse_log()
{
    // const char* file_name = "src/test/resources/hiccup.140623.1028.10646.hlog";
    const char* file_name = "histogram.log";
    struct hdr_histogram* h;
    FILE* log_file = fopen(file_name, "r");

    hdr_parse_log(log_file, &h);

    return 0;
}


static struct mu_result all_tests()
{
    tests_run = 0;

    // mu_run_test(test_encode_and_decode);
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

    // mu_run_test(test_parse_log);
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

    return result.message != 0;
}
