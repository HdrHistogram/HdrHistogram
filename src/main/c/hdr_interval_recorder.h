/**
 * hdr_interval_recorder.h
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#ifndef HDR_INTERVAL_RECORDER_H
#define HDR_INTERVAL_RECORDER_H 1

#include <mintomic/mintomic.h>
#include <hdr_writer_reader_phaser.h>

struct hdr_interval_recorder
{
    mint_atomicPtr_t active;
    void* inactive;
    struct hdr_writer_reader_phaser phaser;
};

int hdr_interval_recorder_init(struct hdr_interval_recorder* r)
{
    return hdr_writer_reader_phaser_init(&r->phaser);
}

void hdr_interval_recorder_destroy(struct hdr_interval_recorder* r)
{
    hdr_writer_reader_phaser_destory(&r->phaser);
}

void hdr_interval_recorder_update(
    struct hdr_interval_recorder* r, 
    void(*update_action)(void*, void*), 
    void* arg)
{
    int64_t val = hdr_phaser_writer_enter(&r->phaser);

    void* active = mint_load_ptr_relaxed(&r->active);
    mint_thread_fence_acquire();

    update_action(active, arg);

    hdr_phaser_writer_exit(&r->phaser, val);
}

void* hdr_interval_recorder_sample(struct hdr_interval_recorder* r)
{
    void* temp;

    hdr_phaser_reader_lock(&r->phaser);

    temp = r->inactive;

    // volatile read
    r->inactive = mint_load_ptr_relaxed(&r->active);
    mint_thread_fence_acquire();

    // volatile write
    mint_thread_fence_release();
    mint_store_ptr_relaxed(&r->active, temp);
    mint_thread_fence_seq_cst();

    hdr_phaser_flip_phase(&r->phaser, 0);

    return r->inactive;
}

#endif