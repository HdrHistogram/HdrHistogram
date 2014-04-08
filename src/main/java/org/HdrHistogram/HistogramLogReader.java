/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.zip.DataFormatException;

/**
 * A histogram log reader.
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
 * <h3>Histogram log format:</h3>
 * A histogram log file consists of text lines. Lines beginning with
 * the "#" character are optional and treated as comments. Lines
 * containing the legend (starting with "Timestamp") are also optional
 * and ignored in parsing the histogram log. All other lines must
 * contain a valid interval description.
 * <p>
 * A valid interval description line must contain exactly three text fields:
 * <ul>
 * <li>Timestamp: The first field must contain a number parse-able as a Double value,
 * representing the timestamp of the interval.</li>
 * <li>Interval_Max: The second field must contain a number parse-able as a Double value,
 * which generally represents the maximum value of the interval histogram.</li>
 * <li>Interval_Compressed_Histogram: The third field must contain a text field
 * parse-able as a Base64 text representation of a compressed HdrHistogram.</li>
 * </ul>
 * The log file may contain an optional indication of a starting time. Starting time
 * is indicated using a special comments starting with "#[StartTime: " and followed
 * by a number parse-able as a double, representing the start time (in seconds)
 * that may be added to timestamps in the file to determine an absolute
 * timestamp (e.g. since the epoch) for each interval.
 */
public class HistogramLogReader {

    private final Scanner scanner;
    private Double startTimeSec = 0.0;

    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified file name.
     * @param inputFileName The name of the file to read from
     * @throws FileNotFoundException
     */
    public HistogramLogReader(final String inputFileName) throws FileNotFoundException {
        scanner = new Scanner(new File(inputFileName));
        scanner.useDelimiter("[ ,\\r\\n]");
    }

    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified InputStream.
     * @param inputStream The InputStream to read from
     * @throws FileNotFoundException
     */
    public HistogramLogReader(final InputStream inputStream) throws FileNotFoundException {
        scanner = new Scanner(inputStream);
        scanner.useDelimiter("[ ,\\r\\n]");
    }

    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified file.
     * @param inputFile The File to read from
     * @throws FileNotFoundException
     */
    public HistogramLogReader(final File inputFile) throws FileNotFoundException {
        scanner = new Scanner(inputFile);
        scanner.useDelimiter("[ ,\\r\\n]");
    }

    /**
     * get the latest start time found in the file so far (or 0.0),
     * per the log file format explained above. Assuming the "#[StartTime:" comment
     * line precedes the actual intervals recorded in the file, getStartTimeSec() can
     * be safely used after each interval is read to determine's the offset of that
     * interval's timestamp from the epoch.
     * @return latest Start Time found in the file (or 0.0 if non found)
     */
    public Double getStartTimeSec() {
        return startTimeSec;
    }

    /**
     * Read the next interval histogram from the log, if interval falls within a time range.
     * <p>
     * Returns a histogram object if an interval line was found with an
     * associated timestamp value that falls between startTimeSec and
     * endTimeSec, or null if no such interval line is found. Note that
     * the range is assumed to be in seconds relative to the actual
     * timstamp value found in each interval line in the log, and not
     * in absolute time.
     *  <p>
     * Timestamps are assumed to appear in order in the log file, and as such
     * this method will return a null upon encountering a timestamp larger than
     * rangeEndTimeSec.
     * <p>
     * The histogram returned will have it's timestamp set to the absolute
     * timestamp calculated from adding the interval's indicated timestamp
     * value to the latest [optional] start time found in the log.
     * <p>
     * Upon encountering any unexpected format errors in reading the next
     * interval from the file, this method will return a null.
     * @param startTimeSec The (non-absolute time) start of the expected
     *                     time range, in seconds.
     * @param endTimeSec The (non-absolute time) end of the expected time
     *                   range, in seconds.
     * @return a histogram, or a null if no appropriate interval found
     */
    public Histogram nextIntervalHistogram(final Double startTimeSec,
                                  final Double endTimeSec) {
        return nextIntervalHistogram(startTimeSec, endTimeSec, false);
    }

