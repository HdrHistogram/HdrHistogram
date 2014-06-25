/*
 * Written by Matt Warren, and released to the public domain,
 * as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This is a .NET port of the original Java version, which was written by
 * Gil Tene as described in
 * https://github.com/HdrHistogram/HdrHistogram
 */

using HdrHistogram.NET.Utilities;
using NUnit.Framework;
using System;

namespace HdrHistogram.NET.Test.Utilities
{
    public class MiscUtilitiesTest
    {
        static long[] TestNumbers = new long[]
                                        {
                                            //-1, long.MinValue, //MiscUtilities.numberOfLeadingZeros doesn't handle -ve numbers!!!
                                            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                                            1024,
                                            int.MaxValue,
                                            long.MaxValue - 1,
                                            long.MaxValue
                                        };

        [Test, TestCaseSource("TestNumbers")]
        public void testnumberOfLeadingZeros(long numberToTest)
        {
            Assert.AreEqual(numberOfLeadingZerosSLOW(numberToTest), MiscUtilities.numberOfLeadingZeros(numberToTest));
        }

        private int numberOfLeadingZerosSLOW(long value)
        {
            var valueAsText = Convert.ToString(value, 2);
            if (valueAsText.Contains("1") == false) //valueAsText.All(c => c == '0')) 
                valueAsText = string.Empty;
            var leadingZeros = 64 - valueAsText.Length;
            //Console.WriteLine("Value: {0} - \"{1}\", leading zeros = {2} length = {3}", value, valueAsText, leadingZeros, valueAsText.Length);
            return leadingZeros;
        }
    }
}
