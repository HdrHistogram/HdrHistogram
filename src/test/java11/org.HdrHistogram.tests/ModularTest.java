/**
 * ModularTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package org.HdrHistogram.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.HdrHistogram.Histogram;
import java.lang.Module;

public class ModularTest {

    // Simplistic but ensure we can use the library from a modular application
    @Test
    public void smokeTest() {
        Histogram histogram = new Histogram(5);
        long res = histogram.getValueAtPercentile(50.0);
        Assertions.assertEquals(0, res);
    }

    // We are an full fledge module
    @Test
    public void moduleTest() {
        Module module = Histogram.class.getModule();
        Assertions.assertEquals("org.HdrHistogram", module.getName());
        Assertions.assertFalse(module.getDescriptor().isAutomatic());
    }
}