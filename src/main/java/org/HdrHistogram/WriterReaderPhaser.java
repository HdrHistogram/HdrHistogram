/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.HdrHistogram;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link WriterReaderPhaser} instances provide an asymmetric means for synchronizing the execution of
 * wait-free "writer" critical sections against a "reader phase flip" that needs to make sure no writer critical
 * sections that were active at the beginning of the flip are still active after the flip is done. Multiple writers
 * and multiple readers are supported.
 * <p>
 * While a {@link WriterReaderPhaser} can be useful in multiple scenarios, a specific and common use case is
 * that of safely managing "double buffered" data stream access in which writers can proceed without being
 * blocked, while readers gain access to stable and unchanging buffer samples
 * <blockquote>
 * NOTE: {@link WriterReaderPhaser} writers are wait-free on architectures that support wait-free atomic
 * increment operations. They remain lock-free (but not wait-free) on architectures that do not support
 * wait-free atomic increment operations.
 * </blockquote>
 * {@link WriterReaderPhaser} "writers" are wait free, "readers" block for other "readers", and
 * "readers" are only blocked by "writers" whose critical section was entered before the reader's
 * {@link WriterReaderPhaser#flipPhase()} attempt.
 * <p>
 * When used to protect an actively recording data structure, the assumptions on how readers and writers act are:
 * <ol>
 * <li>There are two sets of data structures ("active" and "inactive")</li>
 * <li>Writing is done to the perceived active version (as perceived by the writer), and only
 *     within critical sections delineated by {@link WriterReaderPhaser#writerCriticalSectionEnter}
 *     and {@link WriterReaderPhaser#writerCriticalSectionExit}).</li>
 * <li>Only readers switch the perceived roles of the active and inactive data structures.
 *     They do so only while under readerLock(), and only before calling flipPhase().</li>
 * </ol>
 * When the above assumptions are met, {@link WriterReaderPhaser} guarantees that the inactive data structures are not
 * being modified by any writers while being read while under readerLock() protection after a flipPhase()
 * operation.
 *
 *
 *
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
     * {@link WriterReaderPhaser#writerCriticalSectionEnter()} must be matched with a subsequent
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
     * {@link WriterReaderPhaser#writerCriticalSectionExit(long)} must be matched with a preceding
     * {@link WriterReaderPhaser#writerCriticalSectionEnter()} call, and must be provided with the
     * matching {@link WriterReaderPhaser#writerCriticalSectionEnter()} call's return value, in
     * order for CriticalSectionPhaser synchronization to function properly.
     *
     * @param criticalValueAtEnter the (opaque) value returned from the matching
     * {@link WriterReaderPhaser#writerCriticalSectionEnter()} call.
     */
    public void writerCriticalSectionExit(long criticalValueAtEnter) {
        if (criticalValueAtEnter < 0) {
            oddEndEpochUpdater.getAndIncrement(this);
        } else {
            evenEndEpochUpdater.getAndIncrement(this);
        }
    }

    /**
     * Enter to a critical section containing a read operation (mutually excludes against other
     * {@link WriterReaderPhaser#readerLock} calls).
     * <p>
     * {@link WriterReaderPhaser#readerLock} DOES NOT provide synchronization
     * against {@link WriterReaderPhaser#writerCriticalSectionEnter()} calls. Use {@link WriterReaderPhaser#flipPhase()}
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
     * Flip a phase in the {@link WriterReaderPhaser} instance, {@link WriterReaderPhaser#flipPhase()}
     * can only be called while holding the readerLock().
     * {@link WriterReaderPhaser#flipPhase()} will return only after all writer critical sections (protected by
     * {@link WriterReaderPhaser#writerCriticalSectionEnter()} ()} and
     * {@link WriterReaderPhaser#writerCriticalSectionExit(long)} ()}) that may have been in flight when the
     * {@link WriterReaderPhaser#flipPhase()} call were made had completed.
     * <p>
     * No actual writer critical section activity is required for {@link WriterReaderPhaser#flipPhase()} to
     * succeed.
     * <p>
     * However, {@link WriterReaderPhaser#flipPhase()} is lock-free with respect to calls to
     * {@link WriterReaderPhaser#writerCriticalSectionEnter()} and
     * {@link WriterReaderPhaser#writerCriticalSectionExit(long)}. It may spin-wait for for active
     * writer critical section code to complete.
     *
     * @param yieldTimeNsec The amount of time (in nanoseconds) to sleep in each yield if yield loop is needed.
     */
    public void flipPhase(long yieldTimeNsec) {
        if (!readerLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("flipPhase() can only be called while holding the readerLock()");
        }

        boolean nextPhaseIsEven = (startEpoch < 0); // Current phase is odd...

        long initialStartValue;
        // First, clear currently unused [next] phase end epoch (to proper initial value for phase):
        if (nextPhaseIsEven) {
            initialStartValue = 0;
            evenEndEpochUpdater.lazySet(this, initialStartValue);
        } else {
            initialStartValue = Long.MIN_VALUE;
            oddEndEpochUpdater.lazySet(this, initialStartValue);
        }

        // Next, reset start value, indicating new phase, and retain value at flip:
        long startValueAtFlip = startEpochUpdater.getAndSet(this, initialStartValue);

        // Now, spin until previous phase end value catches up with start value at flip:
        boolean caughtUp = false;
        do {
            if (nextPhaseIsEven) {
                caughtUp = (oddEndEpoch == startValueAtFlip);
            } else {
                caughtUp = (evenEndEpoch == startValueAtFlip);
            }
            if (!caughtUp) {
                if (yieldTimeNsec == 0) {
                    Thread.yield();
                } else {
                    try {
                        TimeUnit.NANOSECONDS.sleep(yieldTimeNsec);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        } while (!caughtUp);
    }


    /**
     * Flip a phase in the {@link WriterReaderPhaser} instance, {@link WriterReaderPhaser#flipPhase()}
     * can only be called while holding the readerLock().
     * {@link WriterReaderPhaser#flipPhase()} will return only after all writer critical sections (protected by
     * {@link WriterReaderPhaser#writerCriticalSectionEnter()} ()} and
     * {@link WriterReaderPhaser#writerCriticalSectionExit(long)} ()}) that may have been in flight when the
     * {@link WriterReaderPhaser#flipPhase()} call were made had completed.
     * <p>
     * No actual writer critical section activity is required for {@link WriterReaderPhaser#flipPhase()} to
     * succeed.
     * <p>
     * However, {@link WriterReaderPhaser#flipPhase()} is lock-free with respect to calls to
     * {@link WriterReaderPhaser#writerCriticalSectionEnter()} and
     * {@link WriterReaderPhaser#writerCriticalSectionExit(long)}. It may spin-wait for for active
     * writer critical section code to complete.
     */
    public void flipPhase() {
        flipPhase(0);
    }
}
