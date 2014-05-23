/**
 * HistogramPerfTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

import org.HdrHistogram.*;

import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * A simple example of using HdrHistogram: run for 20 seconds collecting the
 * time it takes to perform a simple Datagram Socket create/close operation,
 * and report a histogram of the times at the end.
 */

public class SimpleHistogramExample {
    // A Histogram covering the range from 1 nsec to 1 hour with 3 decimal point resolution:
    static Histogram histogram = new Histogram(3600000000000L, 3);

    static public volatile DatagramSocket socket;

    static long WARMUP_TIME_MSEC = 5000;
    static long RUN_TIME_MSEC = 20000;


    static void recordTimeToCreateAndCloseDatagramSocket() {
        long startTime = System.nanoTime();
        try {
            socket = new DatagramSocket();
        } catch (SocketException ex) {
        } finally {
            socket.close();
        }
        long endTime = System.nanoTime();
        histogram.recordValue(endTime - startTime);
    }

    public static void main(final String[] args) {
        long startTime = System.currentTimeMillis();
        long now;

        do {
            recordTimeToCreateAndCloseDatagramSocket();
            now = System.currentTimeMillis();
        } while (now - startTime < WARMUP_TIME_MSEC);

        histogram.reset();

        do {
            recordTimeToCreateAndCloseDatagramSocket();
            now = System.currentTimeMillis();
        } while (now - startTime < RUN_TIME_MSEC);

        System.out.println("Recorded latencies [in usec] for Create+Close of a DatagramSocket:");

        histogram.outputPercentileDistribution(System.out, 1000.0);
    }
}
