/**
 * hdr_writer_reader_phaser.h
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#ifndef HDR_WRITER_READER_PHASER_H
#define HDR_WRITER_READER_PHASER_H 1

#include <stdlib.h>
#include <stdbool.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include <errno.h>

struct hdr_writer_reader_phaser
{
    int64_t start_epoch;
    int64_t even_end_epoch;
    int64_t odd_end_epoch;
    pthread_mutex_t* reader_mutex;
} __attribute__((aligned (8)));

int64_t _hdr_phaser_get_epoch(int64_t* field)
{
    return __atomic_load_n(field, __ATOMIC_SEQ_CST);
}

void _hdr_phaser_set_epoch(int64_t* field, int64_t val)
{
    __atomic_store_n(field, val, __ATOMIC_SEQ_CST);
}

int64_t _hdr_phaser_reset_epoch(int64_t* field, int64_t initial_value)
{
    return __atomic_exchange_n(field, initial_value, __ATOMIC_SEQ_CST);
}

int hdr_writer_reader_phaser_init(struct hdr_writer_reader_phaser* p)
{
    if (NULL == p)
    {
        return EINVAL;
    }

    p->start_epoch = 0;
    p->even_end_epoch = 0;
    p->odd_end_epoch = INT64_MIN;
    p->reader_mutex = malloc(sizeof(pthread_mutex_t));

    if (!p->reader_mutex)
    {
        return ENOMEM;
    }

    int rc = pthread_mutex_init(p->reader_mutex, NULL);
    if (0 != rc)
    {
        return rc;
    }

    // TODO: Should I fence here.

    return 0;
}

void hdr_writer_reader_phaser_destory(struct hdr_writer_reader_phaser* p)
{
    pthread_mutex_destroy(p->reader_mutex);
}

int64_t hdr_phaser_writer_enter(struct hdr_writer_reader_phaser* p)
{
    return __atomic_add_fetch(&p->start_epoch, 1, __ATOMIC_SEQ_CST);
}

void hdr_phaser_writer_exit(
    struct hdr_writer_reader_phaser* p, int64_t critical_value_at_enter)
{
    int64_t* end_epoch = 
        (critical_value_at_enter < 0) ? &p->odd_end_epoch : &p->even_end_epoch;
    __atomic_add_fetch(end_epoch, 1, __ATOMIC_SEQ_CST);
}

void hdr_phaser_reader_lock(struct hdr_writer_reader_phaser* p)
{
    pthread_mutex_lock(p->reader_mutex);
}

void hdr_phaser_reader_unlock(struct hdr_writer_reader_phaser* p)
{
    pthread_mutex_unlock(p->reader_mutex);
}

void hdr_phaser_flip_phase(
    struct hdr_writer_reader_phaser* p, int64_t sleep_time_ns)
{
    // TODO: is_held_by_current_thread

    int64_t start_epoch = _hdr_phaser_get_epoch(&p->start_epoch);

    bool next_phase_is_even = (start_epoch < 0);

    // Clear currently used phase end epoch.
    int64_t initial_start_value;
    if (next_phase_is_even)
    {
        initial_start_value = 0;
        _hdr_phaser_set_epoch(&p->even_end_epoch, initial_start_value);
    }
    else
    {
        initial_start_value = INT64_MIN;
        _hdr_phaser_set_epoch(&p->odd_end_epoch, initial_start_value);
    }

    // Reset start value, indicating new phase.
    int64_t start_value_at_flip = 
        _hdr_phaser_reset_epoch(&p->start_epoch, initial_start_value);

    bool caught_up = false;
    do
    {
        int64_t* end_epoch = 
            next_phase_is_even ? &p->odd_end_epoch : &p->even_end_epoch;

        caught_up = _hdr_phaser_get_epoch(end_epoch) == start_value_at_flip;

        if (!caught_up)
        {
            if (sleep_time_ns == 0)
            {
                sched_yield();
            }
            else
            {
                usleep(sleep_time_ns / 1000);
            }
        }
    }
    while (!caught_up);
}

#endif