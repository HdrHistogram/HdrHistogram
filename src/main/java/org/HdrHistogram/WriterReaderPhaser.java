/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.HdrHistogram;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <h2>A WriterReaderPhaser</h2>
 * <p>
 * {@link WriterReaderPhaser} provides an asymmetric means for
 * synchronizing the execution of wait-free "writer" critical sections against
 * a "reader phase flip" that needs to make sure no writer critical sections
 * that were active at the beginning of the flip are still active after the
 * flip is done.  Multiple writers and multiple readers are supported.
 * <p>
 * Using a {@link WriterReaderPhaser} for coordination, writers can continuously
 * perform wait-free/lock-free updates to common data structures, while readers
 * can get hold of atomic and inactive snapshots without stalling writers.
 * <p>
 * While a {@link WriterReaderPhaser} can be useful in multiple scenarios, a
 * specific and common use case is that of safely managing "double buffered"
 * data stream access in which writers can proceed without being blocked, while
 * readers gain access to stable and unchanging buffer samples.
 * {@link WriterReaderPhaser} "writers" are wait free (on architectures that support
 * wait free atomic increment operations), "readers" block for other
 * "readers", and "readers" are only blocked by "writers" whose critical section
 * was entered before the reader's
 * {@link WriterReaderPhaser#flipPhase(ScheduledExecutorService, long, Runnable)} attempt.
 * <h3>Assumptions and Guarantees</h3>
 * <p>
 * When used to protect an actively recording data structure, the assumptions on
 * how readers and writers act are:
 * <ol>
 * <li>There are two sets of data structures ("active" and "inactive")</li>
 * <li>Writing is done to the perceived active version (as perceived by the
 * writer), and only within critical sections delineated by
 * {@link WriterReaderPhaser#writerCriticalSectionEnter} and
 * {@link WriterReaderPhaser#writerCriticalSectionExit writerCriticalSectionExit()}.
 * </li>
 * <li>Only readers switch the perceived roles of the active and inactive data
 * structures. They do so only while under {@link WriterReaderPhaser#readerLock()}
 * protection and only before calling {@link WriterReaderPhaser#flipPhase(ScheduledExecutorService, long, Runnable)}.</li>
 * <li>Writers do not remain in their critical sections indefinitely.</li>
 * <li>Only writers perform {@link WriterReaderPhaser#writerCriticalSectionEnter}
 * and
 * {@link WriterReaderPhaser#writerCriticalSectionExit writerCriticalSectionExit()}.
 * </li>
 * <li>Readers do not hold onto readerLock indefinitely.</li>
 * <li>Only readers perform {@link WriterReaderPhaser#readerLock()} and
 * {@link WriterReaderPhaser#readerUnlock()}.</li>
 * <li>Only readers perform {@link WriterReaderPhaser#flipPhase(ScheduledExecutorService, long, Runnable)} operations,
 * and only while holding the readerLock.</li>
 * </ol>
 * <p>
 * When the above assumptions are met, {@link WriterReaderPhaser} guarantees
 * that the inactive data structures are not being modified by any writers while
 * being read while under readerLock() protection after a
 * {@link WriterReaderPhaser#flipPhase(ScheduledExecutorService, long, Runnable)}() operation.
 * <p>
 * The following progress guarantees are provided to writers and readers that
 * adhere to the above stated assumptions:
 * <ol>
 * <li>Writers operations
 * ({@link WriterReaderPhaser#writerCriticalSectionEnter writerCriticalSectionEnter}
 * and {@link WriterReaderPhaser#writerCriticalSectionExit writerCriticalSectionExit})
 * are wait free on architectures that
 * support wait-free atomic increment operations (they remain lock-free [but not
 * wait-free] on architectures that do not support wait-free atomic increment
 * operations)</li>
 * <li>{@link WriterReaderPhaser#flipPhase(ScheduledExecutorService, long, Runnable)} operations are guaranteed to
 * make forward progress, and will only be blocked by writers whose critical sections
 * were entered prior to the start of the reader's flipPhase operation, and have not
 * yet exited their critical sections.</li>
 * <li>{@link WriterReaderPhaser#readerLock()} only blocks for other
 * readers that are holding the readerLock.</li>
 * </ol>
 *
 * <h3>Example use</h3>
 * Imagine a simple use case where a histogram (which is basically a large set of
 * rapidly updated counters) is being modified by writers, and a reader needs to gain
 * access to stable interval samples of the histogram for reporting or other analysis
 * purposes.
 * <pre><code>
 * final WriterReaderPhaser recordingPhaser = new WriterReaderPhaser();
 *
 * volatile Histogram activeHistogram;
 * Histogram inactiveHistogram;
 * ...
 * </code></pre>
 * A writer may record values the histogram:
 * <pre><code>
 * // Wait-free recording:
 * long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
 * try {
 * activeHistogram.recordValue(value);
 * } finally {
 * recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
 * }
 * </code></pre>
 * A reader gains access to a stable histogram of values recorded during an interval,
 * and reports on it:
 * <pre><code>
 * try {
 * recordingPhaser.readerLock();
 *
 * inactiveHistogram.reset();
 *
 * // Swap active and inactive histograms:
 * final Histogram tempHistogram = inactiveHistogram;
 * inactiveHistogram = activeHistogram;
 * activeHistogram = tempHistogram;
 *
 * // For non-blocking environments (like event loops):
 * ScheduledExecutorService executor = ...;
 * recordingPhaser.flipPhase(executor, 1, () -> {
 * // At this point, inactiveHistogram content is guaranteed to be stable
 * logHistogram(inactiveHistogram);
 * });
 *
 * } finally {
 * recordingPhaser.readerUnlock();
 * }
 * </code></pre>
 */
/*
 * High level design: There are even and odd epochs; the epoch flips for each
 * reader.  Any number of writers can be in the same epoch (odd or even), but
 * after a completed phase flip no writers will be still in the old epoch
 * (and therefore are known to not be updating or observing the old, inactive
 * data structure). Writers can always proceed at full speed in what they
 * perceive to be the current (odd or even) epoch. The epoch flip is fast (a
 * single atomic op).
 */
public class WriterReaderPhaser {
    private volatile long startEpoch = 0;
    private volatile long evenEndEpoch = 0;
    private volatile long oddEndEpoch = Long.MIN_VALUE;

    private final ReentrantLock readerLock = new ReentrantLock();

    private static final AtomicLongFieldUpdater<WriterReaderPhaser> startEpochUpdater =
            AtomicLongFieldUpdater.newUpdater(WriterReaderPhaser.class, "startEpoch");
    private static final AtomicLongFieldUpdater<WriterReaderPhaser> evenEndEpochUpdater =
            AtomicLongFieldUpdater.newUpdater(WriterReaderPhaser.class, "evenEndEpoch");
    private static final AtomicLongFieldUpdater<WriterReaderPhaser> oddEndEpochUpdater =
            AtomicLongFieldUpdater.newUpdater(WriterReaderPhaser.class, "oddEndEpoch");

    /**
     * Indicate entry to a critical section containing a write operation.
     * <p>
     * This call is wait-free on architectures that support wait free atomic increment operations,
     * and is lock-free on architectures that do not.
     * <p>
     * {@code writerCriticalSectionEnter()} must be matched with a subsequent
     * {@link WriterReaderPhaser#writerCriticalSectionExit(long)} in order for CriticalSectionPhaser
     * synchronization to function properly.
     *
     * @return an (opaque) value associated with the critical section entry, which MUST be provided
     * to the matching {@link WriterReaderPhaser#writerCriticalSectionExit} call.
     */
    public long writerCriticalSectionEnter() {
        return startEpochUpdater.getAndIncrement(this);
    }

    /**
     * Indicate exit from a critical section containing a write operation.
     * <p>
     * This call is wait-free on architectures that support wait free atomic increment operations,
     * and is lock-free on architectures that do not.
     * <p>
     * {@code writerCriticalSectionExit(long)} must be matched with a preceding
     * {@link WriterReaderPhaser#writerCriticalSectionEnter()} call, and must be provided with the
     * matching {@link WriterReaderPhaser#writerCriticalSectionEnter()} call's return value, in
     * order for CriticalSectionPhaser synchronization to function properly.
     *
     * @param criticalValueAtEnter the (opaque) value returned from the matching
     * {@link WriterReaderPhaser#writerCriticalSectionEnter()} call.
     */
    public void writerCriticalSectionExit(long criticalValueAtEnter) {
        (criticalValueAtEnter < 0 ? oddEndEpochUpdater : evenEndEpochUpdater).getAndIncrement(this);
    }

    /**
     * Enter to a critical section containing a read operation (reentrant, mutually excludes against
     * {@link WriterReaderPhaser#readerLock} calls by other threads).
     * <p>
     * {@link WriterReaderPhaser#readerLock} DOES NOT provide synchronization
     * against {@link WriterReaderPhaser#writerCriticalSectionEnter()} calls. Use {@link WriterReaderPhaser#flipPhase(ScheduledExecutorService, long, Runnable)}
     * to synchronize reads against writers.
     */
    public void readerLock() {
        readerLock.lock();
    }

    /**
     * Exit from a critical section containing a read operation (relinquishes mutual exclusion against other
     * {@link WriterReaderPhaser#readerLock} calls).
     */
    public void readerUnlock() {
        readerLock.unlock();
    }

    /**
     * Asynchronously flips a phase in the {@link WriterReaderPhaser} instance. This method can only be
     * called while holding the {@link WriterReaderPhaser#readerLock() readerLock}.
     * <p>
     * Instead of blocking, this method schedules a polling task on the provided
     * {@link ScheduledExecutorService}. The task checks for the completion of all writer critical
     * sections that were active when the flip was initiated. Once completed, the {@code onComplete}
     * callback is executed.
     * <p>
     * This non-blocking approach is suitable for use in event loops or other contexts where
     * thread blocking is discouraged.
     *
     * @param executor The executor service to use for scheduling the completion check.
     * @param pollIntervalNsec The interval (in nanoseconds) at which to check for completion.
     * @param onComplete The {@link Runnable} to execute once the phase flip is complete.
     */
    public void flipPhase(
            final ScheduledExecutorService executor,
            final long pollIntervalNsec,
            final Runnable onComplete) {

        if (!readerLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("flipPhase() can only be called while holding the readerLock()");
        }

        boolean nextPhaseIsEven = (startEpoch < 0);
        long initialStartValue = nextPhaseIsEven ? 0 : Long.MIN_VALUE;
        (nextPhaseIsEven ? evenEndEpochUpdater : oddEndEpochUpdater).lazySet(this, initialStartValue);
        long startValueAtFlip = startEpochUpdater.getAndSet(this, initialStartValue);

        checkPhaseFlipCompletion(executor, pollIntervalNsec, onComplete, nextPhaseIsEven, startValueAtFlip);
    }

    private void checkPhaseFlipCompletion(
            final ScheduledExecutorService executor,
            final long pollIntervalNsec,
            final Runnable onComplete,
            final boolean nextPhaseIsEven,
            final long startValueAtFlip) {

        if ((nextPhaseIsEven ? oddEndEpoch : evenEndEpoch) == startValueAtFlip) {
            onComplete.run();
        } else {
            long delay = (pollIntervalNsec == 0) ? 1 : pollIntervalNsec;
            executor.schedule(
                () -> checkPhaseFlipCompletion(executor, pollIntervalNsec, onComplete, nextPhaseIsEven, startValueAtFlip),
                delay,
                TimeUnit.NANOSECONDS
            );
        }
    }

    /**
     * Flips a phase, blocking the calling thread until all writers active at the start of the flip
     * have completed their critical sections.
     * <p>
     * This method can block the calling thread for an indeterminate amount of time and may cause
     * performance issues if used on a non-blocking thread (e.g., an event loop).
     *
     * @deprecated This method is deprecated because it employs a blocking spin-wait loop.
     * Use the non-blocking {@link #flipPhase(ScheduledExecutorService, long, Runnable)} instead,
     * especially in asynchronous or event-driven environments.
     *
     * @param yieldTimeNsec The amount of time (in nanoseconds) to sleep in each yield if a spin loop is needed.
     */
    @Deprecated
    public void flipPhase(long yieldTimeNsec) {
        if (!readerLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("flipPhase() can only be called while holding the readerLock()");
        }

        boolean nextPhaseIsEven = (startEpoch < 0);
        long initialStartValue = nextPhaseIsEven ? 0 : Long.MIN_VALUE;
        (nextPhaseIsEven ? evenEndEpochUpdater : oddEndEpochUpdater).lazySet(this, initialStartValue);
        long startValueAtFlip = startEpochUpdater.getAndSet(this, initialStartValue);

        while ((nextPhaseIsEven ? oddEndEpoch : evenEndEpoch) != startValueAtFlip) {
            if (yieldTimeNsec == 0) {
                Thread.yield();
            } else {
                try {
                    TimeUnit.NANOSECONDS.sleep(yieldTimeNsec);
                } catch (InterruptedException ex) {
                    // Reset the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Flips a phase, blocking the calling thread until writers complete. This is a convenience
     * method that calls {@link #flipPhase(long)} with a yield time of 0.
     *
     * @deprecated This method is deprecated because it employs a blocking spin-wait loop.
     * Use the non-blocking {@link #flipPhase(ScheduledExecutorService, long, Runnable)} instead.
     */
    @Deprecated
    public void flipPhase() {
        flipPhase(0);
    }
}
