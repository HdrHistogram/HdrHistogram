/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package bench;

import org.HdrHistogram.*;
import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.Recorder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.reflect.Constructor;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/*
  Run all benchmarks:
    $ java -jar target/benchmarks.jar

  Run selected benchmarks:
    $ java -jar target/benchmarks.jar (regexp)

  Run the profiling (Linux only):
     $ java -Djmh.perfasm.events=cycles,cache-misses -jar target/benchmarks.jar -f 1 -prof perfasm
 */
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(5)
@State(Scope.Thread)

public class HdrHistogramRecordingBench {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units
    static final int numberOfSignificantValueDigits = 3;
    static final long testValueLevel = 12340;

    AbstractHistogram histogram;
    AbstractHistogram synchronizedHistogram;
    AbstractHistogram atomicHistogram;
    AbstractHistogram concurrentHistogram;
    Recorder recorder;
    SingleWriterRecorder singleWriterRecorder;
    DoubleHistogram doubleHistogram;
    DoubleRecorder doubleRecorder;
    SingleWriterDoubleRecorder singleWriterDoubleRecorder;

    int i;

    @Setup
    public void setup() throws NoSuchMethodException {
        histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        synchronizedHistogram = new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        atomicHistogram = new AtomicHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        concurrentHistogram = new ConcurrentHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        recorder = new Recorder(highestTrackableValue, numberOfSignificantValueDigits);
        singleWriterRecorder = new SingleWriterRecorder(highestTrackableValue, numberOfSignificantValueDigits);
        doubleHistogram = new DoubleHistogram(highestTrackableValue, numberOfSignificantValueDigits);
        doubleRecorder = new DoubleRecorder(highestTrackableValue, numberOfSignificantValueDigits);
        singleWriterDoubleRecorder = new SingleWriterDoubleRecorder(highestTrackableValue, numberOfSignificantValueDigits);
    }

    @Benchmark
    public void rawRecordingSpeed() {
        histogram.recordValue(testValueLevel + (i++ & 0x800));
    }

    @Benchmark
    public void rawSynchroniedRecordingSpeed() {
        synchronizedHistogram.recordValue(testValueLevel + (i++ & 0x800));

    }

    @Benchmark
    public void rawAtomicRecordingSpeed() {
        atomicHistogram.recordValue(testValueLevel + (i++ & 0x800));
    }

    @Benchmark
    public void rawConcurrentRecordingSpeed() {
        concurrentHistogram.recordValue(testValueLevel + (i++ & 0x800));
    }


    @Benchmark
    public void singleWriterRecorderRecordingSpeed() {
        singleWriterRecorder.recordValue(testValueLevel + (i++ & 0x800));;
    }

    @Benchmark
    public void recorderRecordingSpeed() {
        recorder.recordValue(testValueLevel + (i++ & 0x800));;

    }

    @Benchmark
    public void rawDoubleRecordingSpeed() {
        doubleHistogram.recordValue(testValueLevel + (i++ & 0x800));;

    }

    @Benchmark
    public void doubleRecorderRecordingSpeed() {
        doubleRecorder.recordValue(testValueLevel + (i++ & 0x800));;
    }

    @Benchmark
    public void singleWriterDoubleRecorderRecordingSpeed() {
        singleWriterDoubleRecorder.recordValue(testValueLevel + (i++ & 0x800));
    }

}
