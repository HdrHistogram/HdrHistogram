/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package bench;

import org.HdrHistogram.*;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

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
@Fork(3)
@State(Scope.Thread)

public class HdrHistogramEncodingBench {

    @Param({"case1", "case2", "case3", "sparsed1", "sparsed2", "quadratic", "cubic",
            "case1PlusSparsed2", "longestjHiccupLine", "shortestjHiccupLine", "sumOfjHiccupLines"})
    String latencySeriesName;

    @Param({ "2", "3" })
    int numberOfSignificantValueDigits;

    AbstractHistogram histogram;
    SkinnyHistogram skinnyHistogram;

    ByteBuffer buffer;

    @Setup
    public void setup() throws NoSuchMethodException {
        histogram = new Histogram(numberOfSignificantValueDigits);
        skinnyHistogram = new SkinnyHistogram(numberOfSignificantValueDigits);
        Iterable<Long> latencySeries = HistogramData.data.get(latencySeriesName);
        for (long latency : latencySeries) {
            histogram.recordValue(latency);
            skinnyHistogram.recordValue(latency);
        }
        buffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
    }

    @Benchmark
    public void encodeIntoCompressedByteBuffer() {
        buffer.clear();
        histogram.encodeIntoCompressedByteBuffer(buffer);
    }

    @Benchmark
    public void skinnyEncodeIntoCompressedByteBuffer() {
        buffer.clear();
        skinnyHistogram.encodeIntoCompressedByteBuffer(buffer);
    }

    @Benchmark
    public void roundtripCompressed() throws DataFormatException {
        buffer.clear();
        histogram.encodeIntoCompressedByteBuffer(buffer);
        buffer.rewind();
        Histogram.decodeFromCompressedByteBuffer(buffer, 0);
    }
}
