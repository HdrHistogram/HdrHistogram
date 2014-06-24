/**
 * hdr_histogram_log.h
 * Written by Michael Barker and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * This code follows the Plan 9 approach to header declaration.  In order
 * to maintain fast builds does not define it's dependent headers.
 * They should be included manually by the user.  This code requires:
 *
 * - #include <stdint.h>
 * - #include <stdbool.h>
 * - #include <stdio.h>
 *
 * The source for the hdr_histogram utilises a few C99 constructs, specifically
 * the use of stdint/stdbool and inline variable declaration.
 *
 * The implementation makes use of zlib to provide compression.  You will need
 * to link against -lz in order to link applications that include this header.
 */

/**
 * Encode the hdr_histogram into the supplied buffer.  The
 * buffer should be >= than hdr_get_memory_size(hdr_histogram).
 *
 * @param h "This" pointer
 * @param buffer The memory space to encode this buffer into
 * @param length The length to encode up to.  This needs to be
 * >= the memory size of the hdr_histogram.
 * @return false if the hdr_histogram could not be encoded.
 */
size_t hdr_encode(struct hdr_histogram* h, uint8_t* buffer, int length);

/**
 * Decode the supplied buffer into the specified hdr_histogram.  If
 * the supplied hdr_histogram is NULL then this method will allocate
 * new hdr_histogram of the appropriate size.  The hdr_histogram to
 * decode the data into need not match the parameters of the original
 * histogram.  Values larger the that the max_value of the supplied
 * histogram will be discarded.
 *
 * @param buffer The data buffer to decode from.
 * @param length The amount to read from the buffer.
 * @param result The updated histogram
 * @return false if the hdr_histogram could not be decoded.
 */
bool hdr_decode(uint8_t* buffer, size_t length, struct hdr_histogram** result);

int hdr_encode_compressed(struct hdr_histogram* h, uint8_t* buffer, int length);
int hdr_decode_compressed(uint8_t* buffer, size_t length, struct hdr_histogram** result);
int32_t hdr_get_compressed_length(uint8_t* buffer);

int hdr_parse_log(FILE* log, struct hdr_histogram** result);

const char* hdr_strerror(int errnum);
