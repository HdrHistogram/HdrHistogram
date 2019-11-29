/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.HdrHistogram;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link StripedWriterReaderPhaser} is a striped version of {@link WriterReaderPhaser}
 * which reduces contention at the expense of higher space consumption
 * and slightly higher writer critical section enter/exit costs.
 *
 */
public class StripedWriterReaderPhaser extends WriterReaderPhaser {
    private static final int STRIDE_BYTES = 128;
    private static final int STRIDE = STRIDE_BYTES / 8;
    private static final int MAX_STRIPES = Integer.MAX_VALUE / STRIDE;

    private static final int DEFAULT_NUMBER_OF_STRIPES = 8;

    private final int stripes;
    private final int stripeMask;

    private final AtomicLongArray startEpoch;
    private final AtomicLongArray evenEndEpoch;
    private final AtomicLongArray oddEndEpoch;

    private final ReentrantLock readerLock = new ReentrantLock();

    /**
     * Creates a {@link StripedWriterReaderPhaser} with the default number of stripes.
     */
    public StripedWriterReaderPhaser() {
        this(DEFAULT_NUMBER_OF_STRIPES);
    }

    /**
     * Creates a {@link StripedWriterReaderPhaser} with the specified number of stripes.
     */
    public StripedWriterReaderPhaser(final int stripes) {
        if (stripes <= 0 || (stripes & (stripes - 1)) != 0) {
            throw new IllegalArgumentException("The number of stripes must be a power of 2, not " + stripes);
        }
        if (stripes > MAX_STRIPES) {
            throw new IllegalArgumentException("There must be at most " + MAX_STRIPES + " stripes, not " + stripes);
        }
        this.stripes = stripes;
        this.stripeMask = stripes - 1;
        final int paddedStripes = stripes * STRIDE;
        startEpoch = new AtomicLongArray(paddedStripes);
        evenEndEpoch = new AtomicLongArray(paddedStripes);
        oddEndEpoch = new AtomicLongArray(paddedStripes);
        for (int i = 0; i < stripes; i++) {
            oddEndEpoch.set(i * STRIDE, Long.MIN_VALUE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long writerCriticalSectionEnter() {
        return startEpoch.getAndIncrement(threadIndex());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writerCriticalSectionExit(long criticalValueAtEnter) {
        if (criticalValueAtEnter < 0) {
            oddEndEpoch.incrementAndGet(threadIndex());
        } else {
            evenEndEpoch.incrementAndGet(threadIndex());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readerLock() {
        readerLock.lock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readerUnlock() {
        readerLock.unlock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flipPhase(long yieldTimeNsec) {
        if (!readerLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("flipPhase() can only be called while holding the readerLock()");
        }

        boolean nextPhaseIsEven = (startEpoch.get(0) < 0); // Current phase is odd...

        long initialStartValue;
        // First, clear currently unused [next] phase end epoch (to proper initial value for phase):
        if (nextPhaseIsEven) {
            initialStartValue = 0;
            for (int i = 0; i < stripes; i++) {
                evenEndEpoch.lazySet(i * STRIDE, initialStartValue);
            }
        } else {
            initialStartValue = Long.MIN_VALUE;
            for (int i = 0; i < stripes; i++) {
                oddEndEpoch.lazySet(i * STRIDE, initialStartValue);
            }
        }

        // Next, reset start value, indicating new phase, and retain value at flip:
        final long[] startValueAtFlip = new long[stripes];
        for (int i = 0; i < stripes; i++) {
            startValueAtFlip[i] = startEpoch.getAndSet(i * STRIDE, initialStartValue);
        }

        // Now, spin until previous phase end value catches up with start value at flip:
        while (!caughtUp(startValueAtFlip, nextPhaseIsEven ? oddEndEpoch : evenEndEpoch)) {
            if (yieldTimeNsec == 0) {
                Thread.yield();
            } else {
                try {
                    TimeUnit.NANOSECONDS.sleep(yieldTimeNsec);
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    private boolean caughtUp(final long[] startValueAtFlip, final AtomicLongArray endEpoch) {
        for (int i = 0; i < stripes; i++) {
            if (endEpoch.get(i * STRIDE) < startValueAtFlip[i]) {
                return false;
            }
        }

        return true;
    }

    private int threadIndex() {
        final int index = (int) Thread.currentThread().getId();
        return STRIDE * (index & stripeMask);
    }

}
