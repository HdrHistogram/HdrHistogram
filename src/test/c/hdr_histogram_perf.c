/**
 * hdr_histogram_perf.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <stdint.h>
#include <stdlib.h>

#include <stdio.h>
#include <hdr_histogram.h>
#include <hdr_atomic_histogram.h>
#include <locale.h>

#include "hdr_time.h"

struct timespec diff(struct timespec start, struct timespec end)
{
    struct timespec temp;
    if ((end.tv_nsec-start.tv_nsec) < 0)
    {
        temp.tv_sec = end.tv_sec - start.tv_sec - 1;
        temp.tv_nsec = 1000000000 + end.tv_nsec-start.tv_nsec;
    }
    else
    {
        temp.tv_sec = end.tv_sec - start.tv_sec;
        temp.tv_nsec = end.tv_nsec - start.tv_nsec;
    }
    return temp;
}

void inc(struct hdr_histogram* h, int32_t index, int64_t value)
{
    h->counts[index] += value;
    h->total_count += value;
}

int main(int argc, char **argv)
{
    struct hdr_histogram* histogram;
    int64_t max_value = 24 * 60 * 60 * 1000000L;
    int64_t min_value = 1;
    int result = -1;

    if (argc == 1)
    {
        result = hdr_init(min_value, max_value, 4, &histogram);
    }
    else if (argc == 2)
    {
        if (argv[1][0] == 'i')
        {
            printf("Using function pointer\n");

            result = hdr_init(min_value, max_value, 4, &histogram);
            histogram->_increment = inc;
        }
        else if (argv[1][0] == 'a')
        {
            printf("Using function atomic histogram\n");

            result = hdr_atomic_init(min_value, max_value, 4, &histogram);
        }
    }

    if (result != 0)
    {
        fprintf(stderr, "Failed to allocate histogram: %d\n", result);
        return -1;
    }


    struct timespec t0;
    struct timespec t1;
    setlocale(LC_NUMERIC, "");
    int64_t iterations = 400000000;

    for (int i = 0; i < 100; i++)
    {
        hdr_gettime(&t0);
        for (int64_t j = 1; j < iterations; j++)
        {
            hdr_record_value(histogram, j);
        }
        hdr_gettime(&t1);


        struct timespec taken = diff(t0, t1);
        double time_taken = taken.tv_sec + taken.tv_nsec / 1000000000.0;
        double ops_sec = (iterations - 1) / time_taken;

        printf("%s - %d, ops/sec: %'.2f\n", "Iteration", i + 1, ops_sec);
    }

    return 0;
}
