/*
 * Written by Matt Warren, and released to the public domain,
 * as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This is a .NET port of the original Java version, which was written by
 * Gil Tene as described in
 * https://github.com/HdrHistogram/HdrHistogram
 */

namespace HdrHistogram.NET.Utilities
{
    public static class MiscUtilities
    {
        // Code from http://stackoverflow.com/questions/9543410/i-dont-think-numberofleadingzeroslong-i-in-long-java-is-based-floorlog2x/9543537#9543537
        public static int numberOfLeadingZeros(long value)
        {
            // HD, Figure 5-6
            if (value == 0)
                return 64;
            int n = 1;
            // >>> in Java is a "unsigned bit shift", to do the same in C# we use >> (but it HAS to be an unsigned int)
            uint x = (uint)(value >> 32);
            if (x == 0) { n += 32; x = (uint)value; }
            if (x >> 16 == 0) { n += 16; x <<= 16; }
            if (x >> 24 == 0) { n += 8; x <<= 8; }
            if (x >> 28 == 0) { n += 4; x <<= 4; }
            if (x >> 30 == 0) { n += 2; x <<= 2; }
            n -= (int)(x >> 31);
            return n;
        }
    }
}
