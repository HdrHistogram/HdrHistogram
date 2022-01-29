<a href="https://foojay.io/works-with-openjdk"><img align="right" src="https://github.com/foojayio/badges/raw/main/works_with_openjdk/Works-with-OpenJDK.png" width="100"></a>

HdrHistogram
----------------------------------------------
[![Gitter](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/HdrHistogram/HdrHistogram?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/HdrHistogram/HdrHistogram.svg?branch=master)](https://travis-ci.org/HdrHistogram/HdrHistogram)
[![Java CI](https://github.com/hdrhistogram/hdrhistogram/workflows/Java%20CI/badge.svg)](https://github.com/hdrhistogram/hdrhistogram/actions)
[![Javadocs](http://www.javadoc.io/badge/org.hdrhistogram/HdrHistogram.svg)](http://www.javadoc.io/doc/org.hdrhistogram/HdrHistogram)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/HdrHistogram/HdrHistogram.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/HdrHistogram/HdrHistogram/alerts/)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/HdrHistogram/HdrHistogram.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/HdrHistogram/HdrHistogram/context:java)
----------------------------------------------------------------------------
HdrHistogram: A High Dynamic Range (HDR) Histogram

This repository currently includes a Java implementation of
HdrHistogram. C, C#/.NET, Python, Javascript, Rust, Erlang, and Go ports
can be found in other repositories. All of which share common concepts
and data representation capabilities. Look at repositories under the
[HdrHistogram organization](https://github.com/HdrHistogram) for various
implementations and useful tools.

Note: The below is an excerpt from a Histogram JavaDoc. While much
of it generally applies to other language implementations as well,
some details may vary by implementation (e.g. iteration and
synchronization), so you should consult the documentation or header
information of the specific API library you intend to use.

----------------------------------------------

HdrHistogram supports the recording and analyzing of sampled data value
counts across a configurable integer value range with configurable value
precision within the range. Value precision is expressed as the number of
significant digits in the value recording, and provides control over value
quantization behavior across the value range and the subsequent value
resolution at any given level.

For example, a Histogram could be configured to track the counts of
observed integer values between 0 and 3,600,000,000 while maintaining a
value precision of 3 significant digits across that range. Value
quantization within the range will thus be no larger than 1/1,000th
(or 0.1%) of any value. This example Histogram could be used to track and
analyze the counts of observed response times ranging between 1 microsecond
and 1 hour in magnitude, while maintaining a value resolution of 1
microsecond up to 1 millisecond, a resolution of 1 millisecond (or better)
up to one second, and a resolution of 1 second (or better) up to 1,000
seconds. At its maximum tracked value (1 hour), it would still maintain a
resolution of 3.6 seconds (or better).

The HdrHistogram package includes the Histogram implementation, which tracks
value counts in long fields, and is expected to be the commonly used
Histogram form. IntHistogram and ShortHistogram, which track value counts in
int and short fields respectively, are provided for use cases where smaller
count ranges are practical and smaller overall storage is beneficial.

HdrHistogram is designed for recording histograms of value measurements in
latency and performance sensitive applications. Measurements show value
recording times as low as 3-6 nanoseconds on modern (circa 2012) Intel CPUs.
AbstractHistogram maintains a fixed cost in both space and time. A
Histogram's memory footprint is constant, with no allocation operations
involved in recording data values or in iterating through them. The memory
footprint is fixed regardless of the number of data value samples recorded,
and depends solely on the dynamic range and precision chosen. The amount of
work involved in recording a sample is constant, and directly computes
storage index locations such that no iteration or searching is ever involved
in recording data values.

A combination of high dynamic range and precision is useful for collection
and accurate post-recording analysis of sampled value data distribution in
various forms. Whether it's calculating or plotting arbitrary percentiles,
iterating through and summarizing values in various ways, or deriving mean
and standard deviation values, the fact that the recorded data information
is kept in high resolution allows for accurate post-recording analysis with
low [and ultimately configurable] loss in accuracy when compared to
performing the same analysis directly on the potentially infinite series of
sourced data values samples.

A common use example of HdrHistogram would be to record response times
in units of microseconds across a dynamic range stretching from 1 usec to
over an hour, with a good enough resolution to support later performing
post-recording analysis on the collected data. Analysis can include
computing, examining, and reporting of distribution by percentiles, linear
or logarithmic value buckets, mean and standard deviation, or by any other
means that can be easily added by using the various iteration techniques
supported by the Histogram.
In order to facilitate the accuracy needed for various post-recording
analysis techniques, this example can maintain a resolution of ~1 usec
or better for times ranging to ~2 msec in magnitude, while at the same time
maintaining a resolution of ~1 msec or better for times ranging to ~2 sec,
and a resolution of ~1 second or better for values up to 2,000 seconds.
This sort of example resolution can be thought of as "always accurate to 3
decimal points." Such an example Histogram would simply be created with a
highestTrackableValue of 3,600,000,000, and a numberOfSignificantValueDigits
of 3, and would occupy a fixed, unchanging memory footprint of around 185KB
(see "Footprint estimation" below).


Histogram variants and internal representation
----------------------------------------------

The HdrHistogram package includes multiple implementations of the
`AbstractHistogram` class:
- `Histogram`, which is the commonly used Histogram form and tracks
  value counts in long fields.
- `IntHistogram` and `ShortHistogram`, which track value counts in int
  and short fields respectively, are provided for use cases where
  smaller count ranges are practical and smaller overall storage
  is beneficial (e.g. systems where tens of thousands of in-memory
  histogram are being tracked).
- `AtomicHistogram` and `SynchronizedHistogram` (see 'Synchronization 
  and concurrent access' below)

Internally, data in HdrHistogram variants is maintained using a concept
somewhat similar to that of floating point number representation: Using an 
exponent a (non-normalized) mantissa to support a wide dynamic range at
a high but varying (by exponent value) resolution. AbstractHistogram uses
exponentially increasing bucket value ranges (the parallel of the exponent
portion of a floating point number) with each bucket containing a fixed
number (per bucket) set of linear sub-buckets (the parallel of a non-normalized
mantissa portion of a floating point number). Both dynamic range and resolution
are configurable, with highestTrackableValue controlling dynamic range, and
numberOfSignificantValueDigits controlling resolution.

Synchronization and concurrent access
----------------------------------------------

In the interest of keeping value recording cost to a minimum, the commonly
used Histogram class and it's IntHistogram and ShortHistogram variants are
NOT internally synchronized, and do NOT use atomic variables. Callers
wishing to make potentially concurrent, multi-threaded updates or queries
against Histogram objects should either take care to externally synchronize
and/or order their access, or use the ConcurrentHistogram, AtomicHistogram,
or SynchronizedHistogram or variants.

A common pattern seen in histogram value recording involves recording values in
a critical path (multi-threaded or not), coupled with a non-critical path
reading the recorded data for summary/reporting purposes. When such continuous 
non-blocking recording operation (concurrent or not) is desired even when
sampling, analyzing, or reporting operations are needed, consider using
the Recorder and SingleWriterRecorder recorder variants that were specifically
designed for that purpose. Recorders provide a recording API similar to
Histogram, and internally maintain and coordinate active/inactive histograms
such that recording remains wait-free in the presence of accurate and stable
interval sampling.

It is worth mentioning that since Histogram objects are additive, it is
common practice to use per-thread non-synchronized histograms or
SingleWriterRecorders, and use a summary/reporting thread to perform
histogram aggregation math across time and/or threads.  


Iteration
----------------------------------------------

Histograms support multiple convenient forms of iterating through the
histogram data set, including linear, logarithmic, and percentile iteration
mechanisms, as well as means for iterating through each recorded value or
each possible value level. The iteration mechanisms are accessible through
the HistogramData available through `getHistogramData()`.
Iteration mechanisms all provide HistogramIterationValue data points along
the histogram's iterated data set, and are available for the default
(corrected) histogram data set via the following HistogramData methods:

 - `percentiles`: An `Iterable<HistogramIterationValue>` through the histogram
                using a PercentileIterator
 - `linearBucketValues`: An `Iterable<HistogramIterationValue>` through the
                histogram using a LinearIterator
 - `logarithmicBucketValues`: An `Iterable<HistogramIterationValue>` through
                the histogram using a LogarithmicIterator
 - `recordedValues`: An `Iterable<HistogramIterationValue>` through the
                histogram using a RecordedValuesIterator
 - `allValues`: An `Iterable<HistogramIterationValue>` through the histogram
                using a AllValuesIterator

Iteration is typically done with a for-each loop statement. E.g.:

``` java
 for (HistogramIterationValue v :
      histogram.getHistogramData().percentiles(ticksPerHalfDistance)) {
     ...
 }
```

 or

``` java
 for (HistogramIterationValue v :
      histogram.getRawHistogramData().linearBucketValues(unitsPerBucket)) {
     ...
 }
```

The iterators associated with each iteration method are resettable, such
that a caller that would like to avoid allocating a new iterator object for
each iteration loop can re-use an iterator to repeatedly iterate through
the histogram. This iterator re-use usually takes the form of a traditional
for loop using the Iterator's `hasNext()` and `next()` methods.

So to avoid allocating a new iterator object for each iteration loop:

``` java
 PercentileIterator iter =
    histogram.getHistogramData().percentiles().iterator(ticksPerHalfDistance);
 ...
 iter.reset(percentileTicksPerHalfDistance);
 for (iter.hasNext() {
     HistogramIterationValue v = iter.next();
     ...
 }
```

Equivalent Values and value ranges
----------------------------------------------

Due to the finite (and configurable) resolution of the histogram, multiple
adjacent integer data values can be "equivalent". Two values are considered
"equivalent" if samples recorded for both are always counted in a common
total count due to the histogram's resolution level. HdrHistogram provides
methods for determining the lowest and highest equivalent values for any
given value, as well as determining whether two values are equivalent, and
for finding the next non-equivalent value for a given value (useful when
looping through values, in order to avoid a double-counting count).

Corrected vs. Raw value recording calls
----------------------------------------------

In order to support a common use case needed when histogram values are used
to track response time distribution, Histogram provides for the recording
of corrected histogram value by supporting a `recordValueWithExpectedInterval()`
variant is provided. This value recording form is useful in [common latency
measurement] scenarios where response times may exceed the expected interval
between issuing requests, leading to "dropped" response time measurements
that would typically correlate with "bad" results.

When a value recorded in the histogram exceeds the
expectedIntervalBetweenValueSamples parameter, recorded histogram data will
reflect an appropriate number of additional values, linearly decreasing in
steps of expectedIntervalBetweenValueSamples, down to the last value that
would still be higher than expectedIntervalBetweenValueSamples.

To illustrate why this corrective behavior is critically needed in order
to accurately represent value distribution when large value measurements
may lead to missed samples, imagine a system for which response times
samples are taken once every 10 msec to characterize response time
distribution. The hypothetical system behaves "perfectly" for 100 seconds
(10,000 recorded samples), with each sample showing a 1msec response time
value. At each sample for 100 seconds (10,000 logged samples at 1 msec
each). The hypothetical system then encounters a 100 sec pause during which
only a single sample is recorded (with a 100 second value).
The raw data histogram collected for such a hypothetical system (over the
200 second scenario above) would show ~99.99% of results at 1 msec or below,
which is obviously "not right". The same histogram, corrected with the
knowledge of an expectedIntervalBetweenValueSamples of 10msec will correctly
represent the response time distribution. Only ~50% of results will be at
1 msec or below, with the remaining 50% coming from the auto-generated value
records covering the missing increments spread between 10msec and 100 sec.

Data sets recorded with and without an expectedIntervalBetweenValueSamples
parameter will differ only if at least one value recorded with the recordValue
method was greater than its associated expectedIntervalBetweenValueSamples
parameter.
Data sets recorded with an expectedIntervalBetweenValueSamples parameter will
be identical to ones recorded without it if all values recorded via the
recordValue calls were smaller than their associated (and optional)
expectedIntervalBetweenValueSamples parameters.

When used for response time characterization, the recording with the optional
expectedIntervalBetweenValueSamples parameter will tend to produce data sets
that would much more accurately reflect the response time distribution that a
random, uncoordinated request would have experienced.

Footprint estimation
----------------------------------------------

Due to its dynamic range representation, Histogram is relatively efficient
in memory space requirements given the accuracy and dynamic range it covers.
Still, it is useful to be able to estimate the memory footprint involved
for a given highestTrackableValue and numberOfSignificantValueDigits
combination. Beyond a relatively small fixed-size footprint used for internal
fields and stats (which can be estimated as "fixed at well less than 1KB"),
the bulk of a Histogram's storage is taken up by its data value recording
counts array. The total footprint can be conservatively estimated by:

``` java
 largestValueWithSingleUnitResolution =
        2 * (10 ^ numberOfSignificantValueDigits);
 subBucketSize =
        roundedUpToNearestPowerOf2(largestValueWithSingleUnitResolution);

 expectedHistogramFootprintInBytes = 512 +
      ({primitive type size} / 2) *
      (log2RoundedUp((highestTrackableValue) / subBucketSize) + 2) *
      subBucketSize
```

A conservative (high) estimate of a Histogram's footprint in bytes is
available via the `getEstimatedFootprintInBytes()` method.
