/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.HdrHistogram;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * CriticalSectionPhaser provides an asymmetric means for synchronizing wait-free critical section
 * execution against a waiting (but non-blocking) "phase flip" that needs to make sure no critical
 * sections that were active at the beginning of the flip are still active after the flip is done.
 *
 */
public class CriticalSectionPhaser {
    private volatile long startEpoch = 0;
    private volatile long evenEndEpoch = 0;
    private volatile long oddEndEpoch = 1;

    private static final AtomicLongFieldUpdater<CriticalSectionPhaser> startEpochUpdater =
            AtomicLongFieldUpdater.newUpdater(CriticalSectionPhaser.class, "startEpoch");
    private static final AtomicLongFieldUpdater<CriticalSectionPhaser> evenEndEpochUpdater =
            AtomicLongFieldUpdater.newUpdater(CriticalSectionPhaser.class, "evenEndEpoch");
    private static final AtomicLongFieldUpdater<CriticalSectionPhaser> oddEndEpochUpdater =
            AtomicLongFieldUpdater.newUpdater(CriticalSectionPhaser.class, "oddEndEpoch");

    /**
     * Indicate entry to a critical section.
     * <p>
     * This call is wait-free on architectures that support wait free atomic add operations,
     * and is non-blocking on architectures that only support atomic CAS or SWAP operations.
     * <p>
     * {@link CriticalSectionPhaser#enteringCriticalSection()} must be matched with a subsequent
     * {@link CriticalSectionPhaser#exitingCriticalSection} in order for CriticalSectionPhaser
     * synchronization to function properly.
     *
     * @return an (opaque) value associated with the critical section entry, which MUST be provided to the matching
     * {@link CriticalSectionPhaser#exitingCriticalSection} call.
     */
    public long enteringCriticalSection() {
        return startEpochUpdater.getAndAdd(this, 2);
    }

    /**
     * Indicate exit from a critical section.
     * <p>
     * This call is wait-free on architectures that support wait free atomic add operations,
     * and is non-blocking on architectures that only support atomic CAS or SWAP operations.
     * <p>
     * {@link CriticalSectionPhaser#exitingCriticalSection} must be matched with a preceding
     * {@link CriticalSectionPhaser#enteringCriticalSection()} call, and must be provided with the
     * matching {@link CriticalSectionPhaser#enteringCriticalSection()} call's return value, in
     * order for CriticalSectionPhaser synchronization to function properly.
     *
     * @param criticalValueAtEnter the (opaque) value returned from the matching
     * {@link CriticalSectionPhaser#enteringCriticalSection()} call.
     */
    public void exitingCriticalSection(long criticalValueAtEnter) {
        if ((criticalValueAtEnter & 1) == 0) {
            evenEndEpochUpdater.getAndAdd(this, 2);
        } else {
            oddEndEpochUpdater.getAndAdd(this, 2);
        }
    }

    /**
     * Flip a phase in the CriticalSectionPhaser instance. {@link CriticalSectionPhaser#flipPhase()} will
     * return only after all critical sections that may have been in flight when the
     * {@link CriticalSectionPhaser#flipPhase()} call were made had completed.
     * <p>
     * No actual critical section activity is required for {@link CriticalSectionPhaser#flipPhase()} to
     * succeed.
     * <p>
     * {@link CriticalSectionPhaser#flipPhase()} is synchronized, to prevent attempts at concurrent
     * flipping across multiple callers. It is therefore blocking with respect to other calls to
     * {@link CriticalSectionPhaser#flipPhase()}.
     * <p>
     * However, {@link CriticalSectionPhaser#flipPhase()} is non-blocking with respect to calls to
     * {@link CriticalSectionPhaser#enteringCriticalSection()} and
     * {@link CriticalSectionPhaser#exitingCriticalSection}. It may spin-wait for for active
     * critical section code to complete. Therefore, {@link CriticalSectionPhaser#flipPhase()} will remain
     * non-blocking as long as all related critical sections protected by
     * {@link CriticalSectionPhaser#enteringCriticalSection()} and
     * {@link CriticalSectionPhaser#exitingCriticalSection} remain wait-free.
     */
    public synchronized void flipPhase() {
        boolean nextPhaseIsOdd = ((startEpoch & 1) == 0);

        long initialStartValue;
        // First, clear currently unused [next] phase end epoch (to proper initial value for phase):
        if (nextPhaseIsOdd) {
            oddEndEpoch = initialStartValue = 1;
        } else {
            evenEndEpoch = initialStartValue = 0;
        }

        // Next, reset start value, indicating new phase, and retain value at flip:
        long startValueAtFlip = startEpochUpdater.getAndSet(this, initialStartValue);

        // Now, spin until previous phase end value catches up with start value at flip:
        boolean caughtUp = false;
        do {
            if (nextPhaseIsOdd) {
                caughtUp = (evenEndEpoch == startValueAtFlip);
            } else {
                caughtUp = (oddEndEpoch == startValueAtFlip);
            }
        } while (!caughtUp);
    }
}
