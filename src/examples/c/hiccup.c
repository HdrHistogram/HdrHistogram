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
#include <hdr_writer_reader_phaser.h>
#include <hdr_time.h>

struct thread_data
{
	mint_atomicPtr_t active_histogram;
	struct hdr_histogram* inactive_histogram;

	struct hdr_writer_reader_phaser phaser;
};

int64_t diff(struct timespec t0, struct timespec t1)
{
	int64_t delta_us = 0;
	delta_us = (t1.tv_sec - t0.tv_sec) * 1000000L;
	delta_us += (t1.tv_nsec - t0.tv_nsec) / 1000L;

	return delta_us;
}

void* record_hiccups(void* thread_context)
{
	struct pollfd fd;
	struct thread_data* data = thread_context;
	struct timespec t0;
	struct timespec t1;
	struct hdr_histogram* h;
	struct itimerspec timeout; 

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

		int64_t val = hdr_phaser_writer_enter(&data->phaser);

		h = mint_load_ptr_relaxed(&data->active_histogram);
		mint_thread_fence_acquire();

		hdr_record_value(h, delta_us);

		hdr_phaser_writer_exit(&data->phaser, val);
	}

	pthread_exit(NULL);
}

struct hdr_histogram* sample(struct thread_data* data)
{
	struct hdr_histogram* temp;

	hdr_reset(data->inactive_histogram);

	hdr_phaser_reader_lock(&data->phaser);

	temp = data->inactive_histogram;
	data->inactive_histogram =
		mint_load_ptr_relaxed(&data->active_histogram);
	mint_thread_fence_acquire();

	mint_thread_fence_release();
	mint_store_ptr_relaxed(&data->active_histogram, temp);
	mint_thread_fence_acquire();
	mint_thread_fence_seq_cst();

	hdr_phaser_flip_phase(&data->phaser, 0);

	hdr_phaser_reader_unlock(&data->phaser);

	return data->inactive_histogram;
}

int main(int argc, char** argv)
{
	struct thread_data data;
	pthread_t recording_thread;

	hdr_init(
		1, 24L * 60 * 60 * 1000000, 3,
		(struct hdr_histogram**) &data.active_histogram._nonatomic);

	hdr_init(
		1, 24L * 60 * 60 * 1000000, 3, &data.inactive_histogram);

	if (0 != hdr_writer_reader_phaser_init(&data.phaser))
	{
		fprintf(stderr, "%s\n", "Failed to init phaser");
		return -1;
	}

	if (pthread_create(&recording_thread, NULL, record_hiccups, &data))
	{
		fprintf(stderr, "%s\n", "Failed to create thread");
		return -1;
	}

	while (true)
	{
		mint_sleep_millis(5000);

		struct hdr_histogram* h = sample(&data);
		hdr_percentiles_print(h, stdout, 5, 1.0, CLASSIC);
	}

	pthread_exit(NULL);
}