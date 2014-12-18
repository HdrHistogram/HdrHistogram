/**
* hdr_atomic_histogram.c
*
* Written by Michael Barker and released to the public domain,
* as explained at http://creativecommons.org/publicdomain/zero/1.0/
*
*/

#include <hdr_atomic_histogram.h>

int64_t _atomic_get(struct hdr_histogram* h, int32_t index)
{
    return __atomic_load_n(&h->counts[index], __ATOMIC_SEQ_CST);
}

void _atomic_increment(struct hdr_histogram* h, int32_t index, int64_t value)
{
    __atomic_add_fetch(&h->counts[index], value, __ATOMIC_SEQ_CST);
    __atomic_add_fetch(&h->total_count, value, __ATOMIC_SEQ_CST);
}

void _atomic_update_min_max(struct hdr_histogram* h, int64_t value)
{
    int64_t min = INT64_MAX;
    while (value != 0 && (min = __atomic_load_n(&h->min_value, __ATOMIC_SEQ_CST)) > value)
    {
        __atomic_compare_exchange_n(&h->min_value, &min, value, false, __ATOMIC_SEQ_CST, __ATOMIC_RELAXED);
    }

    int64_t max = 0;
    while ((max = __atomic_load_n(&h->max_value, __ATOMIC_SEQ_CST)) < value)
    {
        __atomic_compare_exchange_n(&h->max_value, &max, value, false, __ATOMIC_SEQ_CST, __ATOMIC_RELAXED);
    }
}

int hdr_atomic_init(
        int64_t lowest_trackable_value,
        int64_t highest_trackable_value,
        int significant_figures,
        struct hdr_histogram** result)
{
    int rc = hdr_init(lowest_trackable_value, highest_trackable_value, significant_figures, result);

    if (rc)
    {
        return rc;
    }

    (*result)->_get = _atomic_get;
    (*result)->_increment = _atomic_increment;
    (*result)->_update_min_max = _atomic_update_min_max;

    return rc;
}
