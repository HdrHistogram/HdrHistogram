package org.HdrHistogram;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.zip.Deflater;


/**
 * A histogram log writer.
 * <p>
 * A Histogram logs are used to capture full fidelity, per-time-interval
 * histograms of a recorded value.
 * <p>
 * For example, a histogram log can be used to capture high fidelity
 * reaction-time logs for some measured system or subsystem component.
 * Such a log would capture a full reaction time histogram for each
 * logged interval, and could be used to later reconstruct a full
 * HdrHistogram of the measured reaction time behavior for any arbitrary
 * time range within the log, by adding [only] the relevant interval
 * histograms.
 * <p>
 * This log writer will produce histogram logs that adhere to the
 * histogram log format (see {{@link HistogramLogReader} for log format
 * details). Optional comments, start time, legend, and format version
 * can be logged.
 * <p>
 * By convention, it is typical for the logging application
 * to use a comment to indicate the logging application at the head
 * of the log, followed by the log format version, a start time,
 * and a legend (in that order).
 *
 */
public class HistogramLogWriter {
    private static final String HISTOGRAM_LOG_FORMAT_VERSION = "1.01";

    private final PrintStream log;

    private ByteBuffer targetBuffer;

    /**
     * Constructs a new HistogramLogWriter around a newly created file with the specified file name.
     * @param outputFileName The name of the file to create
     * @throws FileNotFoundException
     */
    public HistogramLogWriter(final String outputFileName) throws FileNotFoundException {
        log = new PrintStream(outputFileName);
    }

    /**
     * Constructs a new HistogramLogWriter that will write into the specified file.
     * @param outputFile The File to write to
     * @throws FileNotFoundException
     */
    public HistogramLogWriter(final File outputFile) throws FileNotFoundException {
        log = new PrintStream(outputFile);
    }

    /**
     * Constructs a new HistogramLogWriter that will write into the specified output stream.
     * @param outputStream The OutputStream to write to
     * @throws FileNotFoundException
     */
    public HistogramLogWriter(final OutputStream outputStream) throws FileNotFoundException {
        log = new PrintStream(outputStream);
    }

    /**
     * Constructs a new HistogramLogWriter that will write into the specified print stream.
     * @param printStream The PrintStream to write to
     * @throws FileNotFoundException
     */
    public HistogramLogWriter(final PrintStream printStream) throws FileNotFoundException {
        log = printStream;
    }

    /**
     * Output an interval histogram, with the given timestamp.
     * (note that the specified timestamp will be used, and the timestamp in the actual
     * histogram will be ignored)
     * @param startTimeStampSec The start timestamp to log with the interval histogram, in seconds.
     * @param endTimeStampSec The end timestamp to log with the interval histogram, in seconds.
     * @param histogram The interval histogram to log.
     */
    public void outputIntervalHistogram(final double startTimeStampSec,
                                        final double endTimeStampSec,
                                        final Histogram histogram) {
        if ((targetBuffer == null) || targetBuffer.capacity() < histogram.getNeededByteBufferCapacity()) {
            targetBuffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        }
        targetBuffer.clear();

        int compressedLength = histogram.encodeIntoCompressedByteBuffer(targetBuffer, Deflater.BEST_COMPRESSION);
        byte[] compressedArray = Arrays.copyOf(targetBuffer.array(), compressedLength);

        log.format(Locale.US, "%.3f,%.3f,%.3f,%s\n",
                startTimeStampSec,
                endTimeStampSec - startTimeStampSec,
                histogram.getHistogramData().getMaxValue() / 1000000.0,
                DatatypeConverter.printBase64Binary(compressedArray)
        );
    }

    /**
     * Output an interval histogram, using the timestamp indicated in the histogram.
     * @param histogram The interval histogram to log.
     */
    public void outputIntervalHistogram(final Histogram histogram) {
        outputIntervalHistogram(histogram.getStartTimeStamp()/1000.0,
                histogram.getEndTimeStamp()/1000.0,
                histogram);
    }

    /**
     * Log a start time in the log.
     * @param startTimeMsec time (in milliseconds) since the absolute start time (the epoch)
     */
    public void outputStartTime(final long startTimeMsec) {
        log.format(Locale.US, "#[StartTime: %.3f (seconds since epoch), %s]\n",
                startTimeMsec/1000.0,
                (new Date(startTimeMsec)).toString());
    }

    /**
     * Log a comment to the log.
     * Comments will be preceded with with the '#' character.
     * @param comment the comment string.
     */
    public void outputComment(final String comment) {
        log.format("#%s\n", comment);
    }

    /**
     * Output a legend line to the log.
     */
    public void outputLegend() {
        log.println("\"StartTimestamp\",\"EndTimestamp\",\"Interval_Max\",\"Interval_Compressed_Histogram\"");
    }

    /**
     * Output a log format version to the log.
     */
    public void outputLogFormatVersion() {
        outputComment("[Histogram log format version " + HISTOGRAM_LOG_FORMAT_VERSION +"]");
    }
}
