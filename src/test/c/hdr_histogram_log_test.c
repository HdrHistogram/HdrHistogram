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

static struct hdr_histogram* raw_histogram = NULL;
static struct hdr_histogram* cor_histogram = NULL;

static void load_histograms()
{
    int i;
    if (raw_histogram)
    {
        free(raw_histogram);
    }

    hdr_alloc(3600L * 1000 * 1000, 3, &raw_histogram);

    if (cor_histogram)
    {
        free(cor_histogram);
    }

    hdr_alloc(3600L * 1000 * 1000, 3, &cor_histogram);

    for (i = 0; i < 10000; i++)
    {
        hdr_record_value(raw_histogram, 1000L);
        hdr_record_corrected_value(cor_histogram, 1000L, 10000L);
    }

    hdr_record_value(raw_histogram, 100000000L);
    hdr_record_corrected_value(cor_histogram, 100000000L, 10000L);
}

static char* test_encode_and_decode()
{
    load_histograms();

    size_t raw_histogram_size = hdr_get_memory_size(cor_histogram);

    uint8_t* buffer = (uint8_t*) malloc(hdr_get_memory_size(cor_histogram));

    size_t encode_result = hdr_encode(cor_histogram, buffer, raw_histogram_size);

    mu_assert("Did not encode", encode_result != 0);
    mu_assert("Incorrect size", encode_result <= raw_histogram_size);

    struct hdr_histogram* loaded_histogram = NULL;
    hdr_decode(buffer, raw_histogram_size, &loaded_histogram);

    int compare_result = memcmp(cor_histogram, loaded_histogram, raw_histogram_size);

    if (compare_result != 0)
    {
        uint8_t* a = (uint8_t*) cor_histogram;
        uint8_t* b = (uint8_t*) loaded_histogram;
        for (int i = 0; i < raw_histogram_size; i++)
        {
            if (a[i] != b[i])
            {
                printf("Mismatch at %d: %x - %x\n", i, a[i] & 0xFF, b[i] & 0xFF);
            }
        }
    }

    mu_assert("Comparison did not match", compare_result == 0);

    return 0;
}


static char* test_encode_and_decode_compressed()
{
    load_histograms();

    size_t raw_histogram_size = hdr_get_memory_size(raw_histogram);

    uint8_t* buffer = (uint8_t*) malloc(hdr_get_memory_size(raw_histogram));

    size_t encode_result = hdr_encode_compressed(raw_histogram, buffer, raw_histogram_size);

    mu_assert("Did not encode", encode_result == 0);

    int32_t compressed_length = hdr_get_compressed_length(buffer);

    struct hdr_histogram* loaded_histogram = NULL;
    int decode_result = hdr_decode_compressed(buffer, compressed_length, &loaded_histogram);

    if (decode_result != 0)
    {
        printf("%s\n", hdr_strerror(decode_result));
    }
    mu_assert("Did not decode", decode_result == 0);

    mu_assert("Loaded histogram is null", loaded_histogram != NULL);
    int compare_result = memcmp(raw_histogram, loaded_histogram, raw_histogram_size);

    mu_assert("Comparison did not match", compare_result == 0);

    return 0;
}

// Prototypes to avoid exporting in header file.
void base64_encode_block(const uint8_t* input, char* output);
int base64_encode(
    const uint8_t* input, size_t input_len, char* output, size_t output_len);

void base64_decode_block(const char* input, uint8_t* output);
int base64_decode(
    const char* input, size_t input_len, uint8_t* output, size_t output_len);

static bool assert_base64_encode(const char* input, const char* expected)
{
    int input_len = strlen(input);
    int output_len = ceil((input_len / 3.0) * 4.0);

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


static bool assert_base64_encode_block(const char* input, const char* expected)
{
    char output[5];
    output[4] = '\0';

    base64_encode_block((uint8_t*)input, output);

    return compare_string(expected, output, 4);
}

static char* base64_encode_block_encodes_3_bytes()
{
    mu_assert("Encoding", assert_base64_encode_block("Man", "TWFu"));

    return 0;
}


static bool assert_base64_decode_block(const char* input, const char* expected)
{
    uint8_t output[4];
    output[3] = '\0';

    base64_decode_block(input, output);

    return compare_string(expected, (char*) output, 3);
}


static char* base64_decode_block_decodes_4_chars()
{
    mu_assert("Decoding", assert_base64_decode_block("TWFu", "Man"));

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
    mu_assert(
        "Input length not multiple of 4",
        base64_decode(NULL, 5, NULL, 3) != 0);
    mu_assert("Input length < 4", base64_decode(NULL, 3, NULL, 3) != 0);
    mu_assert("Output length < 3", base64_decode(NULL, 5, NULL, 2) != 0);

    return 0;
}

static char* test_parse_log()
{
    struct hdr_histogram* h;
    FILE* log_file = fopen("src/test/resources/hiccup.140623.1028.10646.hlog", "r");

    hdr_parse_log(log_file, &h);

    return 0;
}


static struct mu_result all_tests()
{
    tests_run = 0;

    mu_run_test(test_encode_and_decode);
    mu_run_test(test_encode_and_decode_compressed);

    mu_run_test(base64_decode_block_decodes_4_chars);
    mu_run_test(base64_decode_fails_with_invalid_lengths);
    mu_run_test(base64_decode_decodes_strings_without_padding);
    mu_run_test(base64_decode_decodes_strings_with_padding);

    mu_run_test(base64_encode_block_encodes_3_bytes);
    mu_run_test(base64_encode_encodes_without_padding);
    mu_run_test(base64_encode_encodes_with_padding);

    mu_run_test(test_parse_log);

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