    /**
     * Read the next interval histogram from the log, if interval falls within an absolute time range
     * <p>
     * Returns a histogram object if an interval line was found with an
     * associated absolute timestamp value that falls between
     * absoluteStartTimeSec and absoluteEndTimeSec, or null if no such
     * interval line is found.
     * <p>
     * Timestamps are assumed to appear in order in the log file, and as such
     * this method will return a null upon encountering a timestamp larger than
     * rangeEndTimeSec.
     * <p>
     * The histogram returned will have it's timestamp set to the absolute
     * timestamp calculated from adding the interval's indicated timestamp
     * value to the latest [optional] start time found in the log.
     * <p>
     * Absolute timestamps are calculated by adding the timestamp found
     * with the recorded interval to the [latest, optional] start time
     * found in the log. The start time is indicated in the log with
     * a "#[StartTime: " followed by the start time in seconds.
     * <p>
     * Upon encountering any unexpected format errors in reading the next
     * interval from the file, this method will return a null.
     * @param absoluteStartTimeSec The (absolute time) start of the expected
     *                             time range, in seconds.
     * @param absoluteEndTimeSec The (absolute time) end of the expected
     *                           time range, in seconds.
     * @return A histogram, or a null if no appropriate interval found
     */
    public Histogram nextAbsoluteIntervalHistogram(final Double absoluteStartTimeSec,
                                                     final Double absoluteEndTimeSec) {
        return nextIntervalHistogram(absoluteStartTimeSec, absoluteEndTimeSec, false);
    }


    /**
     * Read the next interval histogram from the log. Returns a Histogram object if
     * an interval line was found, or null if not.
     * <p>Upon encountering any unexpected format errors in reading the next interval
     * from the file, this method will return a null.
     * @return a DecodedInterval, or a null if no appropriate interval found
     */
    public Histogram nextIntervalHistogram() {
        return nextIntervalHistogram(0.0, Long.MAX_VALUE * 1.0, true);
    }

    private Histogram nextIntervalHistogram(final Double rangeStartTimeSec,
                                            final Double rangeEndTimeSec, boolean absolute) {
        while (scanner.hasNextLine()) {
            try {
                if (scanner.hasNext("\\#.*")) {
                    // comment line
                    if (scanner.hasNext("#\\[StartTime:")) {
                        scanner.next("#\\[StartTime:");
                        if (scanner.hasNextDouble()) {
                            startTimeSec = scanner.nextDouble(); // start time represented as seconds since epoch
                        }
                    }
                    scanner.nextLine();
                    continue;
                }

                if (scanner.hasNext("\"Timestamp\".*")) {
                    // Legend line
                    scanner.nextLine();
                    continue;
                }

                // Decode: timestamp, maxTime, histogramPayload

                final double offsetTimeStampSec = scanner.nextDouble(); // Timestamp is expect to be in seconds
                final double absoluteTimeStampSec = startTimeSec + offsetTimeStampSec;

                final double timeStampToCheckRangeOn = absolute ? absoluteTimeStampSec : offsetTimeStampSec;

                if (offsetTimeStampSec < rangeStartTimeSec) {
                    scanner.nextLine();
                    continue;
                }

                if (offsetTimeStampSec > rangeEndTimeSec) {
                    return null;
                }

                scanner.nextDouble(); // Skip maxTime field, as max time can be deduced from the histogram.
                final String compressedPayloadString = scanner.next();
                final ByteBuffer buffer = ByteBuffer.wrap(
                        DatatypeConverter.parseBase64Binary(compressedPayloadString));

                Histogram histogram = Histogram.decodeFromCompressedByteBuffer(buffer, 0);

                histogram.setTimeStamp(absoluteTimeStampSec);

                return histogram;

            } catch (java.util.NoSuchElementException ex) {
                return null;
            } catch (DataFormatException ex) {
                return null;
            }
        }
        return null;
    }

}
