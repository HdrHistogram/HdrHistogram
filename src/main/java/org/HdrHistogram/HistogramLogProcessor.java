/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.Locale;

public class HistogramLogProcessor extends Thread {
    public static final String versionString = "Histogram Log Processor version " + Version.version;

    private final HistogramLogProcessorConfiguration config;

    private HistogramLogReader logReader;

    private static class HistogramLogProcessorConfiguration {
        public String outputFileName = null;
        public String inputFileName = null;

        public double rangeStartTimeSec = 0.0;
        public double rangeEndTimeSec = Double.MAX_VALUE;

        public boolean logFormatCsv = false;

        public int percentilesOutputTicksPerHalf = 5;
        public Double outputValueUnitRatio = 1000000.0; // default to msec units for output.

        public boolean error = false;
        public String errorMessage = "";

        public HistogramLogProcessorConfiguration(final String[] args) {
            boolean askedForHelp= false;
            try {
                for (int i = 0; i < args.length; ++i) {
                    if (args[i].equals("-csv")) {
                        logFormatCsv = true;
                    } else if (args[i].equals("-i")) {
                        inputFileName = args[++i];
                    } else if (args[i].equals("-start")) {
                        rangeStartTimeSec = Double.parseDouble(args[++i]);
                    } else if (args[i].equals("-end")) {
                        rangeEndTimeSec = Double.parseDouble(args[++i]);
                    } else if (args[i].equals("-o")) {
                        outputFileName = args[++i];
                    } else if (args[i].equals("-percentilesOutputTicksPerHalf")) {
                        percentilesOutputTicksPerHalf = Integer.parseInt(args[++i]);
                    } else if (args[i].equals("-outputValueUnitRatio")) {
                        outputValueUnitRatio = Double.parseDouble(args[++i]);
                    } else if (args[i].equals("-h")) {
                        askedForHelp = true;
                        throw new Exception("Help: " + args[i]);
                    } else {
                        throw new Exception("Invalid args: " + args[i]);
                    }
                }

            } catch (Exception e) {
                error = true;
                errorMessage = "Error: " + versionString + " launched with the following args:\n";

                for (String arg : args) {
                    errorMessage += arg + " ";
                }
                if (!askedForHelp) {
                    errorMessage += "\nWhich was parsed as an error, indicated by the following exception:\n" + e;
                    System.err.println(errorMessage);
                }

                final String validArgs =
                        "\"[-csv] [-i inputFileName] [-o outputFileName] " +
                                "[-start rangeStartTimeSec] [-end rangeEndTimeSec] " +
                                "[-outputValueUnitRatio r]";

                System.err.println("valid arguments = " + validArgs);

                System.err.println(
                        " [-h]                        help\n" +
                                " [-csv]                      Use CSV format for output log files\n" +
                                " [-i logFileName]            File name of Histogram Log to process (default is standard input)\n" +
                                " [-o outputFileName]         File name to output to (default is standard output)\n" +
                                "                             (will replace occurrences of %pid and %date with appropriate information)\n" +
                                " [-start rangeStartTimeSec]  The start time for the range in the file, in seconds (default 0.0)\n" +
                                " [-end rangeEndTimeSec]      The end time for the range in the file, in seconds (default is infinite)\n" +
                                " [-outputValueUnitRatio r]   The scaling factor by which to divide histogram recorded values units" +
                                "                             in output. [default = 1000000.0 (1 msec in nsec)]"
                );
                System.exit(1);
            }
        }
    }

    private HistogramLogProcessor(final String[] args) throws FileNotFoundException {
        this.setName("HistogramLogProcessor");
        config = new HistogramLogProcessorConfiguration(args);
        if (config.inputFileName != null) {
            logReader = new HistogramLogReader(config.inputFileName);
        } else {
            logReader = new HistogramLogReader(System.in);
        }
    }

    private void outputTimeRange(final PrintStream log, final String title) {
        log.format(Locale.US, "#[%s between %.3f and", title, config.rangeStartTimeSec);
        if (config.rangeEndTimeSec < Double.MAX_VALUE) {
            log.format(" %.3f", config.rangeEndTimeSec);
        } else {
            log.format(" %s", "<Infinite>");
        }
        log.format(" seconds (relative to StartTime)]\n");
    }

    private void outputStartTime(final PrintStream log, final Double startTime) {
        log.format(Locale.US, "#[StartTime: %.3f (seconds since epoch), %s]\n",
                startTime, (new Date((long) (startTime * 1000))).toString());
    }

