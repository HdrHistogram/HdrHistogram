/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Scanner;
import java.util.zip.DataFormatException;

class AbstractHistogramLogReader {

    protected final Scanner scanner;
    private double startTimeSec = 0.0;

    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified file name.
     * @param inputFileName The name of the file to read from
     * @throws java.io.FileNotFoundException when unable to find inputFileName
     */
    public AbstractHistogramLogReader(final String inputFileName) throws FileNotFoundException {
        scanner = new Scanner(new File(inputFileName));
        initScanner();
    }

    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified InputStream.
     * @param inputStream The InputStream to read from
     */
    public AbstractHistogramLogReader(final InputStream inputStream) {
        scanner = new Scanner(inputStream);
        initScanner();
    }

    /**
     * Constructs a new HistogramLogReader that produces intervals read from the specified file.
     * @param inputFile The File to read from
     * @throws java.io.FileNotFoundException when unable to find inputFile
     */
    public AbstractHistogramLogReader(final File inputFile) throws FileNotFoundException {
        scanner = new Scanner(inputFile);
        initScanner();
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

    protected void setStartTimeSec(double startTimeSec) {
        this.startTimeSec = startTimeSec;
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
    public EncodableHistogram nextIntervalHistogram(final Double startTimeSec,
                                           final Double endTimeSec) {
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
    public EncodableHistogram nextAbsoluteIntervalHistogram(final Double absoluteStartTimeSec,
                                                   final Double absoluteEndTimeSec) {
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

    private EncodableHistogram nextIntervalHistogram(final Double rangeStartTimeSec,
                                            final Double rangeEndTimeSec, boolean absolute) {
        while (scanner.hasNextLine()) {
            try {
                if (scanner.hasNext("\\#.*")) {
                    // comment line
                    if (scanner.hasNext("#\\[StartTime:")) {
                        scanner.next("#\\[StartTime:");
                        if (scanner.hasNextDouble()) {
                            setStartTimeSec(scanner.nextDouble()); // start time represented as seconds since epoch
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

                final double offsetStartTimeStampSec = scanner.nextDouble(); // Timestamp start is expect to be in seconds
                final double absoluteStartTimeStampSec = getStartTimeSec() + offsetStartTimeStampSec;

                final double intervalLengthSec = scanner.nextDouble(); // Timestamp length is expect to be in seconds
                final double offsetEndTimeStampSec = offsetStartTimeStampSec + intervalLengthSec;
                final double absoluteEndTimeStampSec = getStartTimeSec() + offsetEndTimeStampSec;

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
                        Base64Helper.parseBase64Binary(compressedPayloadString));

                EncodableHistogram histogram = Histogram.decodeFromCompressedByteBuffer(buffer, 0);

                histogram.setStartTimeStamp((long) (absoluteStartTimeStampSec * 1000.0));
                histogram.setEndTimeStamp((long) (absoluteEndTimeStampSec * 1000.0));

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
