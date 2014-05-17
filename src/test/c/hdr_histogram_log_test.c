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

static struct mu_result all_tests()
{
    tests_run = 0;

    mu_run_test(test_encode_and_decode);
    mu_run_test(test_encode_and_decode_compressed);

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
