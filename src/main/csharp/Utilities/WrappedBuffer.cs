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
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace HdrHistogram.NET.Utilities
{
    // This needs to be a view on-top of a byte array
    class WrappedBuffer<T> where T : struct
    {
        private readonly ByteBuffer _underlyingBuffer;
        private readonly int _parentOffset;
        //private readonly int _wordSizeInBytes;

        public static WrappedBuffer<U> create<U>(ByteBuffer underlyingBuffer, int wordSizeInBytes) where U : struct
        {
            return new WrappedBuffer<U>(underlyingBuffer); //, wordSizeInBytes);
        }

        private WrappedBuffer(ByteBuffer underlyingBuffer) //, int wordSizeInBytes)
        {
            _underlyingBuffer = underlyingBuffer;
            _parentOffset = underlyingBuffer.position();
            //_wordSizeInBytes = wordSizeInBytes;
        }

        internal void put(T[] values, int index, int length)
        {
            //_underlyingBuffer.blockCopy(src: values, srcOffset: index, dstOffset: _parentOffset, count: length * _wordSizeInBytes);
            _underlyingBuffer.blockCopy(src: values, srcOffset: index, dstOffset: _parentOffset, count: length);
        }

        internal void get(T[] destination, int index, int length)
        {
            //_underlyingBuffer.blockGet(dst: destination, dstOffset: index, srcOffset: _parentOffset, count: length * _wordSizeInBytes);
            _underlyingBuffer.blockGet(dst: destination, dstOffset: index, srcOffset: _parentOffset, count: length);
        }

        internal void put(long value)
        {
            _underlyingBuffer.putLong(value);
        }

        internal long get()
        {
            return _underlyingBuffer.getLong();
        }

        internal void rewind()
        {
            _underlyingBuffer.rewind(_parentOffset);
        }
    }
}
