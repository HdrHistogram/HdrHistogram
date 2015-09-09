package org.HdrHistogram;

import junit.framework.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class HistogramLogReaderWriterTest {

    @Test
    public void emptyLog() throws Exception {
        File temp = File.createTempFile("hdrhistogramtesting", "hist");
        FileOutputStream writerStream = new FileOutputStream(temp);
        HistogramLogWriter writer = new HistogramLogWriter(writerStream);
        writer.outputLogFormatVersion();
        long startTimeWritten = 1000;
        writer.outputStartTime(startTimeWritten);
        writer.outputLogFormatVersion();
        writer.outputLegend();
        writerStream.close();

        FileInputStream readerStream = new FileInputStream(temp);
        HistogramLogReader reader = new HistogramLogReader(readerStream);
        EncodableHistogram histogram = reader.nextIntervalHistogram();
        Assert.assertNull(histogram);
        Assert.assertEquals(1.0, reader.getStartTimeSec());
    }

    @Test
    public void jHiccupV2Log() throws Exception {
        InputStream readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("jHiccup-2.0.7S.LogV2.hlog");

        HistogramLogReader reader = new HistogramLogReader(readerStream);
        int histogramCount = 0;
        long totalCount = 0;
        EncodableHistogram encodeableHistogram = null;
        Histogram accumulatedHistogram = new Histogram(3);
        while ((encodeableHistogram = reader.nextIntervalHistogram()) != null) {
            histogramCount++;
            Assert.assertTrue("Expected integer value histogramsin log file", encodeableHistogram instanceof Histogram);
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(38, histogramCount);
        Assert.assertEquals(30874, totalCount);
        Assert.assertEquals(956825599, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(981991423, accumulatedHistogram.getMaxValue());
        Assert.assertEquals(1441753532.570, reader.getStartTimeSec());

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("jHiccup-2.0.7S.LogV2.hlog");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        totalCount = 0;
        accumulatedHistogram.reset();
        while ((encodeableHistogram = reader.nextIntervalHistogram(5, 20)) != null) {
            histogramCount++;
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(14, histogramCount);
        Assert.assertEquals(12406, totalCount);
        Assert.assertEquals(969408511, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(981991423, accumulatedHistogram.getMaxValue());

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("jHiccup-2.0.7S.LogV2.hlog");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        totalCount = 0;
        accumulatedHistogram.reset();
        while ((encodeableHistogram = reader.nextIntervalHistogram(15, 32)) != null) {
            histogramCount++;
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(17, histogramCount);
        Assert.assertEquals(13754, totalCount);
        Assert.assertEquals(969408511, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(981991423, accumulatedHistogram.getMaxValue());
    }

    @Test
    public void jHiccupV1Log() throws Exception {
        InputStream readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("jHiccup-2.0.6.logV1.hlog");
        HistogramLogReader reader = new HistogramLogReader(readerStream);
        int histogramCount = 0;
        long totalCount = 0;
        EncodableHistogram encodeableHistogram = null;
        Histogram accumulatedHistogram = new Histogram(3);
        while ((encodeableHistogram = reader.nextIntervalHistogram()) != null) {
            histogramCount++;
            Assert.assertTrue("Expected integer value histogramsin log file", encodeableHistogram instanceof Histogram);
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(88, histogramCount);
        Assert.assertEquals(65964, totalCount);
        Assert.assertEquals(1829765119, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(1888485375, accumulatedHistogram.getMaxValue());
        Assert.assertEquals(1438867590.285, reader.getStartTimeSec());

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("jHiccup-2.0.6.logV1.hlog");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        totalCount = 0;
        accumulatedHistogram.reset();
        while ((encodeableHistogram = reader.nextIntervalHistogram(5, 20)) != null) {
            histogramCount++;
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(15, histogramCount);
        Assert.assertEquals(11213, totalCount);
        Assert.assertEquals(1019740159, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(1032323071, accumulatedHistogram.getMaxValue());

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("jHiccup-2.0.6.logV1.hlog");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        totalCount = 0;
        accumulatedHistogram.reset();
        while ((encodeableHistogram = reader.nextIntervalHistogram(50, 80)) != null) {
            histogramCount++;
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(29, histogramCount);
        Assert.assertEquals(22630, totalCount);
        Assert.assertEquals(1871708159, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(1888485375, accumulatedHistogram.getMaxValue());
    }

    @Test
    public void jHiccupV0Log() throws Exception {
        InputStream readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("jHiccup-2.0.1.logV0.hlog");
        HistogramLogReader reader = new HistogramLogReader(readerStream);
        int histogramCount = 0;
        long totalCount = 0;
        EncodableHistogram encodeableHistogram = null;
        Histogram accumulatedHistogram = new Histogram(3);
        while ((encodeableHistogram = reader.nextIntervalHistogram()) != null) {
            histogramCount++;
            Assert.assertTrue("Expected integer value histogramsin log file", encodeableHistogram instanceof Histogram);
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(81, histogramCount);
        Assert.assertEquals(61256, totalCount);
        Assert.assertEquals(1510998015, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(1569718271, accumulatedHistogram.getMaxValue());
        Assert.assertEquals(1438869961.225, reader.getStartTimeSec());

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("jHiccup-2.0.1.logV0.hlog");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        totalCount = 0;
        accumulatedHistogram.reset();
        while ((encodeableHistogram = reader.nextIntervalHistogram(20, 45)) != null) {
            histogramCount++;
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(25, histogramCount);
        Assert.assertEquals(18492, totalCount);
        Assert.assertEquals(459007, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(623103, accumulatedHistogram.getMaxValue());

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("jHiccup-2.0.1.logV0.hlog");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        totalCount = 0;
        accumulatedHistogram.reset();
        while ((encodeableHistogram = reader.nextIntervalHistogram(46, 80)) != null) {
            histogramCount++;
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(34, histogramCount);
        Assert.assertEquals(25439, totalCount);
        Assert.assertEquals(1209008127, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(1234173951, accumulatedHistogram.getMaxValue());
    }

    @Test
    public void ycsbLog() throws Exception {
        InputStream readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("ycsb.hist");
        HistogramLogReader reader = new HistogramLogReader(readerStream);
        int histogramCount = 0;
        long totalCount = 0;
        EncodableHistogram encodeableHistogram = null;
        Histogram accumulatedHistogram = new Histogram(3);
        while ((encodeableHistogram = reader.nextIntervalHistogram()) != null) {
            histogramCount++;
            Assert.assertTrue("Expected integer value histogramsin log file", encodeableHistogram instanceof Histogram);
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(602, histogramCount);
        Assert.assertEquals(300056, totalCount);
        Assert.assertEquals(1214463, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(1546239, accumulatedHistogram.getMaxValue());
        Assert.assertEquals(1438613579.295, reader.getStartTimeSec());

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("ycsb.hist");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        totalCount = 0;
        accumulatedHistogram.reset();
        while ((encodeableHistogram = reader.nextIntervalHistogram(0, 180)) != null) {
            histogramCount++;
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        // note the first histogram in the log is before 0, so we drop it on the
        // floor
        Assert.assertEquals(180, histogramCount);
        Assert.assertEquals(90033, totalCount);
        Assert.assertEquals(1375231, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(1546239, accumulatedHistogram.getMaxValue());

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("ycsb.hist");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        totalCount = 0;
        accumulatedHistogram.reset();
        while ((encodeableHistogram = reader.nextIntervalHistogram(180, 700)) != null) {
            histogramCount++;
            Histogram histogram = (Histogram) encodeableHistogram;
            totalCount += histogram.getTotalCount();
            accumulatedHistogram.add(histogram);
        }
        Assert.assertEquals(421, histogramCount);
        Assert.assertEquals(209686, totalCount);
        Assert.assertEquals(530, accumulatedHistogram.getValueAtPercentile(99.9));
        Assert.assertEquals(17775, accumulatedHistogram.getMaxValue());
    }

    @Test
    public void emptyHistogramsInLog() throws Exception {
        File temp = File.createTempFile("hdrhistogramtesting", "hist");
        FileOutputStream writerStream = new FileOutputStream(temp);
        HistogramLogWriter writer = new HistogramLogWriter(writerStream);
        writer.outputLogFormatVersion();
        long startTimeWritten = 11000;
        writer.outputStartTime(startTimeWritten);
        writer.outputLogFormatVersion();
        writer.outputLegend();
        Histogram empty = new Histogram(2);
        empty.setStartTimeStamp(11100);
        empty.setEndTimeStamp(12100);
        writer.outputIntervalHistogram(empty);
        empty.setStartTimeStamp(12100);
        empty.setEndTimeStamp(13100);
        writer.outputIntervalHistogram(empty);
        writerStream.close();

        FileInputStream readerStream = new FileInputStream(temp);
        HistogramLogReader reader = new HistogramLogReader(readerStream);
        Histogram histogram = (Histogram) reader.nextIntervalHistogram();
        Assert.assertEquals(11.0, reader.getStartTimeSec());
        Assert.assertNotNull(histogram);
        Assert.assertEquals(0, histogram.getTotalCount());
        Assert.assertEquals(11100, histogram.getStartTimeStamp());
        Assert.assertEquals(12100, histogram.getEndTimeStamp());
        histogram = (Histogram) reader.nextIntervalHistogram();
        Assert.assertNotNull(histogram);
        Assert.assertEquals(0, histogram.getTotalCount());
        Assert.assertEquals(12100, histogram.getStartTimeStamp());
        Assert.assertEquals(13100, histogram.getEndTimeStamp());
        Assert.assertNull(reader.nextIntervalHistogram());
        readerStream.close();

        readerStream = new FileInputStream(temp);
        reader = new HistogramLogReader(readerStream);
        // relative read from the file, should include both histograms
        histogram = (Histogram) reader.nextIntervalHistogram(0.0, 4.0);
        Assert.assertEquals(11.0, reader.getStartTimeSec());
        Assert.assertNotNull(histogram);
        Assert.assertEquals(0, histogram.getTotalCount());
        Assert.assertEquals(11100, histogram.getStartTimeStamp());
        Assert.assertEquals(12100, histogram.getEndTimeStamp());
        histogram = (Histogram) reader.nextIntervalHistogram(0.0, 4.0);
        Assert.assertNotNull(histogram);
        Assert.assertEquals(0, histogram.getTotalCount());
        Assert.assertEquals(12100, histogram.getStartTimeStamp());
        Assert.assertEquals(13100, histogram.getEndTimeStamp());
        Assert.assertNull(reader.nextIntervalHistogram());
        readerStream.close();

        readerStream = new FileInputStream(temp);
        reader = new HistogramLogReader(readerStream);
        // relative read from the file, should skip first histogram
        histogram = (Histogram) reader.nextIntervalHistogram(1.0, 4.0);
        Assert.assertEquals(11.0, reader.getStartTimeSec());
        Assert.assertNotNull(histogram);
        Assert.assertEquals(0, histogram.getTotalCount());
        Assert.assertEquals(12100, histogram.getStartTimeStamp());
        Assert.assertEquals(13100, histogram.getEndTimeStamp());
        Assert.assertNull(reader.nextIntervalHistogram());
        readerStream.close();
    }

}
