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

int hdr_decode_compressed(uint8_t* buffer, size_t length, struct hdr_histogram** result);

int hdr_parse_log(FILE* log, struct hdr_histogram** result);

const char* hdr_strerror(int errnum);
