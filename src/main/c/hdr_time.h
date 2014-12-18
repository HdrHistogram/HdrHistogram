/**
 * hdr_time.h
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

#include <time.h>

#ifdef __APPLE__
#include <mach/clock.h>
#include <mach/mach.h>

void hdr_gettime(struct timespec* ts)
{
    clock_serv_t cclock;
    mach_timespec_t mts;
    host_get_clock_service(mach_host_self(), CALENDAR_CLOCK, &cclock);
    clock_get_time(cclock, &mts);
    mach_port_deallocate(mach_task_self(), cclock);
    ts->tv_sec = mts.tv_sec;
    ts->tv_nsec = mts.tv_nsec;
}

#elif __linux__

void hdr_gettime(struct timespec* t)
{
    clock_gettime(CLOCK_REALTIME, t);
}

#else

#warning "Platform not supported\n"

#endif
