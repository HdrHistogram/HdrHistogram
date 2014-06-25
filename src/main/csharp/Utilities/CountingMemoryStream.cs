/*
 * Written by Matt Warren, and released to the public domain,
 * as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This is a .NET port of the original Java version, which was written by
 * Gil Tene as described in
 * https://github.com/HdrHistogram/HdrHistogram
 */

using System;
using System.IO;

namespace HdrHistogram.NET.Utilities
{
    internal class CountingMemoryStream : MemoryStream
    {
        public int BytesWritten { get; private set; }

        public CountingMemoryStream(byte[] buffer, int index, int count)
            : base(buffer, index, count)
        {
        }

        public override void Write(byte[] buffer, int offset, int count)
        {
            base.Write(buffer, offset, count);
            BytesWritten += count;
        }
    }
}
