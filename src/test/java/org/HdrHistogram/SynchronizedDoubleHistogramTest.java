package org.HdrHistogram;

import org.junit.Test;

public class SynchronizedDoubleHistogramTest {

  @Test
  public void equalsWillNotThrowClassCastException() {
    SynchronizedDoubleHistogram synchronizedDoubleHistogram = new SynchronizedDoubleHistogram(1);
    IntCountsHistogram other = new IntCountsHistogram(1);
    synchronizedDoubleHistogram.equals(other);
  }
}