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

namespace HdrHistogram.NET.Utilities
{
    // See http://stackoverflow.com/questions/1261543/equivalent-of-javas-bytebuffer-puttype-in-c-sharp
    // and http://stackoverflow.com/questions/18040012/what-is-the-equivalent-of-javas-bytebuffer-wrap-in-c
    // and http://stackoverflow.com/questions/1261543/equivalent-of-javas-bytebuffer-puttype-in-c-sharp
    // Java version http://docs.oracle.com/javase/7/docs/api/java/nio/ByteBuffer.html
    public class ByteBuffer
    {
        private readonly byte[] _internalBuffer;
        private int _position;

        public static ByteBuffer allocate(int bufferCapacity)
        {
            return new ByteBuffer(bufferCapacity);
        }

        private ByteBuffer(int bufferCapacity)
        {
            _internalBuffer = new byte[bufferCapacity];
            _position = 0;
            //for (int i = 0; i < _internalBuffer.Length; i++)
            //    _internalBuffer[i] = 255;
        }

        public int capacity()
        {
            return _internalBuffer.Length;
        }

        public void clear()
        {
            Array.Clear(_internalBuffer, 0, _internalBuffer.Length);
            _position = 0;
        }

        public int getInt()
        {
            var intValue = BitConverter.ToInt32(_internalBuffer, _position);
            _position += sizeof(int);
            return intValue;
        }

        public long getLong()
        {
            var longValue = BitConverter.ToInt64(_internalBuffer, _position);
            _position += sizeof(long);
            return longValue;
        }

        public void putInt(int value)
        {
            var intAsBytes = BitConverter.GetBytes(value);
            Array.Copy(intAsBytes, 0, _internalBuffer, _position, intAsBytes.Length);
            _position += intAsBytes.Length;
        }

        internal void putInt(int index, int value)
        {
            var intAsBytes = BitConverter.GetBytes(value);
            Array.Copy(intAsBytes, 0, _internalBuffer, index, intAsBytes.Length);
            // We don't increment the position here, to match the Java behaviour
        }

        public void putLong(long value)
        {
            var longAsBytes = BitConverter.GetBytes(value);
            Array.Copy(longAsBytes, 0, _internalBuffer, _position, longAsBytes.Length);
            _position += longAsBytes.Length;
        }

        internal byte[] array()
        {
            return _internalBuffer;
        }

        public void rewind()
        {
            _position = 0;
        }

        public void rewind(int position)
        {
            _position = position;
        }

        public int position()
        {
            return _position;
        }

        internal void blockCopy(Array src, int srcOffset, int dstOffset, int count)
        {
            Console.WriteLine("  Buffer.BlockCopy - Copying {0} bytes INTO internalBuffer, scrOffset = {1}, dstOffset = {2}", count, srcOffset, dstOffset);
            Buffer.BlockCopy(src: src, srcOffset: srcOffset, dst: _internalBuffer, dstOffset: dstOffset, count: count);
            _position += count;
        }

        internal void blockGet(Array dst, int dstOffset, int srcOffset, int count)
        {
            Console.WriteLine("  Buffer.BlockCopy - Copying {0} bytes FROM internalBuffer, scrOffset = {1}, dstOffset = {2}", count, srcOffset, dstOffset);
            Buffer.BlockCopy(src: _internalBuffer, srcOffset: srcOffset, dst: dst, dstOffset: dstOffset, count: count);
        }

        internal WrappedBuffer<short> asShortBuffer()
        {
            return WrappedBuffer<short>.create<short>(this, sizeof(short));
        }

        internal WrappedBuffer<int> asIntBuffer()
        {
            return WrappedBuffer<int>.create<int>(this, sizeof(int));
        }

        internal WrappedBuffer<long> asLongBuffer()
        {
            return WrappedBuffer<long>.create<long>(this, sizeof(long));
        }
    }
}