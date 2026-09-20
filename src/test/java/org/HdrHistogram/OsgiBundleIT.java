package org.HdrHistogram;

import org.apache.felix.framework.FrameworkFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OsgiBundleIT {
    @TempDir
    Path frameworkStorage;

    @Test
    public void startsAndRoundTripsLogs() throws Exception {
        Framework framework = new FrameworkFactory().newFramework(
                Collections.singletonMap(Constants.FRAMEWORK_STORAGE, frameworkStorage.toString()));
        framework.start();
        try {
            Bundle bundle = framework.getBundleContext().installBundle(
                    Paths.get(System.getProperty("testBundle")).toUri().toString());
            bundle.start();
            assertEquals(Bundle.ACTIVE, bundle.getState());

            // Using the test class path would hide missing OSGi package wiring.
            Class<?> histogramClass = bundle.loadClass(Histogram.class.getName());
            Object histogram = histogramClass.getConstructor(int.class).newInstance(3);
            histogramClass.getMethod("recordValue", long.class).invoke(histogram, 42L);

            Class<?> writerClass = bundle.loadClass(HistogramLogWriter.class.getName());
            Class<?> encodableClass = bundle.loadClass(EncodableHistogram.class.getName());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Object writer = writerClass.getConstructor(OutputStream.class).newInstance(bytes);
            writerClass.getMethod("outputIntervalHistogram", encodableClass).invoke(writer, histogram);

            Class<?> readerClass = bundle.loadClass(HistogramLogReader.class.getName());
            Object reader = readerClass.getConstructor(InputStream.class).newInstance(
                    new ByteArrayInputStream(bytes.toByteArray()));
            Object decoded = readerClass.getMethod("nextIntervalHistogram").invoke(reader);
            long count = (Long) histogramClass.getMethod("getTotalCount").invoke(decoded);
            long value = (Long) histogramClass.getMethod("getValueAtPercentile", double.class).invoke(decoded, 100.0);
            assertEquals(1L, count);
            assertEquals(42L, value);
            System.out.println("bundle.state=" + bundle.getState());
            System.out.println("log roundtrip count=" + count + ", value=" + value);
        } finally {
            framework.stop();
            assertEquals(FrameworkEvent.STOPPED, framework.waitForStop(5000).getType());
        }
    }
}
