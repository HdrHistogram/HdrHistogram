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

// To prevent exporting the symbol, i.e. visible for testing.
void base64_encode(const uint8_t* buf, int length, FILE* f);

bool assert_base64_encode(const char* in, int len, const char* expected)
{
    const char* file_name = "/tmp/foo";
    FILE* fw = fopen(file_name, "w+");

    base64_encode((const uint8_t*) in, len, fw);

    fflush(fw);

    int num_bytes = (int) ftell(fw);

    rewind(fw);

    char* output = (char*) calloc(num_bytes + 1, sizeof(char));

    fgets(output, num_bytes + 1, fw);

    bool result = strncmp(expected, output, strlen(expected)) == 0;

    fclose(fw);
    remove(file_name);
    free(output);

    return result;
}

static char* test_encode_to_base64()
{
    mu_assert("Encoding 3 bytes", assert_base64_encode("Man", 3, "TWFu"));
    mu_assert("Encoding with padding '='",
              assert_base64_encode("any carnal pleasure.", 20, "YW55IGNhcm5hbCBwbGVhc3VyZS4="));
    mu_assert("Encoding with padding '=='",
              assert_base64_encode("any carnal pleasure",  19, "YW55IGNhcm5hbCBwbGVhc3VyZQ=="));
    mu_assert("Encoding without padding",
              assert_base64_encode("any carnal pleasur", 18, "YW55IGNhcm5hbCBwbGVhc3Vy"));

    return 0;
}

// Prototype to avoid exporting in header file.
int base64_decode_block(char* input, uint8_t* output);

bool assert_base64_decode_block(const char* input, const char* expected)
{
    uint8_t output[4];
    output[3] = '\0';

    int result = base64_decode_block(input, &output);

    return result == 0 && compare_string(expected, (char*) output, 3);
}


static char* test_base64_decode_block()
{
    mu_assert("Decoding", assert_base64_decode_block("TWFu", "Man"));
}

// To prevent exporting the symbol, i.e. visible for testing.
int base64_decode(char* input, size_t input_len, uint8_t* output, size_t output_len);

bool assert_base64_decode(const char* base64_encoded, const char* expected)
{
    int encoded_len = strlen(base64_encoded);
    int output_len = (encoded_len / 4) * 3;

    uint8_t* output = calloc(sizeof(uint8_t), output_len);

    int result = base64_decode(base64_encoded, encoded_len, output, output_len);

    return result == 0 && compare_string(expected, output, output_len);
}

static char* test_decode_from_base64()
{
    mu_assert(
            "Encoding without padding",
            assert_base64_decode("YW55IGNhcm5hbCBwbGVhc3Vy", "any carnal pleasur"));
    mu_assert(
            "Encoding with padding '='",
            assert_base64_decode("YW55IGNhcm5hbCBwbGVhc3VyZS4=", "any carnal pleasure."));
    mu_assert(
            "Encoding with padding '=='",
            assert_base64_decode("YW55IGNhcm5hbCBwbGVhc3VyZQ==", "any carnal pleasure"));

    return 0;
}


static struct mu_result all_tests()
{
    tests_run = 0;

    mu_run_test(test_encode_and_decode);
    mu_run_test(test_encode_and_decode_compressed);
    mu_run_test(test_encode_to_base64);
    mu_run_test(test_base64_decode_block);
    mu_run_test(test_decode_from_base64);

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
