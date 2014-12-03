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
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <hdr_histogram.h>
#include <hdr_histogram_log.h>
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

struct config_t
{
    int interval;
    const char* filename;
};

const char* USAGE =
"hiccup [-i <interval>] [-f <filename>]\n"
"  interval: <number> Time in seconds between samples (default 1).\n"
"  filename: <string> Name of the file to log to (default stdout).\n";

int handle_opts(int argc, char** argv, struct config_t* config)
{
    int c;
    int interval = 1;

    while ((c = getopt(argc, argv, "i:f:")) != -1)
    {
        switch (c)
        {
        case 'h':
            return 0;

        case 'i':
            interval = atoi(optarg);
            if (interval < 1)
            {
                return 0;
            }

            break;
        case 'f':
            config->filename = optarg;
            break;
        default:
            return 0;
        }
    }

    config->interval = interval < 1 ? 1 : interval;
    return 1;
}

int main(int argc, char** argv)
{
    struct timespec timestamp;
    struct timespec start_timestamp;
    struct timespec end_timestamp;
    struct hdr_interval_recorder recorder;
    struct hdr_log_writer log_writer;
    struct config_t config;
    pthread_t recording_thread;
    FILE* output = stdout;

    memset(&config, 0, sizeof(struct config_t));
    if (!handle_opts(argc, argv, &config))
    {
        printf("%s", USAGE);
        return 0;
    }

    if (config.filename)
    {
        output = fopen(config.filename, "a+");
        if (!output)
        {
            fprintf(
                stderr, "Failed to open/create file: %s, %s", 
                config.filename, strerror(errno));

            return -1;
        }
    }

    if (0 != hdr_interval_recorder_init(&recorder))
    {
        fprintf(stderr, "%s\n", "Failed to init phaser");
        return -1;
    }

    if (0 != hdr_init(
        1, 24L * 60 * 60 * 1000000, 3,
        (struct hdr_histogram**) &recorder.active))
    {
        fprintf(stderr, "%s\n", "Failed to init hdr_histogram");
        return -1;
    }

    if (0 != hdr_init(
        1, 24L * 60 * 60 * 1000000, 3, 
        (struct hdr_histogram**) &recorder.inactive))
    {
        fprintf(stderr, "%s\n", "Failed to init hdr_histogram");
        return -1;
    }

    if (pthread_create(&recording_thread, NULL, record_hiccups, &recorder))
    {
        fprintf(stderr, "%s\n", "Failed to create thread");
        return -1;
    }

    hdr_gettime(&start_timestamp);
    hdr_log_writer_init(&log_writer);
    hdr_log_write_header(&log_writer, output, "foobar", &timestamp);

    while (true)
    {        
        sleep(config.interval);

        hdr_reset(recorder.inactive);
        struct hdr_histogram* h = hdr_interval_recorder_sample(&recorder);

        hdr_gettime(&end_timestamp);
        timestamp = start_timestamp;

        hdr_gettime(&start_timestamp);

        hdr_log_write(&log_writer, output, &timestamp, &end_timestamp, h);
        fflush(output);
    }

    pthread_exit(NULL);
}