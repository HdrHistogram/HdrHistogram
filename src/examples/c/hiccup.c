/**
 * hiccup.c
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#define _GNU_SOURCE
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <pthread.h>
#include <sys/timerfd.h>
#include <poll.h>
#include <string.h>
#include <signal.h>

#include <hdr_histogram.h>
#include <hdr_interval_recorder.h>
#include <hdr_time.h>

int64_t diff(struct timespec t0, struct timespec t1)
{
    int64_t delta_us = 0;
    delta_us = (t1.tv_sec - t0.tv_sec) * 1000000L;
    delta_us += (t1.tv_nsec - t0.tv_nsec) / 1000L;

    return delta_us;
}

void update_histogram(void* data, void* arg)
{
    struct hdr_histogram* h = data;
    int64_t* values = arg;

    hdr_record_value(h, values[0]);
}

void* record_hiccups(void* thread_context)
{
    struct pollfd fd;
    struct timespec t0;
    struct timespec t1;
    struct itimerspec timeout; 
    struct hdr_interval_recorder* r = thread_context;

    memset(&fd, 0, sizeof(struct pollfd));
    memset(&timeout, 0, sizeof(struct itimerspec));
    memset(&t0, 0, sizeof(struct timespec));
    memset(&t1, 0, sizeof(struct timespec));

    fd.fd = timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK | TFD_CLOEXEC);
    fd.events = POLLIN|POLLPRI|POLLRDHUP;
    fd.revents = 0;

    while (true)
    {
        timeout.it_value.tv_sec = 0;
          timeout.it_value.tv_nsec = 1000000;
        timerfd_settime(fd.fd, 0, &timeout, NULL);

        hdr_gettime(&t0);
        poll(&fd, 1, -1);
        hdr_gettime(&t1);

        int64_t delta_us = diff(t0, t1) - 1000;
        delta_us = delta_us < 0 ? 0 : delta_us;

        hdr_interval_recorder_update(r, update_histogram, &delta_us);
    }

    pthread_exit(NULL);
}

int main(int argc, char** argv)
{
    struct hdr_interval_recorder recorder;
    pthread_t recording_thread;

    hdr_init(
        1, 24L * 60 * 60 * 1000000, 3,
        (struct hdr_histogram**) &recorder.active._nonatomic);

    hdr_init(
        1, 24L * 60 * 60 * 1000000, 3, 
        (struct hdr_histogram**) &recorder.inactive);

    if (0 != hdr_interval_recorder_init(&recorder))
    {
        fprintf(stderr, "%s\n", "Failed to init phaser");
        return -1;
    }

    if (pthread_create(&recording_thread, NULL, record_hiccups, &recorder))
    {
        fprintf(stderr, "%s\n", "Failed to create thread");
        return -1;
    }

    while (true)
    {
        mint_sleep_millis(5000);

        hdr_reset(recorder.inactive);
        struct hdr_histogram* h = hdr_interval_recorder_sample(&recorder);

        hdr_percentiles_print(h, stdout, 5, 1.0, CLASSIC);
    }

    pthread_exit(NULL);
}