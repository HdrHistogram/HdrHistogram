/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.HdrHistogram;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link StripedWriterReaderPhaser} instances provide an asymmetric means for synchronizing the execution of
 * wait-free "writer" critical sections against a "reader phase flip" that needs to make sure no writer critical
 * sections that were active at the beginning of the flip are still active after the flip is done. Multiple writers
 * and multiple readers are supported.
 * <p>
 * While a {@link StripedWriterReaderPhaser} can be useful in multiple scenarios, a specific and common use case is
 * that of safely managing "double buffered" data stream access in which writers can proceed without being
 * blocked, while readers gain access to stable and unchanging buffer samples
 * <blockquote>
 * NOTE: {@link StripedWriterReaderPhaser} writers are wait-free on architectures that support wait-free atomic
 * increment operations. They remain lock-free (but not wait-free) on architectures that do not support
 * wait-free atomic increment operations.
 * </blockquote>
 * {@link StripedWriterReaderPhaser} "writers" are wait free, "readers" block for other "readers", and
 * "readers" are only blocked by "writers" whose critical section was entered before the reader's
 * {@link StripedWriterReaderPhaser#flipPhase()} attempt.
 * <p>
 * When used to protect an actively recording data structure, the assumptions on how readers and writers act are:
 * <ol>
 * <li>There are two sets of data structures ("active" and "inactive")</li>
 * <li>Writing is done to the perceived active version (as perceived by the writer), and only
 *     within critical sections delineated by {@link StripedWriterReaderPhaser#writerCriticalSectionEnter}
 *     and {@link StripedWriterReaderPhaser#writerCriticalSectionExit}).</li>
 * <li>Only readers switch the perceived roles of the active and inactive data structures.
 *     They do so only while under readerLock(), and only before calling flipPhase().</li>
 * </ol>
 * When the above assumptions are met, {@link StripedWriterReaderPhaser} guarantees that the inactive data structures are not
 * being modified by any writers while being read while under readerLock() protection after a flipPhase()
 * operation.
 *
 *
 *
 */
public class StripedWriterReaderPhaser {
    private final int stripes;

    // TODO: prevent false sharing
    private final AtomicLongArray startEpoch;
    private final AtomicLongArray evenEndEpoch;
    private final AtomicLongArray oddEndEpoch;

    private final ReentrantLock readerLock = new ReentrantLock();

    public StripedWriterReaderPhaser(final int stripes) {
        if (stripes <= 0 || (stripes & (stripes - 1)) != 0) {
            throw new IllegalArgumentException("Stripes must be a power of 2, not " + stripes);
        }
        this.stripes = stripes;
        startEpoch = new AtomicLongArray(stripes);
        evenEndEpoch = new AtomicLongArray(stripes);
        oddEndEpoch = new AtomicLongArray(stripes);
        for (int i=0; i<stripes; i++) {
            oddEndEpoch.set(i, Long.MIN_VALUE);
        }
    }

    /**
     * Indicate entry to a critical section containing a write operation.
     * <p>
     * This call is wait-free on architectures that support wait free atomic increment operations,
     * and is lock-free on architectures that do not.
     * <p>
     * {@link StripedWriterReaderPhaser#writerCriticalSectionEnter()} must be matched with a subsequent
     * {@link StripedWriterReaderPhaser#writerCriticalSectionExit(long)} in order for CriticalSectionPhaser
     * synchronization to function properly.
     *
     * @return an (opaque) value associated with the critical section entry, which MUST be provided
     * to the matching {@link StripedWriterReaderPhaser#writerCriticalSectionExit} call.
     */
    public long writerCriticalSectionEnter() {
        return startEpoch.getAndIncrement(threadIndex());
    }

    /**
     * Indicate exit from a critical section containing a write operation.
     * <p>
     * This call is wait-free on architectures that support wait free atomic increment operations,
     * and is lock-free on architectures that do not.
     * <p>
     * {@link StripedWriterReaderPhaser#writerCriticalSectionExit(long)} must be matched with a preceding
     * {@link StripedWriterReaderPhaser#writerCriticalSectionEnter()} call, and must be provided with the
     * matching {@link StripedWriterReaderPhaser#writerCriticalSectionEnter()} call's return value, in
     * order for CriticalSectionPhaser synchronization to function properly.
     *
     * @param criticalValueAtEnter the (opaque) value returned from the matching
     * {@link StripedWriterReaderPhaser#writerCriticalSectionEnter()} call.
     */
    public void writerCriticalSectionExit(long criticalValueAtEnter) {
        if (criticalValueAtEnter < 0) {
            oddEndEpoch.incrementAndGet(threadIndex());
        } else {
            evenEndEpoch.incrementAndGet(threadIndex());
        }
    }

    /**
     * Enter to a critical section containing a read operation (mutually excludes against other
     * {@link StripedWriterReaderPhaser#readerLock} calls).
     * <p>
     * {@link StripedWriterReaderPhaser#readerLock} DOES NOT provide synchronization
     * against {@link StripedWriterReaderPhaser#writerCriticalSectionEnter()} calls. Use {@link StripedWriterReaderPhaser#flipPhase()}
     * to synchronize reads against writers.
     */
    public void readerLock() {
        readerLock.lock();
    }

    /**
     * Exit from a critical section containing a read operation (relinquishes mutual exclusion against other
     * {@link StripedWriterReaderPhaser#readerLock} calls).
     */
    public void readerUnlock() {
        readerLock.unlock();
    }

    /**
     * Flip a phase in the {@link StripedWriterReaderPhaser} instance, {@link StripedWriterReaderPhaser#flipPhase()}
     * can only be called while holding the readerLock().
     * {@link StripedWriterReaderPhaser#flipPhase()} will return only after all writer critical sections (protected by
     * {@link StripedWriterReaderPhaser#writerCriticalSectionEnter()} ()} and
     * {@link StripedWriterReaderPhaser#writerCriticalSectionExit(long)} ()}) that may have been in flight when the
     * {@link StripedWriterReaderPhaser#flipPhase()} call were made had completed.
     * <p>
     * No actual writer critical section activity is required for {@link StripedWriterReaderPhaser#flipPhase()} to
     * succeed.
     * <p>
     * However, {@link StripedWriterReaderPhaser#flipPhase()} is lock-free with respect to calls to
     * {@link StripedWriterReaderPhaser#writerCriticalSectionEnter()} and
     * {@link StripedWriterReaderPhaser#writerCriticalSectionExit(long)}. It may spin-wait for for active
     * writer critical section code to complete.
     *
     * @param yieldTimeNsec The amount of time (in nanoseconds) to sleep in each yield if yield loop is needed.
     */
    public void flipPhase(long yieldTimeNsec) {
        if (!readerLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("flipPhase() can only be called while holding the readerLock()");
        }

        boolean nextPhaseIsEven = (startEpoch.get(0) < 0); // Current phase is odd...

        long initialStartValue;
        // First, clear currently unused [next] phase end epoch (to proper initial value for phase):
        if (nextPhaseIsEven) {
            initialStartValue = 0;
            for (int i=0; i<stripes; i++) {
                evenEndEpoch.lazySet(i, initialStartValue);
            }
        } else {
            initialStartValue = Long.MIN_VALUE;
            for (int i=0; i<stripes; i++) {
                oddEndEpoch.lazySet(i, initialStartValue);
            }
        }

        // Next, reset start value, indicating new phase, and retain value at flip:
        final long[] startValueAtFlip = new long[stripes];
        for (int i=0; i<stripes; i++) {
            startValueAtFlip[i] = startEpoch.getAndSet(i, initialStartValue);
        }

        // Now, spin until previous phase end value catches up with start value at flip:
        for (;;) {
            if (!caughtUp(startValueAtFlip, nextPhaseIsEven ? oddEndEpoch : evenEndEpoch)) {
                if (yieldTimeNsec == 0) {
                    Thread.yield();
                } else {
                    try {
                        TimeUnit.NANOSECONDS.sleep(yieldTimeNsec);
                    } catch (InterruptedException ex) {
                    }
                }
            } else {
                break;
            }
        }
    }

    private boolean caughtUp(final long[] startValueAtFlip, final AtomicLongArray endEpoch) {
        for (int i=0; i<stripes; i++) {
            if (endEpoch.get(i) < startValueAtFlip[i]) {
                return false;
            }
        }

        return true;
    }

    private int threadIndex() {
        final int index = (int) Thread.currentThread().getId();
        return index & (stripes - 1);
    }

    /**
     * Flip a phase in the {@link StripedWriterReaderPhaser} instance, {@link StripedWriterReaderPhaser#flipPhase()}
     * can only be called while holding the readerLock().
     * {@link StripedWriterReaderPhaser#flipPhase()} will return only after all writer critical sections (protected by
     * {@link StripedWriterReaderPhaser#writerCriticalSectionEnter()} ()} and
     * {@link StripedWriterReaderPhaser#writerCriticalSectionExit(long)} ()}) that may have been in flight when the
     * {@link StripedWriterReaderPhaser#flipPhase()} call were made had completed.
     * <p>
     * No actual writer critical section activity is required for {@link StripedWriterReaderPhaser#flipPhase()} to
     * succeed.
     * <p>
     * However, {@link StripedWriterReaderPhaser#flipPhase()} is lock-free with respect to calls to
     * {@link StripedWriterReaderPhaser#writerCriticalSectionEnter()} and
     * {@link StripedWriterReaderPhaser#writerCriticalSectionExit(long)}. It may spin-wait for for active
     * writer critical section code to complete.
     */
    public void flipPhase() {
        flipPhase(0);
    }
}
