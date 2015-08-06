package org.HdrHistogram;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.junit.Test;

import junit.framework.Assert;

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
    public void ycsbLog() throws Exception {
        InputStream readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("ycsb.hist");
        HistogramLogReader reader = new HistogramLogReader(readerStream);
        int histogramCount = 0;
        while (reader.nextIntervalHistogram() != null) {
            histogramCount++;
        }
        Assert.assertEquals(602, histogramCount);
        Assert.assertEquals(1438613579.295, reader.getStartTimeSec());

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("ycsb.hist");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        while (reader.nextIntervalHistogram(0, 180) != null) {
            histogramCount++;
        }
        // note the first histogram in the log is before 0, so we drop it on the
        // floor
        Assert.assertEquals(180, histogramCount);

        readerStream = HistogramLogReaderWriterTest.class.getResourceAsStream("ycsb.hist");
        reader = new HistogramLogReader(readerStream);
        histogramCount = 0;
        while (reader.nextIntervalHistogram(180, 700) != null) {
            histogramCount++;
        }
        Assert.assertEquals(421, histogramCount);

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
