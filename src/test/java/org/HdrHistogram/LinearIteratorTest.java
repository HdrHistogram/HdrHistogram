/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */
package org.HdrHistogram;

import org.junit.Test;

public class LinearIteratorTest {

	@Test
	public void testMaxRecordedValueHigherThanReportingLevelLowestEquivalent {
		IntCountsHistogram histogram = new IntCountsHistogram(2);
		histogram.recordValue(241);
		int step = 16;
		int i = 0;
		for (HistogramIterationValue itValue : histogram.linearBucketValues(step)) {
			i += step;
			itValue.getCountAddedInThisIterationStep();
		}
	}
}
