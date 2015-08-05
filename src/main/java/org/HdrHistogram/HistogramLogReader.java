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
import java.util.Locale;
import java.util.Scanner;
import java.util.zip.DataFormatException;

/**
 * A histogram log reader.
 * <p>
 * Histogram logs are used to capture full fidelity, per-time-interval
 * histograms of a recorded value.
 * <p>
 * For example, a histogram log can be used to capture high fidelity
 * reaction-time logs for some measured system or subsystem component.
 * Such a log would capture a full reaction time histogram for each
 * logged interval, and could be used to later reconstruct a full
 * HdrHistogram of the measured reaction time behavior for any arbitrary
 * time range within the log, by adding [only] the relevant interval
 * histograms.
 * <h3>Histogram log format:</h3>
 * A histogram log file consists of text lines. Lines beginning with
 * the "#" character are optional and treated as comments. Lines
 * containing the legend (starting with "Timestamp") are also optional
 * and ignored in parsing the histogram log. All other lines must
 * contain a valid interval description.
 * <p>
 * A valid interval description line must contain exactly three text fields:
 * <ul>
 * <li>StartTimestamp: The first field must contain a number parse-able as a Double value,
 * representing the start timestamp of the interval in seconds.</li>
 * <li>intervalLength: The second field must contain a number parse-able as a Double value,
 * representing the length of the interval in seconds.</li>
 * <li>Interval_Max: The third field must contain a number parse-able as a Double value,
 * which generally represents the maximum value of the interval histogram.</li>
 * <li>Interval_Compressed_Histogram: The fourth field must contain a text field
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
    private double startTimeSec = 0.0;
    private boolean observedStartTime = false;
    private double baseTimeSec = 0.0;
    private boolean observedBaseTime = false;
    private final boolean forceTsAbsolute;
    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified file name.
     * @param inputFileName The name of the file to read from
     * @param forceTsAbsolute Force reader to treat TS as absolute
     * @throws java.io.FileNotFoundException when unable to find inputFileName
     */
    public HistogramLogReader(final String inputFileName, final boolean forceTsAbsolute) throws FileNotFoundException {
    	this.forceTsAbsolute = forceTsAbsolute;
        scanner = new Scanner(new File(inputFileName));
        initScanner();
    }
    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified file name.
     * @param inputFileName The name of the file to read from
     * @throws java.io.FileNotFoundException when unable to find inputFileName
     */
    public HistogramLogReader(final String inputFileName) throws FileNotFoundException {
        this(inputFileName, false);
    }
    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified InputStream.
     * @param inputStream The InputStream to read from
     * @param forceTsAbsolute Force reader to treat TS as absolute
     */
    public HistogramLogReader(final InputStream inputStream, final boolean forceTsAbsolute) {
    	this.forceTsAbsolute = forceTsAbsolute;
        scanner = new Scanner(inputStream);
        initScanner();
    }
    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified InputStream.
     * @param inputStream The InputStream to read from
     */
    public HistogramLogReader(final InputStream inputStream) {
    	this(inputStream, false);
    }
    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified file.
     * @param inputFile The File to read from
     * @param forceTsAbsolute Force reader to treat TS as absolute
     * @throws java.io.FileNotFoundException when unable to find inputFile
     */
    public HistogramLogReader(final File inputFile, final boolean forceTsAbsolute) throws FileNotFoundException {
    	this.forceTsAbsolute = forceTsAbsolute;
        scanner = new Scanner(inputFile);
        initScanner();
    }
    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified file.
     * @param inputFile The File to read from
     * @throws java.io.FileNotFoundException when unable to find inputFile
     */
    public HistogramLogReader(final File inputFile) throws FileNotFoundException {
    	this(inputFile, false);
    }


    private void initScanner() {
        scanner.useLocale(Locale.US);
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
    public double getStartTimeSec() {
        return startTimeSec;
    }

    /**
     * Read the next interval histogram from the log, if interval falls within a time range.
     * <p>
     * Returns a histogram object if an interval line was found with an
     * associated start timestamp value that falls between startTimeSec and
     * endTimeSec, or null if no such interval line is found. Note that
     * the range is assumed to be in seconds relative to the actual
     * timestamp value found in each interval line in the log, and not
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
    public EncodableHistogram nextIntervalHistogram(final double startTimeSec,
                                  final double endTimeSec) {
        return nextIntervalHistogram(startTimeSec, endTimeSec, false);
    }

    /**
     * Read the next interval histogram from the log, if interval falls within an absolute time range
     * <p>
     * Returns a histogram object if an interval line was found with an
     * associated absolute start timestamp value that falls between
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
    public EncodableHistogram nextAbsoluteIntervalHistogram(final double absoluteStartTimeSec,
                                                     final double absoluteEndTimeSec) {
        return nextIntervalHistogram(absoluteStartTimeSec, absoluteEndTimeSec, true);
    }


    /**
     * Read the next interval histogram from the log. Returns a Histogram object if
     * an interval line was found, or null if not.
     * <p>Upon encountering any unexpected format errors in reading the next interval
     * from the file, this method will return a null.
     * @return a DecodedInterval, or a null if no appropriate interval found
     */
    public EncodableHistogram nextIntervalHistogram() {
        return nextIntervalHistogram(0.0, Long.MAX_VALUE * 1.0, true);
    }

    private EncodableHistogram nextIntervalHistogram(final double rangeStartTimeSec,
                                            final double rangeEndTimeSec, boolean absolute) {
        while (scanner.hasNextLine()) {
            try {
                if (scanner.hasNext("\\#.*")) {
                    // comment line.
                    // Look for explicit start time or base time notes in comments:
                    if (scanner.hasNext("#\\[StartTime:")) {
                        scanner.next("#\\[StartTime:");
                        if (scanner.hasNextDouble()) {
                            startTimeSec = scanner.nextDouble(); // start time represented as seconds since epoch
                            observedStartTime = true;
                        }
                    } else if (scanner.hasNext("#\\[BaseTime:")) {
                        scanner.next("#\\[BaseTime:");
                        if (scanner.hasNextDouble()) {
                            baseTimeSec = scanner.nextDouble(); // base time represented as seconds since epoch
                            observedBaseTime = true;
                        }
                    }
                    scanner.nextLine();
                    continue;
                }

                if (scanner.hasNext("\"StartTimestamp\".*")) {
                    // Legend line
                    scanner.nextLine();
                    continue;
                }

                // Decode: startTimestamp, intervalLength, maxTime, histogramPayload

                final double logTimeStampInSec = scanner.nextDouble(); // Timestamp is expected to be in seconds

                if (!observedStartTime) {
                    // No explicit start time noted. Use 1st observed time:
                    startTimeSec = logTimeStampInSec;
                    observedStartTime = true;
                }
                if (!observedBaseTime) {
                    // No explicit base time noted. Deduce from 1st observed time (compared to start time):
                    if (logTimeStampInSec < startTimeSec && !forceTsAbsolute) {
                        // Timestamps in log are not absolute
                        baseTimeSec = startTimeSec;
                    } else {
                        // Timestamps are absolute
                        baseTimeSec = 0.0;
                    }
                    observedBaseTime = true;
                }

                final double absoluteStartTimeStampSec = logTimeStampInSec + baseTimeSec;
                final double offsetStartTimeStampSec = absoluteStartTimeStampSec - startTimeSec;

                final double intervalLengthSec = scanner.nextDouble(); // Timestamp length is expect to be in seconds
                final double absoluteEndTimeStampSec = absoluteStartTimeStampSec + intervalLengthSec;

                final double startTimeStampToCheckRangeOn = absolute ? absoluteStartTimeStampSec : offsetStartTimeStampSec;

                if (startTimeStampToCheckRangeOn < rangeStartTimeSec) {
                    scanner.nextLine();
                    continue;
                }

                if (startTimeStampToCheckRangeOn > rangeEndTimeSec) {
                    return null;
                }

                scanner.nextDouble(); // Skip maxTime field, as max time can be deduced from the histogram.
                final String compressedPayloadString = scanner.next();
                final ByteBuffer buffer = ByteBuffer.wrap(
                        DatatypeConverter.parseBase64Binary(compressedPayloadString));

                EncodableHistogram histogram = EncodableHistogram.decodeFromCompressedByteBuffer(buffer, 0);

                histogram.setStartTimeStamp((long) (absoluteStartTimeStampSec * 1000.0));
                histogram.setEndTimeStamp((long) (absoluteEndTimeStampSec * 1000.0));

                scanner.nextLine(); // Move to next line. Very much needed for e.g. windows CR/LF lines

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