    @Override
    public void run() {
        PrintStream timeIntervalLog = null;
        PrintStream histogramPercentileLog = System.out;
        Double firstStartTime = 0.0;
        boolean timeIntervalLogLegendWritten = false;

        if (config.outputFileName != null) {
            try {
                timeIntervalLog = new PrintStream(new FileOutputStream(config.outputFileName), false);
                outputTimeRange(timeIntervalLog, "Interval percentile log");
            } catch (FileNotFoundException ex) {
                System.err.println("Failed to open output file " + config.outputFileName);
            }
            String hgrmOutputFileName = config.outputFileName + ".hgrm";
            try {
                histogramPercentileLog = new PrintStream(new FileOutputStream(hgrmOutputFileName), false);
                outputTimeRange(histogramPercentileLog, "Overall percentile distribution");
            } catch (FileNotFoundException ex) {
                System.err.println("Failed to open percentiles histogram output file " + hgrmOutputFileName);
            }
        }

        final String logFormat;
        if (config.logFormatCsv) {
            logFormat = "%.3f,%d,%.3f,%.3f,%.3f,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f\n";
        } else {
            logFormat = "%4.3f: I:%d ( %7.3f %7.3f %7.3f ) T:%d ( %7.3f %7.3f %7.3f %7.3f %7.3f %7.3f )\n";
        }


        Histogram intervalHistogram = logReader.nextIntervalHistogram(config.rangeStartTimeSec, config.rangeEndTimeSec);

        Histogram accumulatedHistogram = null;
        if (intervalHistogram != null) {
            // Shape the accumulated histogram like the histograms in the log file:
            accumulatedHistogram = intervalHistogram.copy();
            // But clear contents:
            accumulatedHistogram.reset();
        }

        while (intervalHistogram != null) {
            accumulatedHistogram.add(intervalHistogram);

            if ((firstStartTime == 0.0) && (logReader.getStartTimeSec() != 0.0)) {
                firstStartTime = logReader.getStartTimeSec();

                outputStartTime(histogramPercentileLog, firstStartTime);

                if (timeIntervalLog != null) {
                    outputStartTime(timeIntervalLog, firstStartTime);
                }
            }

            if (timeIntervalLog != null) {
                if (!timeIntervalLogLegendWritten) {
                    timeIntervalLogLegendWritten = true;
                    if (config.logFormatCsv) {
                        timeIntervalLog.println("\"Timestamp\",\"Int_Count\",\"Int_50%\",\"Int_90%\",\"Int_Max\",\"Total_Count\"," +
                                "\"Total_50%\",\"Total_90%\",\"Total_99%\",\"Total_99.9%\",\"Total_99.99%\",\"Total_Max\"");
                    } else {
                        timeIntervalLog.println("Time: IntervalPercentiles:count ( 50% 90% Max ) TotalPercentiles:count ( 50% 90% 99% 99.9% 99.99% Max )");
                    }
                }
                HistogramData intervalHistogramData = intervalHistogram.getHistogramData();
                HistogramData accumulatedHistogramData = accumulatedHistogram.getHistogramData();
                timeIntervalLog.format(Locale.US, logFormat,
                        ((intervalHistogram.getEndTimeStamp()/1000.0) - logReader.getStartTimeSec()),
                        // values recorded during the last reporting interval
                        intervalHistogramData.getTotalCount(),
                        intervalHistogramData.getValueAtPercentile(50.0) / config.outputValueUnitRatio,
                        intervalHistogramData.getValueAtPercentile(90.0) / config.outputValueUnitRatio,
                        intervalHistogramData.getMaxValue() / config.outputValueUnitRatio,
                        // values recorded from the beginning until now
                        accumulatedHistogramData.getTotalCount(),
                        accumulatedHistogramData.getValueAtPercentile(50.0) / config.outputValueUnitRatio,
                        accumulatedHistogramData.getValueAtPercentile(90.0) / config.outputValueUnitRatio,
                        accumulatedHistogramData.getValueAtPercentile(99.0) / config.outputValueUnitRatio,
                        accumulatedHistogramData.getValueAtPercentile(99.9) / config.outputValueUnitRatio,
                        accumulatedHistogramData.getValueAtPercentile(99.99) / config.outputValueUnitRatio,
                        accumulatedHistogramData.getMaxValue() / config.outputValueUnitRatio
                );
            }

            // Read and accumulate the next line:
            intervalHistogram = logReader.nextIntervalHistogram(config.rangeStartTimeSec, config.rangeEndTimeSec);
        }

        if (accumulatedHistogram == null) {
            // If there were no histograms in the log file, we still need an empty histogram for the
            // one line output (shape/range doesn't matter because it is empty):
            accumulatedHistogram = new Histogram(1000000L, 2);
        }
        accumulatedHistogram.getHistogramData().outputPercentileDistribution(histogramPercentileLog,
                config.percentilesOutputTicksPerHalf, config.outputValueUnitRatio, config.logFormatCsv);

    }

    public static void main(final String[] args)  {
        try {
            final HistogramLogProcessor processor = new HistogramLogProcessor(args);
            processor.start();
        } catch (FileNotFoundException ex) {
            System.err.println("failed to open input file.");
        }
    }
}
